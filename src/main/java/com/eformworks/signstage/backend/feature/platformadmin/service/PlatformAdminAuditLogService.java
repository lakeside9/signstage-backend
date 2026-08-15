package com.eformworks.signstage.backend.feature.platformadmin.service;

import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.identity.repository.entity.User;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import com.eformworks.signstage.backend.feature.organization.repository.entity.Organization;
import com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminAuditLogDto;
import com.eformworks.signstage.backend.feature.platformadmin.repository.PlatformAdminAuditLogRepository;
import com.eformworks.signstage.backend.feature.platformadmin.repository.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.platformadmin.repository.entity.PlatformAdminAuditLog;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플랫폼 관리자 감사 로그 조회. PLATFORM_SUPPORT 이상 전체가 볼 수 있다(SecurityConfig의
 * /api/platform-admin/** URL 게이트로 충분 — 이 서비스에서 등급을 더 좁히지 않는다,
 * signstage-docs business/user-organization-design.md 7.1절 "PLATFORM_SUPPORT: 조회만").
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlatformAdminAuditLogService {

    private final PlatformAdminAuditLogRepository platformAdminAuditLogRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;

    public Page<PlatformAdminAuditLogDto.Response.AuditLogEntry> findAuditLogs(PlatformAdminAction action, Pageable pageable) {
        Page<PlatformAdminAuditLog> logs = action != null
                ? platformAdminAuditLogRepository.findAllByAction(action, pageable)
                : platformAdminAuditLogRepository.findAll(pageable);

        List<PlatformAdminAuditLog> content = logs.getContent();
        Map<Long, String> loginIdsByUserId = resolveLoginIds(content);
        Map<Long, String> namesByOrganizationId = resolveOrganizationNames(content);

        return logs.map(log -> toEntry(log, loginIdsByUserId, namesByOrganizationId));
    }

    private Map<Long, String> resolveLoginIds(List<PlatformAdminAuditLog> logs) {
        Set<Long> userIds = new HashSet<>();
        for (PlatformAdminAuditLog log : logs) {
            userIds.add(log.getAdminUserId());
            if (log.getTargetUserId() != null) {
                userIds.add(log.getTargetUserId());
            }
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getLoginId));
    }

    private Map<Long, String> resolveOrganizationNames(List<PlatformAdminAuditLog> logs) {
        Set<Long> organizationIds = logs.stream()
                .map(PlatformAdminAuditLog::getOrganizationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return organizationRepository.findAllById(organizationIds).stream()
                .collect(Collectors.toMap(Organization::getId, Organization::getName));
    }

    private PlatformAdminAuditLogDto.Response.AuditLogEntry toEntry(
            PlatformAdminAuditLog log,
            Map<Long, String> loginIdsByUserId,
            Map<Long, String> namesByOrganizationId
    ) {
        return new PlatformAdminAuditLogDto.Response.AuditLogEntry(
                log.getId(),
                log.getAdminUserId(),
                loginIdsByUserId.get(log.getAdminUserId()),
                log.getAction().name(),
                log.getTargetUserId(),
                log.getTargetUserId() != null ? loginIdsByUserId.get(log.getTargetUserId()) : null,
                log.getOrganizationId(),
                log.getOrganizationId() != null ? namesByOrganizationId.get(log.getOrganizationId()) : null,
                log.getDetail(),
                log.getRequestPath(),
                log.getCreatedAt()
        );
    }
}
