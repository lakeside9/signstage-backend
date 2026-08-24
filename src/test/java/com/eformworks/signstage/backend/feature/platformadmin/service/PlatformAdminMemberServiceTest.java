package com.eformworks.signstage.backend.feature.platformadmin.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.identity.entity.PlatformRole;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.error.OrganizationErrorCode;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminMemberDto;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link PlatformAdminMemberService#forceAddMember}의 "플랫폼 관리자는 조직에 소속될 수
 * 없다"(2026-08-24 결정) 검증 단위 테스트 — {@code MemberServiceTest}의 관리자 콘솔 경로 버전.
 */
@ExtendWith(MockitoExtension.class)
class PlatformAdminMemberServiceTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PlatformAdminAuditLogRecorder auditLogRecorder;

    @InjectMocks
    private PlatformAdminMemberService platformAdminMemberService;

    private static final Long ORGANIZATION_ID = 1L;
    private static final Long ACTING_USER_ID = 10L;
    private static final Long TARGET_USER_ID = 20L;

    @Test
    @DisplayName("platform_role이 있는 사용자는 관리자가 강제로도 조직 멤버로 추가할 수 없다")
    void forceAddMember_platformAdmin_isRejected() {
        // given
        Organization organization = Organization.builder().name("조직").code("ORG").build();
        ReflectionTestUtils.setField(organization, "id", ORGANIZATION_ID);

        User targetUser = User.builder()
                .loginId("platform-admin")
                .name("플랫폼 관리자")
                .email("admin@example.com")
                .password("encoded")
                .platformRole(PlatformRole.PLATFORM_OPS)
                .build();
        ReflectionTestUtils.setField(targetUser, "id", TARGET_USER_ID);

        PlatformAdminMemberDto.Request.AddMember request = new PlatformAdminMemberDto.Request.AddMember();
        request.setLoginId(targetUser.getLoginId());
        request.setRole(MemberRole.VIEWER.name());

        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(organization));
        given(userRepository.findByLoginId(targetUser.getLoginId())).willReturn(Optional.of(targetUser));
        given(memberRepository.existsByOrganizationIdAndUserId(ORGANIZATION_ID, TARGET_USER_ID)).willReturn(false);
        given(memberRepository.existsByUserIdAndStatus(TARGET_USER_ID, MemberStatus.ACTIVE)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> platformAdminMemberService.forceAddMember(
                ORGANIZATION_ID, ACTING_USER_ID, "PLATFORM_SUPER", request
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(OrganizationErrorCode.ORGANIZATION_MEMBER_IS_PLATFORM_ADMIN);
        verify(memberRepository, never()).save(any(Member.class));
    }
}
