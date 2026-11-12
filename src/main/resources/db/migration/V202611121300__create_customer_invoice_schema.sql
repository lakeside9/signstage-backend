-- 고객 청구·수납 — signstage-docs business/platform-order-partner-subcontract-review.md 11장 7단계,
-- D12(선금+잔금, 2026-11-12 결정), D16(세금계산서는 발행 기록만, 2026-11-12 결정).

-- 수주별 청구 — 선금(수주 확정 후)과 잔금(행사 완료 후). 입금·세금계산서 발행은 사람이 기록한다.
CREATE TABLE customer_invoices (
    id                          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sales_order_id              BIGINT NOT NULL,
    customer_id                 BIGINT NOT NULL,
    invoice_type                VARCHAR(20) NOT NULL,
    currency_code               CHAR(3) NOT NULL,
    supply_amount               DECIMAL(19,4) NOT NULL,
    vat_amount                  DECIMAL(19,4) NOT NULL,
    total_amount                DECIMAL(19,4) NOT NULL,
    issue_date                  DATE NOT NULL,
    due_date                    DATE NOT NULL,
    status                      VARCHAR(20) NOT NULL,
    issued_by                   BIGINT NOT NULL,
    paid_date                   DATE NULL,
    paid_amount                 DECIMAL(19,4) NULL,
    payment_memo                VARCHAR(500) NULL,
    payment_recorded_by         BIGINT NULL,
    tax_invoice_issued_date     DATE NULL,
    tax_invoice_approval_number VARCHAR(50) NULL,
    cancelled_at                TIMESTAMP(6) NULL,
    cancellation_reason         VARCHAR(500) NULL,
    created_by                  BIGINT NULL,
    updated_by                  BIGINT NULL,
    created_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_customer_invoices_sales_order (sales_order_id, invoice_type, status),
    INDEX idx_customer_invoices_status_due (status, due_date),
    INDEX idx_customer_invoices_customer (customer_id, status),
    CONSTRAINT fk_customer_invoice_sales_order FOREIGN KEY (sales_order_id) REFERENCES sales_orders (id),
    CONSTRAINT fk_customer_invoice_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_customer_invoice_currency FOREIGN KEY (currency_code) REFERENCES currencies (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 메뉴: 관리자 "청구·수납"
INSERT INTO menus (console, parent_menu_id, menu_key, label_key, path, icon_key, display_order)
SELECT 'PLATFORM', g.id, 'MENU_CUSTOMER_INVOICES', 'navigation.customerInvoices', '/admin/customer-invoices', 'Wallet', 6
FROM menus g
WHERE g.menu_key = 'MENU_GROUP_ORDER';

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT menu_key, 'MENU', console, id, label_key, display_order
FROM menus
WHERE menu_key = 'MENU_CUSTOMER_INVOICES';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, TRUE
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key = 'MENU_CUSTOMER_INVOICES' AND pd.permission_type = 'MENU';

-- 액션: 청구 발행·입금·세금계산서 기록·취소는 PLATFORM_OPS 이상.
INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT 'ACTION_CUSTOMER_INVOICE_MANAGE', 'ACTION', 'PLATFORM', m.id, 'permission.action.customerInvoiceManage', 0
FROM menus m
WHERE m.menu_key = 'MENU_CUSTOMER_INVOICES';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, (roles.role_value IN ('PLATFORM_OPS', 'PLATFORM_SUPER'))
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key = 'ACTION_CUSTOMER_INVOICE_MANAGE';
