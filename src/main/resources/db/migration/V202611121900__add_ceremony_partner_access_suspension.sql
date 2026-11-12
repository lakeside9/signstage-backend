-- 수락한 발주가 취소(파트너 교체)되면 새 파트너가 수락할 때까지 이전 파트너의 행사 접근을 막는다
-- (signstage-docs business/platform-order-partner-subcontract-review.md D03·D18).
ALTER TABLE ceremonies ADD COLUMN partner_access_suspended_at TIMESTAMP(6) NULL AFTER completed_at;
