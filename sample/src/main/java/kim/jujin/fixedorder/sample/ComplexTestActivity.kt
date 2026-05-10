package kim.jujin.fixedorder.sample

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
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
private const val OUTER_ITEM_DEFAULT_HEIGHT_DP = 200
private const val OUTER_ITEM_TALL_HEIGHT_DP = 410
private const val OUTER_ITEM_EXTRA_TALL_HEIGHT_DP = 620
private const val INNER_CHILD_COUNT = 5
private const val INNER_CHILD_SIZE_DP = 80
private const val APP_BAR_HEIGHT_DP = 300
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

        val pager = ViewPager2(this).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            adapter = ComplexPageAdapter()
            currentItem = 0
            if (VIEWPAGER_OFFSCREEN_LIMIT >= 0) {
                offscreenPageLimit = VIEWPAGER_OFFSCREEN_LIMIT
            }
        }

        setContentView(pager)
    }

    private class ComplexPageAdapter : RecyclerView.Adapter<PageHolder>() {
        override fun getItemCount(): Int = PAGE_COUNT

        override fun getItemViewType(position: Int): Int = position

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            return PageHolder(ComplexPageView(parent.context))
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            holder.page.bind(position)
        }
    }

    private class PageHolder(val page: ComplexPageView) : RecyclerView.ViewHolder(page)
}

private class ComplexPageView(context: Context) : FrameLayout(context) {
    private var pageIndex: Int = 0
    private var appBarOffset: Int = 0
    private var titleTargetSizeSp: Float = EXPANDED_TITLE_SP
    private var titleAnimator: ValueAnimator? = null
    private var outerScrollState: Int = RecyclerView.SCROLL_STATE_IDLE

    private val titleView: TextView
    private val appBarLayout: AppBarLayout
    private val outerRecyclerView: RecyclerView
    private val outerAdapter = OuterAdapter()

    init {
        layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )

        val coordinator = CoordinatorLayout(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        appBarLayout = AppBarLayout(context).apply {
            setBackgroundColor(Color.rgb(35, 45, 65))
            layoutParams = CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                context.dp(APP_BAR_HEIGHT_DP),
            )
        }

        val appBarContainer = FrameLayout(context).apply {
            minimumHeight = context.actionBarSize()
        }
        val appBarChildParams = AppBarLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ).apply {
            scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED
        }
        appBarLayout.addView(appBarContainer, appBarChildParams)

        val toolbar = MaterialToolbar(context).apply {
            setBackgroundColor(Color.rgb(35, 45, 65))
            elevation = context.dp(4).toFloat()
        }

        titleView = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, EXPANDED_TITLE_SP)
            gravity = Gravity.CENTER_VERTICAL
        }
        toolbar.addView(
            titleView,
            Toolbar.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER_VERTICAL,
            ),
        )

        outerRecyclerView = RecyclerView(context).apply {
            id = View.generateViewId()
            clipToPadding = false
            setPadding(context.dp(8), context.dp(8), context.dp(8), context.dp(8))
            adapter = outerAdapter
            layoutManager = if (USE_GRID_LAYOUT_MANAGER) {
                GridLayoutManager(context, OUTER_SPAN_COUNT)
            } else {
                FixedOrderStaggeredGridLayoutManager(OUTER_SPAN_COUNT)
            }
            addItemDecoration(OuterGapDecoration(context.dp(OUTER_ITEM_GAP_DP)))
        }
        val recyclerParams = CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ).apply {
            behavior = AppBarLayout.ScrollingViewBehavior()
        }

        coordinator.addView(appBarLayout)
        coordinator.addView(outerRecyclerView, recyclerParams)
        coordinator.addView(
            toolbar,
            CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                context.actionBarSize(),
            ).apply {
                gravity = Gravity.TOP
            },
        )
        addView(coordinator)

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

        val startSizeSp = titleView.textSize / resources.displayMetrics.scaledDensity
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
            InnerOuterHolder(parent.context, viewType)
        } else {
            NormalOuterHolder(parent.context, viewType)
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

private class NormalOuterHolder(context: Context, viewType: Int) : OuterHolder(
    TextView(context).apply {
        layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            context.dp(outerItemHeightDp(viewType)),
        )
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setBackgroundColor(colorForViewType(viewType))
    },
) {
    private val label = itemView as TextView

    override fun bind(pageIndex: Int, position: Int, viewType: Int, isInner: Boolean) {
        itemView.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            itemView.context.dp(outerItemHeightDp(position)),
        )
        label.text = "Page $pageIndex / Outer item $position / viewType $viewType / normal / ${outerItemHeightDp(position)}dp"
    }
}

private class InnerOuterHolder(context: Context, viewType: Int) : OuterHolder(
    FrameLayout(context).apply {
        layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            context.dp(outerItemHeightDp(viewType)),
        )
        setBackgroundColor(colorForViewType(viewType))
    },
) {
    private val container = itemView as FrameLayout
    private val labelView = TextView(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(8), 0, context.dp(8), 0)
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        setBackgroundColor(0x99000000.toInt())
    }
    private val contentHost = FrameLayout(context)
    private var innerRecyclerView: RecyclerView? = null
    private var layoutLogged = false

    init {
        container.addView(
            contentHost,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        container.addView(
            labelView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                context.dp(28),
                Gravity.TOP,
            ),
        )
    }

    override fun bind(pageIndex: Int, position: Int, viewType: Int, isInner: Boolean) {
        itemView.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            itemView.context.dp(outerItemHeightDp(position)),
        )

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
        return TextView(itemView.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                itemView.context.dp(INNER_CHILD_SIZE_DP),
                Gravity.CENTER_VERTICAL,
            )
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setBackgroundColor(Color.rgb(230, 230, 230))
            text = "P$pageIndex outer$outerPosition dummy $innerLabel"
        }
    }

    private fun createInnerRecyclerView(pageIndex: Int, outerPosition: Int, innerLabel: String): RecyclerView {
        val manager = LinearLayoutManager(itemView.context, RecyclerView.HORIZONTAL, false).apply {
            if (DISABLE_INNER_PREFETCH) {
                isItemPrefetchEnabled = false
            }
        }

        return RecyclerView(itemView.context).apply {
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
            innerRecyclerView = this
        }
    }
}

private class InnerAdapter(
    private val pageIndex: Int,
    private val outerPosition: Int,
) : RecyclerView.Adapter<InnerHolder>() {
    override fun getItemCount(): Int = INNER_CHILD_COUNT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InnerHolder {
        return InnerHolder(TextView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                parent.context.dp(INNER_CHILD_SIZE_DP),
            )
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setBackgroundColor(Color.rgb(88, 111, 166))
        })
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

private fun Context.actionBarSize(): Int {
    val outValue = TypedValue()
    theme.resolveAttribute(android.R.attr.actionBarSize, outValue, true)
    return TypedValue.complexToDimensionPixelSize(outValue.data, resources.displayMetrics)
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
