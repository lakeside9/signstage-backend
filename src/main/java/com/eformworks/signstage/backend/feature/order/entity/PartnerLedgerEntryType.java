package com.eformworks.signstage.backend.feature.order.entity;

/** 파트너 매입채무 원장 항목 — 검수 승인으로 생긴 지급 의무(PAYABLE, +)와 지급(PAYMENT, −). */
public enum PartnerLedgerEntryType {
    PAYABLE,
    PAYMENT
}
