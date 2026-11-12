-- 단위 상품·과금 플랜을 판매 경로별로 나눈다 — 기존 카탈로그는 파트너 직접 수주용으로 남기고,
-- 플랫폼 수주용은 새로 등록한다(signstage-docs business/platform-order-unit-product-composition-review.md U01).
ALTER TABLE unit_products
    ADD COLUMN sales_channel VARCHAR(20) NOT NULL DEFAULT 'PARTNER_DIRECT' AFTER type;

ALTER TABLE billing_plans
    ADD COLUMN sales_channel VARCHAR(20) NOT NULL DEFAULT 'PARTNER_DIRECT' AFTER plan_type;
