package kim.jujin.fixedorder.sample

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import kim.jujin.fixedorder.FixedOrderStaggeredGridLayoutManager
import kotlin.math.roundToInt

private const val TAG = "FixedOrderComplex"

private const val DAEMONAPP_NOISE_MODE = true

private const val USE_GRID_LAYOUT_MANAGER = false
private const val OUTER_SPAN_COUNT = 2
private const val INNER_RV_COUNT = 2
private const val FIX_INNER_RV_HEIGHT = false
private const val DISABLE_INNER_PREFETCH = false
private const val DISABLE_INNER_ITEM_ANIMATOR = false
private const val DISABLE_INNER_FOCUS = false
private const val USE_DUMMY_VIEW_INSTEAD_OF_INNER_RV = false
private const val VIEWPAGER_OFFSCREEN_LIMIT = -1

private const val USE_ASYNC_SUBMIT_LIST = true
private const val FORCE_INNER_REQUEST_LAYOUT = true
private const val FORCE_OUTER_REQUEST_LAYOUT = true
private const val USE_INNER_ITEM_DECORATION = true
private const val USE_INNER_SNAP_HELPER = true
private const val ENABLE_STAGGERED_REQUEST_LAYOUT_CHURN = true
private const val ENABLE_ACCESSIBILITY_CHURN = true
private const val ENABLE_INNER_ITEM_ALPHA_ANIMATION = true

private const val INNER_ATTACH_ADAPTER_DELAY_MS = 16L
private const val INNER_SUBMIT_LIST_DELAY_MS = 32L
private const val INNER_PAYLOAD_UPDATE_DELAY_MS = 64L
private const val ACCESSIBILITY_UPDATE_DELAY_MS = 48L

private const val PAGE_COUNT = 3
private const val OUTER_ITEM_COUNT = 15
private const val OUTER_ITEM_DEFAULT_HEIGHT_DP = 100
private const val OUTER_ITEM_TALL_HEIGHT_DP = 210
private const val OUTER_ITEM_EXTRA_TALL_HEIGHT_DP = 320
private const val INNER_CHILD_COUNT = 5
private const val INNER_CHILD_MIN_HEIGHT_DP = 80
private const val INNER_CHILD_MAX_HEIGHT_DP = 96
private const val OUTER_ITEM_GAP_DP = 10
private const val INNER_ITEM_GAP_DP = 8
private const val INNER_VIEW_TYPE = 0
private const val EXPANDED_TITLE_SP = 15f
private const val COLLAPSED_TITLE_SP = 20f
private const val TITLE_ANIMATION_MS = 500L

/*
Manual reproduction:
1. Launch Complex Test. Page 0 starts with the AppBar fully expanded and the outer list at top.
2. Do not swipe pages first. Fast fling upward from the top so AppBar collapses and outer RV starts scrolling.
3. Watch logcat for NEGATIVE_DY and visible inner A/B state.
4. Repeat after the first occurrence, then swipe to Page 1 and back to Page 0 and compare.
*/
class ComplexTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_complex_test)

        findViewById<ViewPager2>(R.id.complex_pager).apply {
            adapter = ComplexPageAdapter()
            currentItem = 0
            if (VIEWPAGER_OFFSCREEN_LIMIT >= 0) {
                offscreenPageLimit = VIEWPAGER_OFFSCREEN_LIMIT
            }
        }
    }

    private class ComplexPageAdapter : RecyclerView.Adapter<PageHolder>() {
        override fun getItemCount(): Int = PAGE_COUNT

        override fun getItemViewType(position: Int): Int = position

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_complex_page, parent, false)
            return PageHolder(view)
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            holder.page.bind(position)
        }
    }

    private class PageHolder(view: View) : RecyclerView.ViewHolder(view) {
        val page = ComplexPageController(view)
    }
}

private class ComplexPageController(private val root: View) {
    private var pageIndex: Int = 0
    private var appBarOffset: Int = 0
    private var titleTargetSizeSp: Float = EXPANDED_TITLE_SP
    private var titleAnimator: ValueAnimator? = null
    private var outerScrollState: Int = RecyclerView.SCROLL_STATE_IDLE

