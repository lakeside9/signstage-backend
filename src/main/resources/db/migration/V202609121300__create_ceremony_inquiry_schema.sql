-- 행사별 1:1 문의 — signstage-docs business/partner-support-center-review.md 5장(2026-09-12).
-- 이 코드베이스에 없던 "헤더+메시지 대화 스레드" 패턴을 새로 도입한다. 파트너 쪽 쓰기 권한은
-- 새 권한키 없이 기존 ACTION_CEREMONY_MANAGE를 재사용한다(9장 결정 #6) — 그래서
-- ORGANIZATION 축에는 이 마이그레이션이 아무 행도 추가하지 않는다.

CREATE TABLE ceremony_inquiries (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ceremony_id     BIGINT NOT NULL,
    title           VARCHAR(200) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    last_message_at TIMESTAMP NOT NULL,
    created_by      BIGINT NOT NULL,
    updated_by      BIGINT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ceremony_inquiries_ceremony FOREIGN KEY (ceremony_id) REFERENCES ceremonies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_ceremony_inquiries_ceremony ON ceremony_inquiries (ceremony_id, last_message_at);
CREATE INDEX idx_ceremony_inquiries_status ON ceremony_inquiries (status);

CREATE TABLE ceremony_inquiry_messages (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    inquiry_id  BIGINT NOT NULL,
    sender_type VARCHAR(20) NOT NULL,
    content     TEXT NOT NULL,
    created_by  BIGINT NOT NULL,
    updated_by  BIGINT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ceremony_inquiry_messages_inquiry FOREIGN KEY (inquiry_id) REFERENCES ceremony_inquiries (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_ceremony_inquiry_messages_inquiry ON ceremony_inquiry_messages (inquiry_id, created_at);

-- "고객지원 관리" 그룹(V202609121100) 아래, 공지사항/FAQ 다음 순서. 조회는 다른 관리자 메뉴와
-- 같이 PLATFORM_SUPPORT 이상 전체, 답변/종료(ACTION_CEREMONY_INQUIRY_MANAGE)는 PLATFORM_OPS
-- 이상만 — ACTION_PURCHASE_APPROVAL과 같은 범위.
INSERT INTO menus (console, parent_menu_id, menu_key, label_key, path, icon_key, display_order)
SELECT 'PLATFORM', g.id, 'MENU_CEREMONY_INQUIRIES', 'navigation.ceremonyInquiries', '/admin/ceremony-inquiries', 'MessageCircleQuestion', 2
FROM menus g WHERE g.menu_key = 'MENU_GROUP_SUPPORT';

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT menu_key, 'MENU', 'PLATFORM', id, label_key, display_order FROM menus WHERE menu_key = 'MENU_CEREMONY_INQUIRIES';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, TRUE
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key = 'MENU_CEREMONY_INQUIRIES' AND pd.permission_type = 'MENU';

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT 'ACTION_CEREMONY_INQUIRY_MANAGE', 'ACTION', 'PLATFORM', id, 'permission.action.ceremonyInquiryManage', 0
FROM menus WHERE menu_key = 'MENU_CEREMONY_INQUIRIES';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, (roles.role_value IN ('PLATFORM_OPS', 'PLATFORM_SUPER'))
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key = 'ACTION_CEREMONY_INQUIRY_MANAGE';
