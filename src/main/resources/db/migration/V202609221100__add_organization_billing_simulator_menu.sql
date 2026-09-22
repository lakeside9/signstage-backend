INSERT INTO menus (console, menu_key, label_key, path, icon_key, display_order)
VALUES ('ORGANIZATION', 'MENU_ORG_BILLING_SIMULATOR', 'navigation.billingSimulator',
        '/billing-simulator', 'Calculator', 6);

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT menu_key, 'MENU', 'ORGANIZATION', id, label_key, display_order
FROM menus WHERE menu_key = 'MENU_ORG_BILLING_SIMULATOR';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, TRUE
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'OWNER' AS role_value UNION ALL SELECT 'ADMIN'
    UNION ALL SELECT 'OPERATOR' UNION ALL SELECT 'VIEWER'
) roles
WHERE pd.permission_key = 'MENU_ORG_BILLING_SIMULATOR';
