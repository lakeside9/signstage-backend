-- 효과 정의는 현재 과금 카탈로그의 entitlement 행에 연결한다. 신규 빈 DB처럼 대상 상품이
-- 아직 없으면 0행을 삽입하고 Flyway는 성공한다. 상품 생성 후에는 플랫폼 효과 관리 API에서
-- 정의를 등록해야 하며, 배포 대상 DB에는 preflight에서 두 상품이 각각 한 행인지 확인한다.

INSERT INTO ceremony_effect_definitions
    (code, target_type, trigger_type, required_optional_feature_id,
     display_name, description, renderer_key,
     is_enabled, is_user_visible, manually_triggerable, display_order,
     created_by, updated_by)
SELECT seed.code,
       seed.target_type,
       seed.trigger_type,
       ofe.id,
       seed.display_name,
       seed.description,
       seed.renderer_key,
       1,
       1,
       seed.manually_triggerable,
       seed.display_order,
       ofe.created_by,
       ofe.updated_by
FROM (
    SELECT 'HIGHLIGHT' AS code, 'PROJECTOR' AS target_type,
           'SIGNATURE_COMPLETED' AS trigger_type, 'SIGNER_FIELD_ZOOM' AS feature_code,
           '서명란 강조' AS display_name, '서명이 완료된 서명란을 강조합니다.' AS description,
           'projector-signature-highlight' AS renderer_key, 0 AS manually_triggerable,
           10 AS display_order
    UNION ALL
    SELECT 'PULSE', 'PROJECTOR', 'SIGNATURE_COMPLETED', 'SIGNER_FIELD_ZOOM',
           '서명란 펄스', '서명란을 부드럽게 확대하며 반복 강조합니다.',
           'projector-signature-pulse', 0, 20
    UNION ALL
    SELECT 'RIPPLE', 'PROJECTOR', 'SIGNATURE_COMPLETED', 'SIGNER_FIELD_ZOOM',
           '서명란 물결', '서명란 바깥으로 퍼지는 물결 효과를 표시합니다.',
           'projector-signature-ripple', 0, 30
    UNION ALL
    -- legacy frontend에는 있었지만 legacy Flyway seed에는 빠졌던 항목이다.
    SELECT 'FLASH', 'PROJECTOR', 'SIGNATURE_COMPLETED', 'SIGNER_FIELD_ZOOM',
           '서명란 플래시', '서명이 완료된 서명란을 짧고 선명하게 강조합니다.',
           'projector-signature-flash', 0, 40
    UNION ALL
    SELECT 'CONFETTI', 'PROJECTOR', 'ALL_SIGNATURES_COMPLETED', 'ALL_SIGNED_FIREWORKS',
           '축하 색종이', '화면 위에서 아래로 자연스럽게 떨어지는 색종이 효과를 표시합니다.',
           'projector-confetti', 1, 10
    UNION ALL
    SELECT 'FIREWORKS', 'PROJECTOR', 'ALL_SIGNATURES_COMPLETED', 'ALL_SIGNED_FIREWORKS',
           '축하 불꽃놀이', '화면 전체에 불꽃놀이 효과를 표시합니다.',
           'projector-fireworks', 1, 20
    UNION ALL
    SELECT 'SPARKLE', 'PROJECTOR', 'ALL_SIGNATURES_COMPLETED', 'ALL_SIGNED_FIREWORKS',
           '축하 별빛', '화면 전체에 반짝이는 별빛 효과를 표시합니다.',
           'projector-sparkle', 1, 30
    UNION ALL
    SELECT 'AIR_SHOT', 'PROJECTOR', 'ALL_SIGNATURES_COMPLETED', 'ALL_SIGNED_FIREWORKS',
           '에어샷 꽃가루', '화면 하단에서 종이 꽃가루를 높이 쏘아 올린 뒤 자연스럽게 떨어뜨립니다.',
           'projector-air-shot', 1, 40
    UNION ALL
    SELECT 'PAPER_REEL', 'PROJECTOR', 'ALL_SIGNATURES_COMPLETED', 'ALL_SIGNED_FIREWORKS',
           '페이퍼 릴', '화면 좌우 하단에서 긴 종이 릴이 포물선으로 발사되어 떨어지는 연출을 표시합니다.',
           'projector-paper-reel', 1, 50
) seed
JOIN optional_features ofe ON ofe.code = seed.feature_code;

-- append-only 감사 로그에서 event/signer별 최신 상태를 결정적으로 투영한다.
-- created_at 동률이면 더 큰 log id가 최신이다. target signer가 같은 Ceremony에 속하는 로그만 쓴다.
INSERT INTO ceremony_event_signer_states
    (event_id, signer_id, signature_status, last_completion_log_id, completed_at,
     created_by, updated_by, created_at, updated_at)
SELECT latest.ceremony_event_id,
       latest.target_signer_id,
       CASE latest.event_action
           WHEN 'SIGNATURE_COMPLETE' THEN 'COMPLETED'
           WHEN 'SIGNATURE_REPLACE' THEN 'SIGNING'
           ELSE 'PENDING'
       END,
       CASE WHEN latest.event_action = 'SIGNATURE_COMPLETE' THEN latest.id ELSE NULL END,
       CASE WHEN latest.event_action = 'SIGNATURE_COMPLETE' THEN latest.created_at ELSE NULL END,
       latest.first_created_by,
       latest.created_by,
       latest.first_created_at,
       latest.created_at
