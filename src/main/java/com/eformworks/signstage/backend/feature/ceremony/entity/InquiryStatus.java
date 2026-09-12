package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * 행사별 1:1 문의(CeremonyInquiry) 상태 — signstage-docs
 * business/partner-support-center-review.md 5.2절. 메시지 추가 시 서비스가 자동으로 바꾼다
 * (사람이 수동으로 상태를 고르지 않는다) — 파트너가 쓰면 OPEN, 관리자가 쓰면 ANSWERED.
 * {@code CLOSED}만 명시적 액션(양쪽 누구든 가능)이고, 종결이라 재오픈하지 않는다(같은 문서
 * 9장 결정 #5) — 다시 물어보려면 새 문의를 만든다.
 */
public enum InquiryStatus {
    OPEN,
    ANSWERED,
    CLOSED
}
