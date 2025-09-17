package kim.jujin.fixedorder

/**
 * Determines how many spans an item at [position] should occupy.
 * Must return a value in [1, spanCount].
 */
abstract class SpanSizeLookup {
    abstract fun getSpanSize(position: Int): Int

    companion object {
        @JvmStatic
        val DEFAULT: SpanSizeLookup = object : SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int = 1
        }
    }
}

