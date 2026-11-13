-- 수주 견적 최종 금액(부가세 포함 합계) 단수 절사 단위 — NULL이면 절사하지 않는다. 버린 금액은 TRUNCATION 줄(음수)로 남긴다.
-- 범위 변경도 할인 방식·절사 단위를 함께 제안하고, 반영할 때 수주로 옮긴다.
ALTER TABLE sales_orders ADD COLUMN truncation_unit INT NULL AFTER discount_rate_percent;
ALTER TABLE sales_order_changes
    ADD COLUMN discount_rate_percent DECIMAL(5,2) NULL AFTER discount_reason,
    ADD COLUMN truncation_unit INT NULL AFTER discount_rate_percent;
