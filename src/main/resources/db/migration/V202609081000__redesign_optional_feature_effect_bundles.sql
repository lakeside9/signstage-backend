-- 선택옵션(과금 카탈로그) 재설계 — signstage-docs
-- business/ceremony-event-effect-implementation-tasks.md, 2026-09-08 결정.
--
-- 지금까지는 CeremonyEffectDefinition(효과 하나)이 OptionalFeature(선택옵션) 하나를
-- required_optional_feature_id 단일 FK로 가리켰다. 이제는 "이벤트 효과 묶음"(예: "3종",
-- "5종") 상품을 관리자가 코드 배포 없이 계속 추가할 수 있어야 하고, 같은 효과가 여러 묶음에
-- 겹쳐 들어갈 수도 있어야 한다(자유 N:N). 그래서 이 FK를 없애고 대신
-- ceremony_effect_definition_options N:N 매핑 테이블로 옮긴다 — capacity_addons.capacity_type이
-- 유일 제약 없이 여러 상품 행에 공유되는 것과 같은 패턴이다. optional_features.code도 같은
-- 이유로 uq_of_code 유일 제약을 없앤다: EVENT_EFFECT_BUNDLE 코드 하나를 여러 묶음 상품 행이
-- 공유해야 한다.
--
-- 기존에 등록된 두 선택옵션(SIGNER_FIELD_ZOOM "서명 하이라이트", ALL_SIGNED_FIREWORKS "폭죽
-- 효과")은 각각 프로젝터 효과 4종/5종을 여는 별개 상품이었다. 이 둘을 하나로 통합한다 —
-- ALL_SIGNED_FIREWORKS였던 행을 이름만 바꿔 재사용해 EVENT_EFFECT_BUNDLE "프로젝터 화면
-- 이벤트 효과 전체"로 만들고(코드/카탈로그 행 자체를 지우지 않는 기존 관례), 9종 전체를
-- 이 행에 연결한다. SIGNER_FIELD_ZOOM 행은 비활성화(active=0)만 하고 지우지 않는다 — 이미
-- 이 옵션만 구매한 조직이 있을 수 있어 FK 무결성이 걸린다.
--
-- 통합 후에도 "서명 하이라이트만 구매했던 행사/조직"이 자동으로 전체 프로젝터 효과를 쓸 수
-- 있어야 한다는 요구사항 때문에, billing_plan_optional_features/ceremony_optional_feature_purchases/
-- ceremony_event_optional_features 세 테이블에서 SIGNER_FIELD_ZOOM을 참조하던 행을 통합
-- 상품 id로 옮긴다(이미 통합 상품도 같이 갖고 있던 행이면 중복이라 지운다 — 세 테이블 모두
-- (부모 id, optional_feature_id) 유일 제약이 있다). ceremony_plan_history_optional_features는
-- 건드리지 않는다 — 플랜 확정 시점 스냅샷은 나중 카탈로그 변경으로 소급 수정하지 않는다는
-- 기존 원칙(ceremony-billing-options-review.md) 그대로다.

-- 1) optional_features.code 유일 제약 해제 — EVENT_EFFECT_BUNDLE 묶음 상품이 여러 행을 공유한다.
ALTER TABLE optional_features
    DROP INDEX uq_of_code;

-- 2) 효과 정의 ↔ 선택옵션 N:N 매핑 테이블.
CREATE TABLE ceremony_effect_definition_options (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    effect_definition_id  BIGINT NOT NULL,
    optional_feature_id   BIGINT NOT NULL,
    created_by            BIGINT NOT NULL,
    updated_by            BIGINT NULL,
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_cedo_definition_feature UNIQUE (effect_definition_id, optional_feature_id),
    CONSTRAINT fk_cedo_definition FOREIGN KEY (effect_definition_id)
        REFERENCES ceremony_effect_definitions (id),
    CONSTRAINT fk_cedo_feature FOREIGN KEY (optional_feature_id)
        REFERENCES optional_features (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_cedo_feature ON ceremony_effect_definition_options (optional_feature_id);

-- 3) 기존 단일 FK를 매핑 행으로 백필한다(구성이 바뀌기 전 원본 그대로).
INSERT INTO ceremony_effect_definition_options
    (effect_definition_id, optional_feature_id, created_by, updated_by, created_at, updated_at)
SELECT id, required_optional_feature_id, created_by, updated_by, created_at, updated_at
FROM ceremony_effect_definitions;

-- 4) SIGNER_FIELD_ZOOM이 열던 4종 효과도 통합 상품(ALL_SIGNED_FIREWORKS였던 행)에 추가로
--    연결한다 — 두 상품을 하나로 합치는 핵심 단계. 그 뒤 SIGNER_FIELD_ZOOM을 향한 원래
--    매핑은 지운다(그 상품 자체를 비활성화할 것이라 남겨봐야 참조할 곳이 없다).
INSERT INTO ceremony_effect_definition_options
    (effect_definition_id, optional_feature_id, created_by, updated_by, created_at, updated_at)
SELECT cedo.effect_definition_id,
       fireworks.id,
       cedo.created_by,
       cedo.updated_by,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM ceremony_effect_definition_options cedo
JOIN optional_features signer_zoom ON signer_zoom.id = cedo.optional_feature_id AND signer_zoom.code = 'SIGNER_FIELD_ZOOM'
JOIN optional_features fireworks ON fireworks.code = 'ALL_SIGNED_FIREWORKS';

