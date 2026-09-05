-- 메뉴/역할 권한 관리 스키마 — signstage-docs
-- business/menu-and-action-permission-management-review.md 7장(안 C, 하이브리드) 구현.
--
-- 착수 범위(12장 결정 #2, 2026-09-05): 플랫폼 축(PLATFORM)부터 시작한다. 조직 축
-- (ORGANIZATION)은 테이블 구조는 함께 만들되 이번 마이그레이션에서 실제 행을 시딩하지
-- 않는다 — 착수 시 별도 마이그레이션으로 추가한다.
--
-- 메뉴 편집 범위(12장 결정 #10, 2026-09-05): 이름/경로/순서까지 관리 화면에서 편집을
-- 허용하기로 해, 원안(menus.label_key만)보다 두 테이블(menu_translations,
-- menu_translation_histories)이 더 늘었다 — signstage-docs
-- business/multilingual-content-and-error-handling-review.md 6장 계약을 따른다.
--
-- 초기 시딩(7.5절)은 지금 코드(AdminLayout.tsx의 NAV_ITEMS, PlatformAdminOrganizationService/
-- PlatformAdminUserService/BillingPlanService 등의 하드코딩된 Set.of(...))와 정확히 같은
-- 값으로 채운다 — 이 마이그레이션 자체는 아직 아무 서비스 코드도 참조하지 않으므로
-- (RolePermissionService/MenuService는 조회 전용으로만 붙는다), 배포해도 실제 동작은
-- 바뀌지 않는다. 기존 하드코딩을 이 데이터로 교체하는 건 점진적 후속 작업이다(12장 결정 #8).
--
-- created_by/updated_by는 마이그레이션이 시딩하는 행이라 행위자가 없다 — 최초 플랫폼
-- 관리자 계정 시딩(scripts/seed-platform-admin.sql)과 같은 이유로 NULL을 허용한다
-- (다른 관리형 카탈로그의 created_by NOT NULL과 다른 점 — 그 카탈로그들은 앱 API를 통해
-- 관리자가 만들지만, 이 초기 메뉴/권한 행은 배포가 직접 시딩한다).

CREATE TABLE menus (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    console           VARCHAR(20) NOT NULL,
    parent_menu_id    BIGINT NULL,
    menu_key          VARCHAR(100) NOT NULL,
    label_key         VARCHAR(150) NOT NULL,
    path              VARCHAR(200) NULL,
    icon_key          VARCHAR(50) NULL,
    display_order     INT NOT NULL DEFAULT 0,
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_by        BIGINT NULL,
    updated_by        BIGINT NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_menus_menu_key UNIQUE (menu_key),
    CONSTRAINT fk_menus_parent FOREIGN KEY (parent_menu_id) REFERENCES menus (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_menus_console ON menus (console);
CREATE INDEX idx_menus_parent ON menus (parent_menu_id);

CREATE TABLE menu_histories (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    menu_id           BIGINT NOT NULL,
    path              VARCHAR(200) NULL,
    icon_key          VARCHAR(50) NULL,
    display_order     INT NOT NULL,
    active            BOOLEAN NOT NULL,
    created_by        BIGINT NOT NULL,
    updated_by        BIGINT NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_menu_history_menu FOREIGN KEY (menu_id) REFERENCES menus (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_menu_histories_menu ON menu_histories (menu_id);

CREATE TABLE menu_translations (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    menu_id           BIGINT NOT NULL,
    language_code     VARCHAR(10) NOT NULL,
    label             VARCHAR(150) NOT NULL,
    created_by        BIGINT NULL,
    updated_by        BIGINT NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_menu_translations_menu_language UNIQUE (menu_id, language_code),
    CONSTRAINT fk_menu_translations_menu FOREIGN KEY (menu_id) REFERENCES menus (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE menu_translation_histories (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    menu_translation_id   BIGINT NOT NULL,
    language_code         VARCHAR(10) NOT NULL,
    label                 VARCHAR(150) NOT NULL,
    created_by            BIGINT NOT NULL,
    updated_by            BIGINT NULL,
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_menu_translation_history_translation FOREIGN KEY (menu_translation_id) REFERENCES menu_translations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_menu_translation_histories_translation ON menu_translation_histories (menu_translation_id);

CREATE TABLE permission_definitions (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    permission_key    VARCHAR(100) NOT NULL,
    permission_type   VARCHAR(20) NOT NULL,
    role_axis         VARCHAR(20) NOT NULL,
    menu_id           BIGINT NULL,
    label_key         VARCHAR(150) NOT NULL,
    description_key   VARCHAR(150) NULL,
    display_order     INT NOT NULL DEFAULT 0,
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_by        BIGINT NULL,
    updated_by        BIGINT NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_permission_definitions_key UNIQUE (permission_key),
    CONSTRAINT fk_permission_definitions_menu FOREIGN KEY (menu_id) REFERENCES menus (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE role_permissions (
    id                        BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    permission_definition_id  BIGINT NOT NULL,
    role_value                VARCHAR(30) NOT NULL,
    allowed                   BOOLEAN NOT NULL DEFAULT TRUE,
    created_by                BIGINT NULL,
    updated_by                BIGINT NULL,
    created_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_role_permission UNIQUE (permission_definition_id, role_value),
    CONSTRAINT fk_role_permission_definition FOREIGN KEY (permission_definition_id) REFERENCES permission_definitions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_role_permissions_role ON role_permissions (role_value);

-- 결정(2026-09-04): append-only. role_permissions.allowed가 바뀔 때마다 변경 후 스냅샷을
-- 새 행으로 INSERT하고, 기존 이력 행은 UPDATE/DELETE하지 않는다(7.4절).
CREATE TABLE role_permission_histories (
    id                        BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    permission_definition_id  BIGINT NOT NULL,
    role_value                VARCHAR(30) NOT NULL,
    allowed                   BOOLEAN NOT NULL,
    created_by                BIGINT NOT NULL,
    updated_by                BIGINT NULL,
    created_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_role_permission_history_definition FOREIGN KEY (permission_definition_id) REFERENCES permission_definitions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_role_permission_histories_definition ON role_permission_histories (permission_definition_id);

-- ── 초기 시딩: PLATFORM 콘솔 메뉴 10개 (AdminLayout.tsx NAV_ITEMS, 2026-09-05 기준) ──
-- 지금은 등급 구분 없이 전부 노출되므로(2.2절), role_permissions도 전부 allowed=TRUE로
-- 시딩해 배포 직후 동작이 바뀌지 않게 한다.

INSERT INTO menus (console, menu_key, label_key, path, icon_key, display_order) VALUES
    ('PLATFORM', 'MENU_DASHBOARD',         'navigation.dashboard',        '/admin',                    'LayoutDashboard', 0),
    ('PLATFORM', 'MENU_PARTNERS',          'navigation.partners',         '/admin/organizations',      'Building2',       1),
    ('PLATFORM', 'MENU_PARTNER_REQUESTS',  'navigation.partnerRequests',  '/admin/organization-requests', 'ClipboardCheck', 2),
    ('PLATFORM', 'MENU_USERS',             'navigation.users',            '/admin/users',               'Users',           3),
    ('PLATFORM', 'MENU_ACCOUNTS',          'navigation.adminAccounts',    '/admin/accounts',            'ShieldCheck',     4),
    ('PLATFORM', 'MENU_BILLING_CATALOG',   'navigation.billingCatalog',   '/admin/billing-catalog',     'Package',         5),
    ('PLATFORM', 'MENU_BILLING_SIMULATOR', 'navigation.billingSimulator', '/admin/billing-simulator',   'Calculator',      6),
    ('PLATFORM', 'MENU_PURCHASE_REQUESTS', 'navigation.purchaseRequests', '/admin/purchase-requests',   'ShoppingCart',    7),
    ('PLATFORM', 'MENU_AUDIT_LOGS',        'navigation.auditLogs',        '/admin/audit-logs',          'ClipboardList',   8),
    ('PLATFORM', 'MENU_PROFILE',           'navigation.profile',          '/admin/profile',             'User',            9);

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT menu_key, 'MENU', 'PLATFORM', id, label_key, display_order FROM menus WHERE console = 'PLATFORM';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, role_value, TRUE
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.role_axis = 'PLATFORM' AND pd.permission_type = 'MENU';

-- ── 초기 시딩: PLATFORM 축 ACTION 권한키(발췌, 9장) ──
-- 지금 코드에 흩어진 대표적인 하드코딩 지점만 옮긴다 — 전수 조사는 실제 하드코딩 교체
-- 착수 시점(12장 결정 #8, 점진적 마이그레이션)에 진행한다.

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT 'ACTION_ACCOUNT_CREATE', 'ACTION', 'PLATFORM', id, 'permission.action.accountCreate', 0 FROM menus WHERE menu_key = 'MENU_ACCOUNTS'
UNION ALL
SELECT 'ACTION_ACCOUNT_ROLE_CHANGE', 'ACTION', 'PLATFORM', id, 'permission.action.accountRoleChange', 1 FROM menus WHERE menu_key = 'MENU_ACCOUNTS'
UNION ALL
SELECT 'ACTION_ACCOUNT_REVOKE', 'ACTION', 'PLATFORM', id, 'permission.action.accountRevoke', 2 FROM menus WHERE menu_key = 'MENU_ACCOUNTS'
UNION ALL
SELECT 'ACTION_USER_FORCE_WITHDRAW', 'ACTION', 'PLATFORM', id, 'permission.action.userForceWithdraw', 0 FROM menus WHERE menu_key = 'MENU_USERS'
UNION ALL
SELECT 'ACTION_MEMBER_FORCE_CONTROL', 'ACTION', 'PLATFORM', id, 'permission.action.memberForceControl', 1 FROM menus WHERE menu_key = 'MENU_USERS'
UNION ALL
SELECT 'ACTION_PARTNER_STATUS_CHANGE', 'ACTION', 'PLATFORM', id, 'permission.action.partnerStatusChange', 0 FROM menus WHERE menu_key = 'MENU_PARTNERS'
UNION ALL
SELECT 'ACTION_PARTNER_INFO_EDIT', 'ACTION', 'PLATFORM', id, 'permission.action.partnerInfoEdit', 1 FROM menus WHERE menu_key = 'MENU_PARTNERS'
UNION ALL
SELECT 'ACTION_BILLING_CATALOG_MANAGE', 'ACTION', 'PLATFORM', id, 'permission.action.billingCatalogManage', 0 FROM menus WHERE menu_key = 'MENU_BILLING_CATALOG';

-- PLATFORM_SUPER 전용(현재 PlatformAdminUserService#checkSuperRole 하드코딩과 동일)
INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, (roles.role_value = 'PLATFORM_SUPER')
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key IN ('ACTION_ACCOUNT_CREATE', 'ACTION_ACCOUNT_ROLE_CHANGE', 'ACTION_ACCOUNT_REVOKE', 'ACTION_USER_FORCE_WITHDRAW');

-- PLATFORM_OPS 이상(현재 각 서비스의 *_ALLOWED_ROLES = Set.of("PLATFORM_OPS", "PLATFORM_SUPER")와 동일)
INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, (roles.role_value IN ('PLATFORM_OPS', 'PLATFORM_SUPER'))
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key IN ('ACTION_MEMBER_FORCE_CONTROL', 'ACTION_PARTNER_STATUS_CHANGE', 'ACTION_PARTNER_INFO_EDIT', 'ACTION_BILLING_CATALOG_MANAGE');
