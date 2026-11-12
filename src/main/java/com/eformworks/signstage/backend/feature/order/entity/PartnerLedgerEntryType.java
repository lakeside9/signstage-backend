package com.eformworks.signstage.backend.feature.order.entity;

/**
 * 파트너 매입채무 원장 항목 — 검수 승인으로 생긴 지급 의무(PAYABLE, +), 지급(PAYMENT, −), 귀책 상계
 * 등록(OFFSET, −)과 취소(+), 이의 조정으로 바뀐 지급 대상 금액 차이(ADJUSTMENT, ±).
 */
public enum PartnerLedgerEntryType {
    PAYABLE,
    PAYMENT,
    OFFSET,
    ADJUSTMENT
}
