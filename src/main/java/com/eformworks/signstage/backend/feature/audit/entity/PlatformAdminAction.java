package com.eformworks.signstage.backend.feature.audit.entity;

/**
 * 플랫폼 관리자가 조직 스코핑을 우회해 수행하는 제어 행위의 종류
 * (signstage-docs business/user-organization-design.md 7.4절).
 */
public enum PlatformAdminAction {
    UPDATE_USER_STATUS,
    UNLOCK_USER,
    FORCE_PASSWORD_RESET,
    CREATE_USER,
    CREATE_ACCOUNT,
    REVOKE_ACCOUNT,
    UPDATE_ORGANIZATION_STATUS,
    UPDATE_ORGANIZATION_INFO,
    CREATE_ORGANIZATION,
    FORCE_ADD_MEMBER,
    FORCE_UPDATE_MEMBER_ROLE,
    FORCE_REMOVE_MEMBER,
    FORCE_WITHDRAW_USER,
    UPDATE_ACCOUNT_ROLE,
    REJECT_ORGANIZATION_REQUEST,
    CREATE_BILLING_PLAN,
    UPDATE_BILLING_PLAN,
    DELETE_BILLING_PLAN,
    /** {@code UnitProduct} 통합 카탈로그(signstage-docs
     * business/billing-catalog-unit-product-model-redesign-review.md, 2026-09-10) — 옛
     * {@code CREATE/UPDATE_OPTIONAL_FEATURE}, {@code CREATE/UPDATE_CAPACITY_ADDON}을 대체했다. */
    CREATE_UNIT_PRODUCT,
    UPDATE_UNIT_PRODUCT,
    DELETE_UNIT_PRODUCT,
    REORDER_UNIT_PRODUCTS,
    UPDATE_CEREMONY_STATUS,
    UPDATE_CEREMONY_FINAL_DISCOUNT,
    UPDATE_ORGANIZATION_BILLING_PLAN_DISCOUNT,
    /** 옛 {@code APPROVE/REJECT_CAPACITY_PURCHASE}, {@code APPROVE/REJECT_OPTIONAL_FEATURE_PURCHASE} 통합. */
    APPROVE_UNIT_PRODUCT_PURCHASE,
    REJECT_UNIT_PRODUCT_PURCHASE,
    /** 이미 승인된 구매를 나중에 취소 — signstage-docs business/ceremony-unit-product-purchase-cancellation-review.md(2026-09-12). */
    CANCEL_UNIT_PRODUCT_PURCHASE,
    CREATE_CEREMONY_EFFECT_DEFINITION,
    UPDATE_CEREMONY_EFFECT_DEFINITION,
    REORDER_CEREMONY_EFFECT_DEFINITIONS,
    /** 조직 구독/계약(signstage-docs business/organization-event-discount-pricing-review.md 8장, 2026-09-10). */
    APPROVE_ORGANIZATION_SUBSCRIPTION,
    REJECT_ORGANIZATION_SUBSCRIPTION,
    APPROVE_ORGANIZATION_SUBSCRIPTION_CANCELLATION,
    REJECT_ORGANIZATION_SUBSCRIPTION_CANCELLATION,
    /** 공지사항/FAQ/행사별 1:1 문의 — signstage-docs business/partner-support-center-review.md(2026-09-12). */
    CREATE_ANNOUNCEMENT,
    UPDATE_ANNOUNCEMENT,
    DELETE_ANNOUNCEMENT,
    CREATE_FAQ,
    UPDATE_FAQ,
    DELETE_FAQ,
    REORDER_FAQS,
    /** 답변/종료를 한 액션으로 묶는다({@code APPROVE/REJECT_UNIT_PRODUCT_PURCHASE}와 달리 성격이 대칭이라 통합). */
    REPLY_CEREMONY_INQUIRY,
    CLOSE_CEREMONY_INQUIRY,
    /** 현장지원 요청에 거리 등을 보고 실제 금액을 매긴다 — signstage-docs business/onsite-support-negotiation-and-billing-classification-review.md 3.2절(2026-09-12). */
    QUOTE_ONSITE_SUPPORT_REQUEST,
    /** 플랫폼 수주·파트너 발주 — signstage-docs business/platform-order-partner-subcontract-review.md 4.1절. */
    VERIFY_PARTNER_SETTLEMENT_ACCOUNT,
    CREATE_CUSTOMER,
    UPDATE_CUSTOMER,
    CREATE_CUSTOMER_PRICE,
    CREATE_REGION_SURCHARGE,
    CREATE_PARTNER_RATE_CARD,
    CLOSE_ORDER_PRICE_PERIOD,
    DELETE_ORDER_PRICE,
    UPDATE_ORDER_PRICE,
    /** 견적 공통 문구(2026-11-13). */
    CREATE_QUOTE_TEMPLATE,
    UPDATE_QUOTE_TEMPLATE,
    CLOSE_QUOTE_TEMPLATE,
    DELETE_QUOTE_TEMPLATE,
    CREATE_SALES_ORDER,
    UPDATE_SALES_ORDER,
    ACCEPT_SALES_ORDER,
    CANCEL_SALES_ORDER,
    /** 고객 사정 취소 — signstage-docs business/platform-order-pending-items.md C02. */
    CUSTOMER_CANCEL_SALES_ORDER,
    RECORD_CUSTOMER_REFUND,
    ISSUE_PARTNER_ORDER,
    CANCEL_PARTNER_ORDER,
    UPDATE_PARTNER_ORDER_MEMO,
    /** 검수·파트너 지급 — signstage-docs business/platform-order-partner-subcontract-review.md D11·D13. */
    APPROVE_ORDER_INSPECTION,
    CREATE_PARTNER_PAYOUTS,
    MARK_PARTNER_PAYOUT_PAID,
    CANCEL_PARTNER_PAYOUT,
    /** 감액 이의 조정·귀책 상계·고액 지급 승인 — D13·D19. */
    RESOLVE_INSPECTION_DISPUTE,
    REGISTER_PARTNER_OFFSET,
    CANCEL_PARTNER_OFFSET,
    APPROVE_PARTNER_PAYOUT,
    /** 수주 범위 변경 — signstage-docs business/platform-order-partner-subcontract-review.md D08. */
    PROPOSE_SALES_ORDER_CHANGE,
    RECORD_SALES_ORDER_CHANGE_ACCEPTANCE,
    CANCEL_SALES_ORDER_CHANGE,
    CLOSE_SCOPE_CHANGE_REQUEST,
    /** 고객 청구·수납 — signstage-docs business/platform-order-partner-subcontract-review.md D12·D16. */
    ISSUE_CUSTOMER_INVOICE,
    RECORD_CUSTOMER_PAYMENT,
    RECORD_CUSTOMER_TAX_INVOICE,
    CANCEL_CUSTOMER_INVOICE
}
