-- 국제화 기본 설정과 통화/세금 계산 기반.
-- 기존 데이터 이관은 하지 않는다는 2026-09-04 결정에 따라 새 필드는 제품 기본값으로 채운다.

CREATE TABLE currencies (
    code              CHAR(3) NOT NULL PRIMARY KEY,
    fraction_digits   SMALLINT NOT NULL,
    rounding_mode     VARCHAR(20) NOT NULL,
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_by        BIGINT NULL,
    updated_by        BIGINT NULL,
    created_at        TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at        TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_currency_fraction_digits CHECK (fraction_digits BETWEEN 0 AND 4),
    CONSTRAINT ck_currency_rounding_mode CHECK (rounding_mode IN ('HALF_UP', 'HALF_EVEN', 'DOWN', 'UP'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO currencies (code, fraction_digits, rounding_mode)
VALUES ('KRW', 0, 'HALF_UP'),
       ('USD', 2, 'HALF_UP'),
       ('EUR', 2, 'HALF_UP'),
       ('JPY', 0, 'HALF_UP');

ALTER TABLE users
    ADD COLUMN language_code VARCHAR(10) NOT NULL DEFAULT 'ko' AFTER phone,
    ADD COLUMN time_zone_id VARCHAR(50) NOT NULL DEFAULT 'Asia/Seoul' AFTER locale;

ALTER TABLE user_histories
    ADD COLUMN language_code VARCHAR(10) NOT NULL DEFAULT 'ko' AFTER phone,
    ADD COLUMN time_zone_id VARCHAR(50) NOT NULL DEFAULT 'Asia/Seoul' AFTER locale;

ALTER TABLE organizations
    ADD COLUMN default_language_code VARCHAR(10) NOT NULL DEFAULT 'ko' AFTER status,
    ADD COLUMN default_time_zone_id VARCHAR(50) NOT NULL DEFAULT 'Asia/Seoul' AFTER default_locale,
    ADD COLUMN billing_currency_code CHAR(3) NOT NULL DEFAULT 'KRW' AFTER default_time_zone_id,
    ADD CONSTRAINT fk_org_currency FOREIGN KEY (billing_currency_code) REFERENCES currencies (code);

ALTER TABLE organization_histories
    ADD COLUMN default_language_code VARCHAR(10) NOT NULL DEFAULT 'ko' AFTER status,
    ADD COLUMN default_time_zone_id VARCHAR(50) NOT NULL DEFAULT 'Asia/Seoul' AFTER default_locale,
    ADD COLUMN billing_currency_code CHAR(3) NOT NULL DEFAULT 'KRW' AFTER default_time_zone_id;

ALTER TABLE billing_plans
    MODIFY COLUMN supply_price DECIMAL(19,4) NOT NULL,
    MODIFY COLUMN sale_price DECIMAL(19,4) NOT NULL,
    MODIFY COLUMN discount_value DECIMAL(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN currency_code CHAR(3) NOT NULL DEFAULT 'KRW' AFTER name,
    ADD COLUMN tax_code VARCHAR(50) NOT NULL DEFAULT 'KR_VAT_STANDARD' AFTER discount_value,
    ADD CONSTRAINT fk_plan_currency FOREIGN KEY (currency_code) REFERENCES currencies (code);

ALTER TABLE optional_features
    MODIFY COLUMN supply_price DECIMAL(19,4) NOT NULL,
    MODIFY COLUMN sale_price DECIMAL(19,4) NOT NULL,
    MODIFY COLUMN discount_value DECIMAL(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN currency_code CHAR(3) NOT NULL DEFAULT 'KRW' AFTER name,
    ADD COLUMN tax_code VARCHAR(50) NOT NULL DEFAULT 'KR_VAT_STANDARD' AFTER discount_value,
    ADD CONSTRAINT fk_feature_currency FOREIGN KEY (currency_code) REFERENCES currencies (code);

ALTER TABLE capacity_addons
    MODIFY COLUMN supply_price DECIMAL(19,4) NOT NULL,
    MODIFY COLUMN sale_price DECIMAL(19,4) NOT NULL,
    MODIFY COLUMN discount_value DECIMAL(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN currency_code CHAR(3) NOT NULL DEFAULT 'KRW' AFTER unit_amount,
    ADD COLUMN tax_code VARCHAR(50) NOT NULL DEFAULT 'KR_VAT_STANDARD' AFTER discount_value,
    ADD CONSTRAINT fk_addon_currency FOREIGN KEY (currency_code) REFERENCES currencies (code);

ALTER TABLE billing_plan_histories
    MODIFY COLUMN supply_price DECIMAL(19,4) NOT NULL,
    MODIFY COLUMN sale_price DECIMAL(19,4) NOT NULL,
    MODIFY COLUMN discount_value DECIMAL(19,4) NOT NULL,
    ADD COLUMN currency_code CHAR(3) NOT NULL DEFAULT 'KRW' AFTER name,
    ADD COLUMN tax_code VARCHAR(50) NOT NULL DEFAULT 'KR_VAT_STANDARD' AFTER discount_value;

ALTER TABLE optional_feature_histories
    MODIFY COLUMN supply_price DECIMAL(19,4) NOT NULL,
    MODIFY COLUMN sale_price DECIMAL(19,4) NOT NULL,
    MODIFY COLUMN discount_value DECIMAL(19,4) NOT NULL,
    ADD COLUMN currency_code CHAR(3) NOT NULL DEFAULT 'KRW' AFTER name,
    ADD COLUMN tax_code VARCHAR(50) NOT NULL DEFAULT 'KR_VAT_STANDARD' AFTER discount_value;

ALTER TABLE capacity_addon_histories
    MODIFY COLUMN supply_price DECIMAL(19,4) NOT NULL,
    MODIFY COLUMN sale_price DECIMAL(19,4) NOT NULL,
    MODIFY COLUMN discount_value DECIMAL(19,4) NOT NULL,
    ADD COLUMN currency_code CHAR(3) NOT NULL DEFAULT 'KRW' AFTER unit_amount,
    ADD COLUMN tax_code VARCHAR(50) NOT NULL DEFAULT 'KR_VAT_STANDARD' AFTER discount_value;

ALTER TABLE ceremonies
    MODIFY COLUMN final_discount_value DECIMAL(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN currency_code CHAR(3) NOT NULL DEFAULT 'KRW' AFTER billing_plan_id,
    ADD COLUMN currency_fraction_digits SMALLINT NOT NULL DEFAULT 0 AFTER currency_code,
    ADD COLUMN currency_rounding_mode VARCHAR(20) NOT NULL DEFAULT 'HALF_UP' AFTER currency_fraction_digits,
    ADD COLUMN time_zone_id VARCHAR(50) NOT NULL DEFAULT 'Asia/Seoul' AFTER currency_rounding_mode,
    ADD CONSTRAINT fk_cer_currency FOREIGN KEY (currency_code) REFERENCES currencies (code);

ALTER TABLE ceremony_plan_histories
    MODIFY COLUMN plan_supply_price DECIMAL(19,4) NOT NULL,
    MODIFY COLUMN plan_sale_price DECIMAL(19,4) NOT NULL,
    MODIFY COLUMN plan_discount_value DECIMAL(19,4) NOT NULL,
    ADD COLUMN currency_code CHAR(3) NOT NULL DEFAULT 'KRW' AFTER plan_name,
    ADD COLUMN tax_code VARCHAR(50) NOT NULL DEFAULT 'KR_VAT_STANDARD' AFTER plan_discount_value;

ALTER TABLE ceremony_capacity_purchases
    MODIFY COLUMN purchased_sale_price DECIMAL(19,4) NOT NULL,
    MODIFY COLUMN purchased_discount_value DECIMAL(19,4) NOT NULL,
    ADD COLUMN currency_code CHAR(3) NOT NULL DEFAULT 'KRW' AFTER quantity,
    ADD COLUMN purchased_tax_code VARCHAR(50) NOT NULL DEFAULT 'KR_VAT_STANDARD' AFTER purchased_discount_value;

ALTER TABLE ceremony_optional_feature_purchases
    MODIFY COLUMN purchased_sale_price DECIMAL(19,4) NOT NULL,
    MODIFY COLUMN purchased_discount_value DECIMAL(19,4) NOT NULL,
    ADD COLUMN currency_code CHAR(3) NOT NULL DEFAULT 'KRW' AFTER optional_feature_id,
    ADD COLUMN purchased_tax_code VARCHAR(50) NOT NULL DEFAULT 'KR_VAT_STANDARD' AFTER purchased_discount_value;

CREATE TABLE tax_policies (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    country_code          CHAR(2) NOT NULL,
    administrative_area   VARCHAR(50) NULL,
    tax_code              VARCHAR(50) NOT NULL,
    category              VARCHAR(20) NOT NULL,
    rate_percent          DECIMAL(7,4) NOT NULL,
    price_inclusion       VARCHAR(10) NOT NULL,
    rounding_level        VARCHAR(10) NOT NULL,
    rounding_mode         VARCHAR(20) NOT NULL,
    effective_from        DATE NOT NULL,
    effective_to          DATE NULL,
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    created_by            BIGINT NULL,
    updated_by            BIGINT NULL,
    created_at            TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at            TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_tax_policy_version UNIQUE (country_code, administrative_area, tax_code, effective_from),
    CONSTRAINT ck_tax_rate CHECK (rate_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_tax_category CHECK (category IN ('STANDARD', 'REDUCED', 'ZERO_RATED', 'EXEMPT', 'OUT_OF_SCOPE')),
    CONSTRAINT ck_tax_inclusion CHECK (price_inclusion IN ('EXCLUSIVE', 'INCLUSIVE')),
    CONSTRAINT ck_tax_rounding_level CHECK (rounding_level IN ('LINE', 'DOCUMENT')),
    CONSTRAINT ck_tax_period CHECK (effective_to IS NULL OR effective_to >= effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_tax_policy_lookup
    ON tax_policies (country_code, tax_code, active, effective_from, effective_to);

INSERT INTO tax_policies
    (country_code, administrative_area, tax_code, category, rate_percent, price_inclusion,
     rounding_level, rounding_mode, effective_from)
VALUES
    ('KR', NULL, 'KR_VAT_STANDARD', 'STANDARD', 10.0000, 'EXCLUSIVE', 'LINE', 'HALF_UP', '1977-07-01');

-- 확정 견적은 수정하지 않고 버전을 추가한다. 상태 변경도 별도 이벤트로만 쌓는다.
CREATE TABLE billing_quotes (
    id                         BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ceremony_id                BIGINT NOT NULL,
    version                    INT NOT NULL,
    currency_code              CHAR(3) NOT NULL,
    currency_fraction_digits   SMALLINT NOT NULL,
    currency_rounding_mode     VARCHAR(20) NOT NULL,
    net_amount                 DECIMAL(19,4) NOT NULL,
    discount_amount            DECIMAL(19,4) NOT NULL,
    tax_amount                 DECIMAL(19,4) NOT NULL,
    gross_amount               DECIMAL(19,4) NOT NULL,
    pricing_calculated_at      TIMESTAMP(6) NOT NULL,
    tax_point_date             DATE NOT NULL,
    created_by                 BIGINT NOT NULL,
    created_at                 TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_billing_quote_version UNIQUE (ceremony_id, version),
    CONSTRAINT fk_quote_ceremony FOREIGN KEY (ceremony_id) REFERENCES ceremonies (id),
    CONSTRAINT fk_quote_currency FOREIGN KEY (currency_code) REFERENCES currencies (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE billing_quote_lines (
    id                         BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    billing_quote_id           BIGINT NOT NULL,
    line_type                  VARCHAR(30) NOT NULL,
    item_id                    BIGINT NOT NULL,
    item_name                  VARCHAR(100) NOT NULL,
    quantity                   INT NOT NULL,
    unit_list_amount           DECIMAL(19,4) NOT NULL,
    list_amount                DECIMAL(19,4) NOT NULL,
    item_discount_amount       DECIMAL(19,4) NOT NULL,
    ceremony_discount_amount   DECIMAL(19,4) NOT NULL,
    net_amount                 DECIMAL(19,4) NOT NULL,
    tax_code                   VARCHAR(50) NOT NULL,
    tax_category               VARCHAR(20) NOT NULL,
    tax_rate_percent           DECIMAL(7,4) NOT NULL,
    price_inclusion            VARCHAR(10) NOT NULL,
    tax_amount                 DECIMAL(19,4) NOT NULL,
    gross_amount               DECIMAL(19,4) NOT NULL,
    created_by                 BIGINT NOT NULL,
    created_at                 TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_quote_line_quote FOREIGN KEY (billing_quote_id) REFERENCES billing_quotes (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_quote_line_quote ON billing_quote_lines (billing_quote_id);

CREATE TABLE billing_quote_status_events (
    id                  BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    billing_quote_id    BIGINT NOT NULL,
    status              VARCHAR(20) NOT NULL,
    reason              VARCHAR(500) NULL,
    actor_id            BIGINT NOT NULL,
    occurred_at         TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    CONSTRAINT ck_quote_status CHECK (status IN ('FINALIZED', 'VOID')),
    CONSTRAINT fk_quote_status_quote FOREIGN KEY (billing_quote_id) REFERENCES billing_quotes (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_quote_status_quote_time
    ON billing_quote_status_events (billing_quote_id, occurred_at, id);
