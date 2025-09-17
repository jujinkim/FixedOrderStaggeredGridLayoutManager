package kim.jujin.fixedorder

import android.content.Context
import android.graphics.PointF
import android.graphics.Rect
import android.os.Parcel
import android.os.Parcelable
import android.view.View
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * A deterministic staggered grid layout where item placement is computed once from scroll=0
 * and remains stable (absolute coordinates) as long as item sizes and the dataset order do not change.
 *
 * - spanCount >= 1; when spanCount == 1 behaves like a vertical LinearLayoutManager.
 * - Items may be multi-span (1..spanCount); full-span items (spanSize == spanCount) are placed below
 *   the tallest column.
 * - Optional [ColumnPinningStrategy] to pin items to a specific start column.
 * - Stable, fixed order packing independent of scrolling.
 */
class FixedOrderStaggeredGridLayoutManager(
    context: Context,
    spanCount: Int = 2,
) : RecyclerView.LayoutManager(), RecyclerView.SmoothScroller.ScrollVectorProvider {

    private var spanCount: Int = max(1, spanCount)
    private var spanSizeLookup: SpanSizeLookup = SpanSizeLookup.DEFAULT
    private var pinningStrategy: ColumnPinningStrategy? = null

    private var verticalScrollOffset: Int = 0

    // Absolute rects per adapter position
    private val itemRects = SparseRectArray()
    // Cached column left/right (computed from width)
    private var columnLefts: IntArray = IntArray(this.spanCount)
    private var columnRights: IntArray = IntArray(this.spanCount)
    // Cached column bottoms (absolute Y) after precompute; equals content height per column
    private var columnBottoms: IntArray = IntArray(this.spanCount)
    private var contentHeight: Int = 0
    private var precomputedItemCount: Int = 0

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        return RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    override fun canScrollVertically(): Boolean = true

    fun setSpanCount(count: Int) {
        val newCount = max(1, count)
        if (newCount == spanCount) return
        spanCount = newCount
        itemRects.clear()
        columnLefts = IntArray(spanCount)
        columnRights = IntArray(spanCount)
        columnBottoms = IntArray(spanCount)
        contentHeight = 0
        precomputedItemCount = 0
        requestLayout()
    }

    fun getSpanCount(): Int = spanCount

    fun setSpanSizeLookup(lookup: SpanSizeLookup?) {
        spanSizeLookup = lookup ?: SpanSizeLookup.DEFAULT
        invalidateItemPositions()
    }

    fun getSpanSizeLookup(): SpanSizeLookup = spanSizeLookup

    fun setColumnPinningStrategy(strategy: ColumnPinningStrategy?) {
        pinningStrategy = strategy
        invalidateItemPositions()
    }

    fun getColumnPinningStrategy(): ColumnPinningStrategy? = pinningStrategy

    /** Force full re-precompute on next layout. */
    fun invalidateItemPositions() {
        itemRects.clear()
        precomputedItemCount = 0
        requestLayout()
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        if (itemCount == 0) {
            detachAndScrapAttachedViews(recycler)
            verticalScrollOffset = 0
            return
        }

        ensureColumnBounds()

        if (precomputedItemCount < itemCount) {
            precomputeAll(recycler, state)
        }

        // Clamp offset
        val maxOffset = max(0, contentHeight - verticalSpace())
        verticalScrollOffset = min(verticalScrollOffset, maxOffset)
        verticalScrollOffset = max(0, verticalScrollOffset)

        // Detach & scrap all first; we will layout visible
        detachAndScrapAttachedViews(recycler)
        layoutVisibleChildren(recycler)
    }

    override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        if (childCount == 0 || dy == 0) return 0
        val maxOffset = max(0, contentHeight - verticalSpace())
        val newOffset = (verticalScrollOffset + dy).coerceIn(0, maxOffset)
        val delta = newOffset - verticalScrollOffset
        if (delta == 0) return 0
        verticalScrollOffset = newOffset
        offsetChildrenVertical(-delta)
        recycleAndFill(recycler)
        return delta
    }

    override fun computeVerticalScrollOffset(state: RecyclerView.State): Int = verticalScrollOffset
    override fun computeVerticalScrollExtent(state: RecyclerView.State): Int = verticalSpace()
    override fun computeVerticalScrollRange(state: RecyclerView.State): Int = max(contentHeight, verticalSpace())

    override fun onItemsAdded(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {
        invalidateFromPosition(positionStart)
    }

    override fun onItemsRemoved(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {
        invalidateFromPosition(positionStart)
    }

    override fun onItemsMoved(recyclerView: RecyclerView, from: Int, to: Int, itemCount: Int) {
        invalidateFromPosition(min(from, to))
    }

    override fun onItemsChanged(recyclerView: RecyclerView) {
        invalidateFromPosition(0)
    }

    override fun onItemsUpdated(
        recyclerView: RecyclerView,
        positionStart: Int,
        itemCount: Int,
        payload: Any?
    ) {
        invalidateFromPosition(positionStart)
    }

    private fun invalidateFromPosition(positionStart: Int) {
        // Remove cached rects from positionStart onwards; earlier positions remain stable per spec
        itemRects.removeFrom(positionStart)
        precomputedItemCount = min(precomputedItemCount, positionStart)
        requestLayout()
    }

    // SmoothScroller support
    override fun computeScrollVectorForPosition(targetPosition: Int): PointF? {
        val first = if (childCount > 0) getPosition(getChildAt(0)!!) else 0
        val direction = if (targetPosition > first) 1f else -1f
        return PointF(0f, direction)
    }

    // SavedState for offset
    override fun onSaveInstanceState(): Parcelable? = SavedState(verticalScrollOffset)
    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            verticalScrollOffset = max(0, state.offset)
            requestLayout()
        }
    }

    private class SavedState(val offset: Int) : Parcelable {
        constructor(parcel: Parcel) : this(parcel.readInt())
        override fun writeToParcel(parcel: Parcel, flags: Int) { parcel.writeInt(offset) }
        override fun describeContents(): Int = 0
        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(parcel: Parcel): SavedState = SavedState(parcel)
            override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
        }
    }

    private fun verticalSpace(): Int = height - paddingTop - paddingBottom
    private fun horizontalSpace(): Int = width - paddingLeft - paddingRight

    private fun ensureColumnBounds() {
        if (columnLefts.size != spanCount) {
            columnLefts = IntArray(spanCount)
            columnRights = IntArray(spanCount)
            columnBottoms = IntArray(spanCount)
        }
        if (spanCount == 1) {
            columnLefts[0] = paddingLeft
            columnRights[0] = width - paddingRight
            return
        }
        val total = horizontalSpace()
        val base = total / spanCount
        val remainder = total % spanCount
        var x = paddingLeft
        for (i in 0 until spanCount) {
            val extra = if (i < remainder) 1 else 0
            val w = base + extra
            columnLefts[i] = x
            columnRights[i] = x + w
            x += w
        }
    }

    private fun precomputeAll(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        // Start from precomputedItemCount; when 0, initialize tops to paddingTop
        if (precomputedItemCount == 0) {
            for (i in 0 until spanCount) columnBottoms[i] = paddingTop
        }
        ensureColumnBounds()

        var pos = precomputedItemCount
        while (pos < itemCount) {
            val spanSize = spanSizeFor(pos)
            val spanWin = spanSize.coerceIn(1, spanCount)
            val pinnedStart = pinningStrategy?.invoke(pos)?.let { it.coerceIn(0, spanCount - spanWin) }

            val (startCol, topY) = if (pinnedStart != null) {
                // Pinned: use given start, earliest feasible Y
                val y = windowTop(pinnedStart, spanWin)
                pinnedStart to y
            } else {
                // Choose window that yields the lowest Y; tie -> smallest start column
                var bestStart = 0
                var bestY = Int.MAX_VALUE
                for (start in 0..(spanCount - spanWin)) {
                    val y = windowTop(start, spanWin)
                    if (y < bestY) { bestY = y; bestStart = start }
                }
                bestStart to bestY
            }

            val left = columnLefts[startCol]
            val right = columnRights[startCol + spanWin - 1]
            val measured = measureForPosition(recycler, pos, left, right)
            val top = topY
            val bottom = top + measured

            itemRects.put(pos, Rect(left, top, right, bottom))
            // Update all columns in the window to the new bottom
            for (c in startCol until (startCol + spanWin)) {
                columnBottoms[c] = bottom
            }

            pos++
        }
        precomputedItemCount = itemCount
        contentHeight = columnBottoms.maxOrNull() ?: paddingTop
    }

    private fun windowTop(startCol: Int, spanSize: Int): Int {
        var y = Int.MIN_VALUE
        for (c in startCol until (startCol + spanSize)) {
            y = max(y, columnBottoms[c])
        }
        return y
    }

    private fun spanSizeFor(position: Int): Int =
        try { spanSizeLookup.getSpanSize(position).coerceIn(1, spanCount) } catch (_: Throwable) { 1 }

    private fun measureForPosition(recycler: RecyclerView.Recycler, position: Int, left: Int, right: Int): Int {
        val view = recycler.getViewForPosition(position)
        addView(view)
        val lp = view.layoutParams as RecyclerView.LayoutParams

        val decor = Rect()
        calculateItemDecorationsForChild(view, decor)

        val childWidthAvailable = (right - left) - lp.leftMargin - lp.rightMargin - decor.left - decor.right
        val wSpec = View.MeasureSpec.makeMeasureSpec(max(0, childWidthAvailable), View.MeasureSpec.EXACTLY)

        val hLp = lp.height
        val hMode: Int
        val hSize: Int
        if (hLp >= 0) {
            hMode = View.MeasureSpec.EXACTLY
            hSize = hLp
        } else {
            hMode = View.MeasureSpec.UNSPECIFIED
            hSize = 0
        }
        val hSpec = View.MeasureSpec.makeMeasureSpec(hSize, hMode)

        view.measure(wSpec, hSpec)
        val measuredHeight = view.measuredHeight + lp.topMargin + lp.bottomMargin + decor.top + decor.bottom

        // Scrap without attaching in final position here
        detachAndScrapView(view, recycler)
        return measuredHeight
    }

    private fun layoutVisibleChildren(recycler: RecyclerView.Recycler) {
        // Layout items intersecting viewport
        val viewportTop = verticalScrollOffset + paddingTop
        val viewportBottom = verticalScrollOffset + height - paddingBottom
        val start = findFirstIntersecting(viewportTop, viewportBottom)
        if (start == -1) return
        var pos = start
        while (pos < itemCount) {
            val rect = itemRects.get(pos) ?: break
            if (rect.top > viewportBottom) break
            val existing = findViewByPosition(pos)
            if (existing == null) {
                val view = recycler.getViewForPosition(pos)
                addView(view)
                layoutDecoratedWithMargins(
                    view,
                    rect.left,
                    rect.top - verticalScrollOffset,
                    rect.right,
                    rect.bottom - verticalScrollOffset,
                )
            } else {
                // Ensure position & offset is correct
                layoutDecoratedWithMargins(
                    existing,
                    rect.left,
                    rect.top - verticalScrollOffset,
                    rect.right,
                    rect.bottom - verticalScrollOffset,
                )
            }
            pos++
        }
    }

    private fun recycleAndFill(recycler: RecyclerView.Recycler) {
        val viewportTop = verticalScrollOffset + paddingTop
        val viewportBottom = verticalScrollOffset + height - paddingBottom

        // Recycle views not in viewport
        for (i in childCount - 1 downTo 0) {
            val child = getChildAt(i) ?: continue
            val pos = getPosition(child)
            val rect = itemRects.get(pos) ?: continue
            if (rect.bottom < viewportTop || rect.top > viewportBottom) {
                removeAndRecycleView(child, recycler)
            }
        }

        // Fill any missing
        layoutVisibleChildren(recycler)
    }

    private fun findFirstIntersecting(viewportTop: Int, viewportBottom: Int): Int {
        // Linear scan; simple and robust
        var pos = 0
        while (pos < itemCount) {
            val r = itemRects.get(pos) ?: return -1
            if (r.bottom >= viewportTop && r.top <= viewportBottom) return pos
            pos++
        }
        return -1
    }

    // Utility sparse array of rects
    private class SparseRectArray {
        private var keys = IntArray(16)
        private var values = arrayOfNulls<Rect>(16)
        private var size = 0

        fun put(key: Int, value: Rect) {
            val idx = java.util.Arrays.binarySearch(keys, 0, size, key)
            if (idx >= 0) {
                values[idx] = value
                return
            }
            val insertAt = idx.inv()
            if (size == keys.size) grow()
            if (insertAt < size) {
                System.arraycopy(keys, insertAt, keys, insertAt + 1, size - insertAt)
                System.arraycopy(values, insertAt, values, insertAt + 1, size - insertAt)
            }
            keys[insertAt] = key
            values[insertAt] = value
            size++
        }

        fun get(key: Int): Rect? {
            val idx = java.util.Arrays.binarySearch(keys, 0, size, key)
            return if (idx >= 0) values[idx] else null
        }

        fun clear() { size = 0 }

        fun removeFrom(keyInclusive: Int) {
            val idx = java.util.Arrays.binarySearch(keys, 0, size, keyInclusive)
            val start = if (idx >= 0) idx else idx.inv()
            if (start < size) {
                size = start
            }
        }

        private fun grow() {
            val newCap = max(16, ceil(size * 1.5).toInt())
            keys = keys.copyOf(newCap)
            values = values.copyOf(newCap)
        }
    }

    // Visible for tests
    @VisibleForTesting
    fun getItemRect(position: Int): Rect? = itemRects.get(position)

    @VisibleForTesting
    fun getContentHeight(): Int = contentHeight

    @VisibleForTesting
    fun getColumnBottomsCopy(): IntArray = columnBottoms.copyOf()
}
