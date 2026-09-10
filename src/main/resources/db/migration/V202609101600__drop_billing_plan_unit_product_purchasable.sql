-- 과금 플랜 "추가구매 후보(purchasable)" 플래그 폐지(signstage-docs
-- business/billing-catalog-unit-product-model-redesign-review.md 11장, 2026-09-10, 사용자 지시).
--
-- 확인 결과 billing_plan_unit_products에 included_quantity=0 AND purchasable=0인 행이 하나도
-- 없었다(프런트가 그런 행은 애초에 저장하지 않았다 — included_quantity > 0 || purchasable 조건으로만
-- 제출했다) — 즉 지금 저장된 모든 행은 "행이 존재하면 추가구매 가능"이라는 새 규칙과 이미
-- 일치한다. 컬럼을 지워도 기존 데이터의 의미가 바뀌지 않는다.
ALTER TABLE billing_plan_unit_products DROP COLUMN purchasable;
ALTER TABLE billing_plan_history_unit_products DROP COLUMN purchasable;
ALTER TABLE ceremony_plan_history_unit_products DROP COLUMN purchasable;
