package kim.jujin.fixedorder

/**
 * Strategy to pin certain items to a specific starting column (0-based).
 * Return null for unpinned. Returned column is clamped into a valid window if needed.
 */
typealias ColumnPinningStrategy = (position: Int) -> Int?

