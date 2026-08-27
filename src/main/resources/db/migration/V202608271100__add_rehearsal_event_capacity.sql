-- REHEARSAL 구분을 TEST와 같은 용량 한도 버킷에서 분리해 자기 카탈로그 한도를 갖게 한다
-- (legacy(~/Works/eform/source/signstage) 2026-08-27 포팅 직후 사용자 피드백 반영 — 처음엔
-- REHEARSAL이 TEST_EVENTS 버킷을 공유하도록 임시로 판단했었다).
--
-- max_rehearsal_events는 기존 행 기준으로 max_test_events와 같은 값으로 채운다 — 카탈로그
-- 관리자가 나중에 화면에서 각 플랜별로 다시 조정할 수 있다. 4.9절과 같은 원칙으로 NOT NULL,
-- 무제한 sentinel 없음.

ALTER TABLE billing_plans
    ADD COLUMN max_rehearsal_events INT NULL AFTER max_test_events;
UPDATE billing_plans SET max_rehearsal_events = max_test_events;
ALTER TABLE billing_plans
    MODIFY COLUMN max_rehearsal_events INT NOT NULL;

ALTER TABLE billing_plan_histories
    ADD COLUMN max_rehearsal_events INT NULL AFTER max_test_events;
UPDATE billing_plan_histories SET max_rehearsal_events = max_test_events;
ALTER TABLE billing_plan_histories
    MODIFY COLUMN max_rehearsal_events INT NOT NULL;

ALTER TABLE ceremony_plan_histories
    ADD COLUMN plan_max_rehearsal_events INT NULL AFTER plan_max_test_events;
UPDATE ceremony_plan_histories SET plan_max_rehearsal_events = plan_max_test_events;
ALTER TABLE ceremony_plan_histories
    MODIFY COLUMN plan_max_rehearsal_events INT NOT NULL;
