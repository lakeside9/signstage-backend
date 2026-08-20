-- 과금 카탈로그(플랜/선택옵션/용량 추가구매 상품)에 사용여부(active) 상태값을 추가하고,
-- 상태·값이 바뀔 때마다 변경 이력을 남기는 append-only 이력 테이블을 만든다.
--
-- active: 기본값 TRUE(사용 중). 비활성화해도 행은 지우지 않는다 — 이미 이 품목을 참조하는
--   Ceremony/구매 내역(FK)이 있을 수 있어 삭제는 여전히 범위 밖이다(ceremony-billing-options-review.md
--   7장). 비활성화된 품목은 새 행사 생성/플랜 변경/추가구매 대상에서 제외된다(애플리케이션
--   레이어에서 강제).
--
-- *_histories: CeremonyPlanHistory와 같은 패턴 — 생성 시점과 수정(값 또는 active) 시점마다
-- 그 순간의 전체 상태를 스냅샷으로 한 행씩 남긴다. 수정/삭제하지 않는다.

ALTER TABLE billing_plans
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE optional_features
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE capacity_addons
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE billing_plan_histories (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    billing_plan_id   BIGINT NOT NULL,
    name              VARCHAR(100) NOT NULL,
    supply_price      DECIMAL(12,2) NOT NULL,
    sale_price        DECIMAL(12,2) NOT NULL,
    discount_type     VARCHAR(20) NOT NULL,
    discount_value    DECIMAL(12,2) NOT NULL,
    max_signers       INT NOT NULL,
    max_templates     INT NOT NULL,
    max_test_events   INT NOT NULL,
    max_main_events   INT NOT NULL,
    active            BOOLEAN NOT NULL,
    created_by        BIGINT NOT NULL,
    updated_by        BIGINT NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_bph_plan FOREIGN KEY (billing_plan_id) REFERENCES billing_plans (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_bph_plan ON billing_plan_histories (billing_plan_id);

CREATE TABLE optional_feature_histories (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    optional_feature_id   BIGINT NOT NULL,
    code                  VARCHAR(50) NOT NULL,
    name                  VARCHAR(100) NOT NULL,
    supply_price          DECIMAL(12,2) NOT NULL,
    sale_price            DECIMAL(12,2) NOT NULL,
    discount_type         VARCHAR(20) NOT NULL,
    discount_value        DECIMAL(12,2) NOT NULL,
    active                BOOLEAN NOT NULL,
    created_by            BIGINT NOT NULL,
    updated_by            BIGINT NULL,
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ofh_feature FOREIGN KEY (optional_feature_id) REFERENCES optional_features (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_ofh_feature ON optional_feature_histories (optional_feature_id);

CREATE TABLE capacity_addon_histories (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    capacity_addon_id     BIGINT NOT NULL,
    capacity_type         VARCHAR(20) NOT NULL,
    unit_amount           INT NOT NULL,
    supply_price          DECIMAL(12,2) NOT NULL,
    sale_price            DECIMAL(12,2) NOT NULL,
    discount_type         VARCHAR(20) NOT NULL,
    discount_value        DECIMAL(12,2) NOT NULL,
    active                BOOLEAN NOT NULL,
    created_by            BIGINT NOT NULL,
    updated_by            BIGINT NULL,
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_cah_addon FOREIGN KEY (capacity_addon_id) REFERENCES capacity_addons (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_cah_addon ON capacity_addon_histories (capacity_addon_id);
