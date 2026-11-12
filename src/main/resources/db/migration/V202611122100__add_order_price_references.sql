-- 견적·수주·발주 계산에 쓴 가격표 행을 남긴다 — 쓰이지 않은 가격표만 삭제할 수 있다
-- (signstage-docs business/platform-order-partner-subcontract-review.md 12장).
ALTER TABLE sales_order_lines
    ADD COLUMN customer_price_id BIGINT NULL AFTER display_order,
    ADD CONSTRAINT fk_sales_order_lines_customer_price FOREIGN KEY (customer_price_id) REFERENCES customer_prices (id);

ALTER TABLE sales_order_change_lines
    ADD COLUMN customer_price_id BIGINT NULL AFTER display_order,
    ADD CONSTRAINT fk_sales_order_change_lines_customer_price FOREIGN KEY (customer_price_id) REFERENCES customer_prices (id);

ALTER TABLE sales_orders
    ADD COLUMN region_surcharge_id BIGINT NULL AFTER lodging_persons,
    ADD CONSTRAINT fk_sales_orders_region_surcharge FOREIGN KEY (region_surcharge_id) REFERENCES region_surcharges (id);

ALTER TABLE sales_order_changes
    ADD COLUMN region_surcharge_id BIGINT NULL AFTER lodging_persons,
    ADD CONSTRAINT fk_sales_order_changes_region_surcharge FOREIGN KEY (region_surcharge_id) REFERENCES region_surcharges (id);

ALTER TABLE partner_orders
    ADD COLUMN partner_rate_card_id BIGINT NULL AFTER lodging_night_rate,
    ADD COLUMN region_surcharge_id  BIGINT NULL AFTER partner_rate_card_id,
    ADD CONSTRAINT fk_partner_orders_partner_rate_card FOREIGN KEY (partner_rate_card_id) REFERENCES partner_rate_cards (id),
    ADD CONSTRAINT fk_partner_orders_region_surcharge FOREIGN KEY (region_surcharge_id) REFERENCES region_surcharges (id);

-- 기존 행은 계산 시점(줄·변경 생성일, 발주일)에 유효했던 가격표로 채운다.
UPDATE sales_order_lines l
    JOIN sales_orders s ON s.id = l.sales_order_id
    JOIN customer_prices p ON p.billing_plan_id = s.billing_plan_id
        AND p.effective_from <= DATE(l.created_at) AND (p.effective_to IS NULL OR p.effective_to >= DATE(l.created_at))
SET l.customer_price_id = p.id
WHERE l.line_type = 'BILLING_PLAN';

UPDATE sales_order_lines l
    JOIN customer_prices p ON p.unit_product_id = l.unit_product_id
        AND p.effective_from <= DATE(l.created_at) AND (p.effective_to IS NULL OR p.effective_to >= DATE(l.created_at))
SET l.customer_price_id = p.id
WHERE l.line_type IN ('UNIT_PRODUCT', 'REHEARSAL_STAFF');

UPDATE sales_order_change_lines l
    JOIN sales_order_changes c ON c.id = l.sales_order_change_id
    JOIN sales_orders s ON s.id = c.sales_order_id
    JOIN customer_prices p ON p.billing_plan_id = s.billing_plan_id
        AND p.effective_from <= DATE(l.created_at) AND (p.effective_to IS NULL OR p.effective_to >= DATE(l.created_at))
SET l.customer_price_id = p.id
WHERE l.line_type = 'BILLING_PLAN';

UPDATE sales_order_change_lines l
    JOIN customer_prices p ON p.unit_product_id = l.unit_product_id
        AND p.effective_from <= DATE(l.created_at) AND (p.effective_to IS NULL OR p.effective_to >= DATE(l.created_at))
SET l.customer_price_id = p.id
WHERE l.line_type IN ('UNIT_PRODUCT', 'REHEARSAL_STAFF');

UPDATE sales_orders s
    JOIN region_surcharges r ON r.region = s.region
        AND r.effective_from <= DATE(s.updated_at) AND (r.effective_to IS NULL OR r.effective_to >= DATE(s.updated_at))
SET s.region_surcharge_id = r.id;

UPDATE sales_order_changes c
    JOIN sales_orders s ON s.id = c.sales_order_id
    JOIN region_surcharges r ON r.region = s.region
        AND r.effective_from <= DATE(c.created_at) AND (r.effective_to IS NULL OR r.effective_to >= DATE(c.created_at))
SET c.region_surcharge_id = r.id;

UPDATE partner_orders o
    JOIN sales_orders s ON s.id = o.sales_order_id
    JOIN region_surcharges r ON r.region = s.region
        AND r.effective_from <= DATE(o.issued_at) AND (r.effective_to IS NULL OR r.effective_to >= DATE(o.issued_at))
SET o.region_surcharge_id = r.id;

UPDATE partner_orders o
    JOIN partner_rate_cards c ON c.organization_id = o.organization_id
        AND c.effective_from <= DATE(o.issued_at) AND (c.effective_to IS NULL OR c.effective_to >= DATE(o.issued_at))
SET o.partner_rate_card_id = c.id;

UPDATE partner_orders o
    JOIN partner_rate_cards c ON c.organization_id IS NULL
        AND c.effective_from <= DATE(o.issued_at) AND (c.effective_to IS NULL OR c.effective_to >= DATE(o.issued_at))
SET o.partner_rate_card_id = c.id
WHERE o.partner_rate_card_id IS NULL;
