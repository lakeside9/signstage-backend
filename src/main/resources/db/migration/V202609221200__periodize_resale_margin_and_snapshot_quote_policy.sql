-- 기존 정책과 견적 금액을 유지한다. 기존 견적의 출처/기간은 추측하여 역산하지 않는다.
ALTER TABLE organization_margin_policies
    ADD COLUMN effective_from DATE NULL,
    ADD COLUMN effective_to DATE NULL,
    ADD INDEX idx_org_margin_period (organization_id, effective_from, effective_to);
UPDATE organization_margin_policies SET effective_from = DATE(created_at);
ALTER TABLE organization_margin_policies
    MODIFY COLUMN effective_from DATE NOT NULL,
    DROP INDEX uq_org_margin_policy_org;

ALTER TABLE customer_quotes
    ADD COLUMN margin_source VARCHAR(30) NULL,
    ADD COLUMN margin_source_id BIGINT NULL,
    ADD COLUMN margin_effective_from DATE NULL,
    ADD COLUMN margin_effective_to DATE NULL,
    ADD COLUMN margin_applied_on DATE NULL,
    ADD COLUMN margin_time_zone_id VARCHAR(50) NULL;
