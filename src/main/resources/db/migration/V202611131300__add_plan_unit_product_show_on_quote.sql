-- 플랜 구성 줄마다 고객 견적서 패키지 줄 아래에 보일지 정한다. 지금까지는 서명자만 보였으므로 서명자 줄만 켜 둔다.
ALTER TABLE billing_plan_unit_products ADD COLUMN show_on_quote BOOLEAN NOT NULL DEFAULT FALSE AFTER included_quantity;
ALTER TABLE billing_plan_history_unit_products ADD COLUMN show_on_quote BOOLEAN NOT NULL DEFAULT FALSE AFTER included_quantity;
UPDATE billing_plan_unit_products bpup
    JOIN unit_products up ON up.id = bpup.unit_product_id
SET bpup.show_on_quote = TRUE
WHERE up.type = 'SIGNERS';
UPDATE billing_plan_history_unit_products bphup
    JOIN unit_products up ON up.id = bphup.unit_product_id
SET bphup.show_on_quote = TRUE
WHERE up.type = 'SIGNERS';
