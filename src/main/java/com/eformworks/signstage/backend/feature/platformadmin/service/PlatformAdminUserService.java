package com.eformworks.signstage.backend.feature.platformadmin.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.identity.repository.entity.User;
import com.eformworks.signstage.backend.feature.identity.repository.entity.UserStatus;
import com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminUserDto;
import com.eformworks.signstage.backend.feature.platformadmin.error.PlatformAdminErrorCode;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플랫폼 관리자의 회원 조회/승인 처리를 구현한다(signstage-docs
 * business/platform-admin-member-management.md, backend/signup-approval-implementation-plan.md 4장).
 * URL 레벨({@code SecurityConfig})에서 platform_role 보유자만 이 컨트롤러에 도달하도록 걸러내고,
 * 등급별 세부 권한(상태 변경은 PLATFORM_OPS 이상)은 이 서비스에서 직접 검사한다 —
 * 조직 role을 organization_members로 직접 검사하는 feature.organization과 같은 패턴이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlatformAdminUserService {

    private static final Set<String> STATUS_CHANGE_ALLOWED_ROLES = Set.of("PLATFORM_OPS", "PLATFORM_SUPER");

    private final UserRepository userRepository;

    public Page<PlatformAdminUserDto.Response.UserSummary> findUsers(UserStatus status, Pageable pageable) {
        Page<User> users = status != null
                ? userRepository.findAllByStatus(status, pageable)
                : userRepository.findAll(pageable);
        return users.map(this::toUserSummary);
    }

    public PlatformAdminUserDto.Response.UserSummary retrieveUser(Long userId) {
        return toUserSummary(findUserOrThrow(userId));
    }

    /**
     * PENDING→ACTIVE는 가입 승인, PENDING/ACTIVE→DISABLED는 거절 또는 계정 비활성화다
     * (signstage-docs business/user-organization-design.md 5.1절 (a)).
     */
    @Transactional
    public PlatformAdminUserDto.Response.UserSummary updateUserStatus(
            Long userId,
            String actingPlatformRole,
            PlatformAdminUserDto.Request.UpdateStatus request
    ) {
        if (!STATUS_CHANGE_ALLOWED_ROLES.contains(actingPlatformRole)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }

        User user = findUserOrThrow(userId);
        user.changeStatus(parseAssignableStatus(request.getStatus()));
        return toUserSummary(user);
    }

    private UserStatus parseAssignableStatus(String status) {
        UserStatus parsed;
        try {
            parsed = UserStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }

        if (parsed != UserStatus.ACTIVE && parsed != UserStatus.DISABLED) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
        return parsed;
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationException(PlatformAdminErrorCode.USER_NOT_FOUND));
    }

    private PlatformAdminUserDto.Response.UserSummary toUserSummary(User user) {
        return new PlatformAdminUserDto.Response.UserSummary(
                user.getId(),
                user.getLoginId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getLocale(),
                user.getStatus().name(),
                user.getPlatformRole() != null ? user.getPlatformRole().name() : null,
                user.getCreatedAt()
        );
    }
}