    private val titleView: TextView = root.findViewById(R.id.complex_toolbar_title)
    private val appBarLayout: AppBarLayout = root.findViewById(R.id.complex_app_bar)
    private val outerRecyclerView: RecyclerView = root.findViewById(R.id.complex_outer_recycler)
    private val outerAdapter = OuterAdapter()

    init {
        outerRecyclerView.adapter = outerAdapter
        outerRecyclerView.layoutManager = if (USE_GRID_LAYOUT_MANAGER) {
            GridLayoutManager(root.context, OUTER_SPAN_COUNT)
        } else {
            LoggingFixedOrderLayoutManager(OUTER_SPAN_COUNT) { pageIndex }
        }
        outerRecyclerView.addItemDecoration(OuterGapDecoration(root.context.dp(OUTER_ITEM_GAP_DP)))
        outerRecyclerView.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                logD(pageIndex, "OuterRV child attached pos=${outerRecyclerView.getChildAdapterPosition(view)}")
            }

            override fun onChildViewDetachedFromWindow(view: View) {
                logD(pageIndex, "OuterRV child detached pos=${outerRecyclerView.getChildAdapterPosition(view)}")
            }
        })

        appBarLayout.addOnOffsetChangedListener { appBar, verticalOffset ->
            appBarOffset = verticalOffset
            logD(pageIndex, "AppBar offset=$verticalOffset total=${appBar.totalScrollRange}")
            animateTitleSize(if (verticalOffset == 0) EXPANDED_TITLE_SP else COLLAPSED_TITLE_SP)
        }

        outerRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                outerScrollState = newState
                logD(pageIndex, "OuterRV state=${scrollStateName(newState)}")
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                logD(pageIndex, "OuterRV onScrolled dx=$dx dy=$dy")
                if (dy < 0) {
                    logW(
                        pageIndex,
                        "NEGATIVE_DY dy=$dy state=${scrollStateName(outerScrollState)} " +
                            "appBarOffset=$appBarOffset titleSizeSp=${titleSizeSp()} ${visibleChildLog()}",
                    )
                }
            }
        })
    }

    fun bind(pageIndex: Int) {
        this.pageIndex = pageIndex
        titleView.text = "Page $pageIndex Toolbar Title"
        outerAdapter.bindPage(pageIndex)
        appBarLayout.setExpanded(true, false)
        outerRecyclerView.scrollToPosition(0)
        outerRecyclerView.post {
            logD(pageIndex, "Outer first layout ${visibleChildLog()}")
        }
    }

    private fun animateTitleSize(targetSizeSp: Float) {
        if (titleTargetSizeSp == targetSizeSp) return
        titleTargetSizeSp = targetSizeSp
        titleAnimator?.cancel()

        val startSizeSp = titleSizeSp()
        logD(pageIndex, "Title targetSize=$targetSizeSp startSize=$startSizeSp")

        titleAnimator = ValueAnimator.ofFloat(startSizeSp, targetSizeSp).apply {
            duration = TITLE_ANIMATION_MS
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, value)
            }
            start()
        }
    }

    private fun titleSizeSp(): Float = titleView.textSize / root.resources.displayMetrics.scaledDensity

    private fun visibleChildLog(): String {
        val lm = outerRecyclerView.layoutManager
        val visible = buildString {
            append("visibleOuter=[")
            for (i in 0 until outerRecyclerView.childCount) {
                if (i > 0) append(", ")
                val child = outerRecyclerView.getChildAt(i)
                val position = outerRecyclerView.getChildAdapterPosition(child)
                val top = lm?.getDecoratedTop(child) ?: child.top
                val bottom = lm?.getDecoratedBottom(child) ?: child.bottom
                append("$position:$top..$bottom")
            }
            append("]")
        }
        return "$visible ${innerBoundsLog(1, "A")} ${innerBoundsLog(2, "B")}"
    }

    private fun innerBoundsLog(position: Int, label: String): String {
        val holder = outerRecyclerView.findViewHolderForAdapterPosition(position)
        val lm = outerRecyclerView.layoutManager
        val state = outerAdapter.innerState(position)
        return if (holder == null || !outerAdapter.isInnerPosition(position)) {
            "inner$label=notVisible state=${state.toShortLog()}"
        } else {
            val top = lm?.getDecoratedTop(holder.itemView) ?: holder.itemView.top
            val bottom = lm?.getDecoratedBottom(holder.itemView) ?: holder.itemView.bottom
            "inner$label top=$top bottom=$bottom state=${state.toShortLog()}"
        }
    }
}

