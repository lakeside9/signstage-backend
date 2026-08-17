-- 행사(Ceremony) 본체 + 과금 구매 내역. signstage-docs
-- business/ceremony-feature-migration-review.md(도메인 모델/조직 스코핑/OPERATOR 배정),
-- business/ceremony-billing-options-review.md(4.10/4.11: 플랜 필수 선택, 구매/적용 분리) 참고.
--
-- ceremonies: organization_id만 갖고(4.1 결정), 하위 엔티티는 ceremony_id FK 체인으로 스코핑한다.
--   billing_plan_id는 DB 컬럼 자체는 nullable이다 — 이 기능 배포 전 기존 행사(4.8 예외)만 NULL로
--   남고, 신규 행사는 애플리케이션이 필수로 강제한다(4.10).
-- ceremony_assignments: OPERATOR "본인/배정 건" 판정 테이블(4.7). role 컬럼 없음 — 배정 여부
--   플래그일 뿐이고 권한 등급은 organization_members.role이 그대로 갖는다.
-- ceremony_events: 실제 상태 전이가 일어나는 하위 행사(TEST/MAIN). access_key는 서명자 포털
--   접속에 쓸 예정이나, 이번 라운드는 컬럼만 마련하고 포털 자체는 다음 라운드에 만든다.
-- ceremony_capacity_purchases / ceremony_optional_feature_purchases: 구매 시점 가격을
--   purchased_* 컬럼에 스냅샷으로 저장한다 — 카탈로그 가격이 나중에 바뀌어도 이미 발생한
--   구매 내역(청구 근거)은 바뀌지 않아야 하기 때문이다.
-- ceremony_event_optional_features: 구매(Ceremony 단위)와 적용(CeremonyEvent 단위)을
--   분리한 4.11 결정 — 이 테이블이 "실제로 이 이벤트에 켜진" 선택옵션이다.

CREATE TABLE ceremonies (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id   BIGINT NOT NULL,
    billing_plan_id   BIGINT NULL,
    title             VARCHAR(200) NOT NULL,
    created_by        BIGINT NOT NULL,
    updated_by        BIGINT NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_cer_organization FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_cer_plan FOREIGN KEY (billing_plan_id) REFERENCES billing_plans (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_cer_organization ON ceremonies (organization_id);

CREATE TABLE ceremony_assignments (
    id            BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ceremony_id   BIGINT NOT NULL,
    user_id       BIGINT NOT NULL,
    created_by    BIGINT NOT NULL,
    updated_by    BIGINT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_ca_ceremony_user UNIQUE (ceremony_id, user_id),
    CONSTRAINT fk_ca_ceremony FOREIGN KEY (ceremony_id) REFERENCES ceremonies (id),
    CONSTRAINT fk_ca_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ceremony_events (
    id                   BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ceremony_id          BIGINT NOT NULL,
    name                 VARCHAR(200) NOT NULL,
    event_type           VARCHAR(20) NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    venue                VARCHAR(200) NULL,
    scheduled_start_at   TIMESTAMP NULL,
    scheduled_end_at     TIMESTAMP NULL,
    actual_start_at      TIMESTAMP NULL,
    actual_end_at        TIMESTAMP NULL,
    access_key           VARCHAR(64) NOT NULL,
    description          VARCHAR(1000) NULL,
    created_by           BIGINT NOT NULL,
    updated_by           BIGINT NULL,
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_ce_access_key UNIQUE (access_key),
    CONSTRAINT fk_ce_ceremony FOREIGN KEY (ceremony_id) REFERENCES ceremonies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_ce_ceremony_type ON ceremony_events (ceremony_id, event_type);

CREATE TABLE ceremony_capacity_purchases (
    id                          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ceremony_id                 BIGINT NOT NULL,
    capacity_addon_id           BIGINT NOT NULL,
    quantity                    INT NOT NULL,
    purchased_sale_price        DECIMAL(12,2) NOT NULL,
    purchased_discount_type     VARCHAR(20) NOT NULL,
    purchased_discount_value    DECIMAL(12,2) NOT NULL,
    created_by                  BIGINT NOT NULL,
    updated_by                  BIGINT NULL,
    created_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ccp_ceremony FOREIGN KEY (ceremony_id) REFERENCES ceremonies (id),
    CONSTRAINT fk_ccp_addon FOREIGN KEY (capacity_addon_id) REFERENCES capacity_addons (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ceremony_optional_feature_purchases (
    id                          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ceremony_id                 BIGINT NOT NULL,
    optional_feature_id         BIGINT NOT NULL,
    purchased_sale_price        DECIMAL(12,2) NOT NULL,
    purchased_discount_type     VARCHAR(20) NOT NULL,
    purchased_discount_value    DECIMAL(12,2) NOT NULL,
    created_by                  BIGINT NOT NULL,
    updated_by                  BIGINT NULL,
    created_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_cofp_ceremony_feature UNIQUE (ceremony_id, optional_feature_id),
    CONSTRAINT fk_cofp_ceremony FOREIGN KEY (ceremony_id) REFERENCES ceremonies (id),
    CONSTRAINT fk_cofp_feature FOREIGN KEY (optional_feature_id) REFERENCES optional_features (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ceremony_event_optional_features (
    id                     BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ceremony_event_id      BIGINT NOT NULL,
    optional_feature_id    BIGINT NOT NULL,
    created_by             BIGINT NOT NULL,
    updated_by             BIGINT NULL,
    created_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_ceof_event_feature UNIQUE (ceremony_event_id, optional_feature_id),
    CONSTRAINT fk_ceof_event FOREIGN KEY (ceremony_event_id) REFERENCES ceremony_events (id),
    CONSTRAINT fk_ceof_feature FOREIGN KEY (optional_feature_id) REFERENCES optional_features (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
