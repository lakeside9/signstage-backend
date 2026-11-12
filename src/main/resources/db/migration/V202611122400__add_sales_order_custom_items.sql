-- 수주 견적의 직접 입력 항목(CUSTOM_ITEM 줄) — 카탈로그에 없는 품목을 이름·수량·고객 단가·발주 단가로 넣는다.
ALTER TABLE sales_order_lines ADD COLUMN partner_unit_price DECIMAL(19,4) NULL AFTER customer_price_id;
ALTER TABLE sales_order_change_lines ADD COLUMN partner_unit_price DECIMAL(19,4) NULL AFTER customer_price_id;
ALTER TABLE sales_orders ADD COLUMN custom_item_partner_amount DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER lodging_persons;
ALTER TABLE sales_order_changes
    ADD COLUMN before_custom_item_partner_amount DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER before_lodging_persons,
    ADD COLUMN custom_item_partner_amount DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER lodging_persons;
ALTER TABLE partner_orders ADD COLUMN custom_item_amount DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER lodging_night_rate;
