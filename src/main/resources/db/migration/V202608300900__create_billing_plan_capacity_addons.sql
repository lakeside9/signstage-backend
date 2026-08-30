-- 플랜에서 "구매 가능한" 용량 추가구매 상품 큐레이션(안 A, 2026-08-30). signstage-docs
-- business/optional-feature-display-scope-and-plan-capacity-addon-review.md 4.1/5장 참고.
--
-- billing_plan_optional_features와 겉모습은 같지만 의미가 다르다 — "무료 포함"이 아니라
-- "이 플랜을 쓰는 행사가 이 상품을 구매 후보로 고를 수 있다"는 허용 목록이다. 이 목록에
-- 없는 capacity_addon은 그 플랜의 행사에서 구매할 수 없다.

CREATE TABLE billing_plan_capacity_addons (
    id                  BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    billing_plan_id     BIGINT NOT NULL,
    capacity_addon_id   BIGINT NOT NULL,
    created_by          BIGINT NOT NULL,
    updated_by          BIGINT NULL,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_bpca_plan_addon UNIQUE (billing_plan_id, capacity_addon_id),
    CONSTRAINT fk_bpca_plan FOREIGN KEY (billing_plan_id) REFERENCES billing_plans (id),
    CONSTRAINT fk_bpca_addon FOREIGN KEY (capacity_addon_id) REFERENCES capacity_addons (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_bpca_plan ON billing_plan_capacity_addons (billing_plan_id);

-- 하위호환: 이 기능 배포 전에는 활성 상태인 capacity_addon이면 플랜과 무관하게 전부 구매
-- 가능했다(제한 없음). 그 동작을 그대로 유지하기 위해, 배포 시점 기존 모든 플랜에 기존 모든
-- 활성 용량 추가구매 상품을 기본 연결해둔다 — signstage-docs
-- business/optional-feature-display-scope-and-plan-capacity-addon-review.md §6 항목 3(하위호환).
-- created_by는 시스템 백필임을 구분할 별도 계정이 없어 그 플랜의 최초 생성자를 그대로 물려받는다
-- (V202608212400 백필과 같은 관례).
INSERT INTO billing_plan_capacity_addons
    (billing_plan_id, capacity_addon_id, created_by, created_at, updated_at)
SELECT bp.id, ca.id, bp.created_by, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM billing_plans bp
JOIN capacity_addons ca ON ca.active = TRUE;

-- CeremonyPlanHistory 스냅샷 시점에 그 플랜에서 구매 가능했던 용량 추가구매 상품 매핑
-- (ceremony_plan_history_optional_features와 같은 패턴, 5.5절 — 구매 검증을 스냅샷 기준으로
-- 하기 위해 필요하다).
CREATE TABLE ceremony_plan_history_capacity_addons (
    id                        BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ceremony_plan_history_id  BIGINT NOT NULL,
    capacity_addon_id         BIGINT NOT NULL,
    created_by                BIGINT NOT NULL,
    updated_by                BIGINT NULL,
    created_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_cphca_history FOREIGN KEY (ceremony_plan_history_id) REFERENCES ceremony_plan_histories (id),
    CONSTRAINT fk_cphca_addon FOREIGN KEY (capacity_addon_id) REFERENCES capacity_addons (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_cphca_history ON ceremony_plan_history_capacity_addons (ceremony_plan_history_id);

-- 이미 쌓인 ceremony_plan_histories 행(이번 배포 전까지 만들어진 것)도 위와 같은 이유로
-- 배포 시점 현재 billing_plan_capacity_addons 구성(바로 위에서 전부 연결해둔 값)으로
-- 최선 근사 백필한다(V202608212400과 같은 원칙) — 이렇게 해야 이미 진행 중인 행사도 이번
-- 배포 직후 "구매 가능한 상품이 하나도 없음"으로 갑자기 막히지 않는다.
INSERT INTO ceremony_plan_history_capacity_addons
    (ceremony_plan_history_id, capacity_addon_id, created_by, created_at, updated_at)
SELECT cph.id, bpca.capacity_addon_id, cph.created_by, cph.created_at, cph.updated_at
FROM ceremony_plan_histories cph
JOIN billing_plan_capacity_addons bpca ON bpca.billing_plan_id = cph.billing_plan_id;
