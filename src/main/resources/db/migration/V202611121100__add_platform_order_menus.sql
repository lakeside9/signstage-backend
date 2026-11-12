-- 플랫폼 수주·파트너 발주 화면 메뉴 — signstage-docs business/platform-order-partner-subcontract-review.md
-- 12장. 같은 문서 D29 안 A에 따라 파트너 직접 수주 전용 메뉴는 노출을 끈다(코드·권한은 유지,
-- 다시 열 때 메뉴 관리에서 켠다).

-- 플랫폼 관리자: 수주·발주 관리 그룹
INSERT INTO menus (console, menu_key, label_key, path, icon_key, display_order)
VALUES ('PLATFORM', 'MENU_GROUP_ORDER', 'navigation.groupOrder', NULL, 'Handshake', 5);

INSERT INTO menus (console, parent_menu_id, menu_key, label_key, path, icon_key, display_order)
SELECT 'PLATFORM', g.id, m.menu_key, m.label_key, m.path, m.icon_key, m.display_order
FROM menus g
CROSS JOIN (
    SELECT 'MENU_SALES_ORDERS' AS menu_key, 'navigation.salesOrders' AS label_key,
           '/admin/sales-orders' AS path, 'FileText' AS icon_key, 0 AS display_order
    UNION ALL SELECT 'MENU_PARTNER_ORDERS', 'navigation.partnerOrders', '/admin/partner-orders', 'Send', 1
    UNION ALL SELECT 'MENU_CUSTOMERS', 'navigation.customers', '/admin/customers', 'Contact', 2
    UNION ALL SELECT 'MENU_ORDER_PRICES', 'navigation.orderPrices', '/admin/order-prices', 'Receipt', 3
) m
WHERE g.menu_key = 'MENU_GROUP_ORDER';

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT menu_key, 'MENU', 'PLATFORM', id, label_key, display_order
FROM menus
WHERE menu_key IN ('MENU_SALES_ORDERS', 'MENU_PARTNER_ORDERS', 'MENU_CUSTOMERS', 'MENU_ORDER_PRICES');

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, TRUE
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key IN ('MENU_SALES_ORDERS', 'MENU_PARTNER_ORDERS', 'MENU_CUSTOMERS', 'MENU_ORDER_PRICES')
  AND pd.permission_type = 'MENU';

-- 파트너: 발주함
INSERT INTO menus (console, menu_key, label_key, path, icon_key, display_order)
VALUES ('ORGANIZATION', 'MENU_ORG_PARTNER_ORDERS', 'navigation.receivedPartnerOrders', '/partner-orders', 'Inbox', 1);

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT menu_key, 'MENU', 'ORGANIZATION', id, label_key, display_order
FROM menus WHERE menu_key = 'MENU_ORG_PARTNER_ORDERS';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, (roles.role_value IN ('OWNER', 'ADMIN'))
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'OWNER' AS role_value UNION ALL SELECT 'ADMIN'
    UNION ALL SELECT 'OPERATOR' UNION ALL SELECT 'VIEWER'
) roles
WHERE pd.permission_key = 'MENU_ORG_PARTNER_ORDERS';

-- 액션 권한을 화면 메뉴 아래로 묶는다(권한 관리 화면의 메뉴별 그룹).
UPDATE permission_definitions pd
JOIN menus m ON m.menu_key = CASE pd.permission_key
    WHEN 'ACTION_CUSTOMER_MANAGE' THEN 'MENU_CUSTOMERS'
    WHEN 'ACTION_ORDER_PRICE_MANAGE' THEN 'MENU_ORDER_PRICES'
    WHEN 'ACTION_SALES_ORDER_MANAGE' THEN 'MENU_SALES_ORDERS'
    WHEN 'ACTION_PARTNER_ORDER_ISSUE' THEN 'MENU_SALES_ORDERS'
    WHEN 'ACTION_PARTNER_PROFILE_VERIFY' THEN 'MENU_PARTNERS'
    WHEN 'ACTION_PARTNER_ORDER_RESPOND' THEN 'MENU_ORG_PARTNER_ORDERS'
    WHEN 'ACTION_PARTNER_PROFILE_EDIT' THEN 'MENU_ORG_SETTINGS_COMPANY_INFO'
END
SET pd.menu_id = m.id
WHERE pd.permission_type = 'ACTION'
  AND pd.permission_key IN (
      'ACTION_CUSTOMER_MANAGE', 'ACTION_ORDER_PRICE_MANAGE', 'ACTION_SALES_ORDER_MANAGE',
      'ACTION_PARTNER_ORDER_ISSUE', 'ACTION_PARTNER_PROFILE_VERIFY',
      'ACTION_PARTNER_ORDER_RESPOND', 'ACTION_PARTNER_PROFILE_EDIT'
  );

-- 파트너 직접 수주 전용 메뉴 노출 끄기(D29 안 A).
UPDATE menus SET active = FALSE
WHERE menu_key IN (
    'MENU_ORG_SUBSCRIPTION_MARGIN', 'MENU_ORG_BILLING_SIMULATOR',
    'MENU_PURCHASE_REQUESTS', 'MENU_SUBSCRIPTION_REQUESTS',
    'MENU_ORGANIZATION_DISCOUNT_OVERRIDES', 'MENU_ONSITE_SUPPORT_REQUESTS'
);
