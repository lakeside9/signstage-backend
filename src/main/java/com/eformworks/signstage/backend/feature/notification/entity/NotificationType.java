package com.eformworks.signstage.backend.feature.notification.entity;

/**
 * 앱 안 알림 종류 — signstage-docs business/platform-order-partner-subcontract-review.md 12장(알림).
 * 파트너(발주 응답 권한자)와 플랫폼 담당자(해당 액션 권한자)에게 간다.
 */
public enum NotificationType {
    PARTNER_ORDER_ISSUED,
    PARTNER_ORDER_DUE_SOON,
    PARTNER_ORDER_CANCELLED,
    PARTNER_ORDER_MEMO_UPDATED,
    PARTNER_ORDER_ACCEPTED,
    PARTNER_ORDER_REJECTED,
    SCOPE_CHANGE_PROPOSED,
    SCOPE_CHANGE_RESPONDED,
    SCOPE_CHANGE_APPLIED,
    SCOPE_CHANGE_REQUESTED,
    INSPECTION_APPROVED,
    INSPECTION_DISPUTED,
    INSPECTION_DISPUTE_RESOLVED,
    PARTNER_PAYOUT_APPROVAL_REQUIRED,
    PARTNER_PAYOUT_PAID,
    CUSTOMER_INVOICE_OVERDUE,
    CONTACT_INQUIRY_RECEIVED
}
