package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * 행사 마스터(Ceremony) 전체 완료 상태. 하위 행사(CeremonyEvent)의 상태({@link CeremonyEventStatus})와
 * 별개다 — 이 Ceremony 아래 만들어진 본행사(MAIN 타입 CeremonyEvent)가 전부 FINISHED 또는 FORCE_FINISHED이고
 * 결과 PDF 생성까지 끝나면 자동으로 COMPLETED로 전이한다({@code CeremonyResultService#generateResults}).
 * COMPLETED가 되면 그 Ceremony 하위 데이터는 조회만 가능하다. 플랫폼 관리자는 IN_PROGRESS → COMPLETED로만
 * 강제 변경할 수 있고, 완료를 되돌리는 방법은 없다(2026-11-13 사용자 결정).
 *
 * <p>{@code DRAFT}는 플랜 확정 전 상태다(signstage-docs
 * business/ceremony-plan-confirmation-review.md) — 새로 만든 Ceremony는 이 상태로 시작하고,
 * 이 상태에서만 플랜을 바꿀 수 있다({@code CeremonyPlanService#updatePlan}). "플랜 확정"
 * ({@code CeremonyPlanService#confirmPlan})으로 {@code DRAFT → IN_PROGRESS}로 단방향 전이하면
 * 플랜이 고정되고 그때부터 서명자/문서/하위 행사를 등록할 수 있다(`CeremonyEventStatus`가
 * 이미 쓰는 이름을 그대로 재사용한 것 — 배포 전 기존 Ceremony는 전부 IN_PROGRESS/COMPLETED라
 * 이 상태를 거치지 않는다).
 *
 * <p>{@code CANCELLED}는 고객 사정으로 취소된 플랫폼 수주 행사다(signstage-docs
 * business/platform-order-pending-items.md C01, 2026-11-13) — 수주 "고객 취소"로만 들어가고 되돌리지 않는다.
 * 완료와 같이 조회만 가능하고, 파트너 접근은 막힌 채로 둔다.
 */
public enum CeremonyStatus {
    DRAFT,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
