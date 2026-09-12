package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * 현장지원 요청(관리자 견적) 협상 상태 — signstage-docs
 * business/onsite-support-negotiation-and-billing-classification-review.md 3.2절 결정
 * (2026-09-12). 이 코드베이스에 없던 "요청 → 관리자가 값을 매김 → 요청자가 그 값을
 * 수락/거부" 협상 패턴을 새로 도입한다(기존 승인/반려는 항상 "이미 정해진 값을 그대로
 * 승인/반려"하는 모양이었다).
 *
 * <pre>
 * (파트너 요청, 일시+장소 입력) → REQUESTED
 * REQUESTED --[관리자가 금액을 매김]--> QUOTED
 * QUOTED --[파트너 수락]--> ACCEPTED (동시에 CeremonyUnitProductPurchase 1건 생성)
 * QUOTED --[파트너 거부]--> DECLINED (종결, 재협상 없음 — 다시 필요하면 새 요청)
 * </pre>
 */
public enum OnsiteSupportRequestStatus {
    REQUESTED,
    QUOTED,
    ACCEPTED,
    DECLINED
}
