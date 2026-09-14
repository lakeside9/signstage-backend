-- 단위 상품 판매 단위 수량 — 사용자 요청(2026-09-14). "템플릿 문서 10개를 1묶음 10,000원에
-- 판다"처럼, 추가구매를 N개 단위(배수)로만 허용하는 제약이다. 판매가는 여전히 개당 단가로 두고
-- (예: 1,000원), 이 값을 10으로 등록하면 고객은 10/20/30개 단위로만 살 수 있다. 기본값 1(기존
-- 행 전부 이 기본값으로 시작 — "제약 없음"과 동일해 기존 동작이 그대로 유지된다). 토글형
-- (EVENT_EFFECT_BUNDLE)은 max_purchase_quantity와 같은 이유로 항상 1이 UnitProduct 생성자/
-- updateInfo에서 정규화되어 서비스 계층이 이 컬럼을 별도로 강제하지 않는다.
ALTER TABLE unit_products ADD COLUMN sale_unit_quantity INT NOT NULL DEFAULT 1;
ALTER TABLE unit_product_histories ADD COLUMN sale_unit_quantity INT NOT NULL DEFAULT 1;
