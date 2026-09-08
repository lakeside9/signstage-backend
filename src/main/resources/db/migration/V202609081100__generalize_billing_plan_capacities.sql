-- BillingPlan의 한도(서명자/템플릿/테스트행사/리허설행사/본행사) 5종 고정 컬럼을
-- 용량종류별 일반화 테이블로 재설계한다 — signstage-docs
-- business/billing-catalog-zero-base-schema-redesign-review.md 결정(2026-09-08, 항목 B).
--
-- 예전엔 이 5종만 BillingPlan에 고정 컬럼이 있었고 TABLETS는 대응 컬럼이 없어 "플랜 기본
-- 포함"을 표현할 방법이 없었다(billing-catalog-operations-review.md 6.4절). 이제
-- billing_plan_capacities(capacity_type, included_amount)로 일반화해, 새 용량 종류가
-- 플랜 기본 포함 대상이 되어도 스키마 변경 없이 카탈로그 등록만으로 대응할 수 있다 —
-- CapacityType.isPlanIncludable()이 그 종류를 규정한다(현재는 TABLETS만 제외).
--
-- 카탈로그(billing_plans/billing_plan_histories)와 Ceremony 스냅샷(ceremony_plan_histories)
-- 양쪽 모두 같은 방식으로 일반화한다. 개발 단계라 기존 데이터 보존이 중요하지 않다는
-- 전제이지만, 로컬 개발 DB가 마이그레이션 직후에도 계속 정상 동작하도록 기존 5개 컬럼
-- 값을 새 테이블로 백필한 뒤 원본 컬럼을 없앤다(이 프로젝트가 반복해온 "배포 시점 값으로
-- 최선 근사 백필" 관례와 동일).

CREATE TABLE billing_plan_capacities (
    id                  BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    billing_plan_id     BIGINT NOT NULL,
    capacity_type       VARCHAR(20) NOT NULL,
    included_amount     INT NOT NULL,
    created_by          BIGINT NOT NULL,
    updated_by          BIGINT NULL,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_bpc_plan_type UNIQUE (billing_plan_id, capacity_type),
    CONSTRAINT fk_bpc_plan FOREIGN KEY (billing_plan_id) REFERENCES billing_plans (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_bpc_plan ON billing_plan_capacities (billing_plan_id);

INSERT INTO billing_plan_capacities (billing_plan_id, capacity_type, included_amount, created_by, created_at, updated_at)
SELECT id, 'SIGNERS', max_signers, created_by, created_at, updated_at FROM billing_plans
UNION ALL
SELECT id, 'TEMPLATES', max_templates, created_by, created_at, updated_at FROM billing_plans
UNION ALL
SELECT id, 'TEST_EVENTS', max_test_events, created_by, created_at, updated_at FROM billing_plans
UNION ALL
SELECT id, 'REHEARSAL_EVENTS', max_rehearsal_events, created_by, created_at, updated_at FROM billing_plans
UNION ALL
SELECT id, 'MAIN_EVENTS', max_main_events, created_by, created_at, updated_at FROM billing_plans;

ALTER TABLE billing_plans
    DROP COLUMN max_signers,
    DROP COLUMN max_templates,
    DROP COLUMN max_test_events,
    DROP COLUMN max_rehearsal_events,
    DROP COLUMN max_main_events;

-- billing_plan_histories(카탈로그 값/사용여부 변경 이력)도 같은 방식으로 일반화한다.
CREATE TABLE billing_plan_history_capacities (
    id                         BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    billing_plan_history_id    BIGINT NOT NULL,
    capacity_type              VARCHAR(20) NOT NULL,
    included_amount            INT NOT NULL,
    created_by                 BIGINT NOT NULL,
    updated_by                 BIGINT NULL,
    created_at                 TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                 TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_bphc_history FOREIGN KEY (billing_plan_history_id) REFERENCES billing_plan_histories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_bphc_history ON billing_plan_history_capacities (billing_plan_history_id);

INSERT INTO billing_plan_history_capacities (billing_plan_history_id, capacity_type, included_amount, created_by, created_at, updated_at)
SELECT id, 'SIGNERS', max_signers, created_by, created_at, updated_at FROM billing_plan_histories
UNION ALL
SELECT id, 'TEMPLATES', max_templates, created_by, created_at, updated_at FROM billing_plan_histories
UNION ALL
SELECT id, 'TEST_EVENTS', max_test_events, created_by, created_at, updated_at FROM billing_plan_histories
UNION ALL
SELECT id, 'REHEARSAL_EVENTS', max_rehearsal_events, created_by, created_at, updated_at FROM billing_plan_histories
UNION ALL
SELECT id, 'MAIN_EVENTS', max_main_events, created_by, created_at, updated_at FROM billing_plan_histories;

ALTER TABLE billing_plan_histories
    DROP COLUMN max_signers,
    DROP COLUMN max_templates,
    DROP COLUMN max_test_events,
    DROP COLUMN max_rehearsal_events,
    DROP COLUMN max_main_events;

-- ceremony_plan_histories(Ceremony가 그 순간 플랜에서 스냅샷한 한도)도 같은 방식으로
-- 일반화한다 — ceremony_plan_history_capacity_addons와 같은 패턴(CeremonyService가
-- calculateEffectiveCapacity에서 조회).
CREATE TABLE ceremony_plan_history_capacities (
    id                         BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ceremony_plan_history_id   BIGINT NOT NULL,
    capacity_type              VARCHAR(20) NOT NULL,
    included_amount            INT NOT NULL,
    created_by                 BIGINT NOT NULL,
    updated_by                 BIGINT NULL,
    created_at                 TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                 TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_cphc_history FOREIGN KEY (ceremony_plan_history_id) REFERENCES ceremony_plan_histories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_cphc_history ON ceremony_plan_history_capacities (ceremony_plan_history_id);

INSERT INTO ceremony_plan_history_capacities (ceremony_plan_history_id, capacity_type, included_amount, created_by, created_at, updated_at)
SELECT id, 'SIGNERS', plan_max_signers, created_by, created_at, updated_at FROM ceremony_plan_histories
UNION ALL
SELECT id, 'TEMPLATES', plan_max_templates, created_by, created_at, updated_at FROM ceremony_plan_histories
UNION ALL
SELECT id, 'TEST_EVENTS', plan_max_test_events, created_by, created_at, updated_at FROM ceremony_plan_histories
UNION ALL
SELECT id, 'REHEARSAL_EVENTS', plan_max_rehearsal_events, created_by, created_at, updated_at FROM ceremony_plan_histories
UNION ALL
SELECT id, 'MAIN_EVENTS', plan_max_main_events, created_by, created_at, updated_at FROM ceremony_plan_histories;

ALTER TABLE ceremony_plan_histories
    DROP COLUMN plan_max_signers,
    DROP COLUMN plan_max_templates,
    DROP COLUMN plan_max_test_events,
    DROP COLUMN plan_max_rehearsal_events,
    DROP COLUMN plan_max_main_events;