DELETE cedo FROM ceremony_effect_definition_options cedo
JOIN optional_features signer_zoom ON signer_zoom.id = cedo.optional_feature_id AND signer_zoom.code = 'SIGNER_FIELD_ZOOM';

-- 5) 이제 매핑 테이블이 진실 소스이므로 옛 단일 FK 컬럼을 없앤다.
ALTER TABLE ceremony_effect_definitions
    DROP FOREIGN KEY fk_ced_required_feature,
    DROP INDEX idx_ced_required_feature,
    DROP COLUMN required_optional_feature_id;

-- 6) ALL_SIGNED_FIREWORKS였던 카탈로그 행을 통합 이벤트 효과 묶음 상품으로 이름만 바꿔
--    재사용한다("기존 '폭죽 효과' 행을 이름만 바꿔 재사용" 결정). 변경 이력도 정상 수정
--    흐름과 같은 모양으로 한 행 남긴다(OptionalFeatureService#updateOptionalFeature 참고).
UPDATE optional_features
SET code = 'EVENT_EFFECT_BUNDLE',
    name = '프로젝터 화면 이벤트 효과 전체'
WHERE code = 'ALL_SIGNED_FIREWORKS';

INSERT INTO optional_feature_histories
    (optional_feature_id, code, name, currency_code, supply_price, sale_price,
     discount_type, discount_value, tax_code, active, projector_effect, exclusivity_group,
     created_by, updated_by)
SELECT id, code, name, currency_code, supply_price, sale_price,
       discount_type, discount_value, tax_code, active, projector_effect, exclusivity_group,
       updated_by, updated_by
FROM optional_features
WHERE code = 'EVENT_EFFECT_BUNDLE';

-- 7) SIGNER_FIELD_ZOOM 행은 지우지 않고 비활성화만 한다(이미 이 상품을 참조하는 이력/FK가
--    있을 수 있다 — TABLET_RENTAL 등과 같은 기존 관례).
UPDATE optional_features
SET active = 0
WHERE code = 'SIGNER_FIELD_ZOOM';

INSERT INTO optional_feature_histories
    (optional_feature_id, code, name, currency_code, supply_price, sale_price,
     discount_type, discount_value, tax_code, active, projector_effect, exclusivity_group,
     created_by, updated_by)
SELECT id, code, name, currency_code, supply_price, sale_price,
       discount_type, discount_value, tax_code, active, projector_effect, exclusivity_group,
       updated_by, updated_by
FROM optional_features
WHERE code = 'SIGNER_FIELD_ZOOM';

-- 8) 기존에 SIGNER_FIELD_ZOOM만 구매/적용해 둔 플랜/조직/행사 행을 통합 상품으로 옮긴다.
--    통합 상품 행을 이미 같이 갖고 있던 경우는(부모 id, optional_feature_id) 유일 제약에
--    걸리니 그 중복 행은 지운다.
DELETE bpof FROM billing_plan_optional_features bpof
JOIN optional_features signer_zoom ON signer_zoom.id = bpof.optional_feature_id AND signer_zoom.code = 'SIGNER_FIELD_ZOOM'
JOIN optional_features fireworks ON fireworks.code = 'EVENT_EFFECT_BUNDLE'
JOIN billing_plan_optional_features already
     ON already.billing_plan_id = bpof.billing_plan_id AND already.optional_feature_id = fireworks.id;

UPDATE billing_plan_optional_features bpof
JOIN optional_features signer_zoom ON signer_zoom.id = bpof.optional_feature_id AND signer_zoom.code = 'SIGNER_FIELD_ZOOM'
JOIN optional_features fireworks ON fireworks.code = 'EVENT_EFFECT_BUNDLE'
SET bpof.optional_feature_id = fireworks.id;

DELETE cofp FROM ceremony_optional_feature_purchases cofp
JOIN optional_features signer_zoom ON signer_zoom.id = cofp.optional_feature_id AND signer_zoom.code = 'SIGNER_FIELD_ZOOM'
JOIN optional_features fireworks ON fireworks.code = 'EVENT_EFFECT_BUNDLE'
JOIN ceremony_optional_feature_purchases already
     ON already.ceremony_id = cofp.ceremony_id AND already.optional_feature_id = fireworks.id;

UPDATE ceremony_optional_feature_purchases cofp
JOIN optional_features signer_zoom ON signer_zoom.id = cofp.optional_feature_id AND signer_zoom.code = 'SIGNER_FIELD_ZOOM'
JOIN optional_features fireworks ON fireworks.code = 'EVENT_EFFECT_BUNDLE'
SET cofp.optional_feature_id = fireworks.id;

DELETE ceof FROM ceremony_event_optional_features ceof
JOIN optional_features signer_zoom ON signer_zoom.id = ceof.optional_feature_id AND signer_zoom.code = 'SIGNER_FIELD_ZOOM'
JOIN optional_features fireworks ON fireworks.code = 'EVENT_EFFECT_BUNDLE'
JOIN ceremony_event_optional_features already
     ON already.ceremony_event_id = ceof.ceremony_event_id AND already.optional_feature_id = fireworks.id;

UPDATE ceremony_event_optional_features ceof
JOIN optional_features signer_zoom ON signer_zoom.id = ceof.optional_feature_id AND signer_zoom.code = 'SIGNER_FIELD_ZOOM'
JOIN optional_features fireworks ON fireworks.code = 'EVENT_EFFECT_BUNDLE'
SET ceof.optional_feature_id = fireworks.id;
