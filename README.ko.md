# FixedOrderStaggeredGridLayoutManager

Deterministic, fixed-order staggered grid LayoutManager for RecyclerView (Kotlin).

## 소개
RecyclerView 기본 StaggeredGridLayoutManager는 스크롤이나 재측정 과정에서 아이템 순서를 다시 정렬하는 문제가 있습니다. 이 라이브러리는 스크롤 원점(0) 기준으로 계산된 절대 좌표를 캐시에 보관해, 데이터나 측정값이 변하지 않는 한 재배치를 하지 않는 결정적인(stable) 레이아웃을 제공합니다.

## 주요 특징
- **고정 순서 보장**: 모든 아이템 위치는 최초 계산 이후 변하지 않으며 스크롤 중 재배치가 없습니다.
- **가변 Span 지원**: 1..spanCount 범위의 `SpanSizeLookup`과 full-span 아이템을 지원합니다.
- **Column Pinning**: 아이템을 특정 시작 컬럼에 고정할 수 있습니다.
- **명시적 재계산 API**: `invalidateFromPosition(position)`과 `invalidateItemPositions()`로 필요한 범위만 다시 계산합니다.
- **안정적인 스크롤**: SmoothScroller, `scrollToPosition`, 상태 복원 등 표준 동작을 지원합니다.

## 설치
Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("kim.jujin:fixedorder-staggered-grid-layoutmanager:<version>")
}
```

좌표:
- GroupId: `kim.jujin`
- ArtifactId: `fixedorder-staggered-grid-layoutmanager`

## SpanSizeLookup 가이드
- `getSpanSize(position)`은 레이아웃 매니저가 뷰 홀더를 붙이기 전에 호출되므로, 해당 위치의 홀더가 존재하지 않거나 화면 밖에 있을 수 있습니다.
- 따라서 `findViewHolderForAdapterPosition(position)`은 대부분 `null`을 반환합니다. 스팬 정보는 어댑터 데이터나 별도의 캐시에서 제공해야 합니다.
- 스팬 메타데이터는 불변 구조(List, Map 등)에 보관해 결정적(deterministic) 결과를 유지하세요.
- **사전 측정이 필수는 아닙니다.** 스팬 정보만 결정적이면 되고, 실제 높이는 뷰가 처음 측정될 때 레이아웃 매니저가 지연(lazy) 측정 후 캐시에 채웁니다.
- 이후 높이가 바뀔 수 있다면 `invalidateFromPosition(position)`(부분) 또는 `invalidateItemPositions()`(전체)을 호출해 적절한 지점부터 좌표를 다시 계산하게 하세요.

## 뷰 홀더에서 높이 변경 감지하기
### 인터페이스 방식
```kotlin
class DemoViewHolder(private val container: FrameLayout) :
    RecyclerView.ViewHolder(container), FixedOrderItemSizeChangeAware {

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

### 확장 함수 방식
```kotlin
class DemoViewHolder(private val container: FrameLayout) : RecyclerView.ViewHolder(container) {
    fun rebuildChildren() {
        container.removeAllViews()
        val child = LayoutInflater.from(container.context)
            .inflate(R.layout.item_variant, container, false)
        container.addView(child, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        // 다음 레이아웃 패스에서 전체 좌표를 다시 계산하도록 요청
        notifyFixedOrderItemSizeChanged()
    }
}
```

### 어댑터에서 직접 호출
```kotlin
// 특정 아이템의 높이가 변할 가능성이 있다면
layoutManager.invalidateFromPosition(position)

// 다수 아이템이 변하면
layoutManager.invalidateItemPositions()
```

## 왜 명시적 재계산인가?
- **결정성 유지**: 뷰 내부 변경이 자동으로 “크기가 변했다”고 간주되지 않도록 하기 위함입니다.
- **예측 가능**: 언제 좌표가 바뀌는지 명확히 알고 제어할 수 있습니다.
- **효율성**: `invalidateFromPosition(position)`으로 필요한 지점부터 재계산할 수 있습니다.

## 라이프사이클 요약
1. 레이아웃 매니저는 `SpanSizeLookup`과 핀 전략을 사용해 각 위치의 스팬을 결정합니다.
2. 아직 붙지 않은 아이템이라도 결정된 스팬 정보로 좌표를 미리 계산합니다.
3. 실제 뷰가 붙을 때 측정된 높이가 캐시에 저장됩니다.
4. 높이가 변할 수 있는 경우 위 재계산 API로 명시적으로 갱신을 요청합니다.

## 샘플 앱
모듈 `:sample`에서 2·3 스팬 토글, full-span, pinned 아이템, 불규칙 높이를 데모로 제공합니다.

## 라이선스
MIT — [LICENSE](LICENSE) 참조.
