# Repository Guidelines
The repository currently contains only licensing scaffolding. Use this guide when introducing code for the FixedOrderStaggeredGridLayoutManager library.

## Project Structure & Module Organization
- Place runtime sources in `library/src/main/java/com/.../layoutmanager/`. Mirror package names to match the LayoutManager and helper classes.
- XML attrs and drawables live in `library/src/main/res/values` and `library/src/main/res/drawable`.
- JVM unit tests go under `library/src/test/java/...`; instrumentation tests in `library/src/androidTest/java/...`.
- A sample app belongs in `sample/` with UI assets under `sample/src/main/res/`.
- Long-form docs and diagrams live under `docs/` (create as needed). Keep assets referenced via relative paths.

## Build, Test, and Development Commands
- `./gradlew assembleDebug` — builds the library and optional sample once the Gradle wrapper is added.
- `./gradlew lint ktlintCheck` — runs Android Lint and Kotlin style checks; fix issues with `./gradlew ktlintFormat`.
- `./gradlew test` — executes JVM unit tests.
- `./gradlew connectedAndroidTest` — runs instrumentation tests against a connected emulator or device.

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
