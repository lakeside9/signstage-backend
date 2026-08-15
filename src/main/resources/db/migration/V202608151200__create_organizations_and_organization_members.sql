-- organizations, organization_members 최초 생성.
-- 스키마는 signstage-docs business/user-organization-design.md 3.2절을 그대로 따른다.
-- (초대(organization_invitations), 플랫폼 관리자 감사 로그(platform_admin_audit_log),
--  ceremonies.organization_id 연동은 이번 최소 구현 범위 밖이라 아직 만들지 않는다.)

CREATE TABLE organizations (
    id                              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name                            VARCHAR(100) NOT NULL,
    code                            VARCHAR(50) NOT NULL UNIQUE,
    status                          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    default_locale                  VARCHAR(10) NOT NULL DEFAULT 'ko-KR',
    business_registration_number    VARCHAR(20) NULL,
    business_registration_file_key  VARCHAR(500) NULL,
    representative_name             VARCHAR(100) NULL,
    address                         VARCHAR(500) NULL,
    contact_email                   VARCHAR(255) NULL,
    contact_phone                   VARCHAR(20) NULL,
    created_by                      BIGINT NOT NULL,
    updated_by                      BIGINT NULL,
    created_at                      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE organization_members (
    id               BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id  BIGINT NOT NULL,
    user_id          BIGINT NOT NULL,
    role             VARCHAR(20) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    invited_at       TIMESTAMP NULL,
    joined_at        TIMESTAMP NULL,
    created_by       BIGINT NOT NULL,
    updated_by       BIGINT NULL,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_org_member UNIQUE (organization_id, user_id),
    CONSTRAINT fk_om_organization FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_om_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_om_user_id ON organization_members (user_id);
CREATE INDEX idx_om_org_status ON organization_members (organization_id, status);
