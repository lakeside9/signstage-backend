-- organization_creation_requests 최초 생성.
--
-- signstage-docs business/organization-creation-approval-review.md의 결정 사항을 그대로 따른다.
-- 조직은 더 이상 사용자가 즉시 생성하지 않는다 — 사용자는 이 테이블에 "요청"만 남기고,
-- 플랫폼 관리자(PLATFORM_OPS 이상)가 승인해야 실제로 organizations/organization_members가
-- 생긴다(같은 문서 3.1/3.2절). 관리자가 요청 없이 조직을 직접 만드는 기존 경로도 내부적으로
-- 이 테이블에 APPROVED 상태 행을 함께 남긴다 — "이 조직이 어떤 경위로 만들어졌는지"를
-- 항상 이 테이블 하나로 추적하기 위해서다.
--
-- organization_id는 승인 시에만 채워진다(요청 자체는 조직 코드를 다루지 않는다 — 3.3절,
-- 코드는 승인 시점에 관리자가 정한다).
--
-- reviewed_by는 admin_user_id류 행위자 참조와 같은 이유로 FK를 걸지 않는다
-- (platform_admin_audit_log와 같은 패턴). requested_by/organization_id는 이 테이블의
-- 핵심 업무 관계라 FK를 건다(organization_members/organization_invitations과 같은 패턴).

CREATE TABLE organization_creation_requests (
    id                  BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    requested_by        BIGINT NOT NULL,
    organization_name   VARCHAR(100) NOT NULL,
    note                VARCHAR(500) NULL,               -- 부가설명(선택) — 심사 근거를 요구하는 "사유"가 아니다
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING / APPROVED / REJECTED / CANCELLED
    rejection_reason    VARCHAR(500) NULL,
    reviewed_by         BIGINT NULL,                      -- 행위자 참조. FK 없음
    reviewed_at         TIMESTAMP NULL,
    organization_id     BIGINT NULL,                       -- 승인되어 실제 생성된 조직
    created_by          BIGINT NULL,
    updated_by          BIGINT NULL,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ocr_requested_by FOREIGN KEY (requested_by) REFERENCES users (id),
    CONSTRAINT fk_ocr_organization FOREIGN KEY (organization_id) REFERENCES organizations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_ocr_requested_by_status ON organization_creation_requests (requested_by, status);
CREATE INDEX idx_ocr_status ON organization_creation_requests (status);
