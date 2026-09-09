-- 과금 플랜/선택옵션/용량 추가구매 상품에 판매가격 유효기간(from-to)을 도입 — 다중버전 채택
-- (signstage-docs business/billing-catalog-price-validity-period-review.md 결정, 2026-09-09).
--
-- 지금까지 billing_plans/optional_features/capacity_addons(그리고 각 *_histories) 행이 직접
-- 갖고 있던 가격정보(currency_code/supply_price/sale_price/discount_type/discount_value/
-- tax_code)와 사용여부(active)를 전부 새 *_price_periods 테이블로 옮긴다 — 행 하나 = 기간
-- 하나(TaxPolicy/organization_billing_plan_discounts와 같은 다중 버전 방식). 카탈로그 엔티티는
-- 이제 정체성(이름/코드/분류 등)만 갖고, "지금 유효한 가격이 얼마인지"는 항상
-- *_price_periods에서 findEffective로 조회한다.
--
-- 개발 단계라 기존 데이터 보존 부담이 없다(billing-catalog-zero-base-schema-redesign-review.md와
-- 같은 전제) — 그래도 지금 있는 행은 "생성된 날부터 무기한 유효했다"로 backfill한다
-- (effective_from = DATE(created_at), effective_to = NULL). 기존 값/사용여부 편집 이력
-- (*_histories의 가격/active 컬럼)은 새 *_price_period_histories로 옮기지 않고, 각 상품의
-- "현재 상태" 하나만 최초 기간으로 backfill한다 — 과거 편집 이력 자체는 이 마이그레이션의
-- 범위 밖이다.

-- ================= billing_plans =================

