package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * 선택옵션 카탈로그 코드. signstage-docs business/ceremony-billing-options-review.md 4.6절 결정 —
 * 고정된 값 집합이라 코드 레벨 enum으로 관리하고(DB 컬럼은 VARCHAR), 새 효과가 늘어나면
 * 이 enum에 값을 추가하는 배포로 대응한다(스키마 변경 불필요).
 *
 * <p><b>{@code EVENT_EFFECT_BUNDLE}은 예외다(2026-09-08 결정)</b> — 이 코드 하나를 여러
 * {@code optional_features} 행이 공유한다({@code capacity_addons.capacity_type}이
 * {@code CapacityAddOn} 여러 상품에 공유되는 것과 같은 패턴, {@code optional_features.code}의
 * UNIQUE 제약도 이때 없앴다). "이벤트 효과 3종", "5종"처럼 관리자가 효과를 몇 개씩 묶어 파는
 * 상품을 이 코드 하나로 계속 만들 수 있다 — 새 묶음을 추가해도 이 enum을 건드릴 필요가 없다.
 * 묶음이 실제로 여는 효과 목록은 {@code CeremonyEffectDefinitionOption} N:N 매핑이 갖는다.
 */
public enum OptionalFeatureCode {
    /**
     * 카탈로그 표시명 "서명 하이라이트" — 그 서명자가 서명을 완료하면 프로젝터 화면에서 해당
     * 서명란이 테두리 하이라이트로 잠깐 강조된다(실제 배율 확대가 아니다, signstage-docs
     * business/ceremony-feature-migration-review.md 8.7절 참고).
     *
     * <p><b>더 이상 신규 등록하지 않음(2026-09-08)</b> — {@code EVENT_EFFECT_BUNDLE}로
     * 대체됐다. 이미 등록된 행(비활성화됨)/이력/구매 스냅샷을 역직렬화해야 해서 enum 값 자체는
     * 지우지 않는다({@code TABLET_RENTAL}과 같은 선례).
     */
    SIGNER_FIELD_ZOOM,
    /**
     * 그 CeremonyEvent에 배정된 모든 서명자의 서명이 완료되면 프로젝터 화면에 폭죽을 표시한다.
     *
     * <p><b>더 이상 신규 등록하지 않음(2026-09-08)</b> — {@code EVENT_EFFECT_BUNDLE}로
     * 대체됐다(이 코드였던 기존 행 자체는 그 새 코드로 이름·용도가 바뀌어 재사용됐다). 이미
     * 남아있을 수 있는 이력/구매 스냅샷을 역직렬화해야 해서 enum 값 자체는 지우지 않는다.
     */
    ALL_SIGNED_FIREWORKS,
    /**
     * 이벤트 효과 묶음 상품 — "프로젝터 화면 이벤트 효과 3종/5종"처럼, 관리자가 효과 카탈로그
     * ({@code CeremonyEffectDefinition}) 중 몇 개를 묶어 파는 상품이다. 이 코드 하나를 여러
     * {@code optional_features} 행이 공유한다(클래스 주석 참고) — 묶음마다 새 enum 값이
     * 필요하지 않다. 묶음이 여는 효과 목록은 {@code CeremonyEffectDefinitionOption}이 갖는다.
     */
    EVENT_EFFECT_BUNDLE,
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
