package kim.jujin.fixedorder.sample

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import kim.jujin.fixedorder.FixedOrderStaggeredGridLayoutManager
import kotlin.math.roundToInt

private const val TAG = "FixedOrderComplex"

private const val USE_GRID_LAYOUT_MANAGER = false
private const val OUTER_SPAN_COUNT = 2
private const val INNER_RV_COUNT = 2
private const val FIX_INNER_RV_HEIGHT = false
private const val DISABLE_INNER_PREFETCH = false
private const val DISABLE_INNER_ITEM_ANIMATOR = false
private const val DISABLE_INNER_FOCUS = false
private const val USE_DUMMY_VIEW_INSTEAD_OF_INNER_RV = false
private const val VIEWPAGER_OFFSCREEN_LIMIT = -1

private const val PAGE_COUNT = 3
private const val OUTER_ITEM_COUNT = 15
private const val OUTER_ITEM_DEFAULT_HEIGHT_DP = 90
private const val OUTER_ITEM_TALL_HEIGHT_DP = 190
private const val OUTER_ITEM_EXTRA_TALL_HEIGHT_DP = 290
private const val INNER_CHILD_COUNT = 5
private const val INNER_CHILD_SIZE_DP = 80
private const val OUTER_ITEM_GAP_DP = 10
private const val EXPANDED_TITLE_SP = 15f
private const val COLLAPSED_TITLE_SP = 20f
private const val TITLE_ANIMATION_MS = 500L

