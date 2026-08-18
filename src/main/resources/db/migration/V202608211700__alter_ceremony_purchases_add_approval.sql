-- 용량/선택옵션 추가구매 요청에 플랫폼 관리자 승인 절차를 추가한다.
--
-- status 기본값은 APPROVED다 — 이 마이그레이션 이전에는 구매하면 즉시 반영됐으므로, 이미
-- 사용 중인 조직의 한도를 소급으로 회수하면 안 된다. 앞으로 새로 생기는 행은 애플리케이션
-- 코드가 명시적으로 PENDING을 채운다(DB DEFAULT에 의존하지 않는다).

ALTER TABLE ceremony_capacity_purchases
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN rejection_reason VARCHAR(500) NULL,
    ADD COLUMN reviewed_by BIGINT NULL,
    ADD COLUMN reviewed_at TIMESTAMP NULL;

ALTER TABLE ceremony_optional_feature_purchases
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN rejection_reason VARCHAR(500) NULL,
    ADD COLUMN reviewed_by BIGINT NULL,
    ADD COLUMN reviewed_at TIMESTAMP NULL;

-- 선택옵션 구매가 반려되면 같은 옵션을 다시 요청할 수 있어야 한다 — (ceremony_id,
-- optional_feature_id) 단독 유니크로는 반려된 행이 그 조합을 영구히 차지해버린다.
ALTER TABLE ceremony_optional_feature_purchases
    DROP INDEX uq_cofp_ceremony_feature,
    ADD CONSTRAINT uq_cofp_ceremony_feature_status UNIQUE (ceremony_id, optional_feature_id, status);
