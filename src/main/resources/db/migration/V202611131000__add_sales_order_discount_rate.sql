-- 수주 견적 영업 할인을 퍼센트로도 넣는다 — 값이 있으면 퍼센트 할인(공급가 소계 × 율), NULL이면 정액 할인(할인 줄 금액)이다.
ALTER TABLE sales_orders ADD COLUMN discount_rate_percent DECIMAL(5,2) NULL AFTER total_amount;
