-- 조직 구독/계약 스키마 — signstage-docs
-- business/organization-event-discount-pricing-review.md 8장 결정(2026-09-10 착수 확정, 8.7절
-- "최종 결정 — 착수 확정"). 조직(OWNER)이 구독형 BillingPlan을 신청하면 플랫폼 관리자
-- (PLATFORM_OPS 이상)가 승인해야 실제로 사용할 수 있다 — organization_creation_requests와
-- 같은 요청→승인 패턴. 중도 해지도 같은 구조(요청→승인)를 재사용한다.
--
-- 1) billing_plans에 구독 조건 4개 컬럼을 추가한다. 기존 플랜(전부 STANDARD)과의 하위호환을
--    위해 plan_type은 DEFAULT 'STANDARD'로 채운다 — 나머지 3개는 STANDARD 플랜엔 원래
--    null이라 기존 행에 영향 없다.
-- 2) organization_subscriptions — 조직×구독형 플랜 계약 1건. 조직당 진행 중(PENDING/ACTIVE/
--    CANCELLATION_REQUESTED) 최대 1건은 서비스 레이어에서 강제한다(MySQL이 조건부 유니크
--    인덱스를 지원하지 않아서 — organization_creation_requests의 "1인 1조직" 제약과 같은
--    이유로 DB 제약을 두지 않음).
-- 3) organization_subscription_histories — 상태 전이 append-only 이력.
-- 4) ceremonies.organization_subscription_id — 이 행사가 소진시킨 구독(있다면). 플랜 확정
--    시점에 한 번만 채워지고 이후 바뀌지 않는다.

ALTER TABLE billing_plans
    ADD COLUMN plan_type                  VARCHAR(20) NOT NULL DEFAULT 'STANDARD',  -- STANDARD / SUBSCRIPTION
    ADD COLUMN subscription_type          VARCHAR(20) NULL,                          -- PERIOD_AND_COUNT / COUNT_ONLY
    ADD COLUMN subscription_period_months INT NULL,                                  -- PERIOD_AND_COUNT만(6 또는 12)
    ADD COLUMN subscription_allowed_count INT NULL;                                  -- 구독형이면 필수

CREATE TABLE organization_subscriptions (
    id                          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id             BIGINT NOT NULL,
    billing_plan_id              BIGINT NOT NULL,
    requested_by                BIGINT NOT NULL,
    status                       VARCHAR(25) NOT NULL DEFAULT 'PENDING',
    -- 승인 시점 스냅샷(카탈로그가 나중에 바뀌어도 고정) — 승인 전엔 전부 NULL.
    plan_name_snapshot           VARCHAR(100) NULL,
    subscription_type_snapshot   VARCHAR(20) NULL,
    period_months_snapshot       INT NULL,
    allowed_count_snapshot       INT NULL,
    start_date                   DATE NULL,
    end_date                     DATE NULL,          -- PERIOD_AND_COUNT만 값을 가짐
    approval_source              VARCHAR(10) NOT NULL DEFAULT 'MANUAL',  -- MANUAL / PAYMENT
    reviewed_by                  BIGINT NULL,         -- 행위자 참조. FK 없음(organization_creation_requests와 같은 패턴)
    reviewed_at                  TIMESTAMP NULL,
    rejection_reason             VARCHAR(500) NULL,
    cancellation_reason          VARCHAR(500) NULL,
    created_by                   BIGINT NULL,
    updated_by                   BIGINT NULL,
    created_at                   TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                   TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_os_organization FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_os_billing_plan FOREIGN KEY (billing_plan_id) REFERENCES billing_plans (id),
    CONSTRAINT fk_os_requested_by FOREIGN KEY (requested_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_os_organization_status ON organization_subscriptions (organization_id, status);
CREATE INDEX idx_os_status_end_date ON organization_subscriptions (status, end_date);

CREATE TABLE organization_subscription_histories (
    id                              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_subscription_id    BIGINT NOT NULL,
    status                          VARCHAR(25) NOT NULL,
    reviewed_by                     BIGINT NULL,
    note                            VARCHAR(500) NULL,
    created_by                      BIGINT NULL,
    updated_by                      BIGINT NULL,
    created_at                      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_osh_subscription FOREIGN KEY (organization_subscription_id) REFERENCES organization_subscriptions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_osh_subscription ON organization_subscription_histories (organization_subscription_id);

ALTER TABLE ceremonies
    ADD COLUMN organization_subscription_id BIGINT NULL,
    ADD CONSTRAINT fk_ceremonies_subscription FOREIGN KEY (organization_subscription_id) REFERENCES organization_subscriptions (id);

CREATE INDEX idx_ceremonies_subscription ON ceremonies (organization_subscription_id);

-- 플랫폼 관리자 콘솔 메뉴 2개 — "구독 플랜 카탈로그"는 기존 과금 카탈로그 그룹, "구독 요청 관리"는
-- 기존 구매/할인 요청 그룹 아래 둔다(각각 MENU_UNIT_PRODUCTS, MENU_PURCHASE_REQUESTS와 같은 급).
-- 플랜 카탈로그 자체(BillingPlan 생성/수정)는 기존 ACTION_BILLING_CATALOG_MANAGE를 그대로
-- 재사용한다(신규 화면일 뿐 새 액션이 아님) — 승인 큐만 새 액션이 필요하다.
INSERT INTO menus (console, parent_menu_id, menu_key, label_key, path, icon_key, display_order)
SELECT 'PLATFORM', g.id, 'MENU_SUBSCRIPTION_PLANS', 'navigation.subscriptionPlans', '/admin/billing-catalog/subscription-plans', 'CalendarClock', 11
FROM menus g WHERE g.menu_key = 'MENU_GROUP_BILLING';

INSERT INTO menus (console, parent_menu_id, menu_key, label_key, path, icon_key, display_order)
SELECT 'PLATFORM', g.id, 'MENU_SUBSCRIPTION_REQUESTS', 'navigation.subscriptionRequests', '/admin/subscription-requests', 'ClipboardCheck', 14
FROM menus g WHERE g.menu_key = 'MENU_GROUP_PURCHASE';

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT menu_key, 'MENU', 'PLATFORM', id, label_key, display_order
FROM menus WHERE menu_key IN ('MENU_SUBSCRIPTION_PLANS', 'MENU_SUBSCRIPTION_REQUESTS');

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT 'ACTION_SUBSCRIPTION_REQUEST_REVIEW', 'ACTION', 'PLATFORM', id, 'permission.action.subscriptionRequestReview', 0
FROM menus WHERE menu_key = 'MENU_SUBSCRIPTION_REQUESTS';

-- MENU 노출은 PLATFORM_SUPPORT 이상 전체(다른 조회용 메뉴와 같은 관례), ACTION(승인/반려)은
-- PLATFORM_OPS 이상만(하드코딩 시절 다른 승인 큐들과 같은 범위 — V202609101200과 같은 관례).
INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, TRUE
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_type = 'MENU' AND pd.permission_key IN ('MENU_SUBSCRIPTION_PLANS', 'MENU_SUBSCRIPTION_REQUESTS');

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, (roles.role_value IN ('PLATFORM_OPS', 'PLATFORM_SUPER'))
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key = 'ACTION_SUBSCRIPTION_REQUEST_REVIEW';
