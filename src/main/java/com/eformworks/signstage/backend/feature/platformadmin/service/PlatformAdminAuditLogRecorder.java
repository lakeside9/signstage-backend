package com.eformworks.signstage.backend.feature.platformadmin.service;

import com.eformworks.signstage.backend.feature.platformadmin.repository.PlatformAdminAuditLogRepository;
import com.eformworks.signstage.backend.feature.platformadmin.repository.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.platformadmin.repository.entity.PlatformAdminAuditLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 플랫폼 관리자 제어 API가 성공적으로 끝날 때마다 한 번씩 호출해 감사 로그를 남긴다
 * (signstage-docs business/user-organization-design.md 7.4절).
 *
 * <p>{@code LoginAttemptRecorder}와 달리 별도 트랜잭션(REQUIRES_NEW)으로 분리하지 않는다 —
 * 여기서 기록하는 건 "이미 성공한 행위"라서, 호출한 서비스 메서드의 트랜잭션이 롤백되면
 * (예: 이후 로직에서 실패) 그 행위 자체가 없던 일이 되므로 감사 로그도 함께 사라지는 게 맞다.
 */
@Component
@RequiredArgsConstructor
public class PlatformAdminAuditLogRecorder {

    private final PlatformAdminAuditLogRepository platformAdminAuditLogRepository;

    /**
     * @param targetUserId    행위 대상이 사용자면 그 id, 아니면 null
     * @param organizationId  행위 대상/범위가 조직이면 그 id, 아니면 null
     * @param detail          예: "status: PENDING -> ACTIVE" (사람이 읽을 수 있는 부가 정보)
     */
    public void record(Long adminUserId, PlatformAdminAction action, Long targetUserId, Long organizationId, String detail) {
        platformAdminAuditLogRepository.save(PlatformAdminAuditLog.builder()
                .adminUserId(adminUserId)
                .action(action)
                .targetUserId(targetUserId)
                .organizationId(organizationId)
                .detail(detail)
                .requestPath(currentRequestPath())
                .build());
    }

    private String currentRequestPath() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest().getRequestURI();
        }
        return null;
    }
}
