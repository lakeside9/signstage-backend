package com.eformworks.signstage.backend.feature.platformadmin.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.error.OrganizationErrorCode;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminMemberDto;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.platformadmin.error.PlatformAdminErrorCode;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플랫폼 관리자의 조직 멤버 강제 조정(signstage-docs
 * business/platform-admin-member-management.md 4.2절 "조직 멤버십 강제 조정"). 일반
 * {@code feature.organization.service.MemberService}와 달리 호출자가 그 조직의 멤버일
 * 필요가 없다 — organization_members를 직접 검사하지 않고 platform_role만으로 인가한다
 * (feature.platformadmin의 다른 서비스와 같은 패턴).
 *
 * <p>"최소 1 OWNER" 규칙(user-organization-design.md 4.3절)은 관리자 강제 조정에도
 * 예외 없이 그대로 적용한다 — platform-admin-member-management.md 10장에서 결정이
 * 필요하다고 남겨뒀던 사항을 "예외 없음"으로 확정했다. 관리자가 마지막 OWNER를 강등/제거해야
 * 한다면 먼저 다른 멤버를 OWNER로 올린 뒤 시도해야 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlatformAdminMemberService {

    private static final Set<String> MEMBER_CONTROL_ALLOWED_ROLES = Set.of("PLATFORM_OPS", "PLATFORM_SUPER");

    private final MemberRepository memberRepository;
    private final PlatformAdminAuditLogRecorder auditLogRecorder;

    public List<PlatformAdminMemberDto.Response.MemberSummary> findMembers(Long organizationId) {
        return memberRepository.findAllByOrganizationIdAndStatusNot(organizationId, MemberStatus.REMOVED).stream()
                .map(this::toMemberSummary)
                .toList();
    }

    @Transactional
    public PlatformAdminMemberDto.Response.MemberSummary forceUpdateMemberRole(
            Long organizationId,
            Long memberId,
            Long actingUserId,
            String actingPlatformRole,
            PlatformAdminMemberDto.Request.ChangeRole request
    ) {
        checkCanManage(actingPlatformRole);
        Member member = findMemberInOrganizationOrThrow(organizationId, memberId);
        MemberRole previousRole = member.getRole();
        MemberRole newRole = parseRole(request.getRole());

        if (previousRole == MemberRole.OWNER && newRole != MemberRole.OWNER) {
            checkNotLastOwner(organizationId);
        }

        member.changeRole(newRole);

        auditLogRecorder.record(
                actingUserId, PlatformAdminAction.FORCE_UPDATE_MEMBER_ROLE, member.getUser().getId(), organizationId,
                "member #" + memberId + " role: " + previousRole + " -> " + newRole
        );
        return toMemberSummary(member);
    }

    @Transactional
    public void forceRemoveMember(Long organizationId, Long memberId, Long actingUserId, String actingPlatformRole) {
        checkCanManage(actingPlatformRole);
        Member member = findMemberInOrganizationOrThrow(organizationId, memberId);

        if (member.getRole() == MemberRole.OWNER) {
            checkNotLastOwner(organizationId);
        }

        Long targetUserId = member.getUser().getId();
        member.remove();

        auditLogRecorder.record(
                actingUserId, PlatformAdminAction.FORCE_REMOVE_MEMBER, targetUserId, organizationId,
                "member #" + memberId + " removed"
        );
    }

    /**
     * 남은 ACTIVE OWNER가 1명뿐이면(=지금 바꾸려는 그 멤버 자신) 최소 1 OWNER 규칙 위반이다.
     * feature.organization.service.MemberService.checkNotLastOwner와 같은 로직이다.
     */
    private void checkNotLastOwner(Long organizationId) {
        long activeOwnerCount = memberRepository.countByOrganizationIdAndRoleAndStatus(
                organizationId, MemberRole.OWNER, MemberStatus.ACTIVE
        );
        if (activeOwnerCount <= 1) {
            throw new ApplicationException(OrganizationErrorCode.ORGANIZATION_LAST_OWNER_REQUIRED);
        }
    }

    private void checkCanManage(String actingPlatformRole) {
        if (!MEMBER_CONTROL_ALLOWED_ROLES.contains(actingPlatformRole)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    private MemberRole parseRole(String role) {
        try {
            return MemberRole.valueOf(role);
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    private Member findMemberInOrganizationOrThrow(Long organizationId, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ApplicationException(PlatformAdminErrorCode.MEMBER_NOT_FOUND));
        if (!member.getOrganization().getId().equals(organizationId)) {
            throw new ApplicationException(PlatformAdminErrorCode.MEMBER_NOT_FOUND);
        }
        return member;
    }

    private PlatformAdminMemberDto.Response.MemberSummary toMemberSummary(Member member) {
        return new PlatformAdminMemberDto.Response.MemberSummary(
                member.getId(),
                member.getOrganization().getId(),
                member.getUser().getId(),
                member.getUser().getLoginId(),
                member.getUser().getName(),
                member.getUser().getEmail(),
                member.getRole().name(),
                member.getStatus().name(),
                member.getJoinedAt()
        );
    }
}
