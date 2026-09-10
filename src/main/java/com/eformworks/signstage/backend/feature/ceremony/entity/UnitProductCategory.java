package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * 단위 상품의 상위 분류 — 기존 {@code OptionalFeatureCategory}(EQUIPMENT/PERSONNEL/APPLICATION
 * 3종, {@code CapacityAddOn}엔 컬럼조차 없었음)를 확장해 필수 5종(서명자/템플릿/테스트행사/
 * 리허설행사/본행사)까지 포함한 모든 단위 상품에 적용한다(signstage-docs
 * business/billing-catalog-unit-product-model-redesign-review.md 결정, 2026-09-10) —
 * `AdminBillingSimulator.tsx`가 화면 전용으로 쓰던 "필수옵션 상향"이라는 4번째 버킷을 {@link
 * #ESSENTIAL}로 정식 승격한 것이다. 관리자가 카탈로그 등록 시 직접 지정하는 값이라, 새 분류가
 * 필요해지면 이 enum에 값을 추가하는 배포로만 대응한다(스키마 변경 불필요) —
 * {@code exclusivityGroup}과 같은 원칙.
 */
public enum UnitProductCategory {
    /** 필수 5종(서명자/템플릿/테스트행사/리허설행사/본행사) — 플랜 기본 한도 상향 성격의 단위 상품. */
    ESSENTIAL,
    /** 장비 — 행사에 물리적으로 대여·지급되는 기기(예: 태블릿). */
    EQUIPMENT,
    /** 인력 — 사람이 직접 지원(현장 상주 또는 원격 응대)하는 서비스(예: 현장지원·온라인지원). */
    PERSONNEL,
    /** 애플리케이션 — 전시(프로젝터) 화면·서명 화면의 소프트웨어 동작 설정(예: 이벤트 효과 묶음). */
    APPLICATION
}
