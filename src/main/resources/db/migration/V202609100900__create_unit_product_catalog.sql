-- 카탈로그 단위 상품(UnitProduct) 1단계 — signstage-docs
-- business/billing-catalog-unit-product-model-redesign-review.md 결정(2026-09-10).
--
-- 기존 optional_features/capacity_addons를 대체할 통합 카탈로그 테이블을 추가한다. 1단계는
-- 순수 추가(additive)라 옛 테이블은 그대로 두고, Ceremony 구매/BillingPlan 구성 등은 2단계
-- 전환 때 옮긴다 — 그래서 이 마이그레이션엔 옛 테이블에서 데이터를 옮기는 INSERT가 없다
-- (개발 단계라 optional_features/capacity_addons 모두 0행이었고, 1단계에서 등록되는 단위
-- 상품은 전부 새로 입력한다).
--
-- unit_products.type엔 UNIQUE 제약을 두지 않는다(같은 문서 3.1절 결정) — 현장지원처럼 같은
-- type을 여러 행이 공유하는 경우(거리 구간별 상품 등)를 허용해야 해서다.
--
-- unit_product_price_periods는 CatalogPriceInfo가 아니라 ProductPriceInfo를 쓴다 — 단위
-- 상품은 할인을 갖지 않는다(할인은 오직 BillingPlan에만 있다, 같은 문서 3.5절 결정)는 이유로
-- discount_type/discount_value 컬럼이 없다.

CREATE TABLE unit_products (
    id                  BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    type                VARCHAR(20) NOT NULL,
    name                VARCHAR(100) NOT NULL,
    category            VARCHAR(20) NOT NULL,
    exclusivity_group   VARCHAR(50) NULL,
    created_by          BIGINT NOT NULL,
    updated_by          BIGINT NULL,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE unit_product_histories (
    id                  BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    unit_product_id     BIGINT NOT NULL,
    type                VARCHAR(20) NOT NULL,
    name                VARCHAR(100) NOT NULL,
    category            VARCHAR(20) NOT NULL,
    exclusivity_group   VARCHAR(50) NULL,
    created_by          BIGINT NOT NULL,
    updated_by          BIGINT NULL,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_uph_product FOREIGN KEY (unit_product_id) REFERENCES unit_products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE unit_product_price_periods (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    unit_product_id   BIGINT NOT NULL,
    currency_code     CHAR(3) NOT NULL,
    supply_price      DECIMAL(19,4) NULL,
    sale_price        DECIMAL(19,4) NOT NULL,
    tax_code          VARCHAR(50) NOT NULL,
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from    DATE NOT NULL,
    effective_to      DATE NULL,
    created_by        BIGINT NOT NULL,
    updated_by        BIGINT NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_upp_product_period UNIQUE (unit_product_id, effective_from),
    CONSTRAINT ck_upp_period CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT fk_upp_product FOREIGN KEY (unit_product_id) REFERENCES unit_products (id),
    CONSTRAINT fk_upp_currency FOREIGN KEY (currency_code) REFERENCES currencies (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE unit_product_price_period_histories (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    unit_product_id   BIGINT NOT NULL,
    currency_code     CHAR(3) NOT NULL,
    supply_price      DECIMAL(19,4) NULL,
    sale_price        DECIMAL(19,4) NOT NULL,
    tax_code          VARCHAR(50) NOT NULL,
    active            BOOLEAN NOT NULL,
    effective_from    DATE NOT NULL,
    effective_to      DATE NULL,
    removed           BOOLEAN NOT NULL DEFAULT FALSE,
    created_by        BIGINT NOT NULL,
    updated_by        BIGINT NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_upph_product FOREIGN KEY (unit_product_id) REFERENCES unit_products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
