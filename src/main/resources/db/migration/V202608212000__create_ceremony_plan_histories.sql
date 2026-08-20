-- 행사(Ceremony) 플랜 변경 이력. append-only — 수정/삭제하지 않고 계속 쌓기만 한다.
-- signstage-docs business/ceremony-plan-confirmation-review.md 3.4절 참고.
--
-- Ceremony 생성 시(최초 플랜 선택)와 플랜 변경 시(DRAFT 상태에서만 가능)마다 한 행씩 추가된다.
-- plan_* 컬럼은 그 변경 시점의 플랜 이름/가격/한도 스냅샷이다 — 카탈로그(billing_plans)가
-- 나중에 바뀌어도 이미 쌓인 이력 행은 바뀌지 않는다. "누가/언제"는 BaseEntity의
-- created_by/created_at으로 충분해 별도 컬럼을 두지 않는다.
--
-- 이 테이블은 organization-event-discount-pricing-review.md 6.1절이 제안했던
-- "Ceremony에 플랜 스냅샷 컬럼 9개 직접 추가"를 대체한다 — 확정 시점의 가장 마지막 행이
-- 그 스냅샷 역할을 한다.

CREATE TABLE ceremony_plan_histories (
    id                     BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ceremony_id            BIGINT NOT NULL,
    billing_plan_id        BIGINT NOT NULL,
    plan_name              VARCHAR(100) NOT NULL,
    plan_supply_price      DECIMAL(12,2) NOT NULL,
    plan_sale_price        DECIMAL(12,2) NOT NULL,
    plan_discount_type     VARCHAR(20) NOT NULL,
    plan_discount_value    DECIMAL(12,2) NOT NULL,
    plan_max_signers       INT NOT NULL,
    plan_max_templates     INT NOT NULL,
    plan_max_test_events   INT NOT NULL,
    plan_max_main_events   INT NOT NULL,
    created_by             BIGINT NOT NULL,
    updated_by             BIGINT NULL,
    created_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_cph_ceremony FOREIGN KEY (ceremony_id) REFERENCES ceremonies (id),
    CONSTRAINT fk_cph_plan FOREIGN KEY (billing_plan_id) REFERENCES billing_plans (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_cph_ceremony ON ceremony_plan_histories (ceremony_id);
