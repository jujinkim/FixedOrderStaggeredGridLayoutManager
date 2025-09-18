@file:JvmName("FixedOrderExtensions")

package kim.jujin.fixedorder

import android.view.View
import android.view.ViewParent
import androidx.recyclerview.widget.RecyclerView

/**
 * Call from inside a ViewHolder after changing the internal layout in a way that may affect height
 * (e.g., removeAllViews + addView). This triggers a full recompute on the next layout pass.
 *
 * Safe no-op if the holder is not attached or layout manager is not FixedOrderStaggeredGridLayoutManager.
 */
fun RecyclerView.ViewHolder.notifyFixedOrderItemSizeChanged() {
    val rv = findParentRecyclerView(itemView) ?: return
    val lm = rv.layoutManager as? FixedOrderStaggeredGridLayoutManager ?: return
    lm.invalidateItemPositions()
}

private fun findParentRecyclerView(view: View): RecyclerView? {
    var p: ViewParent? = view.parent
    while (p != null) {
        if (p is RecyclerView) return p
        p = (p as? View)?.parent
    }
    return null
}
