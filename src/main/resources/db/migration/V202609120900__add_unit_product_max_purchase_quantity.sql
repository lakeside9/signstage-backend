-- 단위 상품 최대 구매 수량 — 사용자 요청(2026-09-12). "이 행사가 이 단위 상품을 추가구매로
-- 누적 몇 개까지 살 수 있는가"의 카탈로그 전역 상한이다. NULL이면 무제한(기존 행 전부 이
-- 기본값으로 시작). 토글형(EVENT_EFFECT_BUNDLE)은 이 값과 무관하게 항상 최대 1이
-- UnitProductType#isToggle()로 이미 고정돼 있어(관리자가 조정할 수 없는 타입 자체의 규칙),
-- 서비스 계층이 토글형에는 이 컬럼을 아예 쓰지 않는다(저장은 항상 NULL로 정규화).
ALTER TABLE unit_products ADD COLUMN max_purchase_quantity INT NULL;
ALTER TABLE unit_product_histories ADD COLUMN max_purchase_quantity INT NULL;
