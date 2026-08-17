package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * 선택옵션 카탈로그 코드. signstage-docs business/ceremony-billing-options-review.md 4.6절 결정 —
 * 고정된 값 집합이라 코드 레벨 enum으로 관리하고(DB 컬럼은 VARCHAR), 새 효과가 늘어나면
 * 이 enum에 값을 추가하는 배포로 대응한다(스키마 변경 불필요).
 */
public enum OptionalFeatureCode {
    /** 서명 진행 중 서명자의 서명란이 프로젝터 화면에서 확대되어 보인다. */
    SIGNER_FIELD_ZOOM,
    /** 그 CeremonyEvent에 배정된 모든 서명자의 서명이 완료되면 프로젝터 화면에 폭죽을 표시한다. */
    ALL_SIGNED_FIREWORKS,
    /** 원격 참석자가 영상으로 행사에 참여한다(상세 설계는 ceremony-video-attendance-review.md 별도 진행). */
    VIDEO_ATTENDANCE
}
