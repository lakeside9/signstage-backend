-- 정책만 정규화한다. 이미 저장된 고객 견적 스냅샷은 변경하지 않는다.
UPDATE organization_margin_policies
SET effective_to = '9999-12-31'
WHERE effective_to IS NULL;

ALTER TABLE organization_margin_policies
    MODIFY COLUMN effective_to DATE NOT NULL DEFAULT '9999-12-31';