private class OuterAdapter : RecyclerView.Adapter<OuterHolder>() {
    private val sharedInnerPool = RecyclerView.RecycledViewPool().apply {
        setMaxRecycledViews(INNER_VIEW_TYPE, 10)
    }
    private val innerStates = mutableMapOf<Int, InnerRuntimeState>()
    private var pageIndex: Int = 0

    fun bindPage(pageIndex: Int) {
        this.pageIndex = pageIndex
        innerStates.clear()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = OUTER_ITEM_COUNT

    override fun getItemViewType(position: Int): Int = position

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OuterHolder {
        return if (isInnerPosition(viewType)) {
            InnerOuterHolder(parent, viewType)
        } else {
            NormalOuterHolder(parent, viewType)
        }
    }

    override fun onBindViewHolder(holder: OuterHolder, position: Int) {
        if (holder is InnerOuterHolder) {
            holder.bindInner(pageIndex, position, getItemViewType(position), sharedInnerPool, innerState(position))
        } else {
            holder.bind(pageIndex, position, getItemViewType(position), isInnerPosition(position))
        }
    }

    fun isInnerPosition(position: Int): Boolean {
        return position == 1 || (INNER_RV_COUNT >= 2 && position == 2)
    }

    fun innerState(position: Int): InnerRuntimeState = innerStates.getOrPut(position) { InnerRuntimeState() }
}

private data class InnerRuntimeState(
    var adapterAttached: Boolean = false,
    var submitCompleted: Boolean = false,
    var payloadCompleted: Boolean = false,
) {
    fun reset() {
        adapterAttached = false
        submitCompleted = false
        payloadCompleted = false
    }

    fun toShortLog(): String {
        return "attached=$adapterAttached submitted=$submitCompleted payload=$payloadCompleted"
    }
}

private abstract class OuterHolder(root: View) : RecyclerView.ViewHolder(root) {
    abstract fun bind(pageIndex: Int, position: Int, viewType: Int, isInner: Boolean)
}

private class NormalOuterHolder(parent: ViewGroup, viewType: Int) : OuterHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.item_complex_outer_normal, parent, false),
) {
    private val label = itemView as TextView

    init {
        setOuterLayoutParams(viewType)
        itemView.setBackgroundColor(colorForViewType(viewType))
    }

    override fun bind(pageIndex: Int, position: Int, viewType: Int, isInner: Boolean) {
        setOuterLayoutParams(position)
        itemView.setBackgroundColor(colorForViewType(viewType))
        itemView.contentDescription = "Page=$pageIndex outer=$position normal"
        label.text =
            "Page $pageIndex / Outer item $position / viewType $viewType / normal / ${outerItemHeightDp(position)}dp"
    }

    private fun setOuterLayoutParams(position: Int) {
        itemView.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            itemView.context.dp(outerItemHeightDp(position)),
        )
    }
}

