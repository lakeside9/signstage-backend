package com.eformworks.signstage.backend.feature.platformadmin.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.organization.error.OrganizationErrorCode;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.entity.OrganizationStatus;
import com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminOrganizationDto;
import com.eformworks.signstage.backend.feature.platformadmin.error.PlatformAdminErrorCode;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAction;
import java.time.LocalDateTime;
import java.util.Set;
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

    private static final Set<String> ORGANIZATION_CONTROL_ALLOWED_ROLES = Set.of("PLATFORM_OPS", "PLATFORM_SUPER");

    private final OrganizationRepository organizationRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final PlatformAdminAuditLogRecorder auditLogRecorder;

    /**
     * 관리자가 조직을 직접 만든다. 계정을 새로 만들지 않고 {@code ownerLoginId}로 지정한 기존 사용자를
     * 그대로 OWNER로 붙인다 — "계정 생성"과 "조직 생성"을 분리한 3단계 가입 흐름
     * (signstage-docs business/user-organization-design.md 5장)을 관리자 경로에서도 그대로 지킨다.
     */
    @Transactional
    public PlatformAdminOrganizationDto.Response.OrganizationSummary createOrganization(
            Long actingUserId,
            String actingPlatformRole,
            PlatformAdminOrganizationDto.Request.CreateOrganization request
    ) {
        if (!ORGANIZATION_CONTROL_ALLOWED_ROLES.contains(actingPlatformRole)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
        if (organizationRepository.existsByCode(request.getCode())) {
            throw new ApplicationException(OrganizationErrorCode.ORGANIZATION_CODE_DUPLICATE);
        }

        User owner = userRepository.findByLoginId(request.getOwnerLoginId())
                .orElseThrow(() -> new ApplicationException(OrganizationErrorCode.ORGANIZATION_MEMBER_USER_NOT_FOUND));

        Organization organization = Organization.builder()
                .name(request.getOrganizationName())
                .code(request.getCode())
                .build();
        organizationRepository.save(organization);

        Member ownerMembership = Member.builder()
                .organization(organization)
                .user(owner)
                .role(MemberRole.OWNER)
                .status(MemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();
        memberRepository.save(ownerMembership);

        // targetUserId 대신 organizationId만 채운다 — 감사 로그 목록에서 "조직: {name}" 링크로
        // 방금 만든 조직 상세로 바로 이동하는 게 더 유용하다(오너 정보는 detail 텍스트로 남긴다).
        auditLogRecorder.record(
                actingUserId, PlatformAdminAction.CREATE_ORGANIZATION, null, organization.getId(),
                "code=" + organization.getCode() + ", ownerLoginId=" + owner.getLoginId()
        );
        return toSummary(organization);
    }

    /**
     * ACTIVE↔SUSPENDED만 이 API로 다룬다. TRIAL은 과금 연동 시점에 별도로 다룬다
     * (signstage-docs business/user-organization-design.md 3.2절 "과금 연동 지점").
     */
    @Transactional
    public PlatformAdminOrganizationDto.Response.OrganizationSummary updateOrganizationStatus(
            Long organizationId,
            Long actingUserId,
            String actingPlatformRole,
            PlatformAdminOrganizationDto.Request.UpdateStatus request
    ) {
        if (!ORGANIZATION_CONTROL_ALLOWED_ROLES.contains(actingPlatformRole)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }

        Organization organization = findOrganizationOrThrow(organizationId);
        OrganizationStatus previousStatus = organization.getStatus();
        OrganizationStatus newStatus = parseAssignableStatus(request.getStatus());
        organization.changeStatus(newStatus);

        auditLogRecorder.record(
                actingUserId, PlatformAdminAction.UPDATE_ORGANIZATION_STATUS, null, organizationId,
                "status: " + previousStatus + " -> " + newStatus
        );
        return toSummary(organization);
    }

    private OrganizationStatus parseAssignableStatus(String status) {
        OrganizationStatus parsed;
        try {
            parsed = OrganizationStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }

        if (parsed != OrganizationStatus.ACTIVE && parsed != OrganizationStatus.SUSPENDED) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
        return parsed;
    }

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
