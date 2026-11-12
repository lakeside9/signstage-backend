package com.eformworks.signstage.backend.feature.order.entity;

/**
 * 수주 범위 변경 상태(D08). 제안(PROPOSED)은 고객 수락과 파트너 동의(수락된 발주가 있을 때)를 모두
 * 받으면 반영(APPLIED)된다. 파트너가 거절하면 REJECTED, 플랫폼이 거두면 CANCELLED다.
 */
public enum SalesOrderChangeStatus {
    PROPOSED,
    APPLIED,
    REJECTED,
    CANCELLED
}