FROM (
    SELECT cel.id,
           cel.ceremony_event_id,
           COALESCE(
               cel.target_signer_id,
               CASE WHEN cel.actor_type = 'SIGNER' THEN cel.actor_id ELSE NULL END
           ) AS target_signer_id,
           cel.event_action,
           cel.created_by,
           cel.created_at,
           FIRST_VALUE(cel.created_by) OVER (
               PARTITION BY cel.ceremony_event_id,
                   COALESCE(cel.target_signer_id, CASE WHEN cel.actor_type = 'SIGNER' THEN cel.actor_id ELSE NULL END)
               ORDER BY cel.created_at ASC, cel.id ASC
           ) AS first_created_by,
           MIN(cel.created_at) OVER (
               PARTITION BY cel.ceremony_event_id,
                   COALESCE(cel.target_signer_id, CASE WHEN cel.actor_type = 'SIGNER' THEN cel.actor_id ELSE NULL END)
           ) AS first_created_at,
           ROW_NUMBER() OVER (
               PARTITION BY cel.ceremony_event_id,
                   COALESCE(cel.target_signer_id, CASE WHEN cel.actor_type = 'SIGNER' THEN cel.actor_id ELSE NULL END)
               ORDER BY cel.created_at DESC, cel.id DESC
           ) AS state_rank
    FROM ceremony_event_logs cel
    JOIN ceremony_events ce ON ce.id = cel.ceremony_event_id
    JOIN signers signer
      ON signer.id = COALESCE(
          cel.target_signer_id,
          CASE WHEN cel.actor_type = 'SIGNER' THEN cel.actor_id ELSE NULL END
      )
     AND signer.ceremony_id = ce.ceremony_id
    WHERE cel.event_action IN ('SIGNATURE_COMPLETE', 'SIGNATURE_REPLACE', 'SIGNATURE_CLEAR')
) latest
WHERE latest.state_rank = 1;

-- 이미 종료된 이벤트와, 배포 시점에 필수 signer가 전원 완료된 STARTED 이벤트는 과거 효과를
-- 배포 후 소급 실행하지 않도록 자동 실행 자격을 소비한 상태로 이관한다.
UPDATE ceremony_events
SET auto_celebration_triggered_at = CURRENT_TIMESTAMP
WHERE status IN ('FINISHED', 'FORCE_FINISHED')
  AND auto_celebration_triggered_at IS NULL;

UPDATE ceremony_events ce
JOIN (
    SELECT required_signer.event_id
    FROM (
        SELECT DISTINCT ct.ceremony_event_id AS event_id, tf.signer_id
        FROM ceremony_templates ct
        JOIN template_fields tf ON tf.template_id = ct.template_id
        WHERE ct.document_role IN ('CONTRACT', 'EXHIBITION')
          AND tf.is_required = 1
          AND tf.signer_id IS NOT NULL
    ) required_signer
    LEFT JOIN ceremony_event_signer_states signer_state
      ON signer_state.event_id = required_signer.event_id
     AND signer_state.signer_id = required_signer.signer_id
    GROUP BY required_signer.event_id
    HAVING COUNT(*) > 0
       AND SUM(CASE WHEN signer_state.signature_status = 'COMPLETED' THEN 1 ELSE 0 END) = COUNT(*)
) completed ON completed.event_id = ce.id
SET ce.auto_celebration_triggered_at = CURRENT_TIMESTAMP
WHERE ce.status = 'STARTED'
  AND ce.auto_celebration_triggered_at IS NULL;

-- 기존 적용 entitlement를 프리셋 선택으로 이관한다. 전체 완료의 확정 기본값은 FIREWORKS다.
INSERT INTO ceremony_event_effect_settings
    (event_id, target_type, trigger_type, effect_id, runtime_enabled,
     created_by, updated_by, created_at, updated_at)
SELECT ceof.ceremony_event_id,
       definition.target_type,
       definition.trigger_type,
       definition.id,
       1,
       ceof.created_by,
       ceof.updated_by,
       ceof.created_at,
       ceof.updated_at
FROM ceremony_event_optional_features ceof
JOIN optional_features ofe ON ofe.id = ceof.optional_feature_id
JOIN ceremony_effect_definitions definition
  ON definition.code = 'HIGHLIGHT'
 AND definition.required_optional_feature_id = ofe.id
WHERE ofe.code = 'SIGNER_FIELD_ZOOM';

INSERT INTO ceremony_event_effect_settings
    (event_id, target_type, trigger_type, effect_id, runtime_enabled,
     created_by, updated_by, created_at, updated_at)
SELECT ceof.ceremony_event_id,
       definition.target_type,
       definition.trigger_type,
       definition.id,
       1,
       ceof.created_by,
       ceof.updated_by,
       ceof.created_at,
       ceof.updated_at
FROM ceremony_event_optional_features ceof
JOIN optional_features ofe ON ofe.id = ceof.optional_feature_id
JOIN ceremony_effect_definitions definition
  ON definition.code = 'FIREWORKS'
 AND definition.required_optional_feature_id = ofe.id
WHERE ofe.code = 'ALL_SIGNED_FIREWORKS';
