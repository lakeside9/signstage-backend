-- 수락 후 파트너 교체(D03) — signstage-docs business/platform-order-partner-subcontract-review.md D03.
-- 수락한 발주를 행사 완료 전에 취소하면 다음에 수락하는 발주가 같은 행사를 넘겨받는다. 그래서 한 행사에
-- 발주(취소된 이전 발주 + 새 발주)가 여럿 연결될 수 있다. 수락된 발주는 수주마다 여전히 하나뿐이다.

-- 외래 키가 쓸 인덱스를 먼저 만든 뒤 유일 제약을 지운다.
ALTER TABLE partner_orders ADD INDEX idx_partner_orders_ceremony (ceremony_id);
ALTER TABLE partner_orders DROP INDEX uq_partner_order_ceremony;

-- 액션: 수락한 발주 취소(파트너 교체)는 PLATFORM_SUPER만.
INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT 'ACTION_PARTNER_ORDER_REPLACE', 'ACTION', 'PLATFORM', m.id, 'permission.action.partnerOrderReplace', 0
FROM menus m
WHERE m.menu_key = 'MENU_SALES_ORDERS';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, (roles.role_value = 'PLATFORM_SUPER')
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key = 'ACTION_PARTNER_ORDER_REPLACE';
