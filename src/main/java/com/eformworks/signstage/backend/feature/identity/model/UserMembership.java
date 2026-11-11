package com.eformworks.signstage.backend.feature.identity.model;

import java.time.LocalDateTime;

/**
 * 사용자의 조직 소속 한 건. identity가 organization Entity를 직접 참조하지 않도록
 * {@link com.eformworks.signstage.backend.feature.identity.port.UserMembershipPort}가 이 값으로 돌려준다.
 */
public record UserMembership(
        Long organizationId,
        String organizationName,
        String organizationCode,
        String role,
        String status,
        LocalDateTime joinedAt
) {
}
