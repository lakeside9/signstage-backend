package com.eformworks.signstage.backend.feature.ceremony.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.identity.entity.PlatformRole;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link CeremonyAccessService} 단위 테스트 — 데모 조직의 플랫폼 관리자 가상 멤버 우회.
 * 2026-11-11 {@code CeremonyServiceTest}에서 분리했다. 공용 mock과 서비스 조립은
 * {@link CeremonyServiceTestSupport}에 있다.
 */
class CeremonyAccessServiceTest extends CeremonyServiceTestSupport {

    // ---- 데모 조직 우회 — signstage-docs
    // business/demo-account-exhibition-signer-preview-review.md 11장(2026-09-09, 결정 번복) ----

    @Test
    @DisplayName("데모 조직 + ACTION_DEMO_CEREMONY_MANAGE 허용(PLATFORM_OPS 이상)이면 가상 OWNER 멤버를 돌려준다")
    void findActiveMemberOrThrow_demoOrganizationWithManagePermission_returnsVirtualOwner() {
        Organization demoOrganization = organization();
        ReflectionTestUtils.setField(demoOrganization, "demo", true);
        User platformAdminUser = User.builder().loginId("admin1").name("관리자1").platformRole(PlatformRole.PLATFORM_OPS).build();

        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, 99L, MemberStatus.ACTIVE))
                .willReturn(Optional.empty());
        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(demoOrganization));
        given(userRepository.findById(99L)).willReturn(Optional.of(platformAdminUser));
        given(rolePermissionService.isAllowed("PLATFORM_OPS", "ACTION_DEMO_CEREMONY_MANAGE")).willReturn(true);

        Member virtualMember = ceremonyAccessService.findActiveMemberOrThrow(ORGANIZATION_ID, 99L);

        assertThat(virtualMember.getRole()).isEqualTo(MemberRole.OWNER);
        assertThat(virtualMember.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("데모 조직이지만 ACTION_DEMO_CEREMONY_MANAGE가 없으면(PLATFORM_SUPPORT) 조회만 가능한 가상 VIEWER 멤버를 돌려준다")
    void findActiveMemberOrThrow_demoOrganizationWithoutManagePermission_returnsVirtualViewer() {
        Organization demoOrganization = organization();
        ReflectionTestUtils.setField(demoOrganization, "demo", true);
        User platformAdminUser = User.builder().loginId("admin2").name("관리자2").platformRole(PlatformRole.PLATFORM_SUPPORT).build();

        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, 98L, MemberStatus.ACTIVE))
                .willReturn(Optional.empty());
        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(demoOrganization));
        given(userRepository.findById(98L)).willReturn(Optional.of(platformAdminUser));
        given(rolePermissionService.isAllowed("PLATFORM_SUPPORT", "ACTION_DEMO_CEREMONY_MANAGE")).willReturn(false);

        Member virtualMember = ceremonyAccessService.findActiveMemberOrThrow(ORGANIZATION_ID, 98L);

        assertThat(virtualMember.getRole()).isEqualTo(MemberRole.VIEWER);
    }

    @Test
    @DisplayName("데모 조직이 아니면 플랫폼 관리자라도 우회되지 않고 접근이 거부된다")
    void findActiveMemberOrThrow_nonDemoOrganization_stillDenied() {
        Organization organization = organization();

        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, 99L, MemberStatus.ACTIVE))
                .willReturn(Optional.empty());
        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(organization));

        assertThatThrownBy(() -> ceremonyAccessService.findActiveMemberOrThrow(ORGANIZATION_ID, 99L))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }
}
