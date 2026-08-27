-- 서명자/문서 양식/하위 행사 표시 순서(display_order) 추가. legacy(~/Works/eform/source/signstage)
-- 2026-08-27 포팅(signstage-docs business/ceremony-feature-migration-review.md 최신 라운드 참고).
--
-- display_order: 목록 화면의 위/아래 이동 버튼이 전체 배열을 다시 인덱싱해 저장하는 정수다.
-- 새로 만든 행은 그 시점의 형제 개수를 서비스 계층이 계산해 넣으므로 항상 목록 맨 끝에
-- 붙는다 — 컬럼 자체의 기본값 0은 그 계산을 거치지 않는 예전 코드 경로를 위한 안전망일 뿐이다.
-- (ceremony_id, display_order, id) 인덱스로 정렬 조회
-- (findAllByCeremonyIdOrderByDisplayOrderAscIdAsc)를 뒷받침한다.
--
-- ceremony_events.event_type/status는 코드 레벨 enum + VARCHAR 컬럼이라(4.4절) 하위 행사
-- 리허설 라이프사이클(CeremonyEventType.REHEARSAL, CeremonyEventStatus.FORCE_FINISHED 추가)은
-- 애플리케이션 enum에만 값을 추가하면 되고 스키마 변경이 필요 없다.

ALTER TABLE signers
    ADD COLUMN display_order INT NOT NULL DEFAULT 0 AFTER access_key;

CREATE INDEX idx_signer_ceremony_display_order ON signers (ceremony_id, display_order, id);

ALTER TABLE templates
    ADD COLUMN display_order INT NOT NULL DEFAULT 0 AFTER status;

CREATE INDEX idx_template_ceremony_display_order ON templates (ceremony_id, display_order, id);

ALTER TABLE ceremony_events
    ADD COLUMN display_order INT NOT NULL DEFAULT 0 AFTER description;

CREATE INDEX idx_ce_ceremony_display_order ON ceremony_events (ceremony_id, display_order, id);
