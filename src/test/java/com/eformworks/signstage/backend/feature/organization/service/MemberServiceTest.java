package com.eformworks.signstage.backend.feature.organization.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.identity.entity.PlatformRole;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.organization.dto.MemberDto;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.error.OrganizationErrorCode;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link MemberService#addMember}의 "플랫폼 관리자는 조직에 소속될 수 없다"(2026-08-24 결정)
 * 검증 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MemberService memberService;

    private static final Long ORGANIZATION_ID = 1L;
    private static final Long CURRENT_USER_ID = 10L;
    private static final Long TARGET_USER_ID = 20L;

    @Test
    @DisplayName("platform_role이 있는 사용자는 조직 멤버로 추가할 수 없다")
    void addMember_platformAdmin_isRejected() {
        // given
        Organization organization = Organization.builder().name("조직").code("ORG").build();
        ReflectionTestUtils.setField(organization, "id", ORGANIZATION_ID);

        Member actingMember = Member.builder().organization(organization).role(MemberRole.OWNER).status(MemberStatus.ACTIVE).build();

        User targetUser = User.builder()
                .loginId("platform-admin")
                .name("플랫폼 관리자")
                .email("admin@example.com")
                .password("encoded")
                .platformRole(PlatformRole.PLATFORM_SUPPORT)
                .build();
        ReflectionTestUtils.setField(targetUser, "id", TARGET_USER_ID);

        MemberDto.Request.AddMember request = new MemberDto.Request.AddMember();
        request.setLoginId(targetUser.getLoginId());
        request.setRole(MemberRole.VIEWER.name());

        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(organization));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(actingMember));
        given(userRepository.findByLoginId(targetUser.getLoginId())).willReturn(Optional.of(targetUser));
        given(memberRepository.existsByOrganizationIdAndUserId(ORGANIZATION_ID, TARGET_USER_ID)).willReturn(false);
        given(memberRepository.existsByUserIdAndStatus(TARGET_USER_ID, MemberStatus.ACTIVE)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> memberService.addMember(ORGANIZATION_ID, CURRENT_USER_ID, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(OrganizationErrorCode.ORGANIZATION_MEMBER_IS_PLATFORM_ADMIN);
        verify(memberRepository, never()).save(any(Member.class));
    }
}
