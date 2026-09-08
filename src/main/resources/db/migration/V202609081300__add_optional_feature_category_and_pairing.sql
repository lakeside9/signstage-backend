-- 선택옵션 카탈로그에 상위 분류(category)와 짝이 되는 용량 추가구매 종류(paired_capacity_type)를
-- 추가한다 — signstage-docs business/ceremony-support-services-billing-review.md 결정
-- (2026-09-08, 4.3절 안 B), business/optional-feature-capacity-addon-pairing-review.md 결정
-- (2026-09-08, 5장). 현장지원(ONSITE_SUPPORT)·온라인지원(ONLINE_SUPPORT) 신규 품목의 기반이 되는
-- 마이그레이션이다(OptionalFeatureCode/CapacityType enum 값 추가는 코드 배포만으로 충분해
-- 여기서 다루지 않는다 — 실제 카탈로그 행 등록은 관리자가 화면에서 한다).
--
-- category: 처음엔 NULL 허용으로 추가 → 기존 4개 코드를 최선 근사로 백필 → NOT NULL로 전환
-- (V202608271100의 max_rehearsal_events 백필과 같은 관례).
-- paired_capacity_type: 항상 nullable이다("완결형" 상품은 null) — TABLET_RENTAL 행만
-- CapacityType.TABLETS로 백필한다(짝 명시화 문서 결정 #5).

ALTER TABLE optional_features
    ADD COLUMN category VARCHAR(20) NULL AFTER exclusivity_group,
    ADD COLUMN paired_capacity_type VARCHAR(20) NULL AFTER category;

UPDATE optional_features SET category = 'EQUIPMENT', paired_capacity_type = 'TABLETS' WHERE code = 'TABLET_RENTAL';
UPDATE optional_features SET category = 'APPLICATION'
    WHERE code IN ('SIGNER_FIELD_ZOOM', 'ALL_SIGNED_FIREWORKS', 'VIDEO_ATTENDANCE', 'EVENT_EFFECT_BUNDLE');

ALTER TABLE optional_features
    MODIFY COLUMN category VARCHAR(20) NOT NULL;

ALTER TABLE optional_feature_histories
    ADD COLUMN category VARCHAR(20) NULL AFTER exclusivity_group,
    ADD COLUMN paired_capacity_type VARCHAR(20) NULL AFTER category;

UPDATE optional_feature_histories oh
    JOIN optional_features o ON o.id = oh.optional_feature_id
    SET oh.category = o.category, oh.paired_capacity_type = o.paired_capacity_type;

ALTER TABLE optional_feature_histories
    MODIFY COLUMN category VARCHAR(20) NOT NULL;
