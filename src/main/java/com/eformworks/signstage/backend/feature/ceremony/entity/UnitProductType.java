package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * 카탈로그 "단위 상품" 정체성 구분 — 기존 {@code CapacityType}(8종) + {@code OptionalFeatureCode}의
 * {@code EVENT_EFFECT_BUNDLE}을 하나로 합친 enum이다. signstage-docs
 * business/billing-catalog-unit-product-model-redesign-review.md 결정(2026-09-10, 안건 2) —
 * "선택옵션(토글형)과 용량 추가구매(누적 수량형)를 통합하지 않는다"던 이전 결정
 * ({@code optional-feature-capacity-addon-unification-review.md}, 2026-08-30)을 뒤집었다 —
 * 모든 단위 상품이 이제 수량(quantity)을 갖고, 토글은 그 수량이 0/1인 특수 사례일 뿐이라 계산
 * 모델이 다르다는 원래 통합 반대 근거가 사라졌다.
 *
 * <p><b>레거시 값은 유지하지 않는다(3.1-2절 결정)</b> — {@code SIGNER_FIELD_ZOOM}/
 * {@code ALL_SIGNED_FIREWORKS}/{@code VIDEO_ATTENDANCE}/{@code TABLET_RENTAL}은 신규 등록이
 * 이미 막혀 있었고, 확인 결과 이 값들로 등록된 카탈로그 행이 0건이라 역직렬화 부담 없이 완전히
 * 제외했다. {@code ONSITE_SUPPORT}/{@code ONLINE_SUPPORT}도 기존에는 {@code CapacityType}과
 * {@code OptionalFeatureCode} 양쪽에 따로 존재해 이중 청구 위험을 낳았는데(짝 명시화 문서 9장),
 * 이 통합으로 값이 하나뿐이라 그런 중복 표현 자체가 구조적으로 불가능해졌다.
 *
 * <p><b>플랜 기본 포함 수량 제한 폐지(2026-09-10, 사용자 지시)</b> — 예전엔
 * {@code isPlanIncludable()}이 SIGNERS/TEMPLATES/TEST_EVENTS/REHEARSAL_EVENTS/MAIN_EVENTS
 * 5종만 {@code BillingPlanUnitProduct.includedQuantity}를 가질 수 있도록 잠갔고(옛
 * {@code CapacityType.isPlanIncludable()}을 그대로 복사한 값), TABLETS/ONSITE_SUPPORT/
 * ONLINE_SUPPORT/EVENT_EFFECT_BUNDLE 4종은 화면에서 수량 입력이 막혀 있었다. 사용자가 "태블릿/
 * 현장지원/온라인지원 3종도 수량을 입력할 수 있어야 한다"고 지적해 확인한 결과, 이 제한은
 * 재설계(2026-09-10) 과정에서 생긴 의도치 않은 회귀였다 — 재설계 문서 3.3/3.6절은 옛
 * {@code BillingPlanOptionalFeature}(선택옵션 무료 포함)의 동작을 그대로 옮긴다고 명시했는데,
 * 실제 구현은 옛 {@code CapacityType}의 제한만 복사해 그 약속을 어겼다. 지금은 모든 타입이
 * 제한 없이 {@code includedQuantity}(0 이상)를 가질 수 있다 — {@code isPlanIncludable()}/
 * {@code planIncludableTypes()}는 삭제했다(더 쓰는 곳이 없었다 — 서버도 원래 강제하지 않았고,
 * 프런트 전용 잠금이었다).
 *
 * <p>같은 확인 과정에서 "추가구매 후보(purchasable)"라는 별도 플래그도 없앴다 —
 * {@code BillingPlanUnitProduct}에 행이 있으면(포함 수량이 0이든 N이든) 그 자체로 그 플랜의
 * 행사가 추가구매할 수 있다는 뜻이 됐다({@code CeremonyService#retrievePurchasableUnitProductIds}
 * 참고). "기본 포함 없이 추가구매만 허용"은 이제 {@code includedQuantity=0}인 행으로 표현한다
 * (실사용 데이터 확인 결과 정확히 이 조합 — TABLETS/ONSITE_SUPPORT를 수량 0으로 등록해두고
 * purchasable만 true로 큐레이션하는 패턴 — 이 이미 쓰이고 있었다. 컬럼을 지워도 기존 행은
 * 전부 그대로 같은 뜻이 된다).
 */
public enum UnitProductType {
    SIGNERS,
    TEMPLATES,
    TEST_EVENTS,
    /** 하위 행사 REHEARSAL 구분의 한도 — TEST와는 별도 버킷이다. */
    REHEARSAL_EVENTS,
    MAIN_EVENTS,
    /** 태블릿 대여 대수. */
    TABLETS,
    /** 현장지원 실제 지원 건수 — 고객 정산에서 파트너가 실고객에게 직접 판매하는 고정가 카탈로그(수도권/근·중·원거리 4종)가 쓴다. */
    ONSITE_SUPPORT,
    /**
     * 현장지원 요청(관리자 견적) 전용 앵커 상품 — {@link #ONSITE_SUPPORT}와 별개 타입이다
     * (2026-09-12, signstage-docs
     * business/onsite-support-negotiation-and-billing-classification-review.md 3.2절
     * 결정). {@code CeremonyOnsiteSupportRequest}가 수락되면 이 타입의 단위 상품(정확히
     * 1행만 존재해야 한다, 마이그레이션이 시딩)을 참조하는 구매를 만든다 — 카테고리는
     * PERSONNEL이지만 {@code UnitProduct#isPlatformUsageFee()}는 true다. 관리자 카탈로그
     * 등록 화면에서 새로 만들 수 없다(단위 상품 종류 선택 목록에 없음) — 오직 이 하나뿐이어야
     * 한다.
     */
    ONSITE_SUPPORT_REQUEST,
    /** 온라인지원 실제 지원 건수. */
    ONLINE_SUPPORT,
    /**
     * 이벤트 효과 묶음 — "프로젝터 화면 이벤트 효과 3종/5종"처럼, 관리자가 효과 카탈로그
     * ({@code CeremonyEffectDefinition}) 중 몇 개를 묶어 파는 상품이다. 이 타입 하나를 여러
     * {@code UnitProduct} 행이 공유한다(묶음마다 새 값이 필요하지 않다 — 기존
     * {@code OptionalFeatureCode.EVENT_EFFECT_BUNDLE}과 같은 원칙). 묶음이 여는 효과 목록은
     * {@code CeremonyEffectDefinitionOption}이 갖는다. 토글형(수량 0 또는 1)으로 다룬다 —
     * {@link #isToggle()}이 이 검증에 쓰인다.
     */
    EVENT_EFFECT_BUNDLE;

    /**
     * 수량이 0 또는 1로만 의미가 있는 토글형 타입인지 — 지금은 {@link #EVENT_EFFECT_BUNDLE}만
     * 해당한다(묶음을 "가졌다/안 가졌다"만 의미가 있고, 2개·3개를 가진다는 개념이 없다). 플랜
     * 구성({@code BillingPlanService#resolveUnitProducts})과 행사 추가구매
     * ({@code CeremonyService#purchaseUnitProducts}) 양쪽 모두 이 값으로 수량 상한(1)을
     * 검증한다 — 전에는 두 곳이 각자 {@code type == EVENT_EFFECT_BUNDLE}를 인라인으로 검사해
     * 새 토글형 타입이 추가되면 양쪽 다 고쳐야 했다.
     */
    public boolean isToggle() {
        return this == EVENT_EFFECT_BUNDLE;
    }
}
