-- 파트너 → 실고객 고객 견적서 — signstage-docs
-- business/partner-customer-quote-design-review.md 결정(2026-09-11),
-- business/platform-partner-customer-billing-model-reference.md 4장. "조직 기본 마진 +
-- 행사별 override" 2단 구조(organization_margin_policies/ceremony_margin_overrides, 행의
-- 존재 자체가 "설정됨"을 뜻하는 null sentinel 없는 패턴) 위에, 생성할 때마다 새 버전이 쌓이는
-- append-only 스냅샷(customer_quotes/customer_quote_lines, billing_quotes와 같은 패턴이되
-- 별개 엔티티 — 플랫폼이 확정하는 불변 기록이 아니라 파트너가 여러 번 다시 뽑아보는 영업
-- 도구라 라이프사이클이 다르다)을 둔다.

CREATE TABLE organization_margin_policies (
    id            BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    margin_type   VARCHAR(20) NOT NULL,
    margin_value  DECIMAL(19,4) NOT NULL,
    created_by    BIGINT NOT NULL,
    updated_by    BIGINT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_org_margin_policy_org UNIQUE (organization_id),
    CONSTRAINT fk_org_margin_policy_org FOREIGN KEY (organization_id) REFERENCES organizations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ceremony_margin_overrides (
    id            BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ceremony_id   BIGINT NOT NULL,
    margin_type   VARCHAR(20) NOT NULL,
    margin_value  DECIMAL(19,4) NOT NULL,
    created_by    BIGINT NOT NULL,
    updated_by    BIGINT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_ceremony_margin_override_ceremony UNIQUE (ceremony_id),
    CONSTRAINT fk_ceremony_margin_override_ceremony FOREIGN KEY (ceremony_id) REFERENCES ceremonies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE customer_quotes (
    id                                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ceremony_id                           BIGINT NOT NULL,
    version                               INT NOT NULL,
    currency_code                         CHAR(3) NOT NULL,
    currency_fraction_digits              SMALLINT NOT NULL,
    currency_rounding_mode                VARCHAR(20) NOT NULL,
    system_usage_cost_amount              DECIMAL(19,4) NOT NULL,
    margin_type                           VARCHAR(20) NOT NULL,
    margin_value                          DECIMAL(19,4) NOT NULL,
    system_usage_margin_amount            DECIMAL(19,4) NOT NULL,
    system_usage_customer_amount          DECIMAL(19,4) NOT NULL,
    equipment_personnel_customer_amount   DECIMAL(19,4) NOT NULL,
    total_customer_amount                 DECIMAL(19,4) NOT NULL,
    pricing_calculated_at                 TIMESTAMP(6) NOT NULL,
    created_by                            BIGINT NOT NULL,
    created_at                            TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_customer_quote_version UNIQUE (ceremony_id, version),
    CONSTRAINT fk_customer_quote_ceremony FOREIGN KEY (ceremony_id) REFERENCES ceremonies (id),
    CONSTRAINT fk_customer_quote_currency FOREIGN KEY (currency_code) REFERENCES currencies (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE customer_quote_lines (
    id                          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_quote_id           BIGINT NOT NULL,
    line_type                   VARCHAR(30) NOT NULL,
    item_id                     BIGINT NULL,
    item_name                   VARCHAR(100) NOT NULL,
    quantity                    INT NOT NULL,
    reference_cost_unit_amount  DECIMAL(19,4) NULL,
    customer_unit_amount        DECIMAL(19,4) NOT NULL,
    customer_amount             DECIMAL(19,4) NOT NULL,
    created_by                  BIGINT NOT NULL,
    created_at                  TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_customer_quote_line_quote FOREIGN KEY (customer_quote_id) REFERENCES customer_quotes (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_customer_quote_line_quote ON customer_quote_lines (customer_quote_id);

-- ── RBAC: ACTION_MARGIN_POLICY_MANAGE(조직 기본 마진, 회사정보 수정과 같은 메뉴에 붙인다) /
-- ACTION_CUSTOMER_QUOTE_MANAGE(행사별 마진 override + 고객 견적서, 행사 관리 메뉴에 붙인다) —
-- 둘 다 OWNER만 허용한다(platform-partner-customer-billing-model-reference.md 4장 결정:
-- "파트너 OWNER가 전적으로 자유롭게 설정 — 플랫폼은 상한·승인 등 어떤 통제도 두지 않는다").

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT 'ACTION_MARGIN_POLICY_MANAGE', 'ACTION', 'ORGANIZATION', id, 'permission.action.marginPolicyManage', 2
FROM menus WHERE menu_key = 'MENU_ORG_SETTINGS_COMPANY_INFO'
UNION ALL
SELECT 'ACTION_CUSTOMER_QUOTE_MANAGE', 'ACTION', 'ORGANIZATION', id, 'permission.action.customerQuoteManage', 2
FROM menus WHERE menu_key = 'MENU_ORG_CEREMONIES';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, (roles.role_value = 'OWNER')
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'OWNER' AS role_value
    UNION ALL SELECT 'ADMIN'
    UNION ALL SELECT 'OPERATOR'
    UNION ALL SELECT 'VIEWER'
) roles
WHERE pd.permission_key IN ('ACTION_MARGIN_POLICY_MANAGE', 'ACTION_CUSTOMER_QUOTE_MANAGE');
