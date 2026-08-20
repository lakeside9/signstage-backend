-- 선택옵션 구매 요청에 구매 시점 이름(name) 스냅샷을 추가한다.
-- signstage-docs business/ceremony-billing-options-review.md 9장 참고 —
-- 가격은 이미 purchased_sale_price 등으로 스냅샷돼 있었지만 이름은 아니어서, "적용 가능한
-- 선택옵션" 화면이 라이브 optional_features.name을 보여줬다(카탈로그 관리자가 이름을 바꾸면
-- 이미 구매한 옵션의 표시 이름도 즉시 바뀌는 결함).
--
-- 기존 행은 원래 구매 시점 이름을 복원할 방법이 없으므로, 배포 시점 현재 카탈로그 이름으로
-- 최선 근사 백필한다(이후 신규 구매 요청부터는 정확한 스냅샷이 남는다).

ALTER TABLE ceremony_optional_feature_purchases
    ADD COLUMN purchased_name VARCHAR(100) NULL;

UPDATE ceremony_optional_feature_purchases cofp
    JOIN optional_features of ON of.id = cofp.optional_feature_id
    SET cofp.purchased_name = of.name
    WHERE cofp.purchased_name IS NULL;

ALTER TABLE ceremony_optional_feature_purchases
    MODIFY COLUMN purchased_name VARCHAR(100) NOT NULL;
