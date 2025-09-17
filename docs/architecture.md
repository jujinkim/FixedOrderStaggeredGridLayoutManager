# Architecture Notes

This LayoutManager is deterministic: item coordinates are computed once from scroll=0 and cached per adapter position. Scrolling never changes packing.

Core caches:
- Per-position absolute `Rect` (left/top/right/bottom)
- Per-column bottoms (the next available Y)

Placement algorithm per adapter position:
1. Resolve `spanSize` (1..spanCount) and optional pinned start column.
2. If pinned: start column is clamped to a valid window; Y is `max(columnBottoms[window])`.
3. If not pinned: iterate all feasible windows and choose the one with the smallest `max(columnBottoms[window])` (tie → smallest start column).
4. Measure view width exactly to the window width and height as specified by its layout params; place at `(left, Y)`; update `columnBottoms` for the window to `bottom`.

Recompute triggers:
- Adapter insert/remove/move/update (from the first affected position)
- Span count changes, span sizes change, or explicit `invalidateItemPositions()`

State:
- Saves/restores scroll offset only; caches are rebuilt on layout.

Recycling:
- Attach only items whose rects intersect the viewport; recycle others.

Known constraints:
- Vertical-only orientation
- No built-in item spacing (use item decorations)

