-- 행사(Ceremony) 단위 과금 옵션 카탈로그.
-- signstage-docs business/ceremony-billing-options-review.md 3장(제안 데이터 모델) 참고.
--
-- BillingPlan: 필수옵션(서명자/템플릿/테스트·본행사 수 한도)을 항상 갖는 사전 정의 플랜.
-- OptionalFeature: 서명확대/폭죽/화상참석 같은 선택옵션 상품 카탈로그(4.6/4.7 결정).
-- CapacityAddOn: 필수옵션 상향(추가구매) 상품 카탈로그(4.7/4.9 결정 — 시스템 절대 상한을 코드에
--   두지 않고 카탈로그 데이터로만 표현한다).
-- BillingPlanOptionalFeature: 플랜에 기본으로 포함되는 선택옵션 매핑(다대다 조인).
--
-- 4.9 결정에 따라 필수옵션 4개 컬럼은 전부 NOT NULL이다(무제한 표현을 위한 NULL/-1 sentinel 없음).
-- discount_type/discount_value는 4.7 결정(퍼센트/정액 둘 다 지원)에 따라 같은 컬럼 두 개를
-- discount_type으로 해석을 분기해 재사용한다.

CREATE TABLE billing_plans (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name              VARCHAR(100) NOT NULL,
    supply_price      DECIMAL(12,2) NOT NULL,
    sale_price        DECIMAL(12,2) NOT NULL,
    discount_type     VARCHAR(20) NOT NULL,
    discount_value    DECIMAL(12,2) NOT NULL DEFAULT 0,
    max_signers       INT NOT NULL,
    max_templates     INT NOT NULL,
    max_test_events   INT NOT NULL,
    max_main_events   INT NOT NULL,
    created_by        BIGINT NOT NULL,
    updated_by        BIGINT NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE optional_features (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code              VARCHAR(50) NOT NULL,
    name              VARCHAR(100) NOT NULL,
    supply_price      DECIMAL(12,2) NOT NULL,
    sale_price        DECIMAL(12,2) NOT NULL,
    discount_type     VARCHAR(20) NOT NULL,
    discount_value    DECIMAL(12,2) NOT NULL DEFAULT 0,
    created_by        BIGINT NOT NULL,
    updated_by        BIGINT NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_of_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE capacity_addons (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    capacity_type     VARCHAR(20) NOT NULL,
    unit_amount       INT NOT NULL,
    supply_price      DECIMAL(12,2) NOT NULL,
    sale_price        DECIMAL(12,2) NOT NULL,
    discount_type     VARCHAR(20) NOT NULL,
    discount_value    DECIMAL(12,2) NOT NULL DEFAULT 0,
    created_by        BIGINT NOT NULL,
    updated_by        BIGINT NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE billing_plan_optional_features (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    billing_plan_id       BIGINT NOT NULL,
    optional_feature_id   BIGINT NOT NULL,
    created_by            BIGINT NOT NULL,
    updated_by            BIGINT NULL,
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_bpof_plan_feature UNIQUE (billing_plan_id, optional_feature_id),
    CONSTRAINT fk_bpof_plan FOREIGN KEY (billing_plan_id) REFERENCES billing_plans (id),
    CONSTRAINT fk_bpof_feature FOREIGN KEY (optional_feature_id) REFERENCES optional_features (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_bpof_plan ON billing_plan_optional_features (billing_plan_id);
