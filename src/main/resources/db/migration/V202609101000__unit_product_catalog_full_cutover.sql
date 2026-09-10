-- 과금 카탈로그 "단위 상품" 모델 2단계(전체 전환) — signstage-docs
-- business/billing-catalog-unit-product-model-redesign-review.md 결정(2026-09-10).
--
-- 1단계(V202609100900)에서 UnitProduct 카탈로그 코어를 순수 추가로 들여온 데 이어, 이번
-- 마이그레이션은 옛 OptionalFeature/CapacityAddOn 계열 테이블 전체를 제거하고 BillingPlan/
-- Ceremony 쪽 스키마를 새 모델로 전환한다. 개발 단계라 기존 데이터 보존 부담이 없다(zero-base
-- 재설계 때와 같은 전제) — 확인 결과 billing_plans/optional_features/capacity_addons/
-- ceremony_plan_histories/ceremony_capacity_purchases/ceremony_optional_feature_purchases/
-- ceremonies 전부 0행이고, ceremony_effect_definition_options만 9행인데 전부
-- optional_feature_id=11(이미 삭제된 옛 선택옵션을 가리키는 고아 행)이라 실질적으로 보존할
-- 데이터가 없다.

-- ================= 1. 옛 구매/조직 오버라이드/플랜 조인 테이블 삭제 (자식 먼저) =================

DROP TABLE ceremony_capacity_purchases;
DROP TABLE ceremony_optional_feature_purchases;

DROP TABLE organization_capacity_addon_discount_histories;
DROP TABLE organization_capacity_addon_discounts;
DROP TABLE organization_optional_feature_discount_histories;
DROP TABLE organization_optional_feature_discounts;

DROP TABLE ceremony_plan_history_capacity_addons;
DROP TABLE ceremony_plan_history_optional_features;
DROP TABLE ceremony_plan_history_capacities;

DROP TABLE billing_plan_capacity_addons;
DROP TABLE billing_plan_optional_features;
DROP TABLE billing_plan_history_capacities;
DROP TABLE billing_plan_capacities;

-- ================= 2. 옛 카탈로그 엔티티(OptionalFeature/CapacityAddOn) 삭제 =================

ALTER TABLE ceremony_effect_definition_options DROP FOREIGN KEY fk_cedo_feature;
ALTER TABLE ceremony_event_optional_features DROP FOREIGN KEY fk_ceof_feature;

-- 남은 9행 전부 이미 삭제된 옛 선택옵션(id=11)을 가리키는 고아 행이라 보존할 실질 데이터가
-- 없다 — 새 unit_product_id 컬럼으로 옮기지 않고 비운다.
DELETE FROM ceremony_effect_definition_options;

DROP TABLE optional_feature_price_period_histories;
DROP TABLE optional_feature_price_periods;
DROP TABLE optional_feature_histories;
DROP TABLE optional_features;

DROP TABLE capacity_addon_price_period_histories;
DROP TABLE capacity_addon_price_periods;
DROP TABLE capacity_addon_histories;
DROP TABLE capacity_addons;

DROP TABLE billing_plan_price_period_histories;
DROP TABLE billing_plan_price_periods;

-- ================= 3. ceremony_effect_definition_options / ceremony_event_optional_features
--    FK 대상을 unit_products로 전환(FK 이름만 바뀔 뿐 구조는 그대로, 3.6절) =================

ALTER TABLE ceremony_effect_definition_options
    CHANGE COLUMN optional_feature_id unit_product_id BIGINT NOT NULL,
    DROP INDEX uq_cedo_definition_feature,
    ADD CONSTRAINT uq_cedo_definition_product UNIQUE (effect_definition_id, unit_product_id),
    ADD CONSTRAINT fk_cedo_product FOREIGN KEY (unit_product_id) REFERENCES unit_products (id);

ALTER TABLE ceremony_event_optional_features
    CHANGE COLUMN optional_feature_id unit_product_id BIGINT NOT NULL,
    DROP INDEX uq_ceof_event_feature,
    ADD CONSTRAINT uq_ceof_event_product UNIQUE (ceremony_event_id, unit_product_id),
    ADD CONSTRAINT fk_ceof_product FOREIGN KEY (unit_product_id) REFERENCES unit_products (id);

-- ================= 4. ceremony_plan_histories — 플랜 자체 가격/통화/세금 컬럼 제거
--    (플랜은 더 이상 자기 가격을 갖지 않는다, 3.3/3.5절) =================

ALTER TABLE ceremony_plan_histories
    DROP COLUMN currency_code,
    DROP COLUMN plan_supply_price,
    DROP COLUMN plan_sale_price,
    DROP COLUMN tax_code;

-- ================= 5. 새 테이블 — BillingPlanUnitProduct / BillingPlanDiscountPeriod(+이력)
--    / BillingPlanHistoryUnitProduct =================

