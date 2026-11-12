-- 검수와 파트너 지급 — signstage-docs business/platform-order-partner-subcontract-review.md 11장 6단계,
-- D11(검수 7일·기한 경과 시 승인 간주·월 합산 지급, 2026-11-12 결정), D13(하자·지체 감액은 검수에서 기록).

-- 행사가 처음 완료된 시각 — 검수 기한의 기준. 이미 완료된 행사는 마지막 수정 시각으로 채운다.
ALTER TABLE ceremonies
    ADD COLUMN completed_at TIMESTAMP(6) NULL AFTER status;

UPDATE ceremonies SET completed_at = updated_at WHERE status = 'COMPLETED';

-- 월 합산 지급 — 파트너별·검수 승인월별 한 건. 지급 완료로 바꿀 때 원장에 지급을 남긴다.
CREATE TABLE partner_payouts (
    id                          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id             BIGINT NOT NULL,
    settlement_month            CHAR(7) NOT NULL,
    currency_code               CHAR(3) NOT NULL,
    amount                      DECIMAL(19,4) NOT NULL,
    due_date                    DATE NOT NULL,
    status                      VARCHAR(20) NOT NULL,
    bank_name                   VARCHAR(50) NULL,
    bank_account_number         VARCHAR(50) NULL,
    account_holder_name         VARCHAR(100) NULL,
    paid_date                   DATE NULL,
    transferred_amount          DECIMAL(19,4) NULL,
    payment_memo                VARCHAR(500) NULL,
    paid_by                     BIGINT NULL,
    cancelled_at                TIMESTAMP(6) NULL,
    cancellation_reason         VARCHAR(500) NULL,
    created_by                  BIGINT NULL,
    updated_by                  BIGINT NULL,
    created_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_partner_payouts_month (settlement_month, status),
    INDEX idx_partner_payouts_org (organization_id, settlement_month),
    CONSTRAINT fk_partner_payout_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_partner_payout_currency FOREIGN KEY (currency_code) REFERENCES currencies (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 발주별 검수 — 수락한 발주의 행사가 완료되면 만든다. 배치·스케줄러가 만들고 바꾸므로 작성자는 비울 수 있다.
CREATE TABLE order_inspections (
    id                          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    partner_order_id            BIGINT NOT NULL,
    organization_id             BIGINT NOT NULL,
    ceremony_completed_at       TIMESTAMP(6) NOT NULL,
    due_date                    DATE NOT NULL,
    status                      VARCHAR(20) NOT NULL,
    approval_type               VARCHAR(20) NULL,
    defect_note                 VARCHAR(1000) NULL,
    delay_note                  VARCHAR(1000) NULL,
    deduction_amount            DECIMAL(19,4) NOT NULL,
    deduction_reason            VARCHAR(500) NULL,
    payable_amount              DECIMAL(19,4) NULL,
    approved_date               DATE NULL,
    approved_at                 TIMESTAMP(6) NULL,
    approved_by                 BIGINT NULL,
    partner_payout_id           BIGINT NULL,
    created_by                  BIGINT NULL,
    updated_by                  BIGINT NULL,
    created_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_order_inspection_partner_order UNIQUE (partner_order_id),
    INDEX idx_order_inspections_status_due (status, due_date),
    INDEX idx_order_inspections_payable (organization_id, status, approved_date, partner_payout_id),
    CONSTRAINT fk_order_inspection_partner_order FOREIGN KEY (partner_order_id) REFERENCES partner_orders (id),
    CONSTRAINT fk_order_inspection_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_order_inspection_payout FOREIGN KEY (partner_payout_id) REFERENCES partner_payouts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 파트너 매입채무 원장(append-only, 공급가 기준) — 검수 승인 때 +지급 대상 금액, 지급 완료 때 −지급액.
CREATE TABLE partner_ledger_entries (
    id                          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id             BIGINT NOT NULL,
    entry_type                  VARCHAR(20) NOT NULL,
    currency_code               CHAR(3) NOT NULL,
    amount                      DECIMAL(19,4) NOT NULL,
    partner_order_id            BIGINT NULL,
    partner_payout_id           BIGINT NULL,
    memo                        VARCHAR(500) NULL,
    occurred_on                 DATE NOT NULL,
    created_by                  BIGINT NULL,
    updated_by                  BIGINT NULL,
    created_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_partner_ledger_org (organization_id, occurred_on),
    CONSTRAINT fk_partner_ledger_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_partner_ledger_partner_order FOREIGN KEY (partner_order_id) REFERENCES partner_orders (id),
    CONSTRAINT fk_partner_ledger_payout FOREIGN KEY (partner_payout_id) REFERENCES partner_payouts (id),
    CONSTRAINT fk_partner_ledger_currency FOREIGN KEY (currency_code) REFERENCES currencies (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 메뉴: 관리자 "검수"·"파트너 지급", 파트너 "지급 내역"
INSERT INTO menus (console, parent_menu_id, menu_key, label_key, path, icon_key, display_order)
SELECT 'PLATFORM', g.id, m.menu_key, m.label_key, m.path, m.icon_key, m.display_order
FROM menus g
CROSS JOIN (
    SELECT 'MENU_ORDER_INSPECTIONS' AS menu_key, 'navigation.orderInspections' AS label_key,
           '/admin/order-inspections' AS path, 'ClipboardCheck' AS icon_key, 4 AS display_order
    UNION ALL SELECT 'MENU_PARTNER_PAYOUTS', 'navigation.partnerPayouts', '/admin/partner-payouts', 'Banknote', 5
) m
WHERE g.menu_key = 'MENU_GROUP_ORDER';

INSERT INTO menus (console, menu_key, label_key, path, icon_key, display_order)
VALUES ('ORGANIZATION', 'MENU_ORG_PARTNER_PAYOUTS', 'navigation.receivedPartnerPayouts', '/partner-payouts', 'Banknote', 1);

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT menu_key, 'MENU', console, id, label_key, display_order
FROM menus
WHERE menu_key IN ('MENU_ORDER_INSPECTIONS', 'MENU_PARTNER_PAYOUTS', 'MENU_ORG_PARTNER_PAYOUTS');

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, TRUE
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key IN ('MENU_ORDER_INSPECTIONS', 'MENU_PARTNER_PAYOUTS') AND pd.permission_type = 'MENU';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, (roles.role_value IN ('OWNER', 'ADMIN'))
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'OWNER' AS role_value UNION ALL SELECT 'ADMIN'
    UNION ALL SELECT 'OPERATOR' UNION ALL SELECT 'VIEWER'
) roles
WHERE pd.permission_key = 'MENU_ORG_PARTNER_PAYOUTS';

-- 액션: 검수 승인·지급 처리는 PLATFORM_OPS 이상, 지급 내역 조회는 파트너 OWNER·ADMIN(D19).
INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT a.permission_key, 'ACTION', a.role_axis, m.id, a.label_key, 0
FROM (
    SELECT 'ACTION_ORDER_INSPECTION' AS permission_key, 'PLATFORM' AS role_axis,
           'MENU_ORDER_INSPECTIONS' AS menu_key, 'permission.action.orderInspection' AS label_key
    UNION ALL SELECT 'ACTION_PARTNER_PAYMENT_APPROVE', 'PLATFORM', 'MENU_PARTNER_PAYOUTS', 'permission.action.partnerPaymentApprove'
    UNION ALL SELECT 'ACTION_PARTNER_PAYOUT_VIEW', 'ORGANIZATION', 'MENU_ORG_PARTNER_PAYOUTS', 'permission.action.partnerPayoutView'
) a
JOIN menus m ON m.menu_key = a.menu_key;

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, (roles.role_value IN ('PLATFORM_OPS', 'PLATFORM_SUPER'))
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key IN ('ACTION_ORDER_INSPECTION', 'ACTION_PARTNER_PAYMENT_APPROVE');

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, (roles.role_value IN ('OWNER', 'ADMIN'))
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'OWNER' AS role_value UNION ALL SELECT 'ADMIN'
    UNION ALL SELECT 'OPERATOR' UNION ALL SELECT 'VIEWER'
) roles
WHERE pd.permission_key = 'ACTION_PARTNER_PAYOUT_VIEW';
