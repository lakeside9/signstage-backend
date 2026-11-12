package com.eformworks.signstage.backend.feature.order.entity;

/** 귀책 상계 상태 — 등록하면 PENDING, 정산에서 지급액에서 빼면 APPLIED, 잘못 등록했으면 CANCELLED. */
public enum PartnerOffsetStatus {
    PENDING,
    APPLIED,
    CANCELLED
}
