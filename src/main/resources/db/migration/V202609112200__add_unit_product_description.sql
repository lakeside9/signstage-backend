-- 단위 상품 설명 필드 추가 — 사용자 요청(2026-09-11). 카탈로그 관리자가 이 상품이 무엇인지
-- (특히 이벤트 효과 묶음처럼 이름만으로는 무엇이 포함되는지 알기 어려운 종류)를 설명해두면
-- 파트너가 추가구매/고객 견적 화면에서 품목을 고를 때 참고할 수 있다. nullable — 기존 행은
-- 전부 설명 없음(NULL)으로 시작한다. 이름/분류/배타그룹과 같은 급의 메타데이터라
-- unit_product_histories에도 스냅샷을 남긴다.
ALTER TABLE unit_products ADD COLUMN description VARCHAR(500) NULL;
ALTER TABLE unit_product_histories ADD COLUMN description VARCHAR(500) NULL;