/*
Manual reproduction:
1. Launch Complex Test. Page 0 starts with the AppBar fully expanded and the outer list at top.
2. On Page 0, fast fling upward from the top so the AppBar collapses and the outer RecyclerView starts scrolling.
3. Watch for a short smooth reverse scroll or bounce around the early scroll range.
4. Repeat after the first occurrence.
5. Swipe to Page 1 and back to Page 0, then compare whether the bounce disappears.

Useful variants:
1. USE_GRID_LAYOUT_MANAGER = false, INNER_RV_COUNT = 2
2. USE_GRID_LAYOUT_MANAGER = true
3. INNER_RV_COUNT = 1
4. USE_DUMMY_VIEW_INSTEAD_OF_INNER_RV = true
5. FIX_INNER_RV_HEIGHT = true
6. DISABLE_INNER_PREFETCH = true
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
            FixedOrderStaggeredGridLayoutManager(OUTER_SPAN_COUNT)
        }
        outerRecyclerView.addItemDecoration(OuterGapDecoration(root.context.dp(OUTER_ITEM_GAP_DP)))

        appBarLayout.addOnOffsetChangedListener { appBar, verticalOffset ->
            appBarOffset = verticalOffset
            Log.d(TAG, "P$pageIndex AppBar offset=$verticalOffset total=${appBar.totalScrollRange}")
            animateTitleSize(if (verticalOffset == 0) EXPANDED_TITLE_SP else COLLAPSED_TITLE_SP)
        }

        outerRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                outerScrollState = newState
                Log.d(TAG, "P$pageIndex OuterRV state=${scrollStateName(newState)}")
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                Log.d(TAG, "P$pageIndex OuterRV onScrolled dx=$dx dy=$dy")
                if (dy < 0) {
                    Log.w(
                        TAG,
                        "P$pageIndex NEGATIVE_DY event=outerScroll dy=$dy " +
                            "state=${scrollStateName(outerScrollState)} appBarOffset=$appBarOffset " +
                            "titleTargetSize=$titleTargetSizeSp ${visibleChildLog()}",
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
    }

    private fun animateTitleSize(targetSizeSp: Float) {
        if (titleTargetSizeSp == targetSizeSp) return
        titleTargetSizeSp = targetSizeSp
        titleAnimator?.cancel()

        val startSizeSp = titleView.textSize / root.resources.displayMetrics.scaledDensity
        Log.d(TAG, "P$pageIndex Title targetSize=$targetSizeSp startSize=$startSizeSp")

        titleAnimator = ValueAnimator.ofFloat(startSizeSp, targetSizeSp).apply {
            duration = TITLE_ANIMATION_MS
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, value)
            }
            start()
        }
    }

    private fun visibleChildLog(): String {
        val lm = outerRecyclerView.layoutManager
        val visible = buildString {
            append("visiblePositionsWithTop=[")
            for (i in 0 until outerRecyclerView.childCount) {
                if (i > 0) append(", ")
                val child = outerRecyclerView.getChildAt(i)
                val position = outerRecyclerView.getChildAdapterPosition(child)
                val top = lm?.getDecoratedTop(child) ?: child.top
                append("$position:$top")
            }
            append("]")
        }
        return "$visible ${innerBoundsLog(1, "A")} ${innerBoundsLog(2, "B")}"
    }

    private fun innerBoundsLog(position: Int, label: String): String {
        val holder = outerRecyclerView.findViewHolderForAdapterPosition(position)
        val lm = outerRecyclerView.layoutManager
        return if (holder == null || !outerAdapter.isInnerPosition(position)) {
            "inner$label=notVisible"
        } else {
            val top = lm?.getDecoratedTop(holder.itemView) ?: holder.itemView.top
            val bottom = lm?.getDecoratedBottom(holder.itemView) ?: holder.itemView.bottom
            "inner$label top=$top bottom=$bottom"
        }
    }
}

private class OuterAdapter : RecyclerView.Adapter<OuterHolder>() {
    private var pageIndex: Int = 0

    fun bindPage(pageIndex: Int) {
        this.pageIndex = pageIndex
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
        holder.bind(pageIndex, position, getItemViewType(position), isInnerPosition(position))
    }

    fun isInnerPosition(position: Int): Boolean {
        return position == 1 || (INNER_RV_COUNT >= 2 && position == 2)
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
        itemView.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            itemView.context.dp(outerItemHeightDp(viewType)),
        )
        itemView.setBackgroundColor(colorForViewType(viewType))
    }

    override fun bind(pageIndex: Int, position: Int, viewType: Int, isInner: Boolean) {
        itemView.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            itemView.context.dp(outerItemHeightDp(position)),
        )
        itemView.setBackgroundColor(colorForViewType(viewType))
        label.text = "Page $pageIndex / Outer item $position / viewType $viewType / normal / ${outerItemHeightDp(position)}dp"
    }
}

private class InnerOuterHolder(parent: ViewGroup, viewType: Int) : OuterHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.item_complex_outer_inner, parent, false),
) {
    private val labelView: TextView = itemView.findViewById(R.id.complex_inner_label)
    private val contentHost: FrameLayout = itemView.findViewById(R.id.complex_inner_content_host)
    private var layoutLogged = false

    init {
        itemView.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            itemView.context.dp(outerItemHeightDp(viewType)),
        )
        itemView.setBackgroundColor(colorForViewType(viewType))
    }

    override fun bind(pageIndex: Int, position: Int, viewType: Int, isInner: Boolean) {
        itemView.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            itemView.context.dp(outerItemHeightDp(position)),
        )
        itemView.setBackgroundColor(colorForViewType(viewType))

        val innerLabel = if (position == 1) "A" else "B"
        labelView.text =
            "Page $pageIndex / Outer item $position / viewType $viewType / INNER RV $innerLabel / ${outerItemHeightDp(position)}dp"

        contentHost.removeAllViews()
        layoutLogged = false
        if (USE_DUMMY_VIEW_INSTEAD_OF_INNER_RV) {
            contentHost.addView(createDummyInnerView(pageIndex, position, innerLabel))
        } else {
            contentHost.addView(createInnerRecyclerView(pageIndex, position, innerLabel))
        }
    }

    private fun createDummyInnerView(pageIndex: Int, outerPosition: Int, innerLabel: String): View {
        val view = LayoutInflater.from(itemView.context)
            .inflate(R.layout.view_complex_inner_dummy, contentHost, false) as TextView
        view.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            itemView.context.dp(INNER_CHILD_SIZE_DP),
            Gravity.CENTER_VERTICAL,
        )
        view.text = "P$pageIndex outer$outerPosition dummy $innerLabel"
        return view
    }

    private fun createInnerRecyclerView(pageIndex: Int, outerPosition: Int, innerLabel: String): RecyclerView {
        val manager = LinearLayoutManager(itemView.context, RecyclerView.HORIZONTAL, false).apply {
            if (DISABLE_INNER_PREFETCH) {
                isItemPrefetchEnabled = false
            }
        }

        return (LayoutInflater.from(itemView.context)
            .inflate(R.layout.view_complex_inner_recycler, contentHost, false) as RecyclerView).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                if (FIX_INNER_RV_HEIGHT) itemView.context.dp(INNER_CHILD_SIZE_DP) else ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER_VERTICAL,
            )
            layoutManager = manager
            adapter = InnerAdapter(pageIndex, outerPosition)
            isNestedScrollingEnabled = false
            if (DISABLE_INNER_ITEM_ANIMATOR) {
                itemAnimator = null
            }
            if (DISABLE_INNER_FOCUS) {
                isFocusable = false
                isFocusableInTouchMode = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
            PagerSnapHelper().attachToRecyclerView(this)
            addOnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                val first = !layoutLogged
                layoutLogged = true
                Log.d(
                    TAG,
                    "P$pageIndex InnerRV $innerLabel layout first=$first " +
                        "outer=$outerPosition top=$top bottom=$bottom " +
                        "oldTop=$oldTop oldBottom=$oldBottom width=${right - left} height=${bottom - top}",
                )
            }
        }
    }
}

private class InnerAdapter(
    private val pageIndex: Int,
    private val outerPosition: Int,
) : RecyclerView.Adapter<InnerHolder>() {
    override fun getItemCount(): Int = INNER_CHILD_COUNT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InnerHolder {
        val label = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_complex_inner_text, parent, false) as TextView
        label.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            parent.context.dp(INNER_CHILD_SIZE_DP),
        )
        return InnerHolder(label)
    }

    override fun onBindViewHolder(holder: InnerHolder, position: Int) {
        holder.label.text = "P$pageIndex outer$outerPosition inner$position"
    }
}

private class InnerHolder(val label: TextView) : RecyclerView.ViewHolder(label)

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
