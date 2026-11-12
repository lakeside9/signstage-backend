-- 플랫폼 수주·파트너 발주 1차(핵심 흐름 백엔드) — signstage-docs
-- business/platform-order-partner-subcontract-review.md. D01(2026-11-12 결정: 플랫폼 수주 단독,
-- 파트너 직접 수주 보류)과 나머지 항목의 문서 권장안을 따른다.
--
-- 1) ceremonies.order_route — 수주 경로(PARTNER_DIRECT/PLATFORM_ORDER). 기존 행은 전부 PARTNER_DIRECT다.
-- 2) partner_subcontract_profiles — 발주 요건(D22): 사업자 정보, 정산 계좌와 플랫폼의 예금주 확인, 계약 동의.
-- 3) customers — 플랫폼 소유 고객(D02).
-- 4) customer_prices / region_surcharges / partner_rate_cards — 고객 가격표, 권역 할증(D26),
--    파트너 단가표(D05·D28 안 A). 기간은 시작·종료일 포함, 종료일 NULL이면 열린 기간이다.
-- 5) sales_orders / sales_order_lines — 수주(고객 견적·계약)와 견적 줄 스냅샷.
-- 6) partner_orders — 발주(D04, 금액 스냅샷). 수락하면 ceremony_id가 채워진다(D03 안 A).
-- 7) 권한 — 메뉴는 화면 단계에서 붙이고, 지금은 메뉴 없는 액션 권한만 둔다.

ALTER TABLE ceremonies
    ADD COLUMN order_route VARCHAR(20) NOT NULL DEFAULT 'PARTNER_DIRECT' AFTER organization_id;

