-- 행사 결과물(서명이 그려진 최종 PDF). CONTRACT/EXHIBITION 두 타입만 다룬다 —
-- AUDIT_TRAIL(감사 인증서)은 별도 생성 로직이 필요해 다음 라운드로 미룬다.
-- signstage-docs business/ceremony-feature-migration-review.md §2.1/§2.5 참고.

CREATE TABLE ceremony_results (
    id                   BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ceremony_event_id    BIGINT NOT NULL,
    template_id          BIGINT NOT NULL,
    result_type          VARCHAR(20) NOT NULL,
    storage_key          VARCHAR(500) NOT NULL,
    original_filename    VARCHAR(255) NOT NULL,
    stored_filename      VARCHAR(255) NOT NULL,
    file_size            BIGINT NOT NULL,
    checksum             VARCHAR(64) NOT NULL,
    created_by           BIGINT NOT NULL,
    updated_by           BIGINT NULL,
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_cr_event FOREIGN KEY (ceremony_event_id) REFERENCES ceremony_events (id),
    CONSTRAINT fk_cr_template FOREIGN KEY (template_id) REFERENCES templates (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_cr_event ON ceremony_results (ceremony_event_id);
