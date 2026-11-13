-- 견적 공통 문구(2026-11-13 결정) — 견적서 "제공 서비스" 본문과 하단 안내문구를 플랜이 아니라 기간별 공통 문구로 관리한다.
-- 수주 가격표와 같은 규칙이다: 기간은 겹치지 않고, 견적은 저장일에 유효한 문구를 복사해 두며, 견적에 쓰인 문구는 고치거나 지우지 못한다.
CREATE TABLE quote_templates (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_description TEXT NULL,
    footer_notice       TEXT NULL,
    effective_from      DATE NOT NULL,
    effective_to        DATE NULL,
    created_by          BIGINT NULL,
    updated_by          BIGINT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT ck_quote_template_period CHECK (effective_to IS NULL OR effective_to >= effective_from)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 첫 문구: 지금까지 플랜에 넣은 서비스 설명(가장 먼저 만든 플랫폼 수주 플랜의 것)과 견적서에 고정돼 있던 하단 문구.
INSERT INTO quote_templates (service_description, footer_notice, effective_from, created_by)
SELECT (SELECT bp.service_description
        FROM billing_plans bp
        WHERE bp.service_description IS NOT NULL AND TRIM(bp.service_description) <> ''
        ORDER BY bp.id
        LIMIT 1),
       '본 견적은 유효기간까지 유효하며, 행사 범위가 바뀌면 다시 견적합니다.',
       '2026-11-13',
       NULL;

-- 견적은 쓴 문구를 기억하고(사용 여부 판단) 하단 문구를 복사해 둔다. 이미 저장된 견적은 고정 문구를 그대로 보여주도록 채운다.
ALTER TABLE sales_orders
    ADD COLUMN quote_template_id   BIGINT NULL AFTER service_description,
    ADD COLUMN quote_footer_notice TEXT NULL AFTER quote_template_id,
    ADD CONSTRAINT fk_sales_order_quote_template FOREIGN KEY (quote_template_id) REFERENCES quote_templates (id);

UPDATE sales_orders SET quote_footer_notice = '본 견적은 유효기간까지 유효하며, 행사 범위가 바뀌면 다시 견적합니다.';

-- 서비스 설명은 이제 공통 문구가 갖는다.
ALTER TABLE billing_plans DROP COLUMN service_description;
ALTER TABLE billing_plan_histories DROP COLUMN service_description;

-- 메뉴: 수주·발주 관리 > 견적 공통 문구(수주 가격표 바로 아래). 관리 권한은 수주 가격표의 ACTION_ORDER_PRICE_MANAGE를 함께 쓴다.
UPDATE menus m
JOIN menus g ON m.parent_menu_id = g.id AND g.menu_key = 'MENU_GROUP_ORDER'
SET m.display_order = m.display_order + 1
WHERE m.display_order >= 4;

INSERT INTO menus (console, parent_menu_id, menu_key, label_key, path, icon_key, display_order)
SELECT 'PLATFORM', g.id, 'MENU_QUOTE_TEMPLATES', 'navigation.quoteTemplates', '/admin/quote-templates', 'ScrollText', 4
FROM menus g
WHERE g.menu_key = 'MENU_GROUP_ORDER';

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT menu_key, 'MENU', console, id, label_key, display_order
FROM menus
WHERE menu_key = 'MENU_QUOTE_TEMPLATES';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, TRUE
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key = 'MENU_QUOTE_TEMPLATES' AND pd.permission_type = 'MENU';
