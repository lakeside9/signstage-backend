-- 조직×품목 세밀 할인 오버라이드(안 A). signstage-docs
-- business/organization-event-discount-pricing-review.md 4.1절(2026-08-21 재검토) 결정.
--
-- 카탈로그(billing_plans/optional_features/capacity_addons)는 전역 단일 가격이지만, 조직마다
-- 계약 조건이 다를 수 있어 조직×품목 조합별로 discount_type/discount_value를 오버라이드할 수
-- 있게 한다. 오버라이드 행이 없으면(조직×품목 조합에 매칭되는 행이 없으면) 카탈로그 자체의
-- discount_type/discount_value를 그대로 쓴다 — sentinel 없이 "행의 존재 여부"로 오버라이드
-- 유무를 표현한다.
--
-- 이 값은 Ceremony 생성(플랜)/구매 요청(선택옵션·용량 추가구매) 시점에 각각의 스냅샷 컬럼으로
-- 복사되므로, 이 테이블 값을 나중에 바꿔도 이미 만들어진 Ceremony/구매 건에는 영향을 주지
-- 않는다(라이브 참조가 아니라 생성 시점 스냅샷 고정).

CREATE TABLE organization_billing_plan_discounts (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    billing_plan_id BIGINT NOT NULL,
    discount_type   VARCHAR(20)   NOT NULL,
    discount_value  DECIMAL(12,2) NOT NULL,
    created_by      BIGINT NOT NULL,
    updated_by      BIGINT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_obpd_organization_plan UNIQUE (organization_id, billing_plan_id),
    CONSTRAINT fk_obpd_organization FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_obpd_plan FOREIGN KEY (billing_plan_id) REFERENCES billing_plans (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE organization_optional_feature_discounts (
    id                   BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id      BIGINT NOT NULL,
    optional_feature_id  BIGINT NOT NULL,
    discount_type        VARCHAR(20)   NOT NULL,
    discount_value       DECIMAL(12,2) NOT NULL,
    created_by           BIGINT NOT NULL,
    updated_by           BIGINT NULL,
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_oofd_organization_feature UNIQUE (organization_id, optional_feature_id),
    CONSTRAINT fk_oofd_organization FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_oofd_feature FOREIGN KEY (optional_feature_id) REFERENCES optional_features (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE organization_capacity_addon_discounts (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id   BIGINT NOT NULL,
    capacity_addon_id BIGINT NOT NULL,
    discount_type     VARCHAR(20)   NOT NULL,
    discount_value    DECIMAL(12,2) NOT NULL,
    created_by        BIGINT NOT NULL,
    updated_by        BIGINT NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_ocad_organization_addon UNIQUE (organization_id, capacity_addon_id),
    CONSTRAINT fk_ocad_organization FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_ocad_addon FOREIGN KEY (capacity_addon_id) REFERENCES capacity_addons (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- organization_id로만 거는 조회(findAllByOrganizationId, "이 조직에 걸린 오버라이드 전부")는
-- 위 각 uq_*_organization_* 복합 유니크 인덱스가 organization_id를 선두 컬럼으로 두고 있어
-- 그대로 커버된다(InnoDB 왼쪽-접두사 규칙) — 별도 인덱스를 추가하지 않는다.
