-- 예비 태블릿(D23, 발주에만), 리허설 별도일 현장 인력(D24), 관리자 지정 숙박(D26)
-- (signstage-docs business/platform-order-partner-subcontract-review.md).
ALTER TABLE region_surcharges
    ADD COLUMN lodging_customer_amount DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER partner_amount,
    ADD COLUMN lodging_partner_amount  DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER lodging_customer_amount;

ALTER TABLE sales_orders
    ADD COLUMN spare_tablet_count          INT NOT NULL DEFAULT 0 AFTER onsite_staff_person_days,
    ADD COLUMN rehearsal_staff_person_days INT NOT NULL DEFAULT 0 AFTER spare_tablet_count,
    ADD COLUMN lodging_nights              INT NOT NULL DEFAULT 0 AFTER rehearsal_staff_person_days,
    ADD COLUMN lodging_persons             INT NOT NULL DEFAULT 0 AFTER lodging_nights;

ALTER TABLE partner_orders
    ADD COLUMN spare_tablet_count          INT NOT NULL DEFAULT 0 AFTER region_surcharge,
    ADD COLUMN rehearsal_staff_person_days INT NOT NULL DEFAULT 0 AFTER spare_tablet_count,
    ADD COLUMN lodging_nights              INT NOT NULL DEFAULT 0 AFTER rehearsal_staff_person_days,
    ADD COLUMN lodging_persons             INT NOT NULL DEFAULT 0 AFTER lodging_nights,
    ADD COLUMN lodging_night_rate          DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER lodging_persons;

ALTER TABLE sales_order_changes
    ADD COLUMN before_spare_tablet_count          INT NOT NULL DEFAULT 0 AFTER before_onsite_staff_person_days,
    ADD COLUMN before_rehearsal_staff_person_days INT NOT NULL DEFAULT 0 AFTER before_spare_tablet_count,
    ADD COLUMN before_lodging_nights              INT NOT NULL DEFAULT 0 AFTER before_rehearsal_staff_person_days,
    ADD COLUMN before_lodging_persons             INT NOT NULL DEFAULT 0 AFTER before_lodging_nights,
    ADD COLUMN spare_tablet_count                 INT NOT NULL DEFAULT 0 AFTER onsite_staff_person_days,
    ADD COLUMN rehearsal_staff_person_days        INT NOT NULL DEFAULT 0 AFTER spare_tablet_count,
    ADD COLUMN lodging_nights                     INT NOT NULL DEFAULT 0 AFTER rehearsal_staff_person_days,
    ADD COLUMN lodging_persons                    INT NOT NULL DEFAULT 0 AFTER lodging_nights;
