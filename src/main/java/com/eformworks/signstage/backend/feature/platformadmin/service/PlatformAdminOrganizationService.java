package com.eformworks.signstage.backend.feature.platformadmin.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import com.eformworks.signstage.backend.feature.organization.repository.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.repository.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.repository.entity.OrganizationStatus;
import com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminOrganizationDto;
import com.eformworks.signstage.backend.feature.platformadmin.error.PlatformAdminErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플랫폼 관리자의 조직 조회를 구현한다(조회 전용 — 상태 변경/멤버 강제 조정은 이번 범위 밖,
 * signstage-docs business/platform-admin-member-management.md 참고). 일반 조직 API
 * (feature.organization)는 호출자가 그 조직의 ACTIVE 멤버여야 하지만, 이 서비스는
 * 조직 스코핑을 우회해 전체 조직을 조회한다(signstage-docs
 * business/user-organization-design.md 6장) — URL 레벨({@code SecurityConfig})에서
 * platform_role 보유자만 도달하도록 이미 걸러져 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlatformAdminOrganizationService {

    private final OrganizationRepository organizationRepository;
    private final MemberRepository memberRepository;

    public Page<PlatformAdminOrganizationDto.Response.OrganizationSummary> findOrganizations(
            String name,
            String code,
            OrganizationStatus status,
            Pageable pageable
    ) {
        Page<Organization> organizations = organizationRepository.search(
                blankToNull(name),
                blankToNull(code),
                status,
                pageable
        );
        return organizations.map(this::toSummary);
    }

    public PlatformAdminOrganizationDto.Response.OrganizationSummary retrieveOrganization(Long organizationId) {
        return toSummary(findOrganizationOrThrow(organizationId));
    }

    private Organization findOrganizationOrThrow(Long organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApplicationException(PlatformAdminErrorCode.ORGANIZATION_NOT_FOUND));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private PlatformAdminOrganizationDto.Response.OrganizationSummary toSummary(Organization organization) {
        long activeMemberCount = memberRepository.countByOrganizationIdAndStatus(organization.getId(), MemberStatus.ACTIVE);
        return new PlatformAdminOrganizationDto.Response.OrganizationSummary(
                organization.getId(),
                organization.getName(),
                organization.getCode(),
                organization.getStatus().name(),
                organization.getDefaultLocale(),
                activeMemberCount,
                organization.getCreatedAt()
        );
    }
}
