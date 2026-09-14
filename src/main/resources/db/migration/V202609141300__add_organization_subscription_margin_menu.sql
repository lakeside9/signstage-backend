-- "구독·마진 관리" 최상위 메뉴 신설 — signstage-docs
-- business/platform-admin-partner-ux-confusion-review.md 후속(2026-09-14, 사용자 요청). "구독"과
-- "재판매 마진" 섹션을 조직 상세(회사정보관리, `MENU_ORG_SETTINGS_COMPANY_INFO`) 화면 맨
-- 아래에서 떼어내 독립 최상위 메뉴로 옮긴다 — 관리자 콘솔의 "행사 건별 재량 할인"/"파트너별
-- 할인 오버라이드" 분리(discount-management-screen-separation-review.md)와 같은 모양이다.
-- 프런트(`UserSubscriptionMarginList`/`Detail.tsx`)가 이미 `OrganizationSubscriptionSection`/
-- `OrganizationMarginPolicySection` 컴포넌트를 그대로 재사용해 로직 중복이 없다.
--
-- 두 섹션 다 실질적으로 OWNER 전용이다 — 구독 신청/중도해지는 OWNER만
-- (OrganizationSubscriptionSection 프런트 가드 + 서버), 재판매 마진 조회/설정은
-- `ACTION_MARGIN_POLICY_MANAGE`(OWNER 전용 시딩, platform-partner-customer-billing-model-
-- reference.md 4장)만 가능하다. 화면 내용이 OWNER가 아니면 사실상 비어 보이므로, 메뉴 자체도
-- OWNER에게만 노출한다(2026-09-14 결정 — 2.3절 "고객 견적" 탭을 권한으로 숨긴 것과 같은 원칙).

INSERT INTO menus (console, parent_menu_id, menu_key, label_key, path, icon_key, display_order)
VALUES ('ORGANIZATION', NULL, 'MENU_ORG_SUBSCRIPTION_MARGIN', 'navigation.subscriptionMargin', '/subscription-margin', 'Wallet', 5);

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT menu_key, 'MENU', 'ORGANIZATION', id, label_key, display_order
FROM menus WHERE menu_key = 'MENU_ORG_SUBSCRIPTION_MARGIN';

-- OWNER만 허용 — 기존 ACTION_COMPANY_INFO_EDIT(OWNER 전용) 시딩과 같은 패턴(V202609051400).
INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, (roles.role_value = 'OWNER')
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'OWNER' AS role_value
    UNION ALL SELECT 'ADMIN'
    UNION ALL SELECT 'OPERATOR'
    UNION ALL SELECT 'VIEWER'
) roles
WHERE pd.permission_key = 'MENU_ORG_SUBSCRIPTION_MARGIN' AND pd.permission_type = 'MENU';
