package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * 선택옵션 카탈로그 코드. signstage-docs business/ceremony-billing-options-review.md 4.6절 결정 —
 * 고정된 값 집합이라 코드 레벨 enum으로 관리하고(DB 컬럼은 VARCHAR), 새 효과가 늘어나면
 * 이 enum에 값을 추가하는 배포로 대응한다(스키마 변경 불필요).
 */
public enum OptionalFeatureCode {
    /**
     * 카탈로그 표시명 "서명 하이라이트" — 그 서명자가 서명을 완료하면 프로젝터 화면에서 해당
     * 서명란이 테두리 하이라이트로 잠깐 강조된다(실제 배율 확대가 아니다, signstage-docs
     * business/ceremony-feature-migration-review.md 8.7절 참고).
     */
    SIGNER_FIELD_ZOOM,
    /** 그 CeremonyEvent에 배정된 모든 서명자의 서명이 완료되면 프로젝터 화면에 폭죽을 표시한다. */
    ALL_SIGNED_FIREWORKS,
    /** 원격 참석자가 영상으로 행사에 참여한다(상세 설계는 ceremony-video-attendance-review.md 별도 진행). */
    VIDEO_ATTENDANCE,
    /**
     * 이 조직이 태블릿 대여 서비스를 쓰는지 표시하는 용도였다 — 프로젝터 화면 등 실제 동작에
     * 연결되지 않는다(등록 시 {@code projectorEffect=false}로 만든다). 실제 대여 대수는
     * {@code CapacityType.TABLETS} 용량 추가구매로 별도 관리한다(2026-08-21 추가).
     *
     * <p><b>더 이상 신규 등록하지 않음(2026-08-30)</b> — 선택옵션 카탈로그를 전시화면/서명화면에
     * 실제 효과를 내는 항목으로 좁히기로 하면서, 화면 효과가 없는 이 코드는 관리자 카탈로그
     * 등록 화면({@code AdminBillingCatalog.tsx}의 {@code MANAGEABLE_OPTIONAL_FEATURE_CODES})의
     * 선택지에서 뺐다. 이미 등록된 행/이력/구매 스냅샷을 역직렬화해야 해서 enum 값 자체는
     * 지우지 않는다({@code VIDEO_ATTENDANCE}와 같은 선례) — signstage-docs
     * business/optional-feature-display-scope-and-plan-capacity-addon-review.md 3장 참고.
     */
    TABLET_RENTAL
}
