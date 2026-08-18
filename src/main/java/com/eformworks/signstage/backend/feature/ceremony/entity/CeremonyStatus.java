package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * 행사 마스터(Ceremony) 전체 완료 상태. 하위 행사(CeremonyEvent)의 상태({@link CeremonyEventStatus})와
 * 별개다 — 이 Ceremony 아래 만들어진 본행사(MAIN 타입 CeremonyEvent)가 전부 FINISHED + 결과 PDF
 * 생성까지 끝나면 자동으로 COMPLETED로 전이한다({@code CeremonyResultService#generateResults}).
 * COMPLETED가 되면 그 Ceremony 하위 데이터는 조회만 가능하다. 플랫폼 관리자는 양방향으로
 * 강제 변경할 수 있다(signstage-docs business/ceremony-feature-migration-review.md 참고).
 */
public enum CeremonyStatus {
    IN_PROGRESS,
    COMPLETED
}
