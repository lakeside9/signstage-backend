-- 행사 이벤트 효과 migration 사전 점검용 읽기 전용 SQL.
-- 변경문을 포함하지 않는다. MySQL 8.0, 현재 SignStage schema 기준이다.
-- 모든 결과가 기대 조건을 만족해야 효과 schema migration을 적용할 수 있다.

-- PRE-01-1. optional_features.code는 애플리케이션에서 유일값으로 취급한다.
-- 기대 결과: 0행.
SELECT code, COUNT(*) AS duplicate_count, GROUP_CONCAT(id ORDER BY id) AS ids
FROM optional_features
GROUP BY code
HAVING COUNT(*) > 1;

-- PRE-01-2. 1차 효과 entitlement 상품을 식별한다.
-- 기대 결과: 각 code당 정확히 1행. active=false여도 기존 행사 적용 권리는 유지할 수 있지만,
-- 신규 판매/선택 정책은 별도로 확인해야 한다.
SELECT id, code, name, active, projector_effect, exclusivity_group
FROM optional_features
WHERE code IN ('SIGNER_FIELD_ZOOM', 'ALL_SIGNED_FIREWORKS')
ORDER BY code, id;

-- PRE-01-3. ceremony_event_optional_features의 고아 참조를 점검한다.
-- FK가 정상이라면 기대 결과는 0행이다.
SELECT ceof.id,
       ceof.ceremony_event_id,
       ceof.optional_feature_id,
       CASE WHEN ce.id IS NULL THEN 'MISSING_EVENT' END AS event_problem,
       CASE WHEN ofe.id IS NULL THEN 'MISSING_OPTIONAL_FEATURE' END AS feature_problem
FROM ceremony_event_optional_features ceof
LEFT JOIN ceremony_events ce ON ce.id = ceof.ceremony_event_id
LEFT JOIN optional_features ofe ON ofe.id = ceof.optional_feature_id
WHERE ce.id IS NULL OR ofe.id IS NULL;

-- PRE-01-4. effect backfill 대상 수를 entitlement별로 확인한다.
SELECT ofe.code, COUNT(DISTINCT ceof.ceremony_event_id) AS applied_event_count
FROM ceremony_event_optional_features ceof
JOIN optional_features ofe ON ofe.id = ceof.optional_feature_id
WHERE ofe.code IN ('SIGNER_FIELD_ZOOM', 'ALL_SIGNED_FIREWORKS')
GROUP BY ofe.code
ORDER BY ofe.code;

-- PRE-01-5. 동일 event/signer/created_at에 현재 상태 관련 로그가 여러 건인지 확인한다.
-- 결과가 있어도 id DESC를 보조 정렬로 사용하면 backfill은 결정적이다. 현재 애플리케이션의
-- created_at DESC 단독 조회는 이 경우 비결정적이므로 BE-STATE에서 함께 수정해야 한다.
SELECT ceremony_event_id,
       target_signer_id,
       created_at,
       COUNT(*) AS tied_log_count,
       GROUP_CONCAT(CONCAT(id, ':', event_action) ORDER BY id DESC) AS logs
FROM ceremony_event_logs
WHERE target_signer_id IS NOT NULL
  AND event_action IN ('SIGNATURE_COMPLETE', 'SIGNATURE_REPLACE', 'SIGNATURE_CLEAR')
GROUP BY ceremony_event_id, target_signer_id, created_at
HAVING COUNT(*) > 1
ORDER BY tied_log_count DESC, ceremony_event_id, target_signer_id, created_at;

-- PRE-01-6. target_signer_id가 이벤트와 다른 Ceremony의 Signer를 가리키는지 확인한다.
-- 기대 결과: 0행.
SELECT cel.id AS log_id,
       cel.ceremony_event_id,
       ce.ceremony_id AS event_ceremony_id,
       cel.target_signer_id,
       signer.ceremony_id AS signer_ceremony_id
FROM ceremony_event_logs cel
JOIN ceremony_events ce ON ce.id = cel.ceremony_event_id
JOIN signers signer ON signer.id = cel.target_signer_id
WHERE cel.target_signer_id IS NOT NULL
  AND ce.ceremony_id <> signer.ceremony_id;

-- PRE-01-6a. target_signer_id 도입 전 로그 중 signer를 복원할 수 없는 상태 변경을 찾는다.
-- SIGNER actor의 COMPLETE/CLEAR는 actor_id로 복원 가능하다. 아래 결과는 별도 데이터 정리가
-- 필요한 ADMIN REPLACE 또는 잘못된 actor 참조이므로 기대 결과는 0행이다.
SELECT cel.id,
       cel.ceremony_event_id,
       cel.actor_type,
       cel.actor_id,
       cel.event_action
FROM ceremony_event_logs cel
JOIN ceremony_events ce ON ce.id = cel.ceremony_event_id
LEFT JOIN signers signer
  ON signer.id = CASE WHEN cel.actor_type = 'SIGNER' THEN cel.actor_id ELSE NULL END
 AND signer.ceremony_id = ce.ceremony_id
