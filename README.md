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

## API Reference (Brief)
- `class FixedOrderStaggeredGridLayoutManager(context, spanCount = 2)`
  - `setSpanCount(Int)`, `getSpanCount()`
  - `setSpanSizeLookup(SpanSizeLookup)`, `getSpanSizeLookup()`
  - `setColumnPinningStrategy(ColumnPinningStrategy)`, `getColumnPinningStrategy()`
  - `invalidateItemPositions()` — recompute from scratch on next layout
- `abstract class SpanSizeLookup { fun getSpanSize(position: Int): Int }`
- `typealias ColumnPinningStrategy = (position: Int) -> Int?`

## Notes & Limitations
- Coordinates are recomputed when: adapter items are inserted/removed/moved, span count changes, span sizes change, or an item’s measured size changes.
- Vertical-only orientation. Item decorations and margins are accounted for; no built-in item spacing.
- Column pinning clamps start column into a valid window if spanSize would overflow.

## Sample App
- `:sample` shows 2/3 span toggling, irregular heights, full-span/multi-span items, and pinned items.

## Roadmap / Contributing
- Optional item spacing attrs
- Horizontal orientation
- Precomputed measure hints to reduce layout work

Contributions welcome! Please use Conventional Commits and include test output in PRs.

## License
MIT — see [LICENSE](LICENSE).

