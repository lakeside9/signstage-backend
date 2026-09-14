-- "현장지원 요청" 명칭 정리 — signstage-docs
-- business/platform-admin-partner-ux-confusion-review.md 2.1절/6장 결정(2026-09-14).
-- "고객 견적" 탭의 정액 카탈로그 품목(현장지원(수도권)/현장지원(지방·근거리, ~100km) 등)과
-- 이름이 너무 비슷해 파트너가 두 기능(파트너→실고객 정액 판매 vs 파트너→플랫폼 협상형 비용)을
-- 혼동할 수 있다는 지적을 반영한다. 새 단어를 만들지 않고, 이 거리 기준 요금 자체를 이미
-- "현장지원 출장비"라 불러온 기존 도메인 용어(ceremony-support-services-billing-review.md)를
-- 그대로 가져와 앵커 단위 상품 이름에 붙인다. 가격·타입·카테고리·과금축 등 다른 값은 그대로다
-- — 이름·설명 문구만 바뀐다.
UPDATE unit_products
SET name = '현장지원 출장비(요청형·관리자 견적)',
    description = '파트너가 일시·장소를 적어 요청하면 관리자가 거리 등을 보고 실제 금액을 매기는 협상형 현장지원 출장비입니다. 이 상품의 등록가는 실제 청구에 쓰이지 않습니다 — 관리자가 매긴 금액이 그대로 청구됩니다.',
    updated_by = 1
WHERE type = 'ONSITE_SUPPORT_REQUEST';

INSERT INTO unit_product_histories
    (unit_product_id, type, name, description, category, is_platform_usage_fee, exclusivity_group, created_by, updated_by)
SELECT id, type, name, description, category, is_platform_usage_fee, exclusivity_group, created_by, updated_by
FROM unit_products WHERE type = 'ONSITE_SUPPORT_REQUEST';
