-- 행사 이벤트 효과 카탈로그, 행사별 선택, 서명자 현재 상태를 저장한다.
-- legacy signstage의 효과 구조를 현재 프로젝트의 OptionalFeature entitlement와 감사 규칙에 맞춰 이식한다.
--
-- optional_features(code)는 V202608181000의 uq_of_code로 이미 유일하다. 운영 데이터에서 이
-- 제약이 훼손되었거나 수동 baseline으로 누락된 환경은 scripts/preflight-ceremony-event-effects.sql의
-- 첫 query로 확인하고, 중복 FK를 대표 OptionalFeature로 정리한 뒤 이 migration을 적용한다.
-- migration 실패 시 새 테이블을 임의로 DROP하지 않는다. Flyway 실패 지점을 확인한 뒤
-- preflight/verify query로 원인을 정리하고 repair + 재실행한다(MySQL DDL은 암묵적으로 commit된다).

CREATE TABLE ceremony_effect_definitions (
    id                           BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code                         VARCHAR(50) NOT NULL,
    target_type                  VARCHAR(30) NOT NULL,
    trigger_type                 VARCHAR(40) NOT NULL,
    required_optional_feature_id BIGINT NOT NULL,
    display_name                 VARCHAR(100) NOT NULL,
    description                  VARCHAR(500) NULL,
    renderer_key                 VARCHAR(100) NOT NULL,
    is_enabled                   TINYINT(1) NOT NULL DEFAULT 1,
    is_user_visible              TINYINT(1) NOT NULL DEFAULT 1,
    manually_triggerable         TINYINT(1) NOT NULL DEFAULT 0,
    display_order                INT NOT NULL DEFAULT 0,
    config_json                  JSON NULL,
    created_by                   BIGINT NOT NULL,
    updated_by                   BIGINT NULL,
    created_at                   TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                   TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_ced_code UNIQUE (code),
    CONSTRAINT uq_ced_id_target_trigger UNIQUE (id, target_type, trigger_type),
    CONSTRAINT fk_ced_required_feature FOREIGN KEY (required_optional_feature_id)
        REFERENCES optional_features (id),
    CONSTRAINT chk_ced_code CHECK (code REGEXP '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT chk_ced_target CHECK (target_type IN ('PROJECTOR', 'SIGNER')),
    CONSTRAINT chk_ced_trigger CHECK (
        trigger_type IN ('SIGNATURE_COMPLETED', 'ALL_SIGNATURES_COMPLETED', 'EVENT_FINISHED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_ced_catalog
    ON ceremony_effect_definitions (
        is_enabled, is_user_visible, target_type, trigger_type, display_order
    );
CREATE INDEX idx_ced_required_feature
    ON ceremony_effect_definitions (required_optional_feature_id);

CREATE TABLE ceremony_event_effect_settings (
    event_id           BIGINT NOT NULL,
    target_type        VARCHAR(30) NOT NULL,
    trigger_type       VARCHAR(40) NOT NULL,
    effect_id          BIGINT NOT NULL,
    runtime_enabled    TINYINT(1) NOT NULL DEFAULT 1,
    created_by         BIGINT NOT NULL,
    updated_by         BIGINT NULL,
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id, target_type, trigger_type),
    CONSTRAINT fk_cees_event FOREIGN KEY (event_id)
        REFERENCES ceremony_events (id) ON DELETE CASCADE,
    CONSTRAINT fk_cees_definition FOREIGN KEY (effect_id, target_type, trigger_type)
        REFERENCES ceremony_effect_definitions (id, target_type, trigger_type),
    CONSTRAINT chk_cees_target CHECK (target_type IN ('PROJECTOR', 'SIGNER')),
    CONSTRAINT chk_cees_trigger CHECK (
        trigger_type IN ('SIGNATURE_COMPLETED', 'ALL_SIGNATURES_COMPLETED', 'EVENT_FINISHED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_cees_effect_id ON ceremony_event_effect_settings (effect_id);

CREATE TABLE ceremony_event_signer_states (
    event_id               BIGINT NOT NULL,
    signer_id              BIGINT NOT NULL,
    signature_status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    last_completion_log_id BIGINT NULL,
    completed_at           TIMESTAMP NULL,
    created_by             BIGINT NULL,
    updated_by             BIGINT NULL,
    created_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id, signer_id),
    CONSTRAINT fk_cess_event FOREIGN KEY (event_id)
        REFERENCES ceremony_events (id) ON DELETE CASCADE,
    CONSTRAINT fk_cess_signer FOREIGN KEY (signer_id)
        REFERENCES signers (id) ON DELETE CASCADE,
    CONSTRAINT fk_cess_completion_log FOREIGN KEY (last_completion_log_id)
        REFERENCES ceremony_event_logs (id),
    CONSTRAINT chk_cess_signature_status CHECK (
        signature_status IN ('PENDING', 'SIGNING', 'COMPLETED')
    ),
    CONSTRAINT chk_cess_completion_fields CHECK (
        (signature_status = 'COMPLETED' AND last_completion_log_id IS NOT NULL AND completed_at IS NOT NULL)
        OR
        (signature_status <> 'COMPLETED' AND last_completion_log_id IS NULL AND completed_at IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_cess_completion
    ON ceremony_event_signer_states (event_id, signature_status, signer_id);

ALTER TABLE ceremony_events
    ADD COLUMN auto_celebration_triggered_at TIMESTAMP NULL;
