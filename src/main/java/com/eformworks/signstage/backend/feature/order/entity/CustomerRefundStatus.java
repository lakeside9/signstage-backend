package com.eformworks.signstage.backend.feature.order.entity;

/** 고객 환불 상태 — 고객 취소로 생기면 PENDING, 시스템 밖에서 돌려준 뒤 기록하면 REFUNDED. */
public enum CustomerRefundStatus {
    PENDING,
    REFUNDED
}
