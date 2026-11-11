package com.eformworks.signstage.backend.feature.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.error.OrganizationErrorCode;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link UserMembershipService} 단위 테스트 — identity의 {@code UserMembershipPort} 구현.
 * 데모 뷰어 판정과 회원 탈퇴 시 멤버십 정리(마지막 OWNER 보호).
 */
@ExtendWith(MockitoExtension.class)
class UserMembershipServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private UserMembershipService userMembershipService;

    @Test
    @DisplayName("데모 조직의 활성 VIEWER면 데모 뷰어다")
    void isDemoViewer_trueForDemoOrganizationViewer() {
        Organization demo = Organization.builder().name("데모").code("DEMO").demo(true).build();
        given(memberRepository.findAllByUserIdAndStatus(USER_ID, MemberStatus.ACTIVE))
                .willReturn(List.of(member(demo, MemberRole.VIEWER)));

        assertThat(userMembershipService.isDemoViewer(USER_ID)).isTrue();
    }

    @Test
    @DisplayName("일반 조직의 VIEWER는 데모 뷰어가 아니다")
    void isDemoViewer_falseForRegularOrganization() {
        Organization regular = Organization.builder().name("조직").code("ORG").build();
        given(memberRepository.findAllByUserIdAndStatus(USER_ID, MemberStatus.ACTIVE))
                .willReturn(List.of(member(regular, MemberRole.VIEWER)));

        assertThat(userMembershipService.isDemoViewer(USER_ID)).isFalse();
    }

    @Test
    @DisplayName("탈퇴 정리 — 다른 OWNER가 있으면 활성 멤버십을 모두 해제한다")
    void removeMembershipsForWithdrawal_removesWhenAnotherOwnerExists() {
        Organization organization = organization(10L);
        Member owner = member(organization, MemberRole.OWNER);
        Member operator = member(organization(20L), MemberRole.OPERATOR);
        given(memberRepository.findAllByUserIdAndStatus(USER_ID, MemberStatus.ACTIVE)).willReturn(List.of(owner, operator));
        given(memberRepository.countByOrganizationIdAndRoleAndStatus(10L, MemberRole.OWNER, MemberStatus.ACTIVE)).willReturn(2L);

        userMembershipService.removeMembershipsForWithdrawal(USER_ID);

        assertThat(owner.getStatus()).isEqualTo(MemberStatus.REMOVED);
        assertThat(operator.getStatus()).isEqualTo(MemberStatus.REMOVED);
    }

    @Test
    @DisplayName("탈퇴 정리 — 마지막 OWNER면 거부하고 아무 멤버십도 바꾸지 않는다")
    void removeMembershipsForWithdrawal_rejectsLastOwner() {
        Member operator = member(organization(20L), MemberRole.OPERATOR);
        Member owner = member(organization(10L), MemberRole.OWNER);
        given(memberRepository.findAllByUserIdAndStatus(USER_ID, MemberStatus.ACTIVE)).willReturn(List.of(operator, owner));
        given(memberRepository.countByOrganizationIdAndRoleAndStatus(10L, MemberRole.OWNER, MemberStatus.ACTIVE)).willReturn(1L);

        assertThatThrownBy(() -> userMembershipService.removeMembershipsForWithdrawal(USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(OrganizationErrorCode.ORGANIZATION_LAST_OWNER_REQUIRED);
        assertThat(operator.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(owner.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    private static Organization organization(Long id) {
        Organization organization = Organization.builder().name("조직" + id).code("ORG" + id).build();
        ReflectionTestUtils.setField(organization, "id", id);
        return organization;
    }

    private static Member member(Organization organization, MemberRole role) {
        return Member.builder().organization(organization).role(role).status(MemberStatus.ACTIVE).build();
    }
}
