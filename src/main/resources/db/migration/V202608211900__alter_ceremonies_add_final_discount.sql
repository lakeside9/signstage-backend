-- 행사(Ceremony) 건별 재량 할인.
-- signstage-docs business/organization-event-discount-pricing-review.md 4.2 결정 참고
-- (2026-08-21 갱신: 조직 전역 계약 할인은 우선 보류하고 행사별 할인만 적용한다).
--
-- final_discount_*: 품목 할인과 별개로, 그 행사 건에만 매기는 관리자 재량 할인.
-- 4.2/4.9 결정과 같은 원칙으로 NOT NULL이다 — "할인 없음"은 discount_value=0으로
-- 표현하고, NULL/sentinel로 표현하지 않는다.

ALTER TABLE ceremonies
    ADD COLUMN final_discount_type  VARCHAR(20)  NOT NULL DEFAULT 'FIXED_AMOUNT',
    ADD COLUMN final_discount_value DECIMAL(12,2) NOT NULL DEFAULT 0;
