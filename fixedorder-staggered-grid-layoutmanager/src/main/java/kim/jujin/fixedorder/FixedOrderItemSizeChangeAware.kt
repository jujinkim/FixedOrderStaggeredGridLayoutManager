package kim.jujin.fixedorder

import androidx.recyclerview.widget.RecyclerView

/**
 * Optional contract for ViewHolders that want an explicit callback they can invoke
 * when their internal layout changes in a way that may affect height.
 *
 * The LayoutManager installs the callback on attachment; holders should store it and
 * invoke it (e.g., after removeAllViews/addView sequences).
 */
interface FixedOrderItemSizeChangeAware {
    fun setFixedOrderItemSizeChangeCallback(callback: () -> Unit)
}

