package com.eformworks.signstage.backend.feature.notification.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.entity.UserStatus;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.notification.dto.NotificationDto;
import com.eformworks.signstage.backend.feature.notification.entity.Notification;
import com.eformworks.signstage.backend.feature.notification.entity.NotificationType;
import com.eformworks.signstage.backend.feature.notification.error.NotificationErrorCode;
import com.eformworks.signstage.backend.feature.notification.repository.NotificationRepository;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 앱 안 알림 — 메일·SMS 연동 전 1차 범위(signstage-docs
 * business/platform-order-partner-subcontract-review.md 12장). 받는 사람은 권한으로 정한다.
 *
 * <ul>
 *   <li>조직 알림: 그 조직의 활성 회원 중 역할에 주어진 액션 권한이 있는 사람.</li>
 *   <li>플랫폼 알림: 플랫폼 역할이 있는 활성 사용자 중 주어진 액션 권한이 있는 사람.</li>
 * </ul>
 *
 * <p>알림은 호출한 트랜잭션 안에서 저장한다 — 업무 처리가 롤백되면 알림도 남지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final RolePermissionService rolePermissionService;

    @Transactional
    public void notifyOrganization(
            Long organizationId,
            String permissionKey,
            NotificationType type,
            String title,
            String message,
            String linkPath
    ) {
        memberRepository.findAllByOrganizationIdAndStatus(organizationId, MemberStatus.ACTIVE).stream()
                .filter(member -> rolePermissionService.isAllowed(member.getRole().name(), permissionKey))
                .forEach(member -> save(member.getUser().getId(), type, title, message, linkPath));
    }

    @Transactional
    public void notifyPlatform(String permissionKey, NotificationType type, String title, String message, String linkPath) {
        List<User> users = userRepository.findAllByPlatformRoleIsNotNullAndStatus(UserStatus.ACTIVE);
        users.stream()
                .filter(user -> rolePermissionService.isAllowed(user.getPlatformRole().name(), permissionKey))
                .forEach(user -> save(user.getId(), type, title, message, linkPath));
    }

    public Page<NotificationDto.Response.NotificationSummary> findMine(Long userId, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notificationRepository.findAllByUserIdAndReadAtIsNullOrderByIdDesc(userId, pageable)
                : notificationRepository.findAllByUserIdOrderByIdDesc(userId, pageable);
        return page.map(NotificationDto.Response.NotificationSummary::from);
    }

    public NotificationDto.Response.UnreadCount countUnread(Long userId) {
        return new NotificationDto.Response.UnreadCount(notificationRepository.countByUserIdAndReadAtIsNull(userId));
    }

    @Transactional
    public NotificationDto.Response.NotificationSummary markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ApplicationException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markRead();
        return NotificationDto.Response.NotificationSummary.from(notification);
    }

    @Transactional
    public NotificationDto.Response.UnreadCount markAllRead(Long userId) {
        notificationRepository.markAllRead(userId, LocalDateTime.now());
        return new NotificationDto.Response.UnreadCount(0);
    }

    private void save(Long userId, NotificationType type, String title, String message, String linkPath) {
        notificationRepository.save(Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .linkPath(linkPath)
                .build());
    }
}
