-- V202609071000/V202609071100 적용 후 실행하는 읽기 전용 검증 SQL(MySQL 8.0).

-- 1. 존재하는 entitlement별 예상 프리셋 수. 기대: SIGNER_FIELD_ZOOM=4,
-- ALL_SIGNED_FIREWORKS=5. entitlement가 아직 없는 빈 DB에서는 해당 code가 출력되지 않는다.
SELECT ofe.code,
       COUNT(definition.id) AS definition_count,
       CASE ofe.code
           WHEN 'SIGNER_FIELD_ZOOM' THEN 4
           WHEN 'ALL_SIGNED_FIREWORKS' THEN 5
       END AS expected_count
FROM optional_features ofe
LEFT JOIN ceremony_effect_definitions definition
  ON definition.required_optional_feature_id = ofe.id
WHERE ofe.code IN ('SIGNER_FIELD_ZOOM', 'ALL_SIGNED_FIREWORKS')
GROUP BY ofe.id, ofe.code
HAVING definition_count <> expected_count;

-- 2. 프리셋의 분류·renderer·수동 실행·순서 계약 위반. 기대: 0행.
WITH expected AS (
    SELECT 'HIGHLIGHT' code, 'SIGNATURE_COMPLETED' trigger_type,
           'projector-signature-highlight' renderer_key, 0 manually_triggerable, 10 display_order
    UNION ALL SELECT 'PULSE', 'SIGNATURE_COMPLETED', 'projector-signature-pulse', 0, 20
    UNION ALL SELECT 'RIPPLE', 'SIGNATURE_COMPLETED', 'projector-signature-ripple', 0, 30
    UNION ALL SELECT 'FLASH', 'SIGNATURE_COMPLETED', 'projector-signature-flash', 0, 40
    UNION ALL SELECT 'CONFETTI', 'ALL_SIGNATURES_COMPLETED', 'projector-confetti', 1, 10
    UNION ALL SELECT 'FIREWORKS', 'ALL_SIGNATURES_COMPLETED', 'projector-fireworks', 1, 20
    UNION ALL SELECT 'SPARKLE', 'ALL_SIGNATURES_COMPLETED', 'projector-sparkle', 1, 30
    UNION ALL SELECT 'AIR_SHOT', 'ALL_SIGNATURES_COMPLETED', 'projector-air-shot', 1, 40
    UNION ALL SELECT 'PAPER_REEL', 'ALL_SIGNATURES_COMPLETED', 'projector-paper-reel', 1, 50
)
SELECT definition.id, definition.code
FROM ceremony_effect_definitions definition
JOIN expected ON expected.code = definition.code
WHERE definition.target_type <> 'PROJECTOR'
   OR definition.trigger_type <> expected.trigger_type
   OR definition.renderer_key <> expected.renderer_key
   OR definition.manually_triggerable <> expected.manually_triggerable
   OR definition.display_order <> expected.display_order;

-- 3. 행사 설정의 효과 분류 또는 entitlement 불일치. 기대: 0행.
SELECT setting.event_id, setting.target_type, setting.trigger_type, definition.code
FROM ceremony_event_effect_settings setting
JOIN ceremony_effect_definitions definition ON definition.id = setting.effect_id
LEFT JOIN ceremony_event_optional_features applied
  ON applied.ceremony_event_id = setting.event_id
 AND applied.optional_feature_id = definition.required_optional_feature_id
WHERE definition.target_type <> setting.target_type
   OR definition.trigger_type <> setting.trigger_type
   OR applied.id IS NULL;

-- 4. 최신 감사 로그 투영과 현재 상태가 다른 행. 기대: 0행.
WITH ranked AS (
    SELECT cel.id,
           cel.ceremony_event_id,
           COALESCE(
               cel.target_signer_id,
               CASE WHEN cel.actor_type = 'SIGNER' THEN cel.actor_id ELSE NULL END
           ) AS target_signer_id,
           cel.event_action,
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
),
expected AS (
    SELECT ceremony_event_id AS event_id,
           target_signer_id AS signer_id,
           CASE event_action
               WHEN 'SIGNATURE_COMPLETE' THEN 'COMPLETED'
               WHEN 'SIGNATURE_REPLACE' THEN 'SIGNING'
               ELSE 'PENDING'
           END AS signature_status,
           CASE WHEN event_action = 'SIGNATURE_COMPLETE' THEN id ELSE NULL END AS completion_log_id
    FROM ranked
    WHERE state_rank = 1
)
SELECT expected.event_id, expected.signer_id, expected.signature_status,
       state.signature_status AS actual_status
FROM expected
LEFT JOIN ceremony_event_signer_states state
  ON state.event_id = expected.event_id
 AND state.signer_id = expected.signer_id
WHERE state.event_id IS NULL
   OR state.signature_status <> expected.signature_status
   OR NOT (state.last_completion_log_id <=> expected.completion_log_id);

-- 5. 종료됐는데 자동 실행 이력이 비어 있는 이벤트. 기대: 0행.
SELECT id, status
FROM ceremony_events
WHERE status IN ('FINISHED', 'FORCE_FINISHED')
  AND auto_celebration_triggered_at IS NULL;

-- 6. entitlement backfill이 빠졌거나 잘못 생긴 설정. 기대: 두 query 모두 0행.
SELECT ceof.ceremony_event_id, ofe.code
FROM ceremony_event_optional_features ceof
JOIN optional_features ofe ON ofe.id = ceof.optional_feature_id
LEFT JOIN ceremony_event_effect_settings setting
  ON setting.event_id = ceof.ceremony_event_id
 AND setting.target_type = 'PROJECTOR'
 AND setting.trigger_type = CASE ofe.code
     WHEN 'SIGNER_FIELD_ZOOM' THEN 'SIGNATURE_COMPLETED'
     ELSE 'ALL_SIGNATURES_COMPLETED'
 END
WHERE ofe.code IN ('SIGNER_FIELD_ZOOM', 'ALL_SIGNED_FIREWORKS')
  AND setting.event_id IS NULL;

SELECT setting.event_id, definition.code
FROM ceremony_event_effect_settings setting
JOIN ceremony_effect_definitions definition ON definition.id = setting.effect_id
LEFT JOIN ceremony_event_optional_features applied
  ON applied.ceremony_event_id = setting.event_id
 AND applied.optional_feature_id = definition.required_optional_feature_id
WHERE applied.id IS NULL;