private class InnerOuterHolder(parent: ViewGroup, viewType: Int) : OuterHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.item_complex_outer_inner, parent, false),
) {
    private val labelView: TextView = itemView.findViewById(R.id.complex_inner_label)
    private val contentHost: FrameLayout = itemView.findViewById(R.id.complex_inner_content_host)
    private var layoutLogged = false
    private var bindToken = 0

    init {
        setOuterLayoutParams(viewType)
        itemView.setBackgroundColor(colorForViewType(viewType))
    }

    override fun bind(pageIndex: Int, position: Int, viewType: Int, isInner: Boolean) = Unit

    fun bindInner(
        pageIndex: Int,
        position: Int,
        viewType: Int,
        sharedPool: RecyclerView.RecycledViewPool,
        state: InnerRuntimeState,
    ) {
        bindToken++
        val token = bindToken
        state.reset()
        setOuterLayoutParams(position)
        itemView.setBackgroundColor(colorForViewType(viewType))

        val innerLabel = if (position == 1) "A" else "B"
        labelView.text =
            "Page $pageIndex / Outer item $position / viewType $viewType / INNER RV $innerLabel / ${outerItemHeightDp(position)}dp"
        itemView.contentDescription = "Page=$pageIndex outer=$position inner=$innerLabel"

        if (effectiveAccessibilityChurn()) {
            itemView.postDelayedChecked(token, ACCESSIBILITY_UPDATE_DELAY_MS) {
                itemView.contentDescription = "updated page=$pageIndex outer=$position inner=$innerLabel"
                logD(pageIndex, "Inner$innerLabel outer contentDescription updated pos=$position")
            }
        }

        contentHost.removeAllViews()
        layoutLogged = false
        if (USE_DUMMY_VIEW_INSTEAD_OF_INNER_RV) {
            contentHost.addView(createDummyInnerView(pageIndex, position, innerLabel))
        } else {
            contentHost.addView(createInnerRecyclerView(pageIndex, position, innerLabel, sharedPool, state, token))
        }
    }

    private fun setOuterLayoutParams(position: Int) {
        itemView.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            itemView.context.dp(outerItemHeightDp(position)),
        )
    }

    private fun createDummyInnerView(pageIndex: Int, outerPosition: Int, innerLabel: String): View {
        val view = LayoutInflater.from(itemView.context)
            .inflate(R.layout.view_complex_inner_dummy, contentHost, false) as TextView
        view.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            itemView.context.dp(INNER_CHILD_MIN_HEIGHT_DP),
            Gravity.CENTER_VERTICAL,
        )
        view.text = "P$pageIndex outer$outerPosition dummy $innerLabel"
        return view
    }

    private fun createInnerRecyclerView(
        pageIndex: Int,
        outerPosition: Int,
        innerLabel: String,
        sharedPool: RecyclerView.RecycledViewPool,
        state: InnerRuntimeState,
        token: Int,
    ): RecyclerView {
        val manager = LinearLayoutManager(itemView.context, RecyclerView.HORIZONTAL, false).apply {
            if (DISABLE_INNER_PREFETCH) {
                isItemPrefetchEnabled = false
            }
        }
        val adapter = InnerAdapter(pageIndex, outerPosition, innerLabel)

        return (LayoutInflater.from(itemView.context)
            .inflate(R.layout.view_complex_inner_recycler, contentHost, false) as RecyclerView).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                if (FIX_INNER_RV_HEIGHT) itemView.context.dp(INNER_CHILD_MAX_HEIGHT_DP) else ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER_VERTICAL,
            )
            layoutManager = manager
            setRecycledViewPool(sharedPool)
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            if (effectiveInnerDecoration()) {
                addItemDecoration(HorizontalGapDecoration(itemView.context.dp(INNER_ITEM_GAP_DP)))
            }
            if (DISABLE_INNER_ITEM_ANIMATOR) {
                itemAnimator = null
            }
            if (DISABLE_INNER_FOCUS) {
                isFocusable = false
                isFocusableInTouchMode = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
            if (effectiveInnerSnapHelper()) {
                PagerSnapHelper().attachToRecyclerView(this)
            }
            addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                val first = !layoutLogged
                layoutLogged = true
                logD(
                    pageIndex,
                    "InnerRV $innerLabel layout first=$first outer=$outerPosition top=$top bottom=$bottom " +
                        "oldTop=$oldTop oldBottom=$oldBottom width=${right - left} height=${bottom - top} " +
                        "oldWidth=${oldRight - oldLeft}",
                )
            }
            addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {
                    logD(pageIndex, "InnerRV $innerLabel child attached pos=${getChildAdapterPosition(view)}")
                }

                override fun onChildViewDetachedFromWindow(view: View) {
                    logD(pageIndex, "InnerRV $innerLabel child detached pos=${getChildAdapterPosition(view)}")
                }
            })

            scheduleInnerAdapterLifecycle(pageIndex, outerPosition, innerLabel, adapter, state, token, this)
            scheduleLayoutChurn(pageIndex, outerPosition, innerLabel, token, this)
        }
    }

    private fun scheduleInnerAdapterLifecycle(
        pageIndex: Int,
        outerPosition: Int,
        innerLabel: String,
        adapter: InnerAdapter,
        state: InnerRuntimeState,
        token: Int,
        innerRv: RecyclerView,
    ) {
        val initialItems = buildInnerItems(pageIndex, outerPosition, updated = false)
        val updatedItems = buildInnerItems(pageIndex, outerPosition, updated = true)

        if (!DAEMONAPP_NOISE_MODE) {
            innerRv.adapter = adapter
            state.adapterAttached = true
            adapter.submitList(initialItems) {
                state.submitCompleted = true
            }
            return
        }

        innerRv.postDelayedChecked(token, INNER_ATTACH_ADAPTER_DELAY_MS) {
            innerRv.adapter = adapter
            state.adapterAttached = true
            logD(pageIndex, "Inner$innerLabel adapter attached outer=$outerPosition")
            requestLayoutWithLog(pageIndex, innerLabel, "adapterAttach", innerRv)
        }

        innerRv.postDelayedChecked(token, INNER_SUBMIT_LIST_DELAY_MS) {
            if (effectiveAsyncSubmitList()) {
                adapter.submitList(initialItems) {
                    state.submitCompleted = true
                    logD(pageIndex, "Inner$innerLabel submitList initial complete outer=$outerPosition")
                }
            } else {
                adapter.submitList(initialItems)
                state.submitCompleted = true
                logD(pageIndex, "Inner$innerLabel submitList initial synchronous outer=$outerPosition")
            }
            requestLayoutWithLog(pageIndex, innerLabel, "submitList", innerRv)
        }

        innerRv.postDelayedChecked(token, INNER_PAYLOAD_UPDATE_DELAY_MS) {
            adapter.submitList(updatedItems) {
                state.payloadCompleted = true
                logD(pageIndex, "Inner$innerLabel payload submit complete outer=$outerPosition")
            }
            requestLayoutWithLog(pageIndex, innerLabel, "payload", innerRv)
        }
    }

    private fun scheduleLayoutChurn(
        pageIndex: Int,
        outerPosition: Int,
        innerLabel: String,
        token: Int,
        innerRv: RecyclerView,
    ) {
        if (!effectiveStaggeredRequestLayoutChurn()) return
        val delays = if (innerLabel == "A") longArrayOf(24L, 72L) else longArrayOf(40L, 88L)
        for (delay in delays) {
            innerRv.postDelayedChecked(token, delay) {
                requestLayoutWithLog(pageIndex, innerLabel, "churn${delay}ms outer=$outerPosition", innerRv)
            }
        }
    }

    private fun requestLayoutWithLog(pageIndex: Int, innerLabel: String, reason: String, innerRv: RecyclerView) {
        if (FORCE_OUTER_REQUEST_LAYOUT) {
            itemView.requestLayout()
            logD(pageIndex, "Inner$innerLabel outer item requestLayout reason=$reason")
        }
        if (FORCE_INNER_REQUEST_LAYOUT) {
            innerRv.requestLayout()
            logD(pageIndex, "Inner$innerLabel innerRv requestLayout reason=$reason")
        }
    }

    private fun View.postDelayedChecked(token: Int, delayMillis: Long, action: () -> Unit) {
        postDelayed({
            if (token == bindToken) {
                action()
            }
        }, delayMillis)
    }
}