CREATE TABLE billing_plan_price_periods (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    billing_plan_id   BIGINT NOT NULL,
    currency_code     CHAR(3) NOT NULL,
    supply_price      DECIMAL(19,4) NULL,
    sale_price        DECIMAL(19,4) NOT NULL,
    discount_type     VARCHAR(20) NOT NULL,
    discount_value    DECIMAL(19,4) NOT NULL DEFAULT 0,
    tax_code          VARCHAR(50) NOT NULL,
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from    DATE NOT NULL,
    effective_to      DATE NULL,
    created_by        BIGINT NOT NULL,
    updated_by        BIGINT NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_bppp_plan_period UNIQUE (billing_plan_id, effective_from),
    CONSTRAINT ck_bppp_period CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT fk_bppp_plan FOREIGN KEY (billing_plan_id) REFERENCES billing_plans (id),
    CONSTRAINT fk_bppp_currency FOREIGN KEY (currency_code) REFERENCES currencies (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE billing_plan_price_period_histories (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    billing_plan_id   BIGINT NOT NULL,
    currency_code     CHAR(3) NOT NULL,
    supply_price      DECIMAL(19,4) NULL,
    sale_price        DECIMAL(19,4) NOT NULL,
    discount_type     VARCHAR(20) NOT NULL,
    discount_value    DECIMAL(19,4) NOT NULL,
    tax_code          VARCHAR(50) NOT NULL,
    active            BOOLEAN NOT NULL,
    effective_from    DATE NOT NULL,
    effective_to      DATE NULL,
    removed           BOOLEAN NOT NULL DEFAULT FALSE,
    created_by        BIGINT NOT NULL,
    updated_by        BIGINT NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_bppph_plan FOREIGN KEY (billing_plan_id) REFERENCES billing_plans (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO billing_plan_price_periods
    (billing_plan_id, currency_code, supply_price, sale_price, discount_type, discount_value, tax_code, active,
     effective_from, effective_to, created_by, updated_by, created_at, updated_at)
SELECT id, currency_code, supply_price, sale_price, discount_type, discount_value, tax_code, active,
       DATE(created_at), NULL, created_by, updated_by, created_at, updated_at
FROM billing_plans;

INSERT INTO billing_plan_price_period_histories
    (billing_plan_id, currency_code, supply_price, sale_price, discount_type, discount_value, tax_code, active,
     effective_from, effective_to, removed, created_by, updated_by, created_at, updated_at)
SELECT id, currency_code, supply_price, sale_price, discount_type, discount_value, tax_code, active,
       DATE(created_at), NULL, FALSE, created_by, updated_by, created_at, updated_at
FROM billing_plans;

ALTER TABLE billing_plans
    DROP FOREIGN KEY fk_plan_currency,
    DROP COLUMN currency_code,
    DROP COLUMN supply_price,
    DROP COLUMN sale_price,
    DROP COLUMN discount_type,
    DROP COLUMN discount_value,
    DROP COLUMN tax_code,
    DROP COLUMN active;

ALTER TABLE billing_plan_histories
    DROP COLUMN currency_code,
    DROP COLUMN supply_price,
    DROP COLUMN sale_price,
    DROP COLUMN discount_type,
    DROP COLUMN discount_value,
    DROP COLUMN tax_code,
    DROP COLUMN active;

-- ================= optional_features =================

CREATE TABLE optional_feature_price_periods (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    optional_feature_id   BIGINT NOT NULL,
    currency_code         CHAR(3) NOT NULL,
    supply_price          DECIMAL(19,4) NULL,
    sale_price            DECIMAL(19,4) NOT NULL,
    discount_type         VARCHAR(20) NOT NULL,
    discount_value        DECIMAL(19,4) NOT NULL DEFAULT 0,
    tax_code              VARCHAR(50) NOT NULL,
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from        DATE NOT NULL,
    effective_to          DATE NULL,
    created_by            BIGINT NOT NULL,
    updated_by            BIGINT NULL,
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_ofpp_feature_period UNIQUE (optional_feature_id, effective_from),
    CONSTRAINT ck_ofpp_period CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT fk_ofpp_feature FOREIGN KEY (optional_feature_id) REFERENCES optional_features (id),
    CONSTRAINT fk_ofpp_currency FOREIGN KEY (currency_code) REFERENCES currencies (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE optional_feature_price_period_histories (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    optional_feature_id   BIGINT NOT NULL,
    currency_code         CHAR(3) NOT NULL,
    supply_price          DECIMAL(19,4) NULL,
    sale_price            DECIMAL(19,4) NOT NULL,
    discount_type         VARCHAR(20) NOT NULL,
    discount_value        DECIMAL(19,4) NOT NULL,
    tax_code              VARCHAR(50) NOT NULL,
    active                BOOLEAN NOT NULL,
    effective_from        DATE NOT NULL,
    effective_to          DATE NULL,
    removed               BOOLEAN NOT NULL DEFAULT FALSE,
    created_by            BIGINT NOT NULL,
    updated_by            BIGINT NULL,
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ofpph_feature FOREIGN KEY (optional_feature_id) REFERENCES optional_features (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO optional_feature_price_periods
    (optional_feature_id, currency_code, supply_price, sale_price, discount_type, discount_value, tax_code, active,
     effective_from, effective_to, created_by, updated_by, created_at, updated_at)
SELECT id, currency_code, supply_price, sale_price, discount_type, discount_value, tax_code, active,
       DATE(created_at), NULL, created_by, updated_by, created_at, updated_at
FROM optional_features;

INSERT INTO optional_feature_price_period_histories
    (optional_feature_id, currency_code, supply_price, sale_price, discount_type, discount_value, tax_code, active,
     effective_from, effective_to, removed, created_by, updated_by, created_at, updated_at)
SELECT id, currency_code, supply_price, sale_price, discount_type, discount_value, tax_code, active,
       DATE(created_at), NULL, FALSE, created_by, updated_by, created_at, updated_at
FROM optional_features;

ALTER TABLE optional_features
    DROP FOREIGN KEY fk_feature_currency,
    DROP COLUMN currency_code,
    DROP COLUMN supply_price,
    DROP COLUMN sale_price,
    DROP COLUMN discount_type,
    DROP COLUMN discount_value,
    DROP COLUMN tax_code,
    DROP COLUMN active;

ALTER TABLE optional_feature_histories
    DROP COLUMN currency_code,
    DROP COLUMN supply_price,
    DROP COLUMN sale_price,
    DROP COLUMN discount_type,
    DROP COLUMN discount_value,
    DROP COLUMN tax_code,
    DROP COLUMN active;

-- ================= capacity_addons =================

CREATE TABLE capacity_addon_price_periods (
    id                  BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    capacity_addon_id   BIGINT NOT NULL,
    currency_code       CHAR(3) NOT NULL,
    supply_price        DECIMAL(19,4) NULL,
    sale_price          DECIMAL(19,4) NOT NULL,
    discount_type       VARCHAR(20) NOT NULL,
    discount_value      DECIMAL(19,4) NOT NULL DEFAULT 0,
    tax_code            VARCHAR(50) NOT NULL,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from      DATE NOT NULL,
    effective_to        DATE NULL,
    created_by          BIGINT NOT NULL,
    updated_by          BIGINT NULL,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_cap_addon_period UNIQUE (capacity_addon_id, effective_from),
    CONSTRAINT ck_cap_period CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT fk_cap_addon FOREIGN KEY (capacity_addon_id) REFERENCES capacity_addons (id),
    CONSTRAINT fk_cap_currency FOREIGN KEY (currency_code) REFERENCES currencies (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE capacity_addon_price_period_histories (
    id                  BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    capacity_addon_id   BIGINT NOT NULL,
    currency_code       CHAR(3) NOT NULL,
    supply_price        DECIMAL(19,4) NULL,
    sale_price          DECIMAL(19,4) NOT NULL,
    discount_type       VARCHAR(20) NOT NULL,
    discount_value      DECIMAL(19,4) NOT NULL,
    tax_code            VARCHAR(50) NOT NULL,
    active              BOOLEAN NOT NULL,
    effective_from      DATE NOT NULL,
    effective_to        DATE NULL,
    removed             BOOLEAN NOT NULL DEFAULT FALSE,
    created_by          BIGINT NOT NULL,
    updated_by          BIGINT NULL,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_caph_addon FOREIGN KEY (capacity_addon_id) REFERENCES capacity_addons (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO capacity_addon_price_periods
    (capacity_addon_id, currency_code, supply_price, sale_price, discount_type, discount_value, tax_code, active,
     effective_from, effective_to, created_by, updated_by, created_at, updated_at)
SELECT id, currency_code, supply_price, sale_price, discount_type, discount_value, tax_code, active,
       DATE(created_at), NULL, created_by, updated_by, created_at, updated_at
FROM capacity_addons;

INSERT INTO capacity_addon_price_period_histories
    (capacity_addon_id, currency_code, supply_price, sale_price, discount_type, discount_value, tax_code, active,
     effective_from, effective_to, removed, created_by, updated_by, created_at, updated_at)
SELECT id, currency_code, supply_price, sale_price, discount_type, discount_value, tax_code, active,
       DATE(created_at), NULL, FALSE, created_by, updated_by, created_at, updated_at
FROM capacity_addons;

ALTER TABLE capacity_addons
    DROP FOREIGN KEY fk_addon_currency,
    DROP COLUMN currency_code,
    DROP COLUMN supply_price,
    DROP COLUMN sale_price,
    DROP COLUMN discount_type,
    DROP COLUMN discount_value,
    DROP COLUMN tax_code,
    DROP COLUMN active;

ALTER TABLE capacity_addon_histories
    DROP COLUMN currency_code,
    DROP COLUMN supply_price,
    DROP COLUMN sale_price,
    DROP COLUMN discount_type,
    DROP COLUMN discount_value,
    DROP COLUMN tax_code,
    DROP COLUMN active;
