-- 파트너 발주 메모(2026-11-13 사용자 요청) — 발주할 때 관리자가 파트너에게 전할 내용(준비물·주의사항 등)을 적는다. 파트너 발주함에 보인다.
ALTER TABLE partner_orders ADD COLUMN memo TEXT NULL AFTER adjustment_reason;
