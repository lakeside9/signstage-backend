-- users.email을 NULL 허용으로 완화한다.
--
-- signstage-docs business/user-organization-design.md 8.2절 "회원 탈퇴(계정 삭제)"의
-- PII 마스킹 규칙은 탈퇴 시 email을 NULL로 비운다. 지금까지는 email이 NOT NULL이라
-- 이 규칙을 구현할 수 없었다. UNIQUE 제약은 유지한다 — InnoDB의 UNIQUE 인덱스는
-- NULL을 서로 다른 값으로 취급해 여러 행이 NULL이어도 충돌하지 않는다.
ALTER TABLE users MODIFY COLUMN email VARCHAR(255) NULL;
