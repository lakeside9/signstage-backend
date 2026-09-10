-- 단위 상품 목록 표시 순서 — 서명자/문서양식/하위행사(Signer/Template/CeremonyEvent)와 같은
-- displayOrder 일괄 재정렬 패턴을 카탈로그 목록에도 적용한다(signstage-docs
-- business/billing-catalog-unit-product-model-redesign-review.md 11장, 2026-09-10).
-- 기존 행은 전부 0으로 시작해 id 오름차순(지금 화면에 보이는 순서 그대로)으로 동률 처리된다
-- — 관리자가 실제로 순서를 바꾸기 전까지는 지금 순서와 동일하다.
ALTER TABLE unit_products ADD COLUMN display_order INT NOT NULL DEFAULT 0;
