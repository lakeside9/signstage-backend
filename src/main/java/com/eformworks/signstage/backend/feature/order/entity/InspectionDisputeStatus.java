package com.eformworks.signstage.backend.feature.order.entity;

/** 감액 이의 제기 상태(D13) — 파트너가 제기하면 OPEN(정산 보류), 플랫폼이 조정하면 RESOLVED. */
public enum InspectionDisputeStatus {
    OPEN,
    RESOLVED
}
