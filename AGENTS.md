# Repository Guidelines
The repository currently contains only licensing scaffolding. Use this guide when introducing code for the FixedOrderStaggeredGridLayoutManager library.

## Project Structure & Module Organization
- Library module: `fixedorder-staggered-grid-layoutmanager/`
- Runtime sources: `fixedorder-staggered-grid-layoutmanager/src/main/java/kim/jujin/fixedorder/`
- XML attrs and drawables: `fixedorder-staggered-grid-layoutmanager/src/main/res/values` and `.../res/drawable`
- JVM unit tests: `fixedorder-staggered-grid-layoutmanager/src/test/java/...`
- Instrumentation tests: `fixedorder-staggered-grid-layoutmanager/src/androidTest/java/...`
- Sample app: `sample/` with UI assets in `sample/src/main/res/`
- Long-form docs and diagrams live in `docs/` (create as needed). Keep assets referenced via relative paths.

## Build, Test, and Development Commands
- `./gradlew assembleDebug` — builds the library and sample (wrapper included).
- `./gradlew lint ktlintCheck` — runs Android Lint and Kotlin style checks; fix issues with `./gradlew ktlintFormat`.
- `./gradlew test` — executes JVM unit tests.
- `./gradlew connectedAndroidTest` — runs instrumentation tests against a connected emulator or device.

Publishing (manual)
- GitHub Actions workflow `Publish to Maven Central` is manual (`workflow_dispatch`).
- Requires repo secrets: `OSSRH_USERNAME`, `OSSRH_PASSWORD`, `SIGNING_KEY`, `SIGNING_PASSWORD`.
- Run: Actions → Publish to Maven Central → Run workflow (optionally set version). Close/Release the staged repo in Sonatype for non-SNAPSHOT.

## Coding Style & Naming Conventions
- Kotlin with 4-space indentation and trailing commas enabled for multi-line constructs.
- Public API classes and enums use PascalCase; functions and properties camelCase; constants in UPPER_SNAKE_CASE inside a `companion object`.
- Keep LayoutManager state encapsulated; prefer immutable data classes for span metadata.
- Avoid wildcard imports and keep extension files annotated with `@file:JvmName` when exposing Java-facing APIs.

## Testing Guidelines
- Use JUnit4 with Robolectric for deterministic scroll behaviour; reserve instrumentation-only checks for `androidTest`.
- Name tests `methodUnderTest_condition_expectedResult` for clarity.
- Mock adapter data with small fixtures under `library/src/test/resources/`.
- Target >90% branch coverage for LayoutManager core logic; document any intentional gaps in the PR description.

## Commit & Pull Request Guidelines
- Adopt Conventional Commits (e.g., `feat: add span lookup cache`) even though history currently only has the bootstrap commit.
- PRs need a concise summary, screenshots or GIFs for UI/scroll changes, linked issues, and a checklist of commands run.
- Request review before merging and allow CI to pass; if CI is unavailable, paste local command output in the PR.

## Architecture Notes
Keep the LayoutManager deterministic: lock adapter order, reuse span assignments, and guard against item removal churn. Document significant recycling or measurement changes in `docs/architecture.md`.

Current implementation highlights
- Deterministic, fixed-order staggered packing; absolute rects cached per position
- SpanSizeLookup (1..spanCount), full-span, multi-span contiguous window with push-down
- Column pinning to a start column; pinned ignores shortest-column heuristic
- Vertical scroll/recycling with stable coordinates; SmoothScroller support
- Partial invalidation from first affected position on adapter updates
- Optional auto-invalidate when a visible child’s measured height changes (default ON)
- Sample uses ItemDecoration for gaps, and shows 2/3 toggling, irregular heights, full-span, and pinned items
