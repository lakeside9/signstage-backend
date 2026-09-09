-- 선택옵션(OptionalFeature)의 projector_effect(프로젝터 화면 효과 여부)와
-- paired_capacity_type(짝이 되는 용량 추가구매 종류)를 완전히 제거한다(2026-09-09 결정).
--
-- projector_effect: 실제로 아무 로직도 이 값을 읽지 않는 순수 분류 정보였고(카탈로그 목록
-- 배지 표시 외 용도 없음), category(EQUIPMENT/PERSONNEL/APPLICATION)로 충분히 갈음된다는
-- 판단으로 제거한다.
--
-- paired_capacity_type: "표시용 선택옵션"과 "짝이 되는 용량 추가구매 상품"이 각자 독립된
-- 판매가로 둘 다 구매 가능해, 같은 서비스(예: 온라인지원)에 서로 다른 두 가격이 동시에
-- 존재하고 이중 청구가 가능한 문제를 발견했다(signstage-docs
-- business/optional-feature-capacity-addon-pairing-review.md 참고 — 이 필드를 만든 근거
-- 문서). 짝 필드 자체를 없애 관리자 등록 화면에서 이 개념을 없앤다.
--
-- 개발 단계라 기존 데이터 보존 부담이 없다는 전제(billing-catalog-zero-base-schema-redesign-review.md와
-- 같은 전제)로 컬럼을 그대로 드롭한다.

ALTER TABLE optional_features
    DROP COLUMN projector_effect,
    DROP COLUMN paired_capacity_type;

ALTER TABLE optional_feature_histories
    DROP COLUMN projector_effect,
    DROP COLUMN paired_capacity_type;
