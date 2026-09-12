-- 단위 상품 추가구매 취소(관리자) — 사용자 요청(2026-09-12). 이미 승인(APPROVED)된 구매를
-- 관리자가 사유를 남기고 취소할 수 있게 한다. 승인/반려 감사 기록(reviewed_by/reviewed_at/
-- rejection_reason)과 별도 컬럼으로 둔다 — 덮어쓰면 "누가 언제 승인했는지"라는 원래 기록이
-- 사라진다. PurchaseStatus.CANCELLED는 코드 레벨 enum(컬럼은 문자열 저장)이라 이 마이그레이션
-- 대상이 아니다.
ALTER TABLE ceremony_unit_product_purchases
  ADD COLUMN cancelled_by BIGINT NULL,
  ADD COLUMN cancelled_at DATETIME NULL,
  ADD COLUMN cancellation_reason VARCHAR(500) NULL;
