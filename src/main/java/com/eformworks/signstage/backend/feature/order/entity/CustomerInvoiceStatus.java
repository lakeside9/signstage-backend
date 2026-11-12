package com.eformworks.signstage.backend.feature.order.entity;

/** 고객 청구 상태 — 발행(ISSUED) 후 입금 기록하면 PAID. 입금 전에는 취소(CANCELLED)할 수 있다. */
public enum CustomerInvoiceStatus {
    ISSUED,
    PAID,
    CANCELLED
}
