-- 견적서 공급자 정보와 회사 인감(2026-11-13). 견적 공통 문구마다 두고, 견적은 저장할 때 쓴 문구(quote_template_id)의 값을 보여준다.
-- 인감 이미지는 파일 저장소(DocumentStoragePort)에 두고 관리자 인증 API로만 내려준다 — 프론트 정적 파일로 두면 누구나 받을 수 있다.
ALTER TABLE quote_templates
    ADD COLUMN supplier_name            VARCHAR(100) NULL AFTER footer_notice,
    ADD COLUMN supplier_representative  VARCHAR(50)  NULL AFTER supplier_name,
    ADD COLUMN supplier_business_number VARCHAR(20)  NULL AFTER supplier_representative,
    ADD COLUMN supplier_address         VARCHAR(300) NULL AFTER supplier_business_number,
    ADD COLUMN supplier_phone           VARCHAR(50)  NULL AFTER supplier_address,
    ADD COLUMN seal_storage_key         VARCHAR(500) NULL AFTER supplier_phone,
    ADD COLUMN seal_content_type        VARCHAR(50)  NULL AFTER seal_storage_key;
