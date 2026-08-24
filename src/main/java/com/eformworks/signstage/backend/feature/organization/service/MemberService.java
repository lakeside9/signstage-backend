package com.eformworks.signstage.backend.feature.organization.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.organization.dto.MemberDto;
import com.eformworks.signstage.backend.feature.organization.error.OrganizationErrorCode;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조직 멤버 관리(signstage-docs business/user-organization-design.md 4장).
 *
 * <p>OWNER/ADMIN만 멤버를 추가/변경/제거할 수 있고, OWNER 지정·해제는 OWNER만 할 수 있으며,
 * 조직에는 항상 ACTIVE 상태의 OWNER가 최소 1명 있어야 한다(4.3절). 조직 역할이 아직
 * JWT 클레임에 실리지 않아(5.2절 미구현) 호출자의 권한은 organization_members를 직접
 * 조회해 판단한다. 1인 1조직 제한(2026-08-16 결정)에 따라, 이미 다른 조직에 ACTIVE로 속한
 * 사용자는 추가할 수 없다. platform_role이 있는 사용자(플랫폼 관리자)도 추가할 수 없다
 * (2026-08-24 결정, {@code ORGANIZATION_MEMBER_IS_PLATFORM_ADMIN}) — platform_role과 조직
 * 멤버십은 서로 배타적이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    public List<MemberDto.Response.MemberSummary> findMembers(Long organizationId, Long currentUserId) {
        findActiveMemberOrThrow(organizationId, currentUserId);
        return memberRepository.findAllByOrganizationIdAndStatusNot(organizationId, MemberStatus.REMOVED).stream()
                .map(this::toMemberSummary)
                .toList();
    }

    @Transactional
    public MemberDto.Response.MemberSummary addMember(
            Long organizationId,
            Long currentUserId,
            MemberDto.Request.AddMember request
    ) {
        Organization organization = findOrganizationOrThrow(organizationId);
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCanManageMembers(actingMember);

        MemberRole role = parseRole(request.getRole());
        if (role == MemberRole.OWNER && actingMember.getRole() != MemberRole.OWNER) {
            throw new ApplicationException(OrganizationErrorCode.ORGANIZATION_ONLY_OWNER_CAN_ASSIGN_OWNER);
        }

        User user = userRepository.findByLoginId(request.getLoginId())
                .orElseThrow(() -> new ApplicationException(OrganizationErrorCode.ORGANIZATION_MEMBER_USER_NOT_FOUND));

        if (memberRepository.existsByOrganizationIdAndUserId(organizationId, user.getId())) {
            throw new ApplicationException(OrganizationErrorCode.ORGANIZATION_MEMBER_ALREADY_EXISTS);
        }
        // 1인 1조직 제한(2026-08-16 결정) — 역할과 무관하게 이미 다른 조직에 ACTIVE로 속해 있으면 추가할 수 없다.
        if (memberRepository.existsByUserIdAndStatus(user.getId(), MemberStatus.ACTIVE)) {
            throw new ApplicationException(OrganizationErrorCode.ORGANIZATION_SINGLE_MEMBERSHIP_LIMIT);
        }
        // 플랫폼 관리자는 조직에 소속될 수 없다(2026-08-24 결정) — platform_role과 조직 멤버십은
        // 서로 배타적이다.
        if (user.getPlatformRole() != null) {
            throw new ApplicationException(OrganizationErrorCode.ORGANIZATION_MEMBER_IS_PLATFORM_ADMIN);
        }

        Member member = Member.builder()
                .organization(organization)
                .user(user)
                .role(role)
                .status(MemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();
        memberRepository.save(member);

        return toMemberSummary(member);
    }

    @Transactional
    public MemberDto.Response.MemberSummary updateMemberRole(
            Long organizationId,
            Long memberId,
            Long currentUserId,
            MemberDto.Request.ChangeRole request
    ) {
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCanManageMembers(actingMember);

        Member targetMember = findMemberInOrganizationOrThrow(organizationId, memberId);
        MemberRole newRole = parseRole(request.getRole());

        boolean touchesOwnerRole = targetMember.getRole() == MemberRole.OWNER || newRole == MemberRole.OWNER;
        if (touchesOwnerRole && actingMember.getRole() != MemberRole.OWNER) {
            throw new ApplicationException(OrganizationErrorCode.ORGANIZATION_ONLY_OWNER_CAN_ASSIGN_OWNER);
        }

        if (targetMember.getRole() == MemberRole.OWNER && newRole != MemberRole.OWNER) {
            checkNotLastOwner(organizationId);
        }

        targetMember.changeRole(newRole);
        return toMemberSummary(targetMember);
    }

    @Transactional
    public void removeMember(Long organizationId, Long memberId, Long currentUserId) {
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCanManageMembers(actingMember);

        Member targetMember = findMemberInOrganizationOrThrow(organizationId, memberId);

        if (targetMember.getRole() == MemberRole.OWNER) {
            if (actingMember.getRole() != MemberRole.OWNER) {
                throw new ApplicationException(OrganizationErrorCode.ORGANIZATION_ONLY_OWNER_CAN_REMOVE_OWNER);
            }
            checkNotLastOwner(organizationId);
        }

        targetMember.remove();
    }

    /**
     * 남은 ACTIVE OWNER가 1명뿐이면(=지금 바꾸려는 그 멤버 자신) 최소 1 OWNER 규칙 위반이다.
     * 아직 상태 변경 전에 호출하므로 대상 멤버도 카운트에 포함된다.
     */
    private void checkNotLastOwner(Long organizationId) {
        long activeOwnerCount = memberRepository.countByOrganizationIdAndRoleAndStatus(
                organizationId, MemberRole.OWNER, MemberStatus.ACTIVE
        );
        if (activeOwnerCount <= 1) {
            throw new ApplicationException(OrganizationErrorCode.ORGANIZATION_LAST_OWNER_REQUIRED);
        }
    }

    private void checkCanManageMembers(Member actingMember) {
        if (actingMember.getRole() != MemberRole.OWNER && actingMember.getRole() != MemberRole.ADMIN) {
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

    private Organization findOrganizationOrThrow(Long organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApplicationException(OrganizationErrorCode.ORGANIZATION_NOT_FOUND));
    }

    private Member findActiveMemberOrThrow(Long organizationId, Long userId) {
        return memberRepository.findByOrganizationIdAndUserIdAndStatus(organizationId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApplicationException(CommonErrorCode.ACCESS_DENIED));
    }

    private Member findMemberInOrganizationOrThrow(Long organizationId, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ApplicationException(OrganizationErrorCode.ORGANIZATION_MEMBER_NOT_FOUND));
        if (!member.getOrganization().getId().equals(organizationId)) {
            throw new ApplicationException(OrganizationErrorCode.ORGANIZATION_MEMBER_NOT_FOUND);
        }
        return member;
    }

    private MemberDto.Response.MemberSummary toMemberSummary(Member member) {
        return new MemberDto.Response.MemberSummary(
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
