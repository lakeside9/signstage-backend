-- Template ↔ CeremonyEvent 매핑. document_role은 매핑 시점에 지정하는 값으로,
-- Template 자체의 document_role과 별개다(레거시 원본 모델을 그대로 이식).
-- signstage-docs business/ceremony-feature-migration-review.md §2.1/§2.2 참고.

CREATE TABLE ceremony_templates (
    id                   BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ceremony_event_id    BIGINT NOT NULL,
    template_id          BIGINT NOT NULL,
    document_role        VARCHAR(20) NOT NULL,
    created_by           BIGINT NOT NULL,
    updated_by           BIGINT NULL,
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_cet_event_template UNIQUE (ceremony_event_id, template_id),
    CONSTRAINT fk_cet_event FOREIGN KEY (ceremony_event_id) REFERENCES ceremony_events (id),
    CONSTRAINT fk_cet_template FOREIGN KEY (template_id) REFERENCES templates (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_cet_event ON ceremony_templates (ceremony_event_id);
