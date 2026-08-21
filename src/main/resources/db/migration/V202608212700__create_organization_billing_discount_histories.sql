-- 조직×품목 할인 오버라이드(organization_billing_plan_discounts 등)의 변경 이력.
-- signstage-docs business/organization-event-discount-pricing-review.md 4.1절 후속
-- (사용자 요청 2026-08-21 — 카탈로그(billing_plan_histories 등)처럼 구조화된 이력 테이블 요청).
--
-- 카탈로그 이력과 달리 오버라이드는 실제로 하드 삭제(제거)가 일어난다 — 그래서 살아있는
-- 오버라이드 행을 FK로 참조하는 대신, 삭제되지 않는 organization_id/품목_id 조합으로
-- 스코핑하고 removed 컬럼으로 "이 시점에 오버라이드가 제거됐다"를 표현한다. 제거 이벤트도
-- 그 직전 discount_type/discount_value를 그대로 남긴다 — 이력만 보고 "무엇이 제거됐는지" 알
-- 수 있어야 한다.

CREATE TABLE organization_billing_plan_discount_histories (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    billing_plan_id BIGINT NOT NULL,
    discount_type   VARCHAR(20)   NOT NULL,
    discount_value  DECIMAL(12,2) NOT NULL,
    removed         BOOLEAN NOT NULL DEFAULT FALSE,
    created_by      BIGINT NOT NULL,
    updated_by      BIGINT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_obpdh_organization FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_obpdh_plan FOREIGN KEY (billing_plan_id) REFERENCES billing_plans (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_obpdh_org_plan ON organization_billing_plan_discount_histories (organization_id, billing_plan_id);

CREATE TABLE organization_optional_feature_discount_histories (
    id                   BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id      BIGINT NOT NULL,
    optional_feature_id  BIGINT NOT NULL,
    discount_type        VARCHAR(20)   NOT NULL,
    discount_value       DECIMAL(12,2) NOT NULL,
    removed              BOOLEAN NOT NULL DEFAULT FALSE,
    created_by           BIGINT NOT NULL,
    updated_by           BIGINT NULL,
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_oofdh_organization FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_oofdh_feature FOREIGN KEY (optional_feature_id) REFERENCES optional_features (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_oofdh_org_feature ON organization_optional_feature_discount_histories (organization_id, optional_feature_id);

CREATE TABLE organization_capacity_addon_discount_histories (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id   BIGINT NOT NULL,
    capacity_addon_id BIGINT NOT NULL,
    discount_type     VARCHAR(20)   NOT NULL,
    discount_value    DECIMAL(12,2) NOT NULL,
    removed           BOOLEAN NOT NULL DEFAULT FALSE,
    created_by        BIGINT NOT NULL,
    updated_by        BIGINT NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ocadh_organization FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_ocadh_addon FOREIGN KEY (capacity_addon_id) REFERENCES capacity_addons (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_ocadh_org_addon ON organization_capacity_addon_discount_histories (organization_id, capacity_addon_id);
