-- signstage-docs business/platform-partner-customer-billing-model-reference.md 4.3절 결정
-- (2026-09-11) — billing_quote_lines에 UnitProduct.category 스냅샷을 추가해 "시스템 사용료
-- (ESSENTIAL/APPLICATION) vs 실물·인력 대금(EQUIPMENT/PERSONNEL)" 매출 갈래를 사후에도 정확히
-- 집계할 수 있게 한다. 기존 행은 item_id로 현재 unit_products를 조인해 최선으로 backfill한다
-- (그 상품이 이후 삭제됐다면 배정할 카테고리를 알 수 없으므로 그런 행은 없다고 가정 —
-- unit_products.id는 소프트 삭제 없이 하드 삭제만 쓰므로 참조 무결성이 깨진 행이 있으면 이
-- 마이그레이션이 실패해 드러난다).

ALTER TABLE billing_quote_lines ADD COLUMN category VARCHAR(20) NULL AFTER item_name;

UPDATE billing_quote_lines bql
JOIN unit_products up ON up.id = bql.item_id
SET bql.category = up.category
WHERE bql.category IS NULL;

ALTER TABLE billing_quote_lines MODIFY COLUMN category VARCHAR(20) NOT NULL;
