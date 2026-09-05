package com.eformworks.signstage.backend.feature.platformadmin.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.organization.dto.OrganizationDto;
import com.eformworks.signstage.backend.feature.organization.entity.OrganizationCreationRequest;
import com.eformworks.signstage.backend.feature.organization.error.OrganizationErrorCode;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationCreationRequestRepository;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationHistoryRepository;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.entity.OrganizationHistory;
import com.eformworks.signstage.backend.feature.organization.entity.OrganizationStatus;
import com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminOrganizationDto;
import com.eformworks.signstage.backend.feature.platformadmin.error.PlatformAdminErrorCode;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAction;
import java.time.LocalDateTime;
import java.util.List;
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

    /** 한 사용자가 OWNER로 보유할 수 있는 ACTIVE 조직 최대 개수(organization-creation-approval-review.md 7.3절). */
    static final int MAX_OWNED_ORGANIZATIONS = 10;

    private final OrganizationRepository organizationRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final OrganizationCreationRequestRepository organizationCreationRequestRepository;
    private final OrganizationHistoryRepository organizationHistoryRepository;
    private final PlatformAdminAuditLogRecorder auditLogRecorder;

    /**
     * 관리자가 조직을 직접 만든다. 계정을 새로 만들지 않고 {@code ownerLoginId}로 지정한 기존 사용자를
     * 그대로 OWNER로 붙인다 — "계정 생성"과 "조직 생성"을 분리한 3단계 가입 흐름
     * (signstage-docs business/user-organization-design.md 5장)을 관리자 경로에서도 그대로 지킨다.
     *
     * <p>요청 없이 만드는 경로지만, 내부적으로는 "관리자가 요청을 대신 접수하고 즉시 승인한" 것으로
     * 취급해 {@link OrganizationCreationRequest}를 APPROVED 상태로 함께 남긴다 — 조직이 어떤 경위로
     * 만들어졌는지 항상 요청 레코드 하나로 추적하기 위해서다(organization-creation-approval-review.md 3.1절).
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
        checkSingleOrganizationLimit(owner);
        checkOwnerLimit(owner);
        checkNotPlatformAdmin(owner);

        Organization organization = saveOrganizationWithOwner(request.getOrganizationName(), request.getCode(), owner);

        OrganizationCreationRequest autoApprovedRequest = OrganizationCreationRequest.builder()
                .requestedBy(owner)
                .organizationName(organization.getName())
                .build();
        autoApprovedRequest.approve(actingUserId, organization);
        organizationCreationRequestRepository.save(autoApprovedRequest);

        // targetUserId 대신 organizationId만 채운다 — 감사 로그 목록에서 "조직: {name}" 링크로
        // 방금 만든 조직 상세로 바로 이동하는 게 더 유용하다(오너 정보는 detail 텍스트로 남긴다).
        auditLogRecorder.record(
                actingUserId, PlatformAdminAction.CREATE_ORGANIZATION, null, organization.getId(),
                "code=" + organization.getCode() + ", ownerLoginId=" + owner.getLoginId()
        );
        return toSummary(organization);
    }

    /**
     * 조직 + OWNER 멤버십 저장 로직. 관리자 대행 등록({@link #createOrganization})과 조직 생성 요청
     * 승인({@code PlatformAdminOrganizationRequestService})이 이 메서드를 공유한다
     * (organization-creation-approval-review.md 3.1절 "저장 방식 결정됨").
     */
    Organization saveOrganizationWithOwner(String name, String code, User owner) {
        Organization organization = Organization.builder()
                .name(name)
                .code(code)
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
        recordOrganizationHistory(organization);

        return organization;
    }

    /** 보유 조직 개수 제한(최대 10개, 7.3절)을 검사한다. 승인 경로 전부가 공유한다. */
    void checkOwnerLimit(User owner) {
        long ownedCount = memberRepository.countByUserIdAndRoleAndStatus(owner.getId(), MemberRole.OWNER, MemberStatus.ACTIVE);
        if (ownedCount >= MAX_OWNED_ORGANIZATIONS) {
            throw new ApplicationException(OrganizationErrorCode.ORGANIZATION_OWNER_LIMIT_EXCEEDED);
        }
    }

    /**
     * 1인 1조직 제한(2026-08-16 결정) — 역할과 무관하게 이미 다른 조직에 ACTIVE로 속해 있으면
     * 새 조직 소속을 막는다. {@link #checkOwnerLimit}보다 먼저 걸리는 더 강한 제약이라
     * checkOwnerLimit은 그대로 두고 이 검사만 추가했다 — 나중에 다중 조직을 허용하기로 하면
     * 이 메서드 호출부만 지우면 된다(스키마/JWT는 원래부터 다중 조직을 전제로 돼 있다).
     */
    void checkSingleOrganizationLimit(User owner) {
        if (memberRepository.existsByUserIdAndStatus(owner.getId(), MemberStatus.ACTIVE)) {
            throw new ApplicationException(OrganizationErrorCode.ORGANIZATION_SINGLE_MEMBERSHIP_LIMIT);
        }
    }

    /**
     * 플랫폼 관리자는 조직에 소속될 수 없다(2026-08-24 결정) — 조직을 대행 생성/승인해 OWNER로
     * 붙이려는 사용자가 platform_role을 갖고 있으면 막는다. {@link #checkSingleOrganizationLimit}과
     * 같은 이유로 승인 경로 전부({@link #createOrganization}, {@code PlatformAdminOrganizationRequestService#approve})가
     * 공유한다.
     */
    void checkNotPlatformAdmin(User owner) {
        if (owner.getPlatformRole() != null) {
            throw new ApplicationException(OrganizationErrorCode.ORGANIZATION_MEMBER_IS_PLATFORM_ADMIN);
        }
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
        recordOrganizationHistory(organization);

        auditLogRecorder.record(
                actingUserId, PlatformAdminAction.UPDATE_ORGANIZATION_STATUS, null, organizationId,
                "status: " + previousStatus + " -> " + newStatus
        );
        return toSummary(organization);
    }

    /**
     * 플랫폼 관리자가 파트너 정보(이름/기본 언어)를 수정한다(2026-08-30 요청 — 지금까지는
     * OWNER만 가능했다, {@code OrganizationService#updateOrganization}). code는 조직 식별자라
     * 이 API로도 바꾸지 않는다. 조직 소속 여부와 무관하게 PLATFORM_OPS 이상이면 호출할 수
     * 있다 — 다른 플랫폼 관리자 제어 기능과 같은 등급.
     */
    @Transactional
    public PlatformAdminOrganizationDto.Response.OrganizationSummary updateOrganizationInfo(
            Long organizationId,
            Long actingUserId,
            String actingPlatformRole,
            PlatformAdminOrganizationDto.Request.UpdateOrganizationInfo request
    ) {
        if (!ORGANIZATION_CONTROL_ALLOWED_ROLES.contains(actingPlatformRole)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }

        Organization organization = findOrganizationOrThrow(organizationId);
        String detail = "organizationId=" + organizationId
                + ", name: " + organization.getName() + " -> " + request.getOrganizationName()
                + ", defaultLocale: " + organization.getDefaultLocale() + " -> " + request.getDefaultLocale();

        organization.updateInfo(
                request.getOrganizationName(),
                organization.getDefaultLanguageCode(),
                request.getDefaultLocale(),
                organization.getDefaultTimeZoneId(),
                organization.getBillingCurrencyCode()
        );
        recordOrganizationHistory(organization);

        auditLogRecorder.record(actingUserId, PlatformAdminAction.UPDATE_ORGANIZATION_INFO, null, organizationId, detail);
        return toSummary(organization);
    }

    /**
     * 변경 이력 조회. 조직 스코핑을 우회한다(플랫폼 관리자는 그 조직의 멤버가 아니어도 된다) —
     * {@code OrganizationService#findOrganizationHistory}(일반 사용자용, ACTIVE 멤버 전용)와
     * 같은 데이터를 다른 인가 규칙으로 노출하는 짝이다.
     */
    public List<OrganizationDto.Response.OrganizationHistorySummary> findOrganizationHistory(Long organizationId) {
        findOrganizationOrThrow(organizationId);
        return organizationHistoryRepository.findAllByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .map(this::toHistorySummary)
                .toList();
    }

    private void recordOrganizationHistory(Organization organization) {
        organizationHistoryRepository.save(OrganizationHistory.builder().organization(organization).build());
    }

    private OrganizationDto.Response.OrganizationHistorySummary toHistorySummary(OrganizationHistory history) {
        return new OrganizationDto.Response.OrganizationHistorySummary(
                history.getId(),
                history.getName(),
                history.getCode(),
                history.getStatus().name(),
                history.getDefaultLanguageCode(),
                history.getDefaultLocale(),
                history.getDefaultTimeZoneId(),
                history.getBillingCurrencyCode(),
                history.getCreatedBy(),
                history.getCreatedAt()
        );
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
