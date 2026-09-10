-- 하드코딩 권한 잔여분 2건을 동적 RBAC로 이관 — signstage-docs
-- business/menu-and-action-permission-management-review.md 12장 결정 #8(점진적 마이그레이션).
-- PlatformAdminMemberService#checkCanManage/PlatformAdminOrganizationRequestService#checkCanManage가
-- Set.of("PLATFORM_OPS", "PLATFORM_SUPER") 하드코딩으로 남아 있던 마지막 두 곳이다 — 나머지
-- 서비스는 이미 여러 라운드에 걸쳐 RolePermissionService.isAllowed(...)로 이관됐다.
--
-- ACTION_PARTNER_MEMBER_CONTROL: 조직 멤버 강제 조정(역할 변경/제거) — MENU_PARTNERS 아래
-- ACTION_PARTNER_STATUS_CHANGE/ACTION_PARTNER_INFO_EDIT/ACTION_PARTNER_CREATE와 같은 조직 상세
-- 화면의 세부 액션이다(PlatformAdminMemberController가 /organizations/{id}/members 아래
-- 있다).
--
-- ACTION_PARTNER_REQUEST_REVIEW: 조직 생성 요청 승인/반려 — MENU_PARTNER_REQUESTS(파트너등록
-- 요청관리) 화면 전용이라 그 메뉴 밑에 새로 둔다(지금까지 그 메뉴에는 ACTION 세분화가 없었다).
--
-- 기존 관례(V202609051300)대로 시딩값은 하드코딩과 동일한 범위(PLATFORM_OPS/PLATFORM_SUPER만
-- 허용, PLATFORM_SUPPORT는 조회만)로 시작한다 — 관리 화면에서 나중에 조정 가능.

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT 'ACTION_PARTNER_MEMBER_CONTROL', 'ACTION', 'PLATFORM', id, 'permission.action.partnerMemberControl', 6
FROM menus WHERE menu_key = 'MENU_PARTNERS';

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT 'ACTION_PARTNER_REQUEST_REVIEW', 'ACTION', 'PLATFORM', id, 'permission.action.partnerRequestReview', 0
FROM menus WHERE menu_key = 'MENU_PARTNER_REQUESTS';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, (roles.role_value IN ('PLATFORM_OPS', 'PLATFORM_SUPER'))
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key IN ('ACTION_PARTNER_MEMBER_CONTROL', 'ACTION_PARTNER_REQUEST_REVIEW');
