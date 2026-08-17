-- 서명자(Signer)/템플릿(Template)/서명란(TemplateField). 둘 다 행사 마스터(Ceremony) 직속이다
-- (signstage-docs business/ceremony-feature-migration-review.md 4.2/4.3절 결정 — Template은
-- "행사마다 문서가 다름"이라 조직 표준 문서가 아니라 Ceremony 종속, Signer는 "테스트 및 본행사에
-- 공유"되므로 CeremonyEvent가 아니라 Ceremony 직속).
--
-- document_role/status는 4.4절 결정에 따라 코드 레벨 enum + VARCHAR 컬럼이고, signer_id/
-- role_code는 조직마다 자유로운 라벨이라 문자열로 유지한다.

CREATE TABLE signers (
    id             BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ceremony_id    BIGINT NOT NULL,
    name           VARCHAR(100) NOT NULL,
    position       VARCHAR(100) NULL,
    affiliation    VARCHAR(100) NULL,
    role_code      VARCHAR(50) NULL,
    access_key     VARCHAR(64) NOT NULL,
    created_by     BIGINT NOT NULL,
    updated_by     BIGINT NULL,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_signer_access_key UNIQUE (access_key),
    CONSTRAINT fk_signer_ceremony FOREIGN KEY (ceremony_id) REFERENCES ceremonies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_signer_ceremony ON signers (ceremony_id);

CREATE TABLE templates (
    id                  BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ceremony_id         BIGINT NOT NULL,
    title               VARCHAR(200) NOT NULL,
    document_role       VARCHAR(20) NOT NULL,
    storage_key         VARCHAR(500) NOT NULL,
    original_filename   VARCHAR(255) NOT NULL,
    stored_filename      VARCHAR(255) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by          BIGINT NOT NULL,
    updated_by          BIGINT NULL,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_template_ceremony FOREIGN KEY (ceremony_id) REFERENCES ceremonies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_template_ceremony ON templates (ceremony_id);

CREATE TABLE template_fields (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    template_id     BIGINT NOT NULL,
    signer_id       BIGINT NULL,
    field_key       VARCHAR(100) NOT NULL,
    page_index      INT NOT NULL,
    field_index     INT NOT NULL,
    field_name      VARCHAR(100) NOT NULL,
    role_code       VARCHAR(50) NULL,
    sign_order      INT NULL,
    is_required     TINYINT(1) NOT NULL DEFAULT 1,
    x_ratio         DECIMAL(6,5) NOT NULL,
    y_ratio         DECIMAL(6,5) NOT NULL,
    width_ratio     DECIMAL(6,5) NOT NULL,
    height_ratio    DECIMAL(6,5) NOT NULL,
    created_by      BIGINT NOT NULL,
    updated_by      BIGINT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_tf_template FOREIGN KEY (template_id) REFERENCES templates (id),
    CONSTRAINT fk_tf_signer FOREIGN KEY (signer_id) REFERENCES signers (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_tf_template ON template_fields (template_id);
