-- 선택옵션 카탈로그에 "프로젝터 화면 효과 옵션인지"와 "배타 그룹"을 추가한다.
--
-- projector_effect: 이 옵션이 프로젝터(전시용) 화면에 실제로 효과를 내는 종류인지 표시한다.
-- 지금 있는 두 옵션(서명 하이라이트/폭죽)은 전부 해당하므로 기본값 TRUE로 백필한다. 앞으로
-- 화상참석처럼 프로젝터와 무관한 옵션이 늘어나면 그 상품만 등록 시 FALSE로 두면 된다 — 코드
-- 변경 없이 카탈로그 등록만으로 구분한다.
--
-- exclusivity_group: 같은 값을 가진 선택옵션들은 한 CeremonyEvent에 동시에 적용할 수 없다
-- (CeremonyEventService#applyOptionalFeatures가 강제). enum이 아니라 자유 문자열 라벨이라
-- (Signer.role_code/template_fields.role_code와 같은 패턴), 예를 들어 "서명 하이라이트
-- 파란색"/"빨간색" 두 상품을 등록할 때 같은 그룹값만 매기면 코드 변경 없이 배타 관계를
-- 구성할 수 있다. NULL이면 다른 옵션과 배타 관계가 없다 — 기존 두 옵션은 NULL로 시작한다
-- (지금 동작을 그대로 유지).

ALTER TABLE optional_features
    ADD COLUMN projector_effect  BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN exclusivity_group VARCHAR(50) NULL;

ALTER TABLE optional_feature_histories
    ADD COLUMN projector_effect  BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN exclusivity_group VARCHAR(50) NULL;
