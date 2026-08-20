-- CeremonyPlanHistory 스냅샷 시점에 그 플랜에 포함돼 있던 선택옵션 매핑.
-- signstage-docs business/ceremony-billing-options-review.md 9장 후속 참고 —
-- BillingPlan의 선택옵션 구성(optionalFeatureIds)이 이번에 수정 가능해지면서, 카탈로그
-- 관리자가 나중에 옵션을 빼도 이미 확정/진행 중인 행사가 영향받지 않도록 스냅샷으로 보호한다.
--
-- Ceremony 생성 시(최초 플랜 선택)와 플랜 변경 시(DRAFT 상태에서만 가능)마다, 그 순간
-- billing_plan_optional_features에 있던 행을 그대로 복사해 append-only로 쌓는다.

CREATE TABLE ceremony_plan_history_optional_features (
    id                        BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ceremony_plan_history_id  BIGINT NOT NULL,
    optional_feature_id       BIGINT NOT NULL,
    created_by                BIGINT NOT NULL,
    updated_by                BIGINT NULL,
    created_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_cphof_history FOREIGN KEY (ceremony_plan_history_id) REFERENCES ceremony_plan_histories (id),
    CONSTRAINT fk_cphof_feature FOREIGN KEY (optional_feature_id) REFERENCES optional_features (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_cphof_history ON ceremony_plan_history_optional_features (ceremony_plan_history_id);

-- 이미 쌓인 ceremony_plan_histories 행(이번 배포 전까지 만들어진 것)은 원래 그 순간의 구성을
-- 복원할 방법이 없으므로, 배포 시점 현재 billing_plan_optional_features 구성으로 최선 근사
-- 백필한다(ceremony-billing-options-review.md 4.8과 같은 예외 원칙). created_by는 시스템
-- 백필임을 구분하기 위해 그 히스토리 행을 만든 사용자를 그대로 물려받는다.
INSERT INTO ceremony_plan_history_optional_features
    (ceremony_plan_history_id, optional_feature_id, created_by, created_at, updated_at)
SELECT cph.id, bpof.optional_feature_id, cph.created_by, cph.created_at, cph.updated_at
FROM ceremony_plan_histories cph
JOIN billing_plan_optional_features bpof ON bpof.billing_plan_id = cph.billing_plan_id;
