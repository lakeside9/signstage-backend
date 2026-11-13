-- 이벤트 효과 묶음 선택옵션을 "개별 서명 확인 효과"와 "전체 서명 완료 효과" 둘로 나눈다(2026-11-13).
--
-- 지금까지는 묶음 하나(type=EVENT_EFFECT_BUNDLE)가 개별 서명 확인 효과(SIGNATURE_COMPLETED)와 전체 서명 완료 효과
-- (ALL_SIGNATURES_COMPLETED)를 함께 열었다. 앞으로는 한 묶음이 한 trigger의 효과만 담는다(UnitProductEffectService가
-- 섞인 구성을 거부한다). 두 trigger를 함께 담은 기존 묶음마다:
--   1) 원래 상품은 "개별 서명 확인 효과"로 이름을 바꾸고 개별 서명 확인 효과만 남긴다.
--   2) 같은 설정으로 "전체 서명 완료 효과" 상품을 새로 만들고 전체 서명 완료 효과 매핑을 옮긴다.
--   3) 새 상품에 원래 상품의 판매가격 기간, 현재 플랜 구성, 행사 플랜 스냅샷, 하위 행사 적용을 그대로 복사한다 —
--      지금 묶음을 쓰고 있는 플랜/행사가 두 효과를 계속 쓸 수 있게 한다.
-- 추가구매·장바구니·수주·고객 가격 기록은 금액이 걸린 실제 거래 기록이라 복사하지 않는다(로컬 데이터 기준 0건).

CREATE TEMPORARY TABLE tmp_mixed_effect_bundles AS
SELECT up.id
FROM unit_products up
WHERE up.type = 'EVENT_EFFECT_BUNDLE'
  AND EXISTS (
      SELECT 1 FROM ceremony_effect_definition_options o
      JOIN ceremony_effect_definitions d ON d.id = o.effect_definition_id
      WHERE o.unit_product_id = up.id AND d.trigger_type = 'SIGNATURE_COMPLETED')
  AND EXISTS (
      SELECT 1 FROM ceremony_effect_definition_options o
      JOIN ceremony_effect_definitions d ON d.id = o.effect_definition_id
      WHERE o.unit_product_id = up.id AND d.trigger_type = 'ALL_SIGNATURES_COMPLETED');

-- 새 상품 행과 원래 상품을 잇는 임시 열(마지막에 지운다).
ALTER TABLE unit_products ADD COLUMN split_from_id BIGINT NULL;

INSERT INTO unit_products (
    type, sales_channel, name, category, exclusivity_group, created_by, updated_by, display_order,
    description, quote_description, max_purchase_quantity, is_platform_usage_fee, sale_unit_quantity, split_from_id
)
SELECT up.type, up.sales_channel, '전체 서명 완료 효과', up.category, up.exclusivity_group, up.created_by, up.created_by,
       up.display_order, '모든 서명이 끝났을 때 전시 화면에 나오는 효과입니다.', up.quote_description,
       up.max_purchase_quantity, up.is_platform_usage_fee, up.sale_unit_quantity, up.id
FROM unit_products up
JOIN tmp_mixed_effect_bundles m ON m.id = up.id;

UPDATE unit_products up
JOIN tmp_mixed_effect_bundles m ON m.id = up.id
SET up.name = '개별 서명 확인 효과',
    up.description = '서명자 한 명의 서명이 확인될 때마다 전시 화면에 나오는 효과입니다.';

-- 상품 이력: 이름을 바꾼 원래 상품과 새 상품의 지금 상태를 남긴다(UnitProductService#recordProductHistory와 같은 모양).
INSERT INTO unit_product_histories (
    unit_product_id, type, name, category, exclusivity_group, created_by, updated_by, description, quote_description,
    max_purchase_quantity, is_platform_usage_fee, sale_unit_quantity
)
SELECT up.id, up.type, up.name, up.category, up.exclusivity_group, up.created_by, up.created_by, up.description,
       up.quote_description, up.max_purchase_quantity, up.is_platform_usage_fee, up.sale_unit_quantity
FROM unit_products up
WHERE up.id IN (SELECT id FROM tmp_mixed_effect_bundles) OR up.split_from_id IS NOT NULL;

-- 판매가격 기간(직접 수주 상품만 있다)과 그 이력.
INSERT INTO unit_product_price_periods (
    unit_product_id, currency_code, supply_price, sale_price, tax_code, active, effective_from, effective_to,
    created_by, updated_by
)
SELECT n.id, p.currency_code, p.supply_price, p.sale_price, p.tax_code, p.active, p.effective_from, p.effective_to,
       p.created_by, p.created_by
FROM unit_products n
JOIN unit_product_price_periods p ON p.unit_product_id = n.split_from_id;

INSERT INTO unit_product_price_period_histories (
    unit_product_id, currency_code, supply_price, sale_price, tax_code, active, effective_from, effective_to,
    removed, created_by, updated_by
)
SELECT p.unit_product_id, p.currency_code, p.supply_price, p.sale_price, p.tax_code, p.active, p.effective_from,
       p.effective_to, 0, p.created_by, p.created_by
FROM unit_product_price_periods p
JOIN unit_products n ON n.id = p.unit_product_id AND n.split_from_id IS NOT NULL;

-- 현재 플랜 구성.
INSERT INTO billing_plan_unit_products (
    billing_plan_id, unit_product_id, included_quantity, show_on_quote, created_by, updated_by
)
SELECT b.billing_plan_id, n.id, b.included_quantity, b.show_on_quote, b.created_by, b.created_by
FROM unit_products n
JOIN billing_plan_unit_products b ON b.unit_product_id = n.split_from_id;

-- 이미 플랜을 고른 행사의 플랜 스냅샷(이 행사가 쓸 수 있는 선택옵션의 근거).
INSERT INTO ceremony_plan_history_unit_products (
    ceremony_plan_history_id, unit_product_id, included_quantity, currency_code, snapshot_sale_price,
    snapshot_tax_code, created_by, updated_by
)
SELECT c.ceremony_plan_history_id, n.id, c.included_quantity, c.currency_code, c.snapshot_sale_price,
       c.snapshot_tax_code, c.created_by, c.created_by
FROM unit_products n
JOIN ceremony_plan_history_unit_products c ON c.unit_product_id = n.split_from_id;

-- 묶음을 적용해 둔 하위 행사는 새 상품도 적용해 둔다(이미 고른 전체 서명 완료 효과가 그대로 유지되게).
INSERT INTO ceremony_event_optional_features (ceremony_event_id, unit_product_id, created_by, updated_by)
SELECT f.ceremony_event_id, n.id, f.created_by, f.created_by
FROM unit_products n
JOIN ceremony_event_optional_features f ON f.unit_product_id = n.split_from_id;

-- 전체 서명 완료 효과 매핑을 새 상품으로 옮긴다.
UPDATE ceremony_effect_definition_options o
JOIN ceremony_effect_definitions d ON d.id = o.effect_definition_id
JOIN unit_products n ON n.split_from_id = o.unit_product_id
SET o.unit_product_id = n.id
WHERE d.trigger_type = 'ALL_SIGNATURES_COMPLETED';

ALTER TABLE unit_products DROP COLUMN split_from_id;
DROP TEMPORARY TABLE tmp_mixed_effect_bundles;
