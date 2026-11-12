package com.eformworks.signstage.backend.feature.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.feature.identity.entity.PlatformRole;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.entity.UserStatus;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.notification.entity.Notification;
import com.eformworks.signstage.backend.feature.notification.entity.NotificationType;
import com.eformworks.signstage.backend.feature.notification.repository.NotificationRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** {@link NotificationService} 단위 테스트 — 권한으로 받는 사람을 고른다. */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RolePermissionService rolePermissionService;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    @DisplayName("조직 알림은 활성 회원 중 액션 권한이 있는 역할에게만 간다")
    void notifyOrganization_filtersByPermission() {
        Member owner = member(10L, MemberRole.OWNER);
        Member operator = member(11L, MemberRole.OPERATOR);
        given(memberRepository.findAllByOrganizationIdAndStatus(7L, MemberStatus.ACTIVE)).willReturn(List.of(owner, operator));
        given(rolePermissionService.isAllowed("OWNER", "ACTION_PARTNER_ORDER_RESPOND")).willReturn(true);
        given(rolePermissionService.isAllowed("OPERATOR", "ACTION_PARTNER_ORDER_RESPOND")).willReturn(false);

        notificationService.notifyOrganization(7L, "ACTION_PARTNER_ORDER_RESPOND", NotificationType.PARTNER_ORDER_ISSUED,
                "새 발주가 도착했습니다", "협약식", "/partner-orders/7/1");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(10L);
        assertThat(captor.getValue().getLinkPath()).isEqualTo("/partner-orders/7/1");
    }

    @Test
    @DisplayName("플랫폼 알림은 플랫폼 역할에 액션 권한이 있는 사람에게만 간다")
    void notifyPlatform_filtersByPermission() {
        User ops = user(20L, PlatformRole.PLATFORM_OPS);
        User support = user(21L, PlatformRole.PLATFORM_SUPPORT);
        given(userRepository.findAllByPlatformRoleIsNotNullAndStatus(UserStatus.ACTIVE)).willReturn(List.of(ops, support));
        given(rolePermissionService.isAllowed("PLATFORM_OPS", "ACTION_PARTNER_ORDER_ISSUE")).willReturn(true);
        given(rolePermissionService.isAllowed("PLATFORM_SUPPORT", "ACTION_PARTNER_ORDER_ISSUE")).willReturn(false);

        notificationService.notifyPlatform("ACTION_PARTNER_ORDER_ISSUE", NotificationType.PARTNER_ORDER_ACCEPTED,
                "발주 수락", "협약식", "/admin/sales-orders/1");

        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    private static Member member(Long userId, MemberRole role) {
        return Member.builder().user(user(userId, null)).role(role).status(MemberStatus.ACTIVE).build();
    }

    private static User user(Long id, PlatformRole platformRole) {
        User user = User.builder().loginId("user" + id).name("사용자").platformRole(platformRole).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
