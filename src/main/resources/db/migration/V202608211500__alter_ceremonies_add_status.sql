-- 행사 마스터(Ceremony) 완료 상태. 이 Ceremony 아래 본행사(MAIN)가 전부 FINISHED + 결과 PDF
-- 생성까지 끝나면 COMPLETED로 자동 전이한다(CeremonyResultService#generateResults).
-- 기존 행에는 소급 적용하지 않는다 — DEFAULT로 전부 IN_PROGRESS로 시작한다(billing_plan_id의
-- 4.8절 "기존 행사 예외"와 같은 원칙).

ALTER TABLE ceremonies
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS';
