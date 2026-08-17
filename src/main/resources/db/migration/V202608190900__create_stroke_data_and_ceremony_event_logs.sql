-- 서명 스트로크(StrokeData) + 하위 행사 감사 로그(CeremonyEventLog).
-- signstage-docs business/ceremony-feature-migration-review.md §2.1/§2.2 참고.
--
-- created_by/updated_by가 nullable인 이유: 서명자 포털 요청은 JWT가 없어(4.5절 결정 —
-- accessKey만으로 인가) SecurityAuditorAware가 채울 인증 주체가 없다.
--
-- ceremony_event_logs.actor_id는 FK가 아니라 순수 컬럼이다 — actor_type(ADMIN/SIGNER)에
-- 따라 users.id 또는 signers.id 중 하나를 가리키는 다형적 참조라 단일 FK로 표현할 수 없다.
--
-- stroke_data는 레거시(순수 컬럼)와 달리 실제 FK 관계로 만들었다 — 이 프로젝트는 지금까지
-- 전부 실제 FK 관계를 써왔고, 별도로 "관계를 끊어야 한다"는 결정이 없었다.

CREATE TABLE stroke_data (
    id                   BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ceremony_event_id    BIGINT NOT NULL,
    signer_id            BIGINT NOT NULL,
    template_field_id    BIGINT NOT NULL,
    stroke_seq           INT NOT NULL,
    raw_data             LONGTEXT NOT NULL,
    created_by           BIGINT NULL,
    updated_by           BIGINT NULL,
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_sd_event FOREIGN KEY (ceremony_event_id) REFERENCES ceremony_events (id),
    CONSTRAINT fk_sd_signer FOREIGN KEY (signer_id) REFERENCES signers (id),
    CONSTRAINT fk_sd_field FOREIGN KEY (template_field_id) REFERENCES template_fields (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_sd_event_signer_field ON stroke_data (ceremony_event_id, signer_id, template_field_id);

CREATE TABLE ceremony_event_logs (
    id                   BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ceremony_event_id    BIGINT NOT NULL,
    actor_type           VARCHAR(20) NOT NULL,
    actor_id             BIGINT NOT NULL,
    event_action         VARCHAR(30) NOT NULL,
    message              VARCHAR(1000) NULL,
    ip_address           VARCHAR(45) NULL,
    user_agent           VARCHAR(500) NULL,
    action_detail        TEXT NULL,
    created_by           BIGINT NULL,
    updated_by           BIGINT NULL,
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_cel_event FOREIGN KEY (ceremony_event_id) REFERENCES ceremony_events (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_cel_event ON ceremony_event_logs (ceremony_event_id);