private class InnerAdapter(
    private val pageIndex: Int,
    private val outerPosition: Int,
    private val innerLabel: String,
) : ListAdapter<InnerItem, InnerHolder>(InnerItemDiff) {
    override fun getItemViewType(position: Int): Int = INNER_VIEW_TYPE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InnerHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_complex_inner_text, parent, false)
        return InnerHolder(view)
    }

    override fun onBindViewHolder(holder: InnerHolder, position: Int) {
        holder.bind(getItem(position), payloadChanged = false)
    }

    override fun onBindViewHolder(holder: InnerHolder, position: Int, payloads: MutableList<Any>) {
        holder.bind(getItem(position), payloadChanged = payloads.isNotEmpty())
    }
}

private class InnerHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val icon: View = view.findViewById(R.id.complex_inner_icon)
    private val title: TextView = view.findViewById(R.id.complex_inner_title)
    private val subtitle: TextView = view.findViewById(R.id.complex_inner_subtitle)
    private val value: TextView = view.findViewById(R.id.complex_inner_value)
    private val badge: TextView = view.findViewById(R.id.complex_inner_badge)

    fun bind(item: InnerItem, payloadChanged: Boolean) {
        itemView.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        itemView.minimumHeight = itemView.context.dp(INNER_CHILD_MIN_HEIGHT_DP)
        itemView.contentDescription =
            if (payloadChanged) "updated ${item.accessibilityLabel}" else item.accessibilityLabel
        title.text = item.title
        subtitle.text = item.subtitle
        value.text = item.value
        badge.text = item.badge
        badge.visibility = if (item.badgeVisible) View.VISIBLE else View.INVISIBLE
        icon.setBackgroundColor(if (item.badgeVisible) Color.rgb(255, 193, 7) else Color.rgb(144, 202, 249))

        if (effectiveInnerItemAlphaAnimation()) {
            itemView.alpha = 0.95f
            itemView.translationY = itemView.context.dp(1).toFloat()
            itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(120L)
                .start()
        }
    }
}

