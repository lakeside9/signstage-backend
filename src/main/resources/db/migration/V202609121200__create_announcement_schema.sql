-- 공지사항 — signstage-docs business/partner-support-center-review.md 3장(2026-09-12). v1은
-- 플랫폼 전체 공개만 지원한다(조직별 타겟팅은 범위 밖, 9장 결정 #1). 정렬은 pinned 우선 +
-- created_at 내림차순이라 수동 displayOrder를 두지 않는다.

CREATE TABLE announcements (
    id         BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title      VARCHAR(200) NOT NULL,
    content    TEXT NOT NULL,
    is_pinned  BOOLEAN NOT NULL DEFAULT FALSE,
    is_active  BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_announcements_active_pinned ON announcements (is_active, is_pinned, created_at);

-- "고객지원 관리" 그룹(V202609121100) 아래, FAQ 다음 순서.
INSERT INTO menus (console, parent_menu_id, menu_key, label_key, path, icon_key, display_order)
SELECT 'PLATFORM', g.id, 'MENU_ANNOUNCEMENTS', 'navigation.announcements', '/admin/announcements', 'Megaphone', 0
FROM menus g WHERE g.menu_key = 'MENU_GROUP_SUPPORT';

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT menu_key, 'MENU', 'PLATFORM', id, label_key, display_order FROM menus WHERE menu_key = 'MENU_ANNOUNCEMENTS';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, TRUE
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key = 'MENU_ANNOUNCEMENTS' AND pd.permission_type = 'MENU';

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT 'ACTION_ANNOUNCEMENT_MANAGE', 'ACTION', 'PLATFORM', id, 'permission.action.announcementManage', 0
FROM menus WHERE menu_key = 'MENU_ANNOUNCEMENTS';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, (roles.role_value IN ('PLATFORM_OPS', 'PLATFORM_SUPER'))
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key = 'ACTION_ANNOUNCEMENT_MANAGE';

-- 파트너 쪽 조회 메뉴(ORGANIZATION 축) — FAQ(V202609121100)와 같은 원칙.
INSERT INTO menus (console, menu_key, label_key, path, icon_key, display_order) VALUES
    ('ORGANIZATION', 'MENU_ORG_ANNOUNCEMENTS', 'navigation.announcements', '/announcements', 'Megaphone', 5);

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT menu_key, 'MENU', 'ORGANIZATION', id, label_key, display_order FROM menus WHERE menu_key = 'MENU_ORG_ANNOUNCEMENTS';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, TRUE
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'OWNER' AS role_value
    UNION ALL SELECT 'ADMIN'
    UNION ALL SELECT 'OPERATOR'
    UNION ALL SELECT 'VIEWER'
) roles
WHERE pd.permission_key = 'MENU_ORG_ANNOUNCEMENTS' AND pd.permission_type = 'MENU';
