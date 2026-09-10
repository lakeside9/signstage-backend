-- 현장지원·온라인지원 단위 상품 등록 스크립트
--
-- signstage-docs business/ceremony-support-services-billing-review.md §3.2/4.2 결정 —
-- 현장지원은 수도권 1단계 + 지방 거리 기준 3단계(근거리/중거리/원거리) = 4개 상품이
-- UnitProductType.ONSITE_SUPPORT를 공유하고, 온라인지원은 원격이라 지역 구분 없이 1개
-- 상품(ONLINE_SUPPORT)만 등록한다. 거리 구간(~100km/100~250km/250km 초과)은 그 문서가
-- 참고 예시로 남긴 값을 그대로 썼다 — 시스템이 강제하지 않고 상품명에만 존재한다.
--
-- 1회성 수동 실행 전용이다(scripts/seed-platform-admin.sql과 같은 이유로 Flyway 마이그레이션에
-- 넣지 않는다 — dev/stage/prd 전부에 같은 가격으로 자동 적용되면 안 된다. 실제 가격은
-- 환경마다 플랫폼 관리자가 카탈로그 등록 화면에서 다시 정하는 게 정상 경로다).
--
-- **가격은 실제 정책값이 아니라 임의 placeholder다** — 2026-09-10, 사용자 요청으로 "가격은
-- 임의로 해도 된다"는 확인을 받고 등록한다. 실제 판매가가 정해지면 관리자 콘솔
-- (/admin/billing-catalog/unit-products)의 "판매가격 기간 관리"에서 새 기간을 추가하거나
-- 이 기간을 수정하면 된다 — 코드 변경이 필요 없다.
--
-- UnitProductService.createUnitProduct와 같은 부작용(unit_products +
-- unit_product_histories + unit_product_price_periods + unit_product_price_period_histories
-- + platform_admin_audit_log 각 1행)을 그대로 재현한다.
--
-- 사용 순서:
--   mysql --default-character-set=utf8mb4 -h <host> -P <port> -u <user> -p <database> \
--         < scripts/seed-onsite-online-support-unit-products.sql
--
-- created_by는 이 스크립트를 실행하는 환경의 실제 플랫폼 관리자 user id로 바꿔서 쓴다
-- (로컬 개발 DB 기준 1 = platform-admin@eformworks.com, PLATFORM_SUPER).

SET @admin_id = 1;
SET @today = CURDATE();

-- 1. 현장지원(수도권)
INSERT INTO unit_products (type, name, category, exclusivity_group, created_by, updated_by)
VALUES ('ONSITE_SUPPORT', '현장지원(수도권)', 'PERSONNEL', NULL, @admin_id, @admin_id);
SET @p1 = LAST_INSERT_ID();
INSERT INTO unit_product_histories (unit_product_id, type, name, category, exclusivity_group, created_by, updated_by)
VALUES (@p1, 'ONSITE_SUPPORT', '현장지원(수도권)', 'PERSONNEL', NULL, @admin_id, @admin_id);
INSERT INTO unit_product_price_periods
    (unit_product_id, currency_code, supply_price, sale_price, tax_code, active, effective_from, effective_to, created_by, updated_by)
VALUES (@p1, 'KRW', 150000, 200000, 'KR_VAT_STANDARD', TRUE, @today, NULL, @admin_id, @admin_id);
SET @pp1 = LAST_INSERT_ID();
INSERT INTO unit_product_price_period_histories
    (unit_product_id, currency_code, supply_price, sale_price, tax_code, active, effective_from, effective_to, removed, created_by, updated_by)
VALUES (@p1, 'KRW', 150000, 200000, 'KR_VAT_STANDARD', TRUE, @today, NULL, FALSE, @admin_id, @admin_id);

-- 2. 현장지원(지방·근거리, ~100km)
INSERT INTO unit_products (type, name, category, exclusivity_group, created_by, updated_by)
VALUES ('ONSITE_SUPPORT', '현장지원(지방·근거리, ~100km)', 'PERSONNEL', NULL, @admin_id, @admin_id);
SET @p2 = LAST_INSERT_ID();
INSERT INTO unit_product_histories (unit_product_id, type, name, category, exclusivity_group, created_by, updated_by)
VALUES (@p2, 'ONSITE_SUPPORT', '현장지원(지방·근거리, ~100km)', 'PERSONNEL', NULL, @admin_id, @admin_id);
INSERT INTO unit_product_price_periods
    (unit_product_id, currency_code, supply_price, sale_price, tax_code, active, effective_from, effective_to, created_by, updated_by)
VALUES (@p2, 'KRW', 200000, 260000, 'KR_VAT_STANDARD', TRUE, @today, NULL, @admin_id, @admin_id);
INSERT INTO unit_product_price_period_histories
    (unit_product_id, currency_code, supply_price, sale_price, tax_code, active, effective_from, effective_to, removed, created_by, updated_by)
VALUES (@p2, 'KRW', 200000, 260000, 'KR_VAT_STANDARD', TRUE, @today, NULL, FALSE, @admin_id, @admin_id);

