-- 공급가(supply_price)를 nullable로 푼다 — signstage-docs
-- business/billing-catalog-zero-base-schema-redesign-review.md 결정(2026-09-08, 항목 G).
--
-- 공급가는 내부 전용(마진 계산용) 필드로, 실제 할인/청구액 계산식에는 전혀 관여하지 않는다
-- (billing-catalog-pricing-input-validation-review.md 3.3절). 그런데도 세 카탈로그
-- (billing_plans/optional_features/capacity_addons)와 각 이력 테이블 전부 NOT NULL이라,
-- 원가를 아직 모르는 상품을 급히 등록해야 할 때 관리자가 0을 강제로 넣게 되고, 그러면
-- 나중에 마진율을 뽑을 때 "마진 100%"로 왜곡된다. NOT NULL을 풀어 "원가 미상" 상태를
-- 값 자체로 표현할 수 있게 한다 — 기존에 저장된 값은 그대로 두고(백필 불필요), 앞으로의
-- 등록/수정에서만 null을 허용한다.

ALTER TABLE billing_plans
    MODIFY COLUMN supply_price DECIMAL(19,4) NULL;

ALTER TABLE billing_plan_histories
    MODIFY COLUMN supply_price DECIMAL(19,4) NULL;

ALTER TABLE optional_features
    MODIFY COLUMN supply_price DECIMAL(19,4) NULL;

ALTER TABLE optional_feature_histories
    MODIFY COLUMN supply_price DECIMAL(19,4) NULL;

ALTER TABLE capacity_addons
    MODIFY COLUMN supply_price DECIMAL(19,4) NULL;

ALTER TABLE capacity_addon_histories
    MODIFY COLUMN supply_price DECIMAL(19,4) NULL;

-- ceremony_plan_histories.plan_supply_price는 그 순간 BillingPlan.supplyPrice를 스냅샷한
-- 값이다 — 원본이 nullable이 됐으니 스냅샷 컬럼도 nullable이어야 한다(그렇지 않으면 원가
-- 미상 플랜으로 행사를 만들 때 스냅샷 저장이 NOT NULL 위반으로 실패한다).
ALTER TABLE ceremony_plan_histories
    MODIFY COLUMN plan_supply_price DECIMAL(19,4) NULL;
