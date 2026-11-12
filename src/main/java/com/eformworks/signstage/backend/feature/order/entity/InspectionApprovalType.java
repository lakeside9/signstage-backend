package com.eformworks.signstage.backend.feature.order.entity;

/** 검수 승인 방식 — 플랫폼이 직접 승인(MANUAL)했는지, 검수 기한이 지나 승인으로 간주(DEEMED)했는지(D11). */
public enum InspectionApprovalType {
    MANUAL,
    DEEMED
}