-- 3. 현장지원(지방·중거리, 100~250km)
INSERT INTO unit_products (type, name, category, exclusivity_group, created_by, updated_by)
VALUES ('ONSITE_SUPPORT', '현장지원(지방·중거리, 100~250km)', 'PERSONNEL', NULL, @admin_id, @admin_id);
SET @p3 = LAST_INSERT_ID();
INSERT INTO unit_product_histories (unit_product_id, type, name, category, exclusivity_group, created_by, updated_by)
VALUES (@p3, 'ONSITE_SUPPORT', '현장지원(지방·중거리, 100~250km)', 'PERSONNEL', NULL, @admin_id, @admin_id);
INSERT INTO unit_product_price_periods
    (unit_product_id, currency_code, supply_price, sale_price, tax_code, active, effective_from, effective_to, created_by, updated_by)
VALUES (@p3, 'KRW', 250000, 320000, 'KR_VAT_STANDARD', TRUE, @today, NULL, @admin_id, @admin_id);
INSERT INTO unit_product_price_period_histories
    (unit_product_id, currency_code, supply_price, sale_price, tax_code, active, effective_from, effective_to, removed, created_by, updated_by)
VALUES (@p3, 'KRW', 250000, 320000, 'KR_VAT_STANDARD', TRUE, @today, NULL, FALSE, @admin_id, @admin_id);

-- 4. 현장지원(지방·원거리, 250km 초과)
INSERT INTO unit_products (type, name, category, exclusivity_group, created_by, updated_by)
VALUES ('ONSITE_SUPPORT', '현장지원(지방·원거리, 250km~)', 'PERSONNEL', NULL, @admin_id, @admin_id);
SET @p4 = LAST_INSERT_ID();
INSERT INTO unit_product_histories (unit_product_id, type, name, category, exclusivity_group, created_by, updated_by)
VALUES (@p4, 'ONSITE_SUPPORT', '현장지원(지방·원거리, 250km~)', 'PERSONNEL', NULL, @admin_id, @admin_id);
INSERT INTO unit_product_price_periods
    (unit_product_id, currency_code, supply_price, sale_price, tax_code, active, effective_from, effective_to, created_by, updated_by)
VALUES (@p4, 'KRW', 320000, 400000, 'KR_VAT_STANDARD', TRUE, @today, NULL, @admin_id, @admin_id);
INSERT INTO unit_product_price_period_histories
    (unit_product_id, currency_code, supply_price, sale_price, tax_code, active, effective_from, effective_to, removed, created_by, updated_by)
VALUES (@p4, 'KRW', 320000, 400000, 'KR_VAT_STANDARD', TRUE, @today, NULL, FALSE, @admin_id, @admin_id);

-- 5. 온라인지원 (원격이라 지역 구분 없음, 1개 상품만)
INSERT INTO unit_products (type, name, category, exclusivity_group, created_by, updated_by)
VALUES ('ONLINE_SUPPORT', '온라인지원', 'PERSONNEL', NULL, @admin_id, @admin_id);
SET @p5 = LAST_INSERT_ID();
INSERT INTO unit_product_histories (unit_product_id, type, name, category, exclusivity_group, created_by, updated_by)
VALUES (@p5, 'ONLINE_SUPPORT', '온라인지원', 'PERSONNEL', NULL, @admin_id, @admin_id);
INSERT INTO unit_product_price_periods
    (unit_product_id, currency_code, supply_price, sale_price, tax_code, active, effective_from, effective_to, created_by, updated_by)
VALUES (@p5, 'KRW', 30000, 50000, 'KR_VAT_STANDARD', TRUE, @today, NULL, @admin_id, @admin_id);
INSERT INTO unit_product_price_period_histories
    (unit_product_id, currency_code, supply_price, sale_price, tax_code, active, effective_from, effective_to, removed, created_by, updated_by)
VALUES (@p5, 'KRW', 30000, 50000, 'KR_VAT_STANDARD', TRUE, @today, NULL, FALSE, @admin_id, @admin_id);

-- 플랫폼 관리자 감사 로그 — PlatformAdminAuditLogRecorder.record와 같은 형태(요청 경로는
-- 이 스크립트가 HTTP 요청이 아니라 남기지 않는다).
INSERT INTO platform_admin_audit_log (admin_user_id, action, target_user_id, organization_id, detail)
VALUES
    (@admin_id, 'CREATE_UNIT_PRODUCT', NULL, NULL, CONCAT('unitProductId=', @p1, ', type=ONSITE_SUPPORT (seed script)')),
    (@admin_id, 'CREATE_UNIT_PRODUCT', NULL, NULL, CONCAT('unitProductId=', @p2, ', type=ONSITE_SUPPORT (seed script)')),
    (@admin_id, 'CREATE_UNIT_PRODUCT', NULL, NULL, CONCAT('unitProductId=', @p3, ', type=ONSITE_SUPPORT (seed script)')),
    (@admin_id, 'CREATE_UNIT_PRODUCT', NULL, NULL, CONCAT('unitProductId=', @p4, ', type=ONSITE_SUPPORT (seed script)')),
    (@admin_id, 'CREATE_UNIT_PRODUCT', NULL, NULL, CONCAT('unitProductId=', @p5, ', type=ONLINE_SUPPORT (seed script)'));
