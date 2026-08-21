-- 용량 추가구매 상품(CapacityAddOn)에 "보조 용량" 개념을 추가한다. signstage-docs
-- business/ceremony-billing-options-review.md 4.7절 후속(2026-08-21, 사용자 요청 — 태블릿
-- 대여 대수를 용량추가구매 상품으로 다루되, "서명자", "태블릿", "서명자+태블릿" 세 종류를
-- 카탈로그에서 고를 수 있게).
--
-- secondary_capacity_type/secondary_unit_amount: 한 상품이 주(capacity_type/unit_amount) 외에
-- 두 번째 용량도 함께 늘리는 "묶음 상품"을 표현한다 — 예: "서명자 +10명 / 태블릿 +10대"를
-- 상품 하나로 구매. 단일 상품(묶음이 아닌 경우)은 둘 다 NULL이다.
--
-- CapacityType에 TABLETS 값이 추가됐지만(애플리케이션 enum, 이 테이블 컬럼은 이미 VARCHAR라
-- 스키마 변경이 필요 없다), BillingPlan에는 대응하는 필수 필드(예: max_tablets)를 추가하지
-- 않는다 — 태블릿은 플랜 기본 포함 없이 항상 0에서 시작해 추가구매로만 늘어난다.

ALTER TABLE capacity_addons
    ADD COLUMN secondary_capacity_type VARCHAR(20) NULL,
    ADD COLUMN secondary_unit_amount   INT NULL;

ALTER TABLE capacity_addon_histories
    ADD COLUMN secondary_capacity_type VARCHAR(20) NULL,
    ADD COLUMN secondary_unit_amount   INT NULL;

-- 묶음 상품 구매 시점의 보조 용량 단가 스냅샷 — purchased_unit_amount와 같은 이유(9장).
ALTER TABLE ceremony_capacity_purchases
    ADD COLUMN purchased_secondary_unit_amount INT NULL;
