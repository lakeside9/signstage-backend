package com.eformworks.signstage.backend.feature.platformadmin.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.core.security.TemporaryPasswordGenerator;
import com.eformworks.signstage.backend.feature.identity.error.IdentityErrorCode;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.identity.repository.entity.User;
import com.eformworks.signstage.backend.feature.identity.repository.entity.UserStatus;
import com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminUserDto;
import com.eformworks.signstage.backend.feature.platformadmin.error.PlatformAdminErrorCode;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플랫폼 관리자의 회원 조회/제어를 구현한다(signstage-docs
 * business/platform-admin-member-management.md, backend/signup-approval-implementation-plan.md 4장).
 * URL 레벨({@code SecurityConfig})에서 platform_role 보유자만 이 컨트롤러에 도달하도록 걸러내고,
 * 등급별 세부 권한(제어 기능은 PLATFORM_OPS 이상)은 이 서비스에서 직접 검사한다 —
 * 조직 role을 organization_members로 직접 검사하는 feature.organization과 같은 패턴이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlatformAdminUserService {

    /** 회원 상태 변경/잠금 해제/강제 비밀번호 재설정 등 "제어" 기능에 공통으로 적용되는 최소 등급. */
    private static final Set<String> MEMBER_CONTROL_ALLOWED_ROLES = Set.of("PLATFORM_OPS", "PLATFORM_SUPER");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator temporaryPasswordGenerator;

    /**
     * 관리자가 회원 계정을 직접 만든다. 회원가입(PENDING)→승인 경로를 거치지 않는 대신,
     * 관리자가 만든다는 행위 자체가 승인이라 즉시 ACTIVE로 생성한다. 비밀번호는 서버가
     * 임시로 생성하고, 다음 로그인 시 변경을 강제한다(5.3절과 같은 원칙 — 관리자는
     * 비밀번호를 직접 정하지 않는다). platform_role은 이 경로로 설정하지 않는다(7.2절).
     */
    @Transactional
    public PlatformAdminUserDto.Response.CreatedUser createUser(
            String actingPlatformRole,
            PlatformAdminUserDto.Request.CreateUser request
    ) {
        if (!MEMBER_CONTROL_ALLOWED_ROLES.contains(actingPlatformRole)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
        if (userRepository.existsByLoginId(request.getLoginId())) {
            throw new ApplicationException(IdentityErrorCode.DUPLICATE_LOGIN_ID);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ApplicationException(IdentityErrorCode.DUPLICATE_EMAIL);
        }

        String temporaryPassword = temporaryPasswordGenerator.generate();
        User user = User.builder()
                .loginId(request.getLoginId())
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .locale(request.getLocale())
                .password(passwordEncoder.encode(temporaryPassword))
                .status(UserStatus.ACTIVE)
                .passwordResetRequired(true)
                .build();
        userRepository.save(user);

        return new PlatformAdminUserDto.Response.CreatedUser(toUserSummary(user), temporaryPassword);
    }

    /**
     * loginId/name/email은 부분 일치 검색이다. 빈 문자열은 "조건 없음"으로 취급해 null로 바꿔 넘긴다
     * ({@link UserRepository#search}는 null인 조건만 무시한다).
     */
    public Page<PlatformAdminUserDto.Response.UserSummary> findUsers(
            String loginId,
            String name,
            String email,
            UserStatus status,
            Pageable pageable
    ) {
        Page<User> users = userRepository.search(
                blankToNull(loginId),
                blankToNull(name),
                blankToNull(email),
                status,
                pageable
        );
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
            Long actingUserId,
            String actingPlatformRole,
            PlatformAdminUserDto.Request.UpdateStatus request
    ) {
        checkCanManage(userId, actingUserId, actingPlatformRole);

        User user = findUserOrThrow(userId);
        user.changeStatus(parseAssignableStatus(request.getStatus()));
        return toUserSummary(user);
    }

    /**
     * 연속 로그인 실패로 잠긴 계정을 즉시 풀어준다(failed_login_count=0, locked_until=NULL).
     * 잠금 자체는 15분 뒤 자동 해제되지만(signstage-docs business/login-security.md 4.2절),
     * CS 응대 등으로 즉시 풀어줘야 하는 경우를 위한 기능이다.
     */
    @Transactional
    public PlatformAdminUserDto.Response.UserSummary unlockUser(Long userId, Long actingUserId, String actingPlatformRole) {
        checkCanManage(userId, actingUserId, actingPlatformRole);

        User user = findUserOrThrow(userId);
        user.resetFailedLoginCount();
        return toUserSummary(user);
    }

    /**
     * 다음 로그인 시 비밀번호 변경을 강제한다(is_password_reset_required=TRUE). 비밀번호를
     * 잊어버린 사용자를 위한 우회로다 — 관리자가 비밀번호를 직접 조회/대신 설정하지는 않는다
     * (signstage-docs business/user-organization-design.md 5.3절과 같은 원칙).
     */
    @Transactional
    public PlatformAdminUserDto.Response.UserSummary forcePasswordReset(Long userId, Long actingUserId, String actingPlatformRole) {
        checkCanManage(userId, actingUserId, actingPlatformRole);

        User user = findUserOrThrow(userId);
        user.requirePasswordReset();
        return toUserSummary(user);
    }

    private void checkCanManage(Long targetUserId, Long actingUserId, String actingPlatformRole) {
        if (!MEMBER_CONTROL_ALLOWED_ROLES.contains(actingPlatformRole)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
        if (targetUserId.equals(actingUserId)) {
            throw new ApplicationException(PlatformAdminErrorCode.CANNOT_TARGET_SELF);
        }
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
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
                user.isLocked(),
                user.isPasswordResetRequired(),
                user.getCreatedAt()
        );
    }
}
