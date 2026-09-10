package com.eformworks.signstage.backend.feature.ceremony.entity;

import java.util.EnumSet;
import java.util.Set;

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
 */
public enum UnitProductType {
    SIGNERS,
    TEMPLATES,
    TEST_EVENTS,
    /** 하위 행사 REHEARSAL 구분의 한도 — TEST와는 별도 버킷이다. */
    REHEARSAL_EVENTS,
    MAIN_EVENTS,
    /** 태블릿 대여 대수. 플랜 기본 포함 개념이 없다 — {@link #isPlanIncludable()}가 {@code false}. */
    TABLETS,
    /** 현장지원 실제 지원 건수. 플랜 기본 포함 개념이 없다. */
    ONSITE_SUPPORT,
    /** 온라인지원 실제 지원 건수. 플랜 기본 포함 개념이 없다. */
    ONLINE_SUPPORT,
    /**
     * 이벤트 효과 묶음 — "프로젝터 화면 이벤트 효과 3종/5종"처럼, 관리자가 효과 카탈로그
     * ({@code CeremonyEffectDefinition}) 중 몇 개를 묶어 파는 상품이다. 이 타입 하나를 여러
     * {@code UnitProduct} 행이 공유한다(묶음마다 새 값이 필요하지 않다 — 기존
     * {@code OptionalFeatureCode.EVENT_EFFECT_BUNDLE}과 같은 원칙). 묶음이 여는 효과 목록은
     * {@code CeremonyEffectDefinitionOption}이 갖는다. 토글형(수량 0 또는 1)으로 다룬다.
     */
    EVENT_EFFECT_BUNDLE;

    /**
     * 이 값이 {@link com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlan}에
     * 기본 포함 수량으로 등록될 수 있는지 — 기존 {@code CapacityType.isPlanIncludable()}과 같은
     * 집합(SIGNERS/TEMPLATES/TEST_EVENTS/REHEARSAL_EVENTS/MAIN_EVENTS)을 그대로 승계한다.
     */
    public boolean isPlanIncludable() {
        return PLAN_INCLUDABLE.contains(this);
    }

    private static final Set<UnitProductType> PLAN_INCLUDABLE =
            EnumSet.of(SIGNERS, TEMPLATES, TEST_EVENTS, REHEARSAL_EVENTS, MAIN_EVENTS);

    public static Set<UnitProductType> planIncludableTypes() {
        return EnumSet.copyOf(PLAN_INCLUDABLE);
    }
}
