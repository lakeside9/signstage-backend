package com.eformworks.signstage.backend.feature.order.entity;

/**
 * 발주 상태. ISSUED(응답 대기)에서 파트너가 수락·거절한다. 응답 기한이 지난 ISSUED는 응답할 수 없고
 * 화면에는 EXPIRED로 보인다 — 다음 발주를 발급할 때 EXPIRED로 확정한다.
 */
public enum PartnerOrderStatus {
    ISSUED,
    ACCEPTED,
    REJECTED,
    EXPIRED,
    CANCELLED
}
