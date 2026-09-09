-- 과금 카탈로그 화면을 과금플랜/선택옵션/용량 추가구매 3개 페이지로 분리 — 사용자 요청
-- (2026-09-09) "화면 구성은 파트너관리(admin/organizations)처럼, 목록에 검색+페이지네비게이션을
-- 두고 등록/수정은 인라인이 아닌 별도 페이지로". 프런트 AdminBillingCatalog.tsx(인라인 생성/수정
-- 3섹션 한 페이지)가 12개 파일(타입 3 × 화면 4: 목록/등록/상세/수정)로 나뉘면서, 메뉴도 기존
-- "과금 카탈로그" 1개 대신 3개가 필요해졌다.
--
-- 기존 MENU_BILLING_CATALOG 행은 지우지 않고 비활성화만 한다 — "메뉴 자체를 새로 만들거나
-- 삭제할 수 없다, 배포로만 등록/변경한다"는 기존 관례(AdminMenuManager.tsx 안내문)를 그대로
-- 지키면서, menu_histories 이력도 자연스럽게 남긴다.
UPDATE menus SET active = FALSE WHERE menu_key = 'MENU_BILLING_CATALOG';
UPDATE permission_definitions SET active = FALSE WHERE permission_key = 'MENU_BILLING_CATALOG' AND permission_type = 'MENU';

INSERT INTO menus (console, parent_menu_id, menu_key, label_key, path, icon_key, display_order)
SELECT 'PLATFORM', g.id, 'MENU_BILLING_PLANS', 'navigation.billingPlans', '/admin/billing-catalog/plans', 'Package', 7
FROM menus g WHERE g.menu_key = 'MENU_GROUP_BILLING';

INSERT INTO menus (console, parent_menu_id, menu_key, label_key, path, icon_key, display_order)
SELECT 'PLATFORM', g.id, 'MENU_OPTIONAL_FEATURES', 'navigation.optionalFeatures', '/admin/billing-catalog/optional-features', 'ListChecks', 8
FROM menus g WHERE g.menu_key = 'MENU_GROUP_BILLING';

INSERT INTO menus (console, parent_menu_id, menu_key, label_key, path, icon_key, display_order)
SELECT 'PLATFORM', g.id, 'MENU_CAPACITY_ADDONS', 'navigation.capacityAddOns', '/admin/billing-catalog/capacity-addons', 'Boxes', 9
FROM menus g WHERE g.menu_key = 'MENU_GROUP_BILLING';

-- 형제 메뉴(이벤트 효과/과금 시뮬레이터) 순서를 새 메뉴 2개만큼 뒤로 민다.
UPDATE menus SET display_order = 10 WHERE menu_key = 'MENU_EFFECTS';
UPDATE menus SET display_order = 11 WHERE menu_key = 'MENU_BILLING_SIMULATOR';

-- 새 메뉴 3개도 기존 리프 메뉴와 동일한 관례로 MENU 타입 permission_definitions + 3개 역할 모두
-- allowed=TRUE인 role_permissions를 시딩한다(과거 MENU_BILLING_CATALOG와 같은 노출 범위 —
-- 등록/수정 버튼 자체는 화면에서 ACTION_BILLING_CATALOG_MANAGE로 이미 별도 검사한다).
INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT menu_key, 'MENU', 'PLATFORM', id, label_key, display_order
FROM menus WHERE menu_key IN ('MENU_BILLING_PLANS', 'MENU_OPTIONAL_FEATURES', 'MENU_CAPACITY_ADDONS');

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, TRUE
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_type = 'MENU' AND pd.permission_key IN ('MENU_BILLING_PLANS', 'MENU_OPTIONAL_FEATURES', 'MENU_CAPACITY_ADDONS');
