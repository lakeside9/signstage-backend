-- 와이파이 장비 대여(WIFI_EQUIPMENT): 플랫폼 수주에서 파트너가 수행하고 파트너 단가표의 대당 단가로 발주한다.
-- 수주·범위 변경·발주에 대수를, 단가표와 발주에 대당 단가를 둔다. 기존 행은 0이다.

ALTER TABLE partner_rate_cards
    ADD COLUMN wifi_unit_rate DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER tablet_unit_rate;

ALTER TABLE sales_orders
    ADD COLUMN wifi_count INT NOT NULL DEFAULT 0 AFTER tablet_count;

ALTER TABLE sales_order_changes
    ADD COLUMN before_wifi_count INT NOT NULL DEFAULT 0 AFTER before_tablet_count,
    ADD COLUMN wifi_count        INT NOT NULL DEFAULT 0 AFTER tablet_count;

ALTER TABLE partner_orders
    ADD COLUMN wifi_count     INT NOT NULL DEFAULT 0 AFTER tablet_unit_rate,
    ADD COLUMN wifi_unit_rate DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER wifi_count;
