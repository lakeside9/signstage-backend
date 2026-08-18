-- 행사 마스터(Ceremony) 설명. 생성 시에는 title만 받고, 이 컬럼은 행사 수정 화면에서만
-- 채운다 — 그래서 nullable이다(기존 행/신규 생성 모두 처음엔 비어 있다).

ALTER TABLE ceremonies
    ADD COLUMN description VARCHAR(1000) NULL;
