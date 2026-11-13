-- 고객 문의하기(2026-11-13) — 로그인 없이 보는 공개 화면에서 도입·요금·현장 지원을 문의한다.
-- 답변은 시스템 밖(전화·메일)에서 하고, 관리자는 처리 상태와 내부 메모만 기록한다.
-- 행사별 1:1 문의(ceremony_inquiries, 파트너↔관리자)와는 다른 기능이다.
CREATE TABLE contact_inquiries (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    inquiry_type          VARCHAR(30) NOT NULL,
    company_name          VARCHAR(100) NOT NULL,
    contact_name          VARCHAR(50) NOT NULL,
    email                 VARCHAR(200) NOT NULL,
    phone                 VARCHAR(50) NULL,
    event_date            DATE NULL,
    expected_signers      INT NULL,
    message               TEXT NOT NULL,
    privacy_consented_at  TIMESTAMP(6) NOT NULL,
    requester_ip          VARCHAR(45) NULL,
    status                VARCHAR(20) NOT NULL,
    admin_memo            VARCHAR(1000) NULL,
    handled_by            BIGINT NULL,
    handled_at            TIMESTAMP(6) NULL,
    created_by            BIGINT NULL,
    updated_by            BIGINT NULL,
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_contact_inquiries_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 메뉴: 고객지원 관리 > 고객 문의(행사 문의 관리 아래).
INSERT INTO menus (console, parent_menu_id, menu_key, label_key, path, icon_key, display_order)
SELECT 'PLATFORM', g.id, 'MENU_CONTACT_INQUIRIES', 'navigation.contactInquiries', '/admin/contact-inquiries', 'Inbox', 3
FROM menus g WHERE g.menu_key = 'MENU_GROUP_SUPPORT';

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT menu_key, 'MENU', 'PLATFORM', id, label_key, display_order FROM menus WHERE menu_key = 'MENU_CONTACT_INQUIRIES';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, TRUE
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key = 'MENU_CONTACT_INQUIRIES' AND pd.permission_type = 'MENU';

-- 처리 상태·메모 기록. 고객 응대는 지원 담당 업무라 세 등급 모두 허용한다(새 문의 알림도 이 권한자에게 간다).
INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT 'ACTION_CONTACT_INQUIRY_MANAGE', 'ACTION', 'PLATFORM', id, 'permission.action.contactInquiryManage', 0
FROM menus WHERE menu_key = 'MENU_CONTACT_INQUIRIES';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, TRUE
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key = 'ACTION_CONTACT_INQUIRY_MANAGE';
