-- 결과 PDF 위변조 검증(signstage-docs business/ceremony-feature-migration-review.md §2.5)에
-- 쓸 컬럼. is_verified/verification_at은 "몇 번 검증됐는지"가 아니라 "마지막으로 언제
-- 검증됐는지"만 남긴다(최소 범위, 7라운드에서 미뤄둔 컬럼).

ALTER TABLE ceremony_results
    ADD COLUMN is_verified TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN verification_at TIMESTAMP NULL;
