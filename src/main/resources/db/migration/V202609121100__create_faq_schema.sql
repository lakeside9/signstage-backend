-- FAQ — signstage-docs business/partner-support-center-review.md 4장(2026-09-12).
-- CeremonyEffectDefinition과 같은 카탈로그 CRUD 패턴. category는 고정 enum이 아니라 자유
-- 문자열(nullable) — UnitProduct.exclusivityGroup과 같은 원칙.

CREATE TABLE faqs (
    id            BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    category      VARCHAR(50) NULL,
    question      VARCHAR(500) NOT NULL,
    answer        TEXT NOT NULL,
    display_order INT NOT NULL,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_by    BIGINT NOT NULL,
    updated_by    BIGINT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_faqs_active_order ON faqs (is_active, display_order);

-- 신규 PLATFORM 콘솔 그룹 "고객지원 관리"(공지사항/FAQ/행사 문의를 여기 모은다) — 기존
-- 그룹들(1/6/10/14/16) 사이에서 SYSTEM(16)과 PROFILE(20) 사이에 끼워 넣는다. 자리를 만들려고
-- PROFILE만 한 칸 뒤로 민다(V202609091400이 EFFECTS 추가 때 했던 것과 같은 방식).
UPDATE menus SET display_order = display_order + 1
WHERE console = 'PLATFORM' AND parent_menu_id IS NULL AND display_order >= 20;

INSERT INTO menus (console, menu_key, label_key, path, icon_key, display_order) VALUES
    ('PLATFORM', 'MENU_GROUP_SUPPORT', 'navigation.groupSupport', NULL, 'LifeBuoy', 20);

INSERT INTO menus (console, parent_menu_id, menu_key, label_key, path, icon_key, display_order)
SELECT 'PLATFORM', g.id, 'MENU_FAQS', 'navigation.faqs', '/admin/faqs', 'HelpCircle', 1
FROM menus g WHERE g.menu_key = 'MENU_GROUP_SUPPORT';

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT menu_key, 'MENU', 'PLATFORM', id, label_key, display_order FROM menus WHERE menu_key = 'MENU_FAQS';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, TRUE
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key = 'MENU_FAQS' AND pd.permission_type = 'MENU';

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT 'ACTION_FAQ_MANAGE', 'ACTION', 'PLATFORM', id, 'permission.action.faqManage', 0
FROM menus WHERE menu_key = 'MENU_FAQS';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, (roles.role_value IN ('PLATFORM_OPS', 'PLATFORM_SUPER'))
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key = 'ACTION_FAQ_MANAGE';

-- 파트너 쪽 조회 메뉴(ORGANIZATION 축) — 4개 등급 모두 조회만(쓰기 권한 없음, 공개 API가
-- 이미 권한 검사 없이 활성 항목만 반환한다).
INSERT INTO menus (console, menu_key, label_key, path, icon_key, display_order) VALUES
    ('ORGANIZATION', 'MENU_ORG_FAQS', 'navigation.faqs', '/faqs', 'HelpCircle', 4);

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT menu_key, 'MENU', 'ORGANIZATION', id, label_key, display_order FROM menus WHERE menu_key = 'MENU_ORG_FAQS';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, TRUE
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'OWNER' AS role_value
    UNION ALL SELECT 'ADMIN'
    UNION ALL SELECT 'OPERATOR'
    UNION ALL SELECT 'VIEWER'
) roles
WHERE pd.permission_key = 'MENU_ORG_FAQS' AND pd.permission_type = 'MENU';
