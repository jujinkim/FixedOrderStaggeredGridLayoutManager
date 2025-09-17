# FixedOrderStaggeredGridLayoutManager

Deterministic, fixed-order staggered grid LayoutManager for RecyclerView (Kotlin).

![Android CI](https://img.shields.io/badge/android-library-green)
![Kotlin](https://img.shields.io/badge/kotlin-2.0.0-blue)
![AGP](https://img.shields.io/badge/agp-8.5.2-blueviolet)

## Motivation
compose의 시대가 왔지만 아직 많은 프로젝트가 recyclerview를 쓰고 staggered grid layout manager를 쓴다. 하지만 버그가 있어서 커스텀 레이아웃을 만들었다. 슬프다.

RecyclerView's default StaggeredGridLayoutManager can reorder items and repack on scroll. This library provides a "fixed-order" staggered grid: item coordinates are computed once from scroll=0 and remain immutable while data and sizes are unchanged.

## Features
- Fixed order guarantee: absolute coordinates are stable across scrolling
- Staggered packing by height; no row alignment
- Span count ≥ 1; with 1 it behaves like vertical LinearLayoutManager
- SpanSizeLookup with sizes 1..spanCount; full-span supported
- Multi-span window packing with push-down to avoid overlap
- Column pinning strategy to fix starting column per item
 - Proper vertical scrolling, recycling, smooth scrolling, and state restore
 - scrollToPosition/smoothScrollToPosition with SNAP_TO_START
 - Explicit recompute APIs for runtime size changes (per-item and bulk)
 - Holder-side integration options (interface callback or extension helper)

## Installation
Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("kim.jujin:fixedorder-staggered-grid-layoutmanager:<version>")
}
```

Coordinates:
- GroupId: `kim.jujin`
- ArtifactId: `fixedorder-staggered-grid-layoutmanager`

## Quick Start
```kotlin
val lm = FixedOrderStaggeredGridLayoutManager(context, spanCount = 3)

lm.setSpanSizeLookup(object : SpanSizeLookup() {
    override fun getSpanSize(position: Int): Int = when (position % 7) {
        0 -> 3 // full-span
        3 -> 2 // two columns
        else -> 1
    }
})

lm.setColumnPinningStrategy { position ->
    if (position % 11 == 4) 0 else null // pin some items to column 0
}

recyclerView.layoutManager = lm
```

### Runtime Size Changes (ViewHolder/Adapter)
When a ViewHolder replaces children at runtime and its measured height may change, choose one of:

1) Implement the interface and use the injected callback
```kotlin
class VH(private val container: FrameLayout) : RecyclerView.ViewHolder(container),
    FixedOrderItemSizeChangeAware {
    private var onSizeChange: (() -> Unit)? = null
    override fun setFixedOrderItemSizeChangeCallback(callback: () -> Unit) {
        onSizeChange = callback
    }
    fun rebuildChildren() {
        container.removeAllViews()
        val child = LayoutInflater.from(container.context)
            .inflate(R.layout.item_variant, container, false)
        container.addView(child, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        onSizeChange?.invoke()
    }
}
```

2) Holder calls the convenience extension
```kotlin
class VH(private val container: FrameLayout) : RecyclerView.ViewHolder(container) {
    fun rebuildChildren() {
        container.removeAllViews()
        val child = LayoutInflater.from(container.context)
            .inflate(R.layout.item_variant, container, false)
        container.addView(child, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        // Tell the LayoutManager to recompute from this position
        notifyFixedOrderItemSizeChanged()
    }
}
```

3) Adapter-level (alternative)
```kotlin
// After updating data that affects height at a position
adapter.notifyItemChanged(position, /* payload= */ "size_changed")
// or
layoutManager.invalidateFromPosition(position)

// For many items changed
layoutManager.invalidateItemPositions()
```

### Why Explicit Recompute APIs?
- Determinism: This LayoutManager computes absolute item coordinates once from scroll=0 and does not silently repack on scroll/layout passes. Arbitrary child view mutations in a ViewHolder are not implicitly interpreted as “the item’s measured size changed”.
- Predictability: You stay in control of when rebalancing happens, so scroll position and visual order never jump due to background repacks.
- Performance: Recompute can start from the first affected position (`invalidateFromPosition(position)`), instead of re-laying out all items. For batch updates, `invalidateItemPositions()` makes the intent explicit.
- Contrast with SGLM: The platform StaggeredGridLayoutManager may remeasure/repack visible items on scroll, which can reorder or shift content. This library favors stable, fixed order; explicit signals are required when item sizes actually change.
- Practical tip: If the internal change does not alter measured height (e.g., swapping equal-height content), no call is needed. When in doubt, calling the holder callback/extension is safe and cheap.

## API Reference (Brief)
- `class FixedOrderStaggeredGridLayoutManager(context, spanCount = 2)`
  - `setSpanCount(Int)`, `getSpanCount()`
  - `setSpanSizeLookup(SpanSizeLookup)`, `getSpanSizeLookup()`
  - `setColumnPinningStrategy(ColumnPinningStrategy)`, `getColumnPinningStrategy()`
  - `invalidateFromPosition(Int)` — recompute from the first affected position (use when a single item’s size may change at runtime)
  - `invalidateItemPositions()` — recompute from scratch on next layout (use for bulk/global changes)
  - `scrollToPosition(Int)`, `smoothScrollToPosition(...)` — snaps target to start using absolute cached rects
- `abstract class SpanSizeLookup { fun getSpanSize(position: Int): Int }`
- `typealias ColumnPinningStrategy = (position: Int) -> Int?`
 - `interface FixedOrderItemSizeChangeAware` — implement in your ViewHolder to receive a size-change callback injected by the LayoutManager
 - `fun RecyclerView.ViewHolder.notifyFixedOrderItemSizeChanged()` — convenience extension to trigger a per-item recompute from inside the holder

## Notes & Limitations
- Coordinates are recomputed when: adapter items are inserted/removed/moved, span count changes, span sizes change, or an item’s measured size changes.
- Vertical-only orientation. Item decorations and margins are accounted for; no built-in item spacing.
- Column pinning clamps start column into a valid window if spanSize would overflow.

## Important Differences vs Android's StaggeredGridLayoutManager
- Fixed-order placement uses cached absolute rects; scrolling does not repack or reorder items.
- If a ViewHolder’s internal layout changes and height may change, you must explicitly trigger a recompute. See “Runtime Size Changes (ViewHolder/Adapter)”.
- Platform SGLM may remeasure/repack on scroll; this library keeps coordinates deterministic and only changes them when explicitly signaled.

## Sample App
- `:sample` shows 2/3 span toggling, irregular heights, full-span/multi-span items, and pinned items.

## Roadmap / Contributing
- Optional item spacing attrs
- Horizontal orientation
- Precomputed measure hints to reduce layout work

Contributions welcome! Please use Conventional Commits and include test output in PRs.

## License
MIT — see [LICENSE](LICENSE).