WHERE cel.event_action IN ('SIGNATURE_COMPLETE', 'SIGNATURE_REPLACE', 'SIGNATURE_CLEAR')
  AND cel.target_signer_id IS NULL
  AND signer.id IS NULL;

-- PRE-01-7. 최신 로그를 id까지 포함해 결정적으로 계산한다.
-- 아래 CTE는 DB-04 상태 backfill query와 같은 기준으로 사용한다.
WITH ranked_signature_state_logs AS (
    SELECT cel.id,
           cel.ceremony_event_id,
           cel.target_signer_id,
           cel.event_action,
           cel.created_at,
           ROW_NUMBER() OVER (
               PARTITION BY cel.ceremony_event_id, cel.target_signer_id
               ORDER BY cel.created_at DESC, cel.id DESC
           ) AS state_rank
    FROM ceremony_event_logs cel
    WHERE cel.target_signer_id IS NOT NULL
      AND cel.event_action IN ('SIGNATURE_COMPLETE', 'SIGNATURE_REPLACE', 'SIGNATURE_CLEAR')
)
SELECT ceremony_event_id,
       target_signer_id,
       id AS latest_log_id,
       event_action AS latest_action,
       created_at AS latest_action_at
FROM ranked_signature_state_logs
WHERE state_rank = 1
ORDER BY ceremony_event_id, target_signer_id;

-- PRE-01-8. STARTED 이벤트 중 현재 필수 서명자가 전원 완료된 이벤트를 찾는다.
-- 현재 CeremonyEventService의 종료 기준과 같이 CONTRACT/EXHIBITION 매핑의 필수 signer
-- 합집합을 사용한다. 이 결과는 auto_celebration_triggered_at을 migration 시각으로 채워
-- 과거 전체 완료 효과가 배포 후 소급 실행되지 않게 할 대상이다.
WITH required_signers AS (
    SELECT DISTINCT ct.ceremony_event_id AS event_id, tf.signer_id
    FROM ceremony_templates ct
    JOIN template_fields tf ON tf.template_id = ct.template_id
    WHERE ct.document_role IN ('CONTRACT', 'EXHIBITION')
      AND tf.is_required = 1
      AND tf.signer_id IS NOT NULL
),
ranked_signature_state_logs AS (
    SELECT cel.id,
           cel.ceremony_event_id,
           cel.target_signer_id,
           cel.event_action,
           ROW_NUMBER() OVER (
               PARTITION BY cel.ceremony_event_id, cel.target_signer_id
               ORDER BY cel.created_at DESC, cel.id DESC
           ) AS state_rank
    FROM ceremony_event_logs cel
    WHERE cel.target_signer_id IS NOT NULL
      AND cel.event_action IN ('SIGNATURE_COMPLETE', 'SIGNATURE_REPLACE', 'SIGNATURE_CLEAR')
),
latest_signature_states AS (
    SELECT ceremony_event_id AS event_id,
           target_signer_id AS signer_id,
           event_action
    FROM ranked_signature_state_logs
    WHERE state_rank = 1
),
started_event_progress AS (
    SELECT ce.id AS event_id,
           COUNT(rs.signer_id) AS required_signer_count,
           SUM(CASE WHEN lss.event_action = 'SIGNATURE_COMPLETE' THEN 1 ELSE 0 END) AS completed_signer_count
    FROM ceremony_events ce
    JOIN required_signers rs ON rs.event_id = ce.id
    LEFT JOIN latest_signature_states lss
      ON lss.event_id = rs.event_id
     AND lss.signer_id = rs.signer_id
    WHERE ce.status = 'STARTED'
    GROUP BY ce.id
)
SELECT event_id, required_signer_count, completed_signer_count
FROM started_event_progress
WHERE required_signer_count > 0
  AND required_signer_count = completed_signer_count
ORDER BY event_id;

-- PRE-01-9. 필수 signer가 한 명도 없는 STARTED 이벤트를 별도로 확인한다.
-- 이 이벤트들은 자동 전체 완료 대상으로 간주하지 않는다.
WITH required_signers AS (
    SELECT DISTINCT ct.ceremony_event_id AS event_id, tf.signer_id
    FROM ceremony_templates ct
    JOIN template_fields tf ON tf.template_id = ct.template_id
    WHERE ct.document_role IN ('CONTRACT', 'EXHIBITION')
      AND tf.is_required = 1
      AND tf.signer_id IS NOT NULL
)
SELECT ce.id AS event_id, ce.name
FROM ceremony_events ce
LEFT JOIN required_signers rs ON rs.event_id = ce.id
WHERE ce.status = 'STARTED'
GROUP BY ce.id, ce.name
HAVING COUNT(rs.signer_id) = 0
ORDER BY ce.id;
