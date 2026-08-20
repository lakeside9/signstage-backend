-- 용량 추가구매 요청에 구매 시점 단가(unit_amount) 스냅샷을 추가한다.
-- signstage-docs business/ceremony-billing-options-review.md 9장 참고 —
-- 지금까지는 유효 한도 계산이 capacity_addons.unit_amount를 라이브로 조회해서, 카탈로그
-- 관리자가 나중에 단가를 고치면 이미 승인된 구매 내역까지 소급으로 바뀌는 결함이 있었다.
--
-- 기존 행은 원래 구매 시점 값을 복원할 방법이 없으므로, 배포 시점 현재 카탈로그 값으로
-- 최선 근사 백필한다(이후 신규 구매 요청부터는 정확한 스냅샷이 남는다).

ALTER TABLE ceremony_capacity_purchases
    ADD COLUMN purchased_unit_amount INT NULL;

UPDATE ceremony_capacity_purchases cp
    JOIN capacity_addons ca ON ca.id = cp.capacity_addon_id
    SET cp.purchased_unit_amount = ca.unit_amount
    WHERE cp.purchased_unit_amount IS NULL;

ALTER TABLE ceremony_capacity_purchases
    MODIFY COLUMN purchased_unit_amount INT NOT NULL;
