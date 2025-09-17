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

### ViewHolder-side runtime size change
If your ViewHolder replaces children at runtime and height may change, call the helper from inside the holder:
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

## API Reference (Brief)
- `class FixedOrderStaggeredGridLayoutManager(context, spanCount = 2)`
  - `setSpanCount(Int)`, `getSpanCount()`
  - `setSpanSizeLookup(SpanSizeLookup)`, `getSpanSizeLookup()`
  - `setColumnPinningStrategy(ColumnPinningStrategy)`, `getColumnPinningStrategy()`
  - `invalidateItemPositions()` — recompute from scratch on next layout
  - `invalidateFromPosition(Int)` — recompute from the first affected position (use when a single item’s size may change at runtime)
  - `scrollToPosition(Int)`, `smoothScrollToPosition(...)` — snaps target to start using absolute cached rects
- `abstract class SpanSizeLookup { fun getSpanSize(position: Int): Int }`
- `typealias ColumnPinningStrategy = (position: Int) -> Int?`

## Notes & Limitations
- Coordinates are recomputed when: adapter items are inserted/removed/moved, span count changes, span sizes change, or an item’s measured size changes.
- Vertical-only orientation. Item decorations and margins are accounted for; no built-in item spacing.
- Column pinning clamps start column into a valid window if spanSize would overflow.

## Important Differences vs Android's StaggeredGridLayoutManager
- Fixed-order placement uses cached absolute rects; scrolling does not trigger repacking or implicit reseat of views.
- If a ViewHolder’s internal layout changes at runtime (e.g., `removeAllViews()` then `addView()`), and the measured height can change, this layout does not repack on scroll like platform SGLM. Two options:
  - Explicitly notify for runtime size changes (권장):
    - Single item: `adapter.notifyItemChanged(position, /* payload= */ "size_changed")` 또는 `layoutManager.invalidateFromPosition(position)`
    - Many changed: `layoutManager.invalidateItemPositions()`
- This differs from the platform SGLM, which may remeasure and repack visible items on scroll and layout passes; here, item coordinates are deterministic and only change upon explicit data/size changes.

Example (dynamic child swap):
```kotlin
override fun onBindViewHolder(holder: VH, position: Int) {
    val container = holder.container // FrameLayout, etc.
    container.removeAllViews()
    val newChild = inflater.inflate(R.layout.item_variant, container, false)
    container.addView(
        newChild,
        FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    )
    container.requestLayout()
}

// After data change that affects height
adapter.notifyItemChanged(position, "size_changed")
// or, when many changed
layoutManager.invalidateItemPositions()
```

## Sample App
- `:sample` shows 2/3 span toggling, irregular heights, full-span/multi-span items, and pinned items.

## Roadmap / Contributing
- Optional item spacing attrs
- Horizontal orientation
- Precomputed measure hints to reduce layout work

Contributions welcome! Please use Conventional Commits and include test output in PRs.

## License
MIT — see [LICENSE](LICENSE).
