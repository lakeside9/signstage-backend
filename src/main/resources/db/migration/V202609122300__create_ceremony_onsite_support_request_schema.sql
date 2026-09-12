-- 현장지원 요청(관리자 견적) 협상 — signstage-docs
-- business/onsite-support-negotiation-and-billing-classification-review.md 3.2절 결정
-- (2026-09-12). 파트너가 일시·장소를 적어 요청하면, 관리자가 거리 등을 보고 실제 금액을
-- 매기고, 파트너가 그 금액을 수락/거부한다. 수락 시 만들어지는 구매가 참조할 앵커
-- 단위 상품(ONSITE_SUPPORT_REQUEST 타입, 정확히 1행) 1건을 여기서 시딩한다.

CREATE TABLE ceremony_onsite_support_requests (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ceremony_id     BIGINT NOT NULL,
    requested_at    DATETIME NOT NULL,
    location        VARCHAR(200) NOT NULL,
    requester_note  VARCHAR(500) NULL,
    status          VARCHAR(20) NOT NULL,
    quoted_amount   DECIMAL(19,4) NULL,
    quoted_note     VARCHAR(500) NULL,
    quoted_by       BIGINT NULL,
    quoted_at       DATETIME NULL,
    responded_at    DATETIME NULL,
    purchase_id     BIGINT NULL,
    created_by      BIGINT NOT NULL,
    updated_by      BIGINT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ceremony_onsite_support_requests_ceremony FOREIGN KEY (ceremony_id) REFERENCES ceremonies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_cosr_ceremony ON ceremony_onsite_support_requests (ceremony_id, created_at);
CREATE INDEX idx_cosr_status ON ceremony_onsite_support_requests (status);

-- 앵커 단위 상품 — 카테고리는 PERSONNEL(물리적 성격 그대로)이지만 과금 축은 플랫폼 이용료다
-- (UnitProduct.isPlatformUsageFee = TRUE, 2026-09-12 신설 필드). 어느 BillingPlan 구성에도
-- 넣지 않아 자가-체크아웃 후보 목록에는 절대 나타나지 않는다(카테고리·플랜포함 이중 게이트,
-- 3.2절) — 오직 이 협상 플로우의 수락 시에만 내부적으로 참조된다. 가격 기간은 명목값 1건만
-- 등록한다(세금코드 지정용, 실제 청구엔 이 가격이 쓰이지 않고 관리자가 매긴 quoted_amount가
-- 쓰인다).
-- unit_products.type/unit_product_histories.type가 VARCHAR(20)이라 'ONSITE_SUPPORT_REQUEST'
-- (22자)가 들어가지 않는다 — 값 이름을 줄이는 대신 컬럼을 넓힌다(다른 타입 값 이름에 영향
-- 없음).
ALTER TABLE unit_products MODIFY COLUMN type VARCHAR(30) NOT NULL;
ALTER TABLE unit_product_histories MODIFY COLUMN type VARCHAR(30) NOT NULL;

SET @admin_id = 1;
SET @today = CURDATE();

INSERT INTO unit_products (type, name, description, category, is_platform_usage_fee, exclusivity_group, created_by, updated_by)
VALUES (
    'ONSITE_SUPPORT_REQUEST', '현장지원(요청형·관리자 견적)',
    '파트너가 일시·장소를 적어 요청하면 관리자가 거리 등을 보고 실제 금액을 매기는 협상형 현장지원입니다. 이 상품의 등록가는 실제 청구에 쓰이지 않습니다 — 관리자가 매긴 금액이 그대로 청구됩니다.',
    'PERSONNEL', TRUE, NULL, @admin_id, @admin_id
);
SET @anchor_id = LAST_INSERT_ID();

INSERT INTO unit_product_histories (unit_product_id, type, name, description, category, is_platform_usage_fee, exclusivity_group, created_by, updated_by)
SELECT id, type, name, description, category, is_platform_usage_fee, exclusivity_group, created_by, updated_by
FROM unit_products WHERE id = @anchor_id;

INSERT INTO unit_product_price_periods
    (unit_product_id, currency_code, supply_price, sale_price, tax_code, active, effective_from, effective_to, created_by, updated_by)
VALUES (@anchor_id, 'KRW', NULL, 0, 'KR_VAT_STANDARD', TRUE, @today, NULL, @admin_id, @admin_id);

INSERT INTO unit_product_price_period_histories
    (unit_product_id, currency_code, supply_price, sale_price, tax_code, active, effective_from, effective_to, removed, created_by, updated_by)
SELECT unit_product_id, currency_code, supply_price, sale_price, tax_code, active, effective_from, effective_to, FALSE, created_by, updated_by
FROM unit_product_price_periods WHERE unit_product_id = @anchor_id;

-- "구매·할인 관리" 그룹(MENU_GROUP_PURCHASE) 아래, 기존 자식 4개(11~14) 다음 순서. 조회는
-- PLATFORM_SUPPORT 이상 전체, 견적 입력(ACTION_ONSITE_SUPPORT_REQUEST_MANAGE)은 PLATFORM_OPS
-- 이상만 — 다른 이 그룹 액션들과 같은 범위.
INSERT INTO menus (console, parent_menu_id, menu_key, label_key, path, icon_key, display_order)
SELECT 'PLATFORM', g.id, 'MENU_ONSITE_SUPPORT_REQUESTS', 'navigation.onsiteSupportRequests', '/admin/onsite-support-requests', 'MapPin', 15
FROM menus g WHERE g.menu_key = 'MENU_GROUP_PURCHASE';

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT menu_key, 'MENU', 'PLATFORM', id, label_key, display_order FROM menus WHERE menu_key = 'MENU_ONSITE_SUPPORT_REQUESTS';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, TRUE
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key = 'MENU_ONSITE_SUPPORT_REQUESTS' AND pd.permission_type = 'MENU';

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT 'ACTION_ONSITE_SUPPORT_REQUEST_MANAGE', 'ACTION', 'PLATFORM', id, 'permission.action.onsiteSupportRequestManage', 0
FROM menus WHERE menu_key = 'MENU_ONSITE_SUPPORT_REQUESTS';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, (roles.role_value IN ('PLATFORM_OPS', 'PLATFORM_SUPER'))
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key = 'ACTION_ONSITE_SUPPORT_REQUEST_MANAGE';
