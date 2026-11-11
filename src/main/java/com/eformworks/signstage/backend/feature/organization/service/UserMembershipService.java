package com.eformworks.signstage.backend.feature.organization.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.model.UserMembership;
import com.eformworks.signstage.backend.feature.identity.port.UserMembershipPort;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.error.OrganizationErrorCode;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** identity의 {@link UserMembershipPort} 구현 — 사용자 단위 멤버십 조회와 탈퇴 시 정리. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserMembershipService implements UserMembershipPort {

    private final MemberRepository memberRepository;

    /**
     * 소속 VIEWER면 프런트가 UserLayout이 아니라 DemoLayout으로 보내야 한다(signstage-docs
     * business/demo-account-exhibition-signer-preview-review.md 4.1/4.2절).
     */
    @Override
    public boolean isDemoViewer(Long userId) {
        return memberRepository.findAllByUserIdAndStatus(userId, MemberStatus.ACTIVE).stream()
                .anyMatch(member -> member.getOrganization().isDemo() && member.getRole() == MemberRole.VIEWER);
    }

    @Override
    public Page<User> searchUsersWithoutOrganization(String loginId, String name, String email, Pageable pageable) {
        return memberRepository.searchUsersWithoutOrganization(loginId, name, email, pageable);
    }

    @Override
    public List<UserMembership> findMemberships(Long userId) {
        return memberRepository.findAllByUserIdAndStatusNot(userId, MemberStatus.REMOVED).stream()
                .map(UserMembershipService::toUserMembership)
                .toList();
    }

    @Override
    @Transactional
    public void removeMembershipsForWithdrawal(Long userId) {
        List<Member> activeMemberships = memberRepository.findAllByUserIdAndStatus(userId, MemberStatus.ACTIVE);
        for (Member membership : activeMemberships) {
            if (membership.getRole() == MemberRole.OWNER) {
                long activeOwnerCount = memberRepository.countByOrganizationIdAndRoleAndStatus(
                        membership.getOrganization().getId(), MemberRole.OWNER, MemberStatus.ACTIVE
                );
                if (activeOwnerCount <= 1) {
                    throw new ApplicationException(OrganizationErrorCode.ORGANIZATION_LAST_OWNER_REQUIRED);
                }
            }
        }
        activeMemberships.forEach(Member::remove);
    }

    private static UserMembership toUserMembership(Member member) {
        return new UserMembership(
                member.getOrganization().getId(),
                member.getOrganization().getName(),
                member.getOrganization().getCode(),
                member.getRole().name(),
                member.getStatus().name(),
                member.getJoinedAt()
        );
    }
}
