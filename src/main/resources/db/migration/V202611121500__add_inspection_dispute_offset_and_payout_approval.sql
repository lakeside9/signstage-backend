-- 감액 기준·이의 제기·귀책 상계(D13)와 고액 지급 승인(D19) — signstage-docs
-- business/platform-order-partner-subcontract-review.md D13·D19(2026-11-12 권장안으로 결정).

-- 감액 유형(계약 기준표)과 파트너 이의 제기. 이의 제기 중인 검수는 정산하지 않는다.
ALTER TABLE order_inspections
    ADD COLUMN deduction_type VARCHAR(40) NULL AFTER deduction_reason,
    ADD COLUMN dispute_status VARCHAR(20) NULL AFTER partner_payout_id,
    ADD COLUMN dispute_reason VARCHAR(1000) NULL AFTER dispute_status,
    ADD COLUMN disputed_at TIMESTAMP(6) NULL AFTER dispute_reason,
    ADD COLUMN disputed_by BIGINT NULL AFTER disputed_at,
    ADD COLUMN dispute_resolution_note VARCHAR(1000) NULL AFTER disputed_by,
    ADD COLUMN dispute_resolved_at TIMESTAMP(6) NULL AFTER dispute_resolution_note,
    ADD COLUMN dispute_resolved_by BIGINT NULL AFTER dispute_resolved_at;

-- 지급 건: 검수 합계(gross) − 상계(offset) = 지급액(amount). 고액은 SUPER 승인 후에만 지급 완료로 기록한다.
ALTER TABLE partner_payouts
    ADD COLUMN gross_amount DECIMAL(19,4) NULL AFTER currency_code,
    ADD COLUMN offset_amount DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER gross_amount,
    ADD COLUMN approval_required BOOLEAN NOT NULL DEFAULT FALSE AFTER status,
    ADD COLUMN approved_by BIGINT NULL AFTER approval_required,
    ADD COLUMN approved_at TIMESTAMP(6) NULL AFTER approved_by;

UPDATE partner_payouts SET gross_amount = amount;

ALTER TABLE partner_payouts MODIFY COLUMN gross_amount DECIMAL(19,4) NOT NULL;

-- 파트너 귀책 상계 — 고객 환불 중 파트너 귀책분 등. 다음 정산에서 지급액에서 빼고, 지급액을 넘으면
-- 넘는 만큼을 새 상계(이월)로 남긴다. 등록·취소는 원장에 남기고, 이월은 같은 채무라 원장에 남기지 않는다.
CREATE TABLE partner_offsets (
    id                              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id                 BIGINT NOT NULL,
    partner_order_id                BIGINT NOT NULL,
    currency_code                   CHAR(3) NOT NULL,
    amount                          DECIMAL(19,4) NOT NULL,
    reason                          VARCHAR(500) NOT NULL,
    occurred_on                     DATE NOT NULL,
    status                          VARCHAR(20) NOT NULL,
    partner_payout_id               BIGINT NULL,
    carried_over_from_payout_id     BIGINT NULL,
    cancelled_at                    TIMESTAMP(6) NULL,
    cancellation_reason             VARCHAR(500) NULL,
    created_by                      BIGINT NULL,
    updated_by                      BIGINT NULL,
    created_at                      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_partner_offsets_pending (organization_id, status, occurred_on),
    CONSTRAINT fk_partner_offset_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_partner_offset_partner_order FOREIGN KEY (partner_order_id) REFERENCES partner_orders (id),
    CONSTRAINT fk_partner_offset_payout FOREIGN KEY (partner_payout_id) REFERENCES partner_payouts (id),
    CONSTRAINT fk_partner_offset_carried_from FOREIGN KEY (carried_over_from_payout_id) REFERENCES partner_payouts (id),
    CONSTRAINT fk_partner_offset_currency FOREIGN KEY (currency_code) REFERENCES currencies (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 액션: 큰 감액(발주 금액의 30% 초과) 승인·이의 조정·고액 지급 승인은 PLATFORM_SUPER만(D13·D19).
INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT a.permission_key, 'ACTION', 'PLATFORM', m.id, a.label_key, 0
FROM (
    SELECT 'ACTION_INSPECTION_LARGE_DEDUCTION' AS permission_key, 'MENU_ORDER_INSPECTIONS' AS menu_key,
           'permission.action.inspectionLargeDeduction' AS label_key
    UNION ALL SELECT 'ACTION_INSPECTION_DISPUTE_RESOLVE', 'MENU_ORDER_INSPECTIONS', 'permission.action.inspectionDisputeResolve'
    UNION ALL SELECT 'ACTION_PARTNER_PAYOUT_LARGE_APPROVE', 'MENU_PARTNER_PAYOUTS', 'permission.action.partnerPayoutLargeApprove'
) a
JOIN menus m ON m.menu_key = a.menu_key;

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, (roles.role_value = 'PLATFORM_SUPER')
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key IN (
    'ACTION_INSPECTION_LARGE_DEDUCTION', 'ACTION_INSPECTION_DISPUTE_RESOLVE', 'ACTION_PARTNER_PAYOUT_LARGE_APPROVE'
);
