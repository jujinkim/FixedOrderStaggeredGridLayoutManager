package kim.jujin.fixedorder

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FixedOrderLayoutManagerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
    }

    @Test
    fun noOverlaps_generatedDataset() {
        val rv = RecyclerView(context)
        rv.layoutParams = ViewGroup.LayoutParams(1080, 1920)

        val lm = FixedOrderStaggeredGridLayoutManager(context, spanCount = 3)
        rv.layoutManager = lm

        val heights = (0 until 60).map { baseHeight(it) }
        val adapter = FixedAdapter(heights)
        rv.adapter = adapter

        // Layout
        rv.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
        )
        rv.layout(0, 0, 1080, 1920)

        // Collect rects
        val rects = (0 until adapter.itemCount).mapNotNull { lm.getItemRect(it) }
        assertEquals(adapter.itemCount, rects.size)

        // Pairwise intersection check
        for (i in rects.indices) {
            for (j in i + 1 until rects.size) {
                assertFalse("Rects $i and $j overlap: ${rects[i]} vs ${rects[j]}", intersects(rects[i], rects[j]))
            }
        }
    }

    @Test
    fun fullSpanForcesFollowingY_geFullSpanBottom() {
        val rv = RecyclerView(context)
        rv.layoutParams = ViewGroup.LayoutParams(800, 1200)
        val lm = FixedOrderStaggeredGridLayoutManager(context, spanCount = 3)
        rv.layoutManager = lm

        val heights = (0 until 30).map { baseHeight(it) }
        val adapter = FixedAdapter(heights)
        rv.adapter = adapter

        lm.setSpanSizeLookup(object : SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int = if (position == 5) 3 else 1
        })

        rv.measure(
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY),
        )
        rv.layout(0, 0, 800, 1200)

        val fullSpanBottom = lm.getItemRect(5)!!.bottom
        for (p in 6 until adapter.itemCount) {
            val r = lm.getItemRect(p)!!
            assertTrue("Position $p top ${r.top} < full-span bottom $fullSpanBottom", r.top >= fullSpanBottom)
        }
    }

    @Test
    fun pinnedItem_span2_startCol0_inSpanCount3_hasCorrectX_andValidPlacement() {
        val rv = RecyclerView(context)
        rv.layoutParams = ViewGroup.LayoutParams(900, 1400)
        val lm = FixedOrderStaggeredGridLayoutManager(context, spanCount = 3)
        rv.layoutManager = lm

        val heights = (0 until 40).map { baseHeight(it) }
        val adapter = FixedAdapter(heights)
        rv.adapter = adapter

        val pinnedPos = 7
        lm.setSpanSizeLookup(object : SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int = if (position == pinnedPos) 2 else 1
        })
        lm.setColumnPinningStrategy { pos -> if (pos == pinnedPos) 0 else null }

        rv.measure(
            View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1400, View.MeasureSpec.EXACTLY),
        )
        rv.layout(0, 0, 900, 1400)

        val pinnedRect = lm.getItemRect(pinnedPos)!!
        assertEquals("Pinned item must start at column 0 (left == paddingLeft)", rv.paddingLeft, pinnedRect.left)
        assertTrue("Pinned item must have width spanning two columns", pinnedRect.width() > (900 - rv.paddingLeft - rv.paddingRight) / 3)
        // No overlap with any prior item
        for (p in 0 until pinnedPos) {
            val r = lm.getItemRect(p)!!
            assertFalse("Pinned overlaps with $p: $r vs $pinnedRect", intersects(r, pinnedRect))
        }
    }

    @Test
    fun spanCount2_allSpan1_monotonicColumns_andStableAcrossScroll() {
        val rv = RecyclerView(context)
        rv.layoutParams = ViewGroup.LayoutParams(1000, 1200)
        val lm = FixedOrderStaggeredGridLayoutManager(context, spanCount = 2)
        rv.layoutManager = lm

        val heights = (0 until 80).map { baseHeight(it) }
        val adapter = FixedAdapter(heights)
        rv.adapter = adapter

        rv.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY),
        )
        rv.layout(0, 0, 1000, 1200)

        val initial = (0 until adapter.itemCount).map { lm.getItemRect(it)!!.let { Rect(it) } }

        // Column monotonic: split by left coordinate
        val left0 = initial.filter { it.left == rv.paddingLeft }
        val left1 = initial.filter { it.left != rv.paddingLeft }
        assertMonotonicNonDecreasing(left0.map { it.top })
        assertMonotonicNonDecreasing(left1.map { it.top })

        // Scroll and ensure absolute rects unchanged
        rv.scrollBy(0, 500)
        rv.scrollBy(0, 500)
        rv.scrollBy(0, -300)

        (0 until adapter.itemCount).forEach { idx ->
            val after = lm.getItemRect(idx)!!
            assertEquals(initial[idx], after)
        }
    }

    private fun baseHeight(i: Int): Int = 40 + (i % 9) * 15 + (if (i % 7 == 0) 50 else 0)

    private fun intersects(a: Rect, b: Rect): Boolean {
        return a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top
    }

    private fun assertMonotonicNonDecreasing(values: List<Int>) {
        var prev = Int.MIN_VALUE
        for (v in values) {
            assertTrue("Sequence not monotonic: prev=$prev, v=$v", v >= prev)
            prev = v
        }
    }

    // Minimal adapter producing fixed-height views
    private class FixedAdapter(private val heights: List<Int>) : RecyclerView.Adapter<VH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = TestItemView(parent.context)
            return VH(v)
        }
        override fun getItemCount(): Int = heights.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            val h = heights[position]
            holder.bind(h)
        }
    }

    private class VH(view: TestItemView) : RecyclerView.ViewHolder(view) {
        fun bind(h: Int) {
            val lp = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
            itemView.layoutParams = lp
        }
    }

    private class TestItemView(ctx: Context) : FrameLayout(ctx) {
        init { setBackgroundColor(0x220000FF) }
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}