private data class InnerItem(
    val id: Int,
    val pageIndex: Int,
    val outerPosition: Int,
    val innerIndex: Int,
    val title: String,
    val subtitle: String,
    val value: String,
    val badge: String,
    val badgeVisible: Boolean,
) {
    val accessibilityLabel: String =
        "P$pageIndex outer$outerPosition inner$innerIndex $title $value"
}

private object InnerItemDiff : DiffUtil.ItemCallback<InnerItem>() {
    override fun areItemsTheSame(oldItem: InnerItem, newItem: InnerItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: InnerItem, newItem: InnerItem): Boolean {
        return oldItem == newItem
    }

    override fun getChangePayload(oldItem: InnerItem, newItem: InnerItem): Any {
        return "payload"
    }
}

private class LoggingFixedOrderLayoutManager(
    spanCount: Int,
    private val pageIndexProvider: () -> Int,
) : FixedOrderStaggeredGridLayoutManager(spanCount) {
    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        logD(
            pageIndexProvider(),
            "LM onLayoutChildren entry itemCount=${state.itemCount} childCount=$childCount visible=${visibleChildren()}",
        )
        super.onLayoutChildren(recycler, state)
        logD(
            pageIndexProvider(),
            "LM onLayoutChildren exit childCount=$childCount offset=${computeVerticalScrollOffset(state)} visible=${visibleChildren()}",
        )
    }

    override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        val consumed = super.scrollVerticallyBy(dy, recycler, state)
        logD(
            pageIndexProvider(),
            "LM scrollVerticallyBy dy=$dy consumed=$consumed childCount=$childCount " +
                "offset=${computeVerticalScrollOffset(state)} visible=${visibleChildren()}",
        )
        return consumed
    }

    private fun visibleChildren(): String {
        return buildString {
            append("[")
            for (i in 0 until childCount) {
                if (i > 0) append(", ")
                val child = getChildAt(i) ?: continue
                append("${getPosition(child)}:${getDecoratedTop(child)}..${getDecoratedBottom(child)}")
            }
            append("]")
        }
    }
}

