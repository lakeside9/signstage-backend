package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * 선택옵션 카탈로그의 상위 분류 — 장비/인력/애플리케이션 3분류. signstage-docs
 * business/ceremony-support-services-billing-review.md 결정(2026-09-08, 4.3절 안 B 채택).
 * 관리자가 카탈로그 등록 시 직접 지정하는 카탈로그 데이터라, 새 분류가 필요해져도 이 enum에
 * 값을 추가하는 배포로만 대응한다(스키마 변경 불필요) — {@code exclusivityGroup}과 같은 원칙.
 *
 * <p>{@link CapacityAddOn}에는 이 필드가 없다(4.3절 결정).
 */
public enum OptionalFeatureCategory {
    /** 장비 — 행사에 물리적으로 대여·지급되는 기기(예: 태블릿 대여). */
    EQUIPMENT,
    /** 인력 — 사람이 직접 지원(현장 상주 또는 원격 응대)하는 서비스(예: 현장지원·온라인지원). */
    PERSONNEL,
    /** 애플리케이션 — 전시(프로젝터) 화면·서명 화면의 소프트웨어 동작 설정(예: 이벤트 효과 묶음). */
    APPLICATION
}
