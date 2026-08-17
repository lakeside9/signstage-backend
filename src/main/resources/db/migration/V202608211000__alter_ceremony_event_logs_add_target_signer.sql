-- SIGNATURE_COMPLETE/SIGNATURE_CLEAR/SIGNATURE_REPLACE 세 행위 중 "이 서명자에 대해 가장
-- 최근 로그가 뭔가"를 조회하는 데 쓴다. actor_id는 폴리모픽(ADMIN이면 User.id, SIGNER면
-- Signer.id)이라 그대로는 못 쓴다 — SIGNATURE_REPLACE는 관리자가 실행하지만(actor_type=ADMIN,
-- 감사 기록은 그대로 관리자를 가리켜야 한다) "영향받은 서명자"는 별도 컬럼으로 남긴다.
-- SIGNATURE_COMPLETE/CLEAR는 서명자 본인이 실행하므로 actor_id와 값이 같지만, 조회를
-- actor_id/actor_type 조합에 의존하지 않게 하기 위해 셋 다 이 컬럼을 채운다.

ALTER TABLE ceremony_event_logs
    ADD COLUMN target_signer_id BIGINT NULL,
    ADD CONSTRAINT fk_cel_target_signer FOREIGN KEY (target_signer_id) REFERENCES signers (id);

CREATE INDEX idx_cel_target_signer ON ceremony_event_logs (ceremony_event_id, target_signer_id);