CREATE TABLE partner_subcontract_profiles (
    id                              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id                 BIGINT NOT NULL,
    business_type                   VARCHAR(20) NULL,
    business_registration_number    VARCHAR(20) NULL,
    representative_name             VARCHAR(100) NULL,
    bank_name                       VARCHAR(50) NULL,
    bank_account_number             VARCHAR(50) NULL,
    account_holder_name             VARCHAR(100) NULL,
    account_verified_by             BIGINT NULL,
    account_verified_at             TIMESTAMP(6) NULL,
    subcontract_terms_agreed_at     TIMESTAMP(6) NULL,
    privacy_consignment_agreed_at   TIMESTAMP(6) NULL,
    created_by                      BIGINT NOT NULL,
    updated_by                      BIGINT NULL,
    created_at                      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_partner_subcontract_profile_org UNIQUE (organization_id),
    CONSTRAINT fk_partner_subcontract_profile_org FOREIGN KEY (organization_id) REFERENCES organizations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE customers (
    id                              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name                            VARCHAR(200) NOT NULL,
    business_registration_number    VARCHAR(20) NULL,
    representative_name             VARCHAR(100) NULL,
    contact_name                    VARCHAR(100) NULL,
    contact_phone                   VARCHAR(20) NULL,
    contact_email                   VARCHAR(255) NULL,
    tax_invoice_email               VARCHAR(255) NULL,
    address                         VARCHAR(500) NULL,
    memo                            VARCHAR(1000) NULL,
    status                          VARCHAR(20) NOT NULL,
    created_by                      BIGINT NOT NULL,
    updated_by                      BIGINT NULL,
    created_at                      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_customers_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE customer_prices (
    id                  BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    billing_plan_id     BIGINT NULL,
    unit_product_id     BIGINT NULL,
    currency_code       CHAR(3) NOT NULL,
    unit_price          DECIMAL(19,4) NOT NULL,
    tax_code            VARCHAR(50) NOT NULL,
    effective_from      DATE NOT NULL,
    effective_to        DATE NULL,
    created_by          BIGINT NOT NULL,
    updated_by          BIGINT NULL,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT ck_customer_price_target CHECK ((billing_plan_id IS NULL) <> (unit_product_id IS NULL)),
    CONSTRAINT fk_customer_price_plan FOREIGN KEY (billing_plan_id) REFERENCES billing_plans (id),
    CONSTRAINT fk_customer_price_unit_product FOREIGN KEY (unit_product_id) REFERENCES unit_products (id),
    CONSTRAINT fk_customer_price_currency FOREIGN KEY (currency_code) REFERENCES currencies (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE region_surcharges (
    id                  BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    region              VARCHAR(30) NOT NULL,
    currency_code       CHAR(3) NOT NULL,
    customer_amount     DECIMAL(19,4) NOT NULL,
    partner_amount      DECIMAL(19,4) NOT NULL,
    tax_code            VARCHAR(50) NOT NULL,
    effective_from      DATE NOT NULL,
    effective_to        DATE NULL,
    created_by          BIGINT NOT NULL,
    updated_by          BIGINT NULL,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_region_surcharges_region (region, effective_from),
    CONSTRAINT fk_region_surcharge_currency FOREIGN KEY (currency_code) REFERENCES currencies (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE partner_rate_cards (
    id                      BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id         BIGINT NULL,
    currency_code           CHAR(3) NOT NULL,
    base_fee                DECIMAL(19,4) NOT NULL,
    tablet_unit_rate        DECIMAL(19,4) NOT NULL,
    onsite_staff_day_rate   DECIMAL(19,4) NOT NULL,
    effective_from          DATE NOT NULL,
    effective_to            DATE NULL,
    created_by              BIGINT NOT NULL,
    updated_by              BIGINT NULL,
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_partner_rate_card_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_partner_rate_card_currency FOREIGN KEY (currency_code) REFERENCES currencies (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sales_orders (
    id                          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id                 BIGINT NOT NULL,
    title                       VARCHAR(200) NOT NULL,
    billing_plan_id             BIGINT NOT NULL,
    region                      VARCHAR(30) NOT NULL,
    location                    VARCHAR(500) NULL,
    starts_at                   DATETIME(6) NULL,
    ends_at                     DATETIME(6) NULL,
    currency_code               CHAR(3) NOT NULL,
    currency_fraction_digits    SMALLINT NOT NULL,
    currency_rounding_mode      VARCHAR(20) NOT NULL,
    tablet_count                INT NOT NULL,
    onsite_staff_person_days    INT NOT NULL,
    supply_amount               DECIMAL(19,4) NOT NULL,
    vat_amount                  DECIMAL(19,4) NOT NULL,
    total_amount                DECIMAL(19,4) NOT NULL,
    discount_reason             VARCHAR(500) NULL,
    estimated_partner_amount    DECIMAL(19,4) NOT NULL,
    quote_valid_until           DATE NOT NULL,
    quote_access_token          VARCHAR(64) NOT NULL,
    status                      VARCHAR(20) NOT NULL,
    accepted_at                 TIMESTAMP(6) NULL,
    accepted_by_name            VARCHAR(100) NULL,
    acceptance_channel          VARCHAR(20) NULL,
    cancelled_at                TIMESTAMP(6) NULL,
    cancellation_reason         VARCHAR(500) NULL,
    memo                        VARCHAR(1000) NULL,
    created_by                  BIGINT NOT NULL,
    updated_by                  BIGINT NULL,
    created_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_sales_order_quote_token UNIQUE (quote_access_token),
    INDEX idx_sales_orders_status (status, created_at),
    CONSTRAINT fk_sales_order_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_sales_order_plan FOREIGN KEY (billing_plan_id) REFERENCES billing_plans (id),
    CONSTRAINT fk_sales_order_currency FOREIGN KEY (currency_code) REFERENCES currencies (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sales_order_lines (
    id                  BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sales_order_id      BIGINT NOT NULL,
    line_type           VARCHAR(20) NOT NULL,
    unit_product_id     BIGINT NULL,
    name                VARCHAR(200) NOT NULL,
    quantity            INT NOT NULL,
    unit_price          DECIMAL(19,4) NOT NULL,
    amount              DECIMAL(19,4) NOT NULL,
    tax_code            VARCHAR(50) NOT NULL,
    tax_rate_percent    DECIMAL(7,4) NOT NULL,
    tax_amount          DECIMAL(19,4) NOT NULL,
    display_order       INT NOT NULL,
    created_by          BIGINT NOT NULL,
    updated_by          BIGINT NULL,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_sales_order_line_order FOREIGN KEY (sales_order_id) REFERENCES sales_orders (id),
    CONSTRAINT fk_sales_order_line_unit_product FOREIGN KEY (unit_product_id) REFERENCES unit_products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE partner_orders (
    id                          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sales_order_id              BIGINT NOT NULL,
    organization_id             BIGINT NOT NULL,
    currency_code               CHAR(3) NOT NULL,
    base_fee                    DECIMAL(19,4) NOT NULL,
    tablet_count                INT NOT NULL,
    tablet_unit_rate            DECIMAL(19,4) NOT NULL,
    onsite_staff_person_days    INT NOT NULL,
    onsite_staff_day_rate       DECIMAL(19,4) NOT NULL,
    region_surcharge            DECIMAL(19,4) NOT NULL,
    adjustment_amount           DECIMAL(19,4) NOT NULL,
    adjustment_reason           VARCHAR(500) NULL,
    total_amount                DECIMAL(19,4) NOT NULL,
    status                      VARCHAR(20) NOT NULL,
    response_due_at             DATETIME(6) NOT NULL,
    issued_by                   BIGINT NOT NULL,
    issued_at                   TIMESTAMP(6) NOT NULL,
    responded_by                BIGINT NULL,
    responded_at                TIMESTAMP(6) NULL,
    rejection_reason            VARCHAR(500) NULL,
    cancelled_at                TIMESTAMP(6) NULL,
    cancellation_reason         VARCHAR(500) NULL,
    ceremony_id                 BIGINT NULL,
    created_by                  BIGINT NOT NULL,
    updated_by                  BIGINT NULL,
    created_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_partner_orders_org_status (organization_id, status),
    CONSTRAINT uq_partner_order_ceremony UNIQUE (ceremony_id),
    CONSTRAINT fk_partner_order_sales_order FOREIGN KEY (sales_order_id) REFERENCES sales_orders (id),
    CONSTRAINT fk_partner_order_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_partner_order_ceremony FOREIGN KEY (ceremony_id) REFERENCES ceremonies (id),
    CONSTRAINT fk_partner_order_currency FOREIGN KEY (currency_code) REFERENCES currencies (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 플랫폼 축 액션 — 조회는 플랫폼 관리자 누구나, 쓰기는 PLATFORM_OPS 이상으로 시작한다(기존 관례).
INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
VALUES
    ('ACTION_CUSTOMER_MANAGE', 'ACTION', 'PLATFORM', NULL, 'permission.action.customerManage', 0),
    ('ACTION_ORDER_PRICE_MANAGE', 'ACTION', 'PLATFORM', NULL, 'permission.action.orderPriceManage', 1),
    ('ACTION_SALES_ORDER_MANAGE', 'ACTION', 'PLATFORM', NULL, 'permission.action.salesOrderManage', 2),
    ('ACTION_PARTNER_ORDER_ISSUE', 'ACTION', 'PLATFORM', NULL, 'permission.action.partnerOrderIssue', 3),
    ('ACTION_PARTNER_PROFILE_VERIFY', 'ACTION', 'PLATFORM', NULL, 'permission.action.partnerProfileVerify', 4);

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, (roles.role_value IN ('PLATFORM_OPS', 'PLATFORM_SUPER'))
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key IN (
    'ACTION_CUSTOMER_MANAGE', 'ACTION_ORDER_PRICE_MANAGE', 'ACTION_SALES_ORDER_MANAGE',
    'ACTION_PARTNER_ORDER_ISSUE', 'ACTION_PARTNER_PROFILE_VERIFY'
);

-- 조직 축 액션 — 발주 수락·거절(금액 포함 조회)은 OWNER·ADMIN, 외주 정보(계좌·계약 동의)는 OWNER만.
INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
VALUES
    ('ACTION_PARTNER_ORDER_RESPOND', 'ACTION', 'ORGANIZATION', NULL, 'permission.action.partnerOrderRespond', 0),
    ('ACTION_PARTNER_PROFILE_EDIT', 'ACTION', 'ORGANIZATION', NULL, 'permission.action.partnerProfileEdit', 1);

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value,
       CASE pd.permission_key
           WHEN 'ACTION_PARTNER_ORDER_RESPOND' THEN roles.role_value IN ('OWNER', 'ADMIN')
           ELSE roles.role_value = 'OWNER'
       END
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'OWNER' AS role_value UNION ALL SELECT 'ADMIN'
    UNION ALL SELECT 'OPERATOR' UNION ALL SELECT 'VIEWER'
) roles
WHERE pd.permission_key IN ('ACTION_PARTNER_ORDER_RESPOND', 'ACTION_PARTNER_PROFILE_EDIT');
