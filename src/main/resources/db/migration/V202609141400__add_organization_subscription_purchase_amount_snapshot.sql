-- 구독 승인 시점 "구매 비용" 스냅샷 — signstage-docs
-- business/subscription-margin-screen-separation-review.md 후속(2026-09-14, 사용자 요청)
-- "기간/횟수이면 시작날짜와 종료날짜를, 횟수이면 구매날짜를, 공통으로 구매 비용을
-- 보여주세요." 시작일/종료일은 이미 있었지만(start_date/end_date), 구매 비용은 이
-- 엔티티 어디에도 스냅샷돼 있지 않았다 — 추가한다.
--
-- 이 프로젝트의 기존 관례(plan_name_snapshot 등, "승인 시점에 고정하고 카탈로그가 나중에
-- 바뀌어도 영향받지 않는다")를 그대로 따른다. 아직 승인 전(PENDING/REJECTED)이면 null이다.
ALTER TABLE organization_subscriptions
    ADD COLUMN purchase_amount_snapshot DECIMAL(19,4) NULL AFTER allowed_count_snapshot,
    ADD COLUMN currency_code_snapshot CHAR(3) NULL AFTER purchase_amount_snapshot;
