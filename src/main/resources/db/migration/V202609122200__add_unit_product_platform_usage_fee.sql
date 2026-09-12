-- 단위 상품 과금 축 명시화 — signstage-docs
-- business/onsite-support-negotiation-and-billing-classification-review.md 3.1절 결정
-- (2026-09-12). 기존 UnitProductCategory#isSystemUsageFee()의 카테고리 파생 판정을 저장된
-- 값으로 승격한다 — 카테고리(물리적 분류)와 과금 축("플랫폼 이용료" 대상인지)이 항상
-- 일치하지는 않게 될 예정이라서다(뒤이어 등록할 현장지원 요청 전용 앵커 상품이 카테고리는
-- PERSONNEL이지만 과금 축은 플랫폼 이용료다).
--
-- DEFAULT FALSE로 걸어두고, 시스템 사용료 카테고리(ESSENTIAL/APPLICATION)만 TRUE로
-- 백필한다 — 백필 후 값이 기존 isSystemUsageFee() 계산 결과와 완전히 같아 회귀가 없다.
ALTER TABLE unit_products
    ADD COLUMN is_platform_usage_fee BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE unit_product_histories
    ADD COLUMN is_platform_usage_fee BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE unit_products SET is_platform_usage_fee = TRUE WHERE category IN ('ESSENTIAL', 'APPLICATION');
UPDATE unit_product_histories SET is_platform_usage_fee = TRUE WHERE category IN ('ESSENTIAL', 'APPLICATION');