CREATE TABLE billing_plan_unit_products (
    id                  BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    billing_plan_id     BIGINT NOT NULL,
    unit_product_id     BIGINT NOT NULL,
    included_quantity   INT NOT NULL,
    purchasable         BOOLEAN NOT NULL,
    created_by          BIGINT NOT NULL,
    updated_by          BIGINT NULL,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_bpup_plan_product UNIQUE (billing_plan_id, unit_product_id),
    CONSTRAINT fk_bpup_plan FOREIGN KEY (billing_plan_id) REFERENCES billing_plans (id),
    CONSTRAINT fk_bpup_product FOREIGN KEY (unit_product_id) REFERENCES unit_products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE billing_plan_discount_periods (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    billing_plan_id   BIGINT NOT NULL,
    discount_type     VARCHAR(20) NOT NULL,
    discount_value    DECIMAL(19,4) NOT NULL DEFAULT 0,
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from    DATE NOT NULL,
    effective_to      DATE NULL,
    created_by        BIGINT NOT NULL,
    updated_by        BIGINT NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_bpdp_plan_period UNIQUE (billing_plan_id, effective_from),
    CONSTRAINT ck_bpdp_period CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT fk_bpdp_plan FOREIGN KEY (billing_plan_id) REFERENCES billing_plans (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE billing_plan_discount_period_histories (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    billing_plan_id   BIGINT NOT NULL,
    discount_type     VARCHAR(20) NOT NULL,
    discount_value    DECIMAL(19,4) NOT NULL,
    active            BOOLEAN NOT NULL,
    effective_from    DATE NOT NULL,
    effective_to      DATE NULL,
    removed           BOOLEAN NOT NULL DEFAULT FALSE,
    created_by        BIGINT NOT NULL,
    updated_by        BIGINT NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_bpdph_plan FOREIGN KEY (billing_plan_id) REFERENCES billing_plans (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE billing_plan_history_unit_products (
    id                      BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    billing_plan_history_id BIGINT NOT NULL,
    unit_product_id         BIGINT NOT NULL,
    included_quantity       INT NOT NULL,
    purchasable             BOOLEAN NOT NULL,
    created_by              BIGINT NOT NULL,
    updated_by              BIGINT NULL,
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_bphup_history FOREIGN KEY (billing_plan_history_id) REFERENCES billing_plan_histories (id),
    CONSTRAINT fk_bphup_product FOREIGN KEY (unit_product_id) REFERENCES unit_products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================= 6. 새 테이블 — CeremonyPlanHistoryUnitProduct =================

CREATE TABLE ceremony_plan_history_unit_products (
    id                        BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ceremony_plan_history_id BIGINT NOT NULL,
    unit_product_id           BIGINT NOT NULL,
    included_quantity         INT NOT NULL,
    purchasable                BOOLEAN NOT NULL,
    currency_code              CHAR(3) NOT NULL,
    snapshot_sale_price        DECIMAL(19,4) NOT NULL,
    snapshot_tax_code          VARCHAR(50) NOT NULL,
    created_by                 BIGINT NOT NULL,
    updated_by                 BIGINT NULL,
    created_at                 TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                 TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_cphup_history FOREIGN KEY (ceremony_plan_history_id) REFERENCES ceremony_plan_histories (id),
    CONSTRAINT fk_cphup_product FOREIGN KEY (unit_product_id) REFERENCES unit_products (id),
    CONSTRAINT fk_cphup_currency FOREIGN KEY (currency_code) REFERENCES currencies (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================= 7. 새 테이블 — CeremonyUnitProductPurchase(헤더) + Line(장바구니형, 3.4절) =================

CREATE TABLE ceremony_unit_product_purchases (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ceremony_id       BIGINT NOT NULL,
    status            VARCHAR(20) NOT NULL,
    rejection_reason  VARCHAR(500) NULL,
    reviewed_by       BIGINT NULL,
    reviewed_at       TIMESTAMP NULL,
    created_by        BIGINT NOT NULL,
    updated_by        BIGINT NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_cupp_ceremony FOREIGN KEY (ceremony_id) REFERENCES ceremonies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ceremony_unit_product_purchase_lines (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    purchase_id           BIGINT NOT NULL,
    unit_product_id       BIGINT NOT NULL,
    quantity              INT NOT NULL,
    currency_code         CHAR(3) NOT NULL,
    purchased_name        VARCHAR(100) NOT NULL,
    purchased_sale_price  DECIMAL(19,4) NOT NULL,
    purchased_tax_code    VARCHAR(50) NOT NULL,
    created_by            BIGINT NOT NULL,
    updated_by            BIGINT NULL,
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_cuppl_purchase FOREIGN KEY (purchase_id) REFERENCES ceremony_unit_product_purchases (id),
    CONSTRAINT fk_cuppl_product FOREIGN KEY (unit_product_id) REFERENCES unit_products (id),
    CONSTRAINT fk_cuppl_currency FOREIGN KEY (currency_code) REFERENCES currencies (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
