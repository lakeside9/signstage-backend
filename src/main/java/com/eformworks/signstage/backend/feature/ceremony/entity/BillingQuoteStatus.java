package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * {@link BillingQuote}의 상태 — signstage-docs
 * business/currency-tax-internationalization-review.md 9장. 이 프로젝트는 초안(DRAFT)을
 * 저장하지 않는다 — "예상 청구 금액"(draft, {@code CeremonyService#calculateEstimatedTotal})은
 * 조회 시점 계산일 뿐이고, {@link BillingQuote} 행 자체가 생기는 순간이 곧 확정(FINALIZED)
 * 시점이다. 현재 상태는 {@link BillingQuoteStatusEvent}의 가장 최근 이벤트로 판정한다.
 */
public enum BillingQuoteStatus {
    FINALIZED,
    VOID
}
