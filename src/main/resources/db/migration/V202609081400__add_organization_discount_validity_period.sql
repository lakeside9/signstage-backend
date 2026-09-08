-- 조직×품목 할인 오버라이드에 유효기간(effective_from/effective_to)을 도입 — 안 B(다중 버전,
-- TaxPolicy 방식). 행 하나가 기간 하나를 뜻하도록 재정의한다 — signstage-docs
-- business/organization-discount-override-security-and-validity-period-review.md 결정
-- #4(2026-09-08).
--
-- 개발 단계라 기존 데이터 보존 부담이 없다(billing-catalog-zero-base-schema-redesign-review.md와
-- 같은 전제) — 그래도 지금 있는 행은 "생성된 날부터 무기한 유효했다"로 backfill한다
-- (effective_from = DATE(created_at), effective_to = NULL). 유니크 제약을 (organization_id,
-- item_id) 단일에서 (organization_id, item_id, effective_from) 다중 버전으로 바꾼다 —
-- TaxPolicy의 uq_tax_policy_version과 같은 형태.

-- ---- organization_billing_plan_discounts ----
ALTER TABLE organization_billing_plan_discounts
    ADD COLUMN effective_from DATE NULL AFTER discount_value,
    ADD COLUMN effective_to DATE NULL AFTER effective_from;

UPDATE organization_billing_plan_discounts SET effective_from = DATE(created_at) WHERE effective_from IS NULL;

ALTER TABLE organization_billing_plan_discounts
    MODIFY COLUMN effective_from DATE NOT NULL,
    DROP INDEX uq_obpd_organization_plan,
    ADD CONSTRAINT uq_obpd_organization_plan_period UNIQUE (organization_id, billing_plan_id, effective_from),
    ADD CONSTRAINT ck_obpd_period CHECK (effective_to IS NULL OR effective_to >= effective_from);

-- ---- organization_optional_feature_discounts ----
ALTER TABLE organization_optional_feature_discounts
    ADD COLUMN effective_from DATE NULL AFTER discount_value,
    ADD COLUMN effective_to DATE NULL AFTER effective_from;

UPDATE organization_optional_feature_discounts SET effective_from = DATE(created_at) WHERE effective_from IS NULL;

ALTER TABLE organization_optional_feature_discounts
    MODIFY COLUMN effective_from DATE NOT NULL,
    DROP INDEX uq_oofd_organization_feature,
    ADD CONSTRAINT uq_oofd_organization_feature_period UNIQUE (organization_id, optional_feature_id, effective_from),
    ADD CONSTRAINT ck_oofd_period CHECK (effective_to IS NULL OR effective_to >= effective_from);

-- ---- organization_capacity_addon_discounts ----
ALTER TABLE organization_capacity_addon_discounts
    ADD COLUMN effective_from DATE NULL AFTER discount_value,
    ADD COLUMN effective_to DATE NULL AFTER effective_from;

UPDATE organization_capacity_addon_discounts SET effective_from = DATE(created_at) WHERE effective_from IS NULL;

ALTER TABLE organization_capacity_addon_discounts
    MODIFY COLUMN effective_from DATE NOT NULL,
    DROP INDEX uq_ocad_organization_addon,
    ADD CONSTRAINT uq_ocad_organization_addon_period UNIQUE (organization_id, capacity_addon_id, effective_from),
    ADD CONSTRAINT ck_ocad_period CHECK (effective_to IS NULL OR effective_to >= effective_from);

-- ---- 이력 테이블 3종 — 유니크 제약 없음, 컬럼만 추가하고 backfill ----
ALTER TABLE organization_billing_plan_discount_histories
    ADD COLUMN effective_from DATE NULL AFTER discount_value,
    ADD COLUMN effective_to DATE NULL AFTER effective_from;
UPDATE organization_billing_plan_discount_histories SET effective_from = DATE(created_at) WHERE effective_from IS NULL;
ALTER TABLE organization_billing_plan_discount_histories MODIFY COLUMN effective_from DATE NOT NULL;

ALTER TABLE organization_optional_feature_discount_histories
    ADD COLUMN effective_from DATE NULL AFTER discount_value,
    ADD COLUMN effective_to DATE NULL AFTER effective_from;
UPDATE organization_optional_feature_discount_histories SET effective_from = DATE(created_at) WHERE effective_from IS NULL;
ALTER TABLE organization_optional_feature_discount_histories MODIFY COLUMN effective_from DATE NOT NULL;

ALTER TABLE organization_capacity_addon_discount_histories
    ADD COLUMN effective_from DATE NULL AFTER discount_value,
    ADD COLUMN effective_to DATE NULL AFTER effective_from;
UPDATE organization_capacity_addon_discount_histories SET effective_from = DATE(created_at) WHERE effective_from IS NULL;
ALTER TABLE organization_capacity_addon_discount_histories MODIFY COLUMN effective_from DATE NOT NULL;

-- ---- 권한 이원화 정리 — 4개 액션을 동적 RBAC로 이관 ----
-- signstage-docs business/organization-discount-override-security-and-validity-period-review.md
-- 결정 #3(2026-09-08). 하드코딩 Set.of("PLATFORM_OPS", "PLATFORM_SUPER")와 동작이 같도록
-- PLATFORM_OPS/PLATFORM_SUPER만 TRUE로 시딩한다(ACTION_BILLING_CATALOG_MANAGE와 같은 패턴).
-- 조직 할인 오버라이드/행사 재량 할인/행사 상태 강제 변경은 MENU_PARTNERS(조직 상세 화면에서
-- 쓰는 액션이라), 구매요청 승인/반려는 MENU_PURCHASE_REQUESTS 아래에 건다.

-- display_order 0/1/2는 각각 ACTION_PARTNER_STATUS_CHANGE/ACTION_PARTNER_INFO_EDIT
-- (V202609051200)/ACTION_PARTNER_CREATE(V202609051300)가 이미 쓰고 있어 3부터 잇는다.
INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT 'ACTION_ORGANIZATION_DISCOUNT_MANAGE', 'ACTION', 'PLATFORM', id, 'permission.action.organizationDiscountManage', 3
FROM menus WHERE menu_key = 'MENU_PARTNERS';

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT 'ACTION_CEREMONY_FINAL_DISCOUNT_MANAGE', 'ACTION', 'PLATFORM', id, 'permission.action.ceremonyFinalDiscountManage', 4
FROM menus WHERE menu_key = 'MENU_PARTNERS';

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT 'ACTION_CEREMONY_STATUS_CONTROL', 'ACTION', 'PLATFORM', id, 'permission.action.ceremonyStatusControl', 5
FROM menus WHERE menu_key = 'MENU_PARTNERS';

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT 'ACTION_PURCHASE_APPROVAL', 'ACTION', 'PLATFORM', id, 'permission.action.purchaseApproval', 0
FROM menus WHERE menu_key = 'MENU_PURCHASE_REQUESTS';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, (roles.role_value IN ('PLATFORM_OPS', 'PLATFORM_SUPER'))
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key IN (
    'ACTION_ORGANIZATION_DISCOUNT_MANAGE', 'ACTION_CEREMONY_FINAL_DISCOUNT_MANAGE',
    'ACTION_CEREMONY_STATUS_CONTROL', 'ACTION_PURCHASE_APPROVAL'
);