private fun buildInnerItems(pageIndex: Int, outerPosition: Int, updated: Boolean): List<InnerItem> {
    return List(INNER_CHILD_COUNT) { index ->
        val suffix = if (updated) "updated" else "initial"
        InnerItem(
            id = outerPosition * 100 + index,
            pageIndex = pageIndex,
            outerPosition = outerPosition,
            innerIndex = index,
            title = "Weather tile $index",
            subtitle = "P$pageIndex outer$outerPosition $suffix ${".".repeat(index + 1)}",
            value = if (updated) "${20 + outerPosition + index} deg" else "${18 + outerPosition + index} deg",
            badge = if (updated) "LIVE" else "NOW",
            badgeVisible = (index + outerPosition + if (updated) 1 else 0) % 2 == 0,
        )
    }
}

private fun outerItemHeightDp(position: Int): Int {
    if (position == 0) return OUTER_ITEM_TALL_HEIGHT_DP
    return when (((position * 37) + 2) % 3) {
        0 -> OUTER_ITEM_DEFAULT_HEIGHT_DP
        1 -> OUTER_ITEM_TALL_HEIGHT_DP
        else -> OUTER_ITEM_EXTRA_TALL_HEIGHT_DP
    }
}

private class OuterGapDecoration(private val gapPx: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        outRect.set(gapPx / 2, gapPx / 2, gapPx / 2, gapPx / 2)
    }
}

private class HorizontalGapDecoration(private val gapPx: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        outRect.set(gapPx / 2, 0, gapPx / 2, 0)
    }
}

private fun effectiveAsyncSubmitList(): Boolean = DAEMONAPP_NOISE_MODE && USE_ASYNC_SUBMIT_LIST
private fun effectiveInnerDecoration(): Boolean = DAEMONAPP_NOISE_MODE && USE_INNER_ITEM_DECORATION
private fun effectiveInnerSnapHelper(): Boolean = DAEMONAPP_NOISE_MODE && USE_INNER_SNAP_HELPER
private fun effectiveStaggeredRequestLayoutChurn(): Boolean =
    DAEMONAPP_NOISE_MODE && ENABLE_STAGGERED_REQUEST_LAYOUT_CHURN
private fun effectiveAccessibilityChurn(): Boolean = DAEMONAPP_NOISE_MODE && ENABLE_ACCESSIBILITY_CHURN
private fun effectiveInnerItemAlphaAnimation(): Boolean =
    DAEMONAPP_NOISE_MODE && ENABLE_INNER_ITEM_ALPHA_ANIMATION

private fun Context.dp(value: Int): Int {
    return (value * resources.displayMetrics.density).roundToInt()
}

private fun scrollStateName(state: Int): String {
    return when (state) {
        RecyclerView.SCROLL_STATE_IDLE -> "IDLE"
        RecyclerView.SCROLL_STATE_DRAGGING -> "DRAGGING"
        RecyclerView.SCROLL_STATE_SETTLING -> "SETTLING"
        else -> "UNKNOWN($state)"
    }
}

private fun logD(pageIndex: Int, message: String) {
    Log.d(TAG, "[P$pageIndex][${SystemClock.uptimeMillis()}ms] $message")
}

private fun logW(pageIndex: Int, message: String) {
    Log.w(TAG, "[P$pageIndex][${SystemClock.uptimeMillis()}ms] $message")
}

private fun colorForViewType(viewType: Int): Int {
    val colors = intArrayOf(
        Color.rgb(69, 90, 100),
        Color.rgb(0, 121, 107),
        Color.rgb(93, 64, 55),
        Color.rgb(81, 45, 168),
        Color.rgb(38, 50, 56),
        Color.rgb(121, 85, 72),
        Color.rgb(25, 118, 210),
        Color.rgb(85, 139, 47),
        Color.rgb(194, 24, 91),
        Color.rgb(245, 124, 0),
    )
    return colors[viewType % colors.size]
}
