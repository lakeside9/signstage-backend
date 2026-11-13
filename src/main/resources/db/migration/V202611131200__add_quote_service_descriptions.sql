-- 견적서 서비스 설명. 플랜은 견적서 "제공 서비스" 본문, 단위 상품은 견적 줄 아래 설명을 갖는다(관리자용 description과 별개).
-- 견적에는 저장 시점의 문구를 복사해 두어, 이후 카탈로그 문구를 고쳐도 이미 보낸·수락한 견적은 바뀌지 않는다.
ALTER TABLE billing_plans ADD COLUMN service_description TEXT NULL AFTER name;
ALTER TABLE billing_plan_histories ADD COLUMN service_description TEXT NULL AFTER name;
ALTER TABLE unit_products ADD COLUMN quote_description VARCHAR(500) NULL AFTER description;
ALTER TABLE unit_product_histories ADD COLUMN quote_description VARCHAR(500) NULL AFTER description;
ALTER TABLE sales_orders ADD COLUMN service_description TEXT NULL;
ALTER TABLE sales_order_lines ADD COLUMN description TEXT NULL AFTER name;
ALTER TABLE sales_order_change_lines ADD COLUMN description TEXT NULL AFTER name;
