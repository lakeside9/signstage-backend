-- 행사 마스터에 주관 기관/부서, 담당자 정보를 추가한다. 전부 선택 입력이라 NULL 허용이다
-- (기존 행은 전부 NULL로 남는다) — 행사 수정 화면에서만 채운다(설명과 같은 패턴).
ALTER TABLE ceremonies
    ADD COLUMN organizing_institution VARCHAR(200) NULL,
    ADD COLUMN organizing_department VARCHAR(200) NULL,
    ADD COLUMN contact_name VARCHAR(100) NULL,
    ADD COLUMN contact_title VARCHAR(100) NULL,
    ADD COLUMN contact_phone VARCHAR(20) NULL,
    ADD COLUMN contact_email VARCHAR(255) NULL;
