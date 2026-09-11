-- "확정 이용료"(BillingQuote) 완전 삭제 — signstage-docs
-- business/unit-product-purchase-self-checkout-review.md 6장 결정(2026-09-11): "구매 이력으로
-- 전환 가능한지 확인하고, 어려우면 완전 삭제"라는 조건부 요청을 검토한 결과 — BillingQuote는
-- 한 시점의 전체 누적 상태를 얼리는 집계 스냅샷이고 구매 이력은 거래 한 건을 기록하는
-- 트랜잭션 로그라 데이터의 결이 달라 전환이 불가능해 완전 삭제로 결정했다(같은 문서 4.3절).
-- 자가-체크아웃으로 시스템 사용료 구매가 "구매하기"를 누르는 순간 이미 확정(APPROVED)되고
-- 그 줄 자체가 이미 자기 스냅샷(purchased_sale_price 등)을 갖고 있어, 별도로 "전체 합계를
-- 한 번 더 얼려두는" 이 기능의 존재 이유가 사라졌다.

DROP TABLE IF EXISTS billing_quote_status_events;
DROP TABLE IF EXISTS billing_quote_lines;
DROP TABLE IF EXISTS billing_quotes;
