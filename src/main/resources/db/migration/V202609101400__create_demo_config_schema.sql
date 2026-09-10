-- 체험형 데모 사이트(legacy ~/Works/eform/source/signstage/demo-signstage-frontend, 별도
-- 저장소, 그대로 재사용) 연동 스키마 — signstage-docs
-- business/demo-account-exhibition-signer-preview-review.md 13장 결정(2026-09-10). legacy
-- feature.demo.entity.DemoConfig와 같은 테이블 모양을 그대로 가져왔다 — legacy 데모 셸이
-- GET /api/demo/config?slug=, GET /api/demo/configs 응답 필드 이름을 그대로 소비하므로,
-- 새로 설계하기보다 검증된 모양을 재사용한다.
--
-- eventAccessKey는 ceremony_events를 FK 없이 문자열로 참조한다 — 서명자 포털/프로젝터
-- 화면들이 이미 accessKey 소지 기반 공개 접근 모델을 쓰고 있어 같은 관례를 따른다.

CREATE TABLE demo_configs (
    id                 BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    slug               VARCHAR(50) NOT NULL,
    event_access_key   VARCHAR(100) NOT NULL,
    pages              INT NULL,          -- 전시 화면 도구모음 미리 지정용(선택), 1~3
    start_page         INT NULL,
    spacing            VARCHAR(10) NULL,  -- spaced | joined
    is_enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_by         BIGINT NULL,
    updated_by         BIGINT NULL,
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_demo_configs_slug UNIQUE (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 프로필 하나에 여러 서명자를 지정할 수 있다(데모 셸은 최대 2명까지만 나란히 보여준다) —
-- display_order로 지정한 순서를 그대로 보존한다(@OrderColumn).
CREATE TABLE demo_config_signers (
    demo_config_id    BIGINT NOT NULL,
    display_order     INT NOT NULL,
    signer_access_key VARCHAR(100) NOT NULL,
    PRIMARY KEY (demo_config_id, display_order),
    CONSTRAINT fk_demo_config_signers_config FOREIGN KEY (demo_config_id) REFERENCES demo_configs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 관리자 콘솔 메뉴 — 기존 "데모 관리" 그룹(MENU_GROUP_DEMO) 아래, 데모 행사 관리
-- (MENU_DEMO_CEREMONIES) 다음 순서. 쓰기 권한은 신규 액션을 만들지 않고 기존
-- ACTION_DEMO_CEREMONY_MANAGE(데모 행사 관리와 같은 등급)를 재사용한다.
INSERT INTO menus (console, parent_menu_id, menu_key, label_key, path, icon_key, display_order)
SELECT 'PLATFORM', g.id, 'MENU_DEMO_CONFIGS', 'navigation.demoConfigs', '/admin/demo-configs', 'Link', 15
FROM menus g WHERE g.menu_key = 'MENU_GROUP_DEMO';

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT menu_key, 'MENU', 'PLATFORM', id, label_key, display_order
FROM menus WHERE menu_key = 'MENU_DEMO_CONFIGS';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, TRUE
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_type = 'MENU' AND pd.permission_key = 'MENU_DEMO_CONFIGS';
