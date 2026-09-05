package com.eformworks.signstage.backend.feature.platformadmin.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.core.security.TemporaryPasswordGenerator;
import com.eformworks.signstage.backend.feature.identity.entity.LoginHistory;
import com.eformworks.signstage.backend.feature.identity.error.IdentityErrorCode;
import com.eformworks.signstage.backend.feature.identity.repository.LoginHistoryRepository;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.identity.entity.PlatformRole;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.entity.UserHistory;
import com.eformworks.signstage.backend.feature.identity.entity.UserStatus;
import com.eformworks.signstage.backend.feature.identity.repository.UserHistoryRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.error.OrganizationErrorCode;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminAccountDto;
import com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminLoginHistoryDto;
import com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminUserDto;
import com.eformworks.signstage.backend.feature.platformadmin.error.PlatformAdminErrorCode;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.util.List;
import java.util.UUID;
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

    private final UserRepository userRepository;
    private final MemberRepository memberRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final UserHistoryRepository userHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator temporaryPasswordGenerator;
    private final PlatformAdminAuditLogRecorder auditLogRecorder;
    private final RolePermissionService rolePermissionService;

    /**
     * 관리자가 회원 계정을 직접 만든다. 회원가입(PENDING)→승인 경로를 거치지 않는 대신,
     * 관리자가 만든다는 행위 자체가 승인이라 즉시 ACTIVE로 생성한다. 비밀번호는 서버가
     * 임시로 생성하고, 다음 로그인 시 변경을 강제한다(5.3절과 같은 원칙 — 관리자는
     * 비밀번호를 직접 정하지 않는다). platform_role은 이 경로로 설정하지 않는다(7.2절).
     * loginId는 요청으로 받지 않고 이메일을 그대로 쓴다(2026-08-16 결정).
     */
    @Transactional
    public PlatformAdminUserDto.Response.CreatedUser createUser(
            Long actingUserId,
            String actingPlatformRole,
            PlatformAdminUserDto.Request.CreateUser request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_MEMBER_FORCE_CONTROL");
        if (userRepository.existsByLoginId(request.getEmail())) {
            throw new ApplicationException(IdentityErrorCode.DUPLICATE_LOGIN_ID);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ApplicationException(IdentityErrorCode.DUPLICATE_EMAIL);
        }

        String temporaryPassword = temporaryPasswordGenerator.generate();
        User user = User.builder()
                .loginId(request.getEmail())
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .languageCode(request.getLanguageCode())
                .locale(request.getLocale())
                .timeZoneId(request.getTimeZoneId())
                .password(passwordEncoder.encode(temporaryPassword))
                .status(UserStatus.ACTIVE)
                .passwordResetRequired(true)
                .build();
        userRepository.save(user);
        recordUserHistory(user);

        auditLogRecorder.record(actingUserId, PlatformAdminAction.CREATE_USER, user.getId(), null, "loginId=" + user.getLoginId());
        return new PlatformAdminUserDto.Response.CreatedUser(toUserSummary(user), temporaryPassword);
    }

    /**
     * loginId/name/email은 부분 일치 검색이다. 빈 문자열은 "조건 없음"으로 취급해 null로 바꿔 넘긴다
     * ({@link UserRepository#search}는 null인 조건만 무시한다). {@code withoutOrganization=false}
     * (일반 "회원 관리" 목록)일 때도 {@link UserRepository#search}가 플랫폼 관리자는 제외한다
     * (2026-08-24 결정) — 관리자 계정은 별도 화면({@link #findAccounts})에서 다룬다.
     *
     * <p>{@code withoutOrganization=true}면 어느 조직에도 ACTIVE로 속하지 않은 ACTIVE 사용자만
     * 반환한다 — 관리자 콘솔의 "조직 멤버 강제 추가" 화면에서 후보를 고를 때 쓴다(1인 1조직
     * 제한, 2026-08-16 결정 — 이미 조직이 있는 사용자는 애초에 후보가 아니다). 플랫폼 관리자도
     * 후보에서 빠진다(2026-08-24 결정 — 조직 소속과 배타적이다). 이때 {@code status}는
     * 무시된다(항상 ACTIVE로 고정).
     */
    public Page<PlatformAdminUserDto.Response.UserSummary> findUsers(
            String loginId,
            String name,
            String email,
            UserStatus status,
            boolean withoutOrganization,
            Pageable pageable
    ) {
        Page<User> users = withoutOrganization
                ? memberRepository.searchUsersWithoutOrganization(blankToNull(loginId), blankToNull(name), blankToNull(email), pageable)
                : userRepository.search(blankToNull(loginId), blankToNull(name), blankToNull(email), status, pageable);
        return users.map(this::toUserSummary);
    }

    /**
     * 기본 정보에 소속 조직 목록을 더해 반환한다(signstage-docs
     * business/platform-admin-member-management.md 4.1절). REMOVED 멤버십은 뺀다.
     */
    public PlatformAdminUserDto.Response.UserDetail retrieveUser(Long userId) {
        User user = findUserOrThrow(userId);
        List<PlatformAdminUserDto.Response.OrganizationMembership> organizations =
                memberRepository.findAllByUserIdAndStatusNot(userId, MemberStatus.REMOVED).stream()
                        .map(this::toOrganizationMembership)
                        .toList();
        return new PlatformAdminUserDto.Response.UserDetail(toUserSummary(user), organizations);
    }

    /**
     * 로그인 이력 조회. signstage-docs business/login-security.md 6장에 따라 다른 조회 API와
     * 달리 PLATFORM_OPS 이상만 볼 수 있다(PLATFORM_SUPPORT는 회원 목록/상세는 봐도 로그인
     * 이력은 못 본다 — 조회 강도가 다른 유일한 예외).
     */
    public Page<PlatformAdminLoginHistoryDto.Response.LoginHistoryEntry> findLoginHistory(
            Long userId,
            String actingPlatformRole,
            Pageable pageable
    ) {
        checkAllowed(actingPlatformRole, "ACTION_MEMBER_FORCE_CONTROL");
        findUserOrThrow(userId);
        return loginHistoryRepository.findAllByUserId(userId, pageable).map(this::toLoginHistoryEntry);
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
        UserStatus previousStatus = user.getStatus();
        UserStatus newStatus = parseAssignableStatus(request.getStatus());
        user.changeStatus(newStatus);
        recordUserHistory(user);

        auditLogRecorder.record(
                actingUserId, PlatformAdminAction.UPDATE_USER_STATUS, userId, null,
                "status: " + previousStatus + " -> " + newStatus
        );
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

        auditLogRecorder.record(actingUserId, PlatformAdminAction.UNLOCK_USER, userId, null, null);
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
        recordUserHistory(user);

        auditLogRecorder.record(actingUserId, PlatformAdminAction.FORCE_PASSWORD_RESET, userId, null, null);
        return toUserSummary(user);
    }

    /**
     * 회원을 강제 탈퇴시킨다(signstage-docs business/platform-admin-member-management.md
     * 4.2절 "회원 탈퇴 강제 처리", user-organization-design.md 8.2절). 민감도가 가장 높아
     * PLATFORM_SUPER만 호출할 수 있다.
     *
     * <p>선행 조건 두 가지를 검사한다 — 8.2절 4번: platform_role이 있으면 먼저 해제해야 한다.
     * 4.3절 "최소 1 OWNER": 탈퇴자가 마지막 OWNER인 조직이 하나라도 있으면 전체를 막는다
     * (관리자 강제 조정이라도 이 규칙에 예외를 두지 않기로 했다 — platform-admin-member-management.md
     * 10장에서 결정 필요 사항으로 남아 있던 것을 "예외 없음"으로 확정했다).
     */
    @Transactional
    public PlatformAdminUserDto.Response.UserSummary forceWithdrawUser(Long userId, Long actingUserId, String actingPlatformRole) {
        checkAllowed(actingPlatformRole, "ACTION_USER_FORCE_WITHDRAW");
        if (userId.equals(actingUserId)) {
            throw new ApplicationException(PlatformAdminErrorCode.CANNOT_TARGET_SELF);
        }

        User user = findUserOrThrow(userId);
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new ApplicationException(PlatformAdminErrorCode.USER_ALREADY_WITHDRAWN);
        }
        if (user.getPlatformRole() != null) {
            throw new ApplicationException(PlatformAdminErrorCode.CANNOT_WITHDRAW_PLATFORM_ADMIN);
        }

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

        String originalLoginId = user.getLoginId();
        String unusablePasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
        user.withdraw(unusablePasswordHash);
        activeMemberships.forEach(Member::remove);
        // user_histories는 업무 트랜잭션에서 기존 행을 고치지 않는 append-only 스냅샷이다.
        // 과거 이력의 PII 보존기간 만료 처리는 이 탈퇴 트랜잭션이 아니라 별도 정리 배치가 맡는다.
        recordUserHistory(user);

        auditLogRecorder.record(actingUserId, PlatformAdminAction.FORCE_WITHDRAW_USER, userId, null, "loginId was " + originalLoginId);
        return toUserSummary(user);
    }

    /**
     * 회원 정보 변경 이력 조회(2026-08-30 요청). 회원 본인의 프로필 수정({@code IdentityService})과
     * 이 서비스의 회원 제어 기능 전부가 남긴 스냅샷을 함께 보여준다 — createdBy로 누가 바꿨는지
     * 구분한다. 다른 조회 API와 같이 PLATFORM_SUPPORT 이상이면 누구나 볼 수 있다(로그인 이력만
     * PLATFORM_OPS 이상으로 더 좁다, {@link #findLoginHistory}).
     */
    public List<PlatformAdminUserDto.Response.UserHistorySummary> findUserHistory(Long userId) {
        findUserOrThrow(userId);
        return userHistoryRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toHistorySummary)
                .toList();
    }

    private void recordUserHistory(User user) {
        userHistoryRepository.save(UserHistory.builder().user(user).build());
    }

    private PlatformAdminUserDto.Response.UserHistorySummary toHistorySummary(UserHistory history) {
        return new PlatformAdminUserDto.Response.UserHistorySummary(
                history.getId(),
                history.getLoginId(),
                history.getName(),
                history.getEmail(),
                history.getPhone(),
                history.getLanguageCode(),
                history.getLocale(),
                history.getTimeZoneId(),
                history.getStatus().name(),
                history.getPlatformRole() != null ? history.getPlatformRole().name() : null,
                history.isPasswordResetRequired(),
                history.getCreatedBy(),
                history.getCreatedAt()
        );
    }

    private void checkCanManage(Long targetUserId, Long actingUserId, String actingPlatformRole) {
        checkAllowed(actingPlatformRole, "ACTION_MEMBER_FORCE_CONTROL");
        if (targetUserId.equals(actingUserId)) {
            throw new ApplicationException(PlatformAdminErrorCode.CANNOT_TARGET_SELF);
        }
    }

    // ── 플랫폼 관리자 계정 관리 (PLATFORM_SUPER 전용, signstage-docs
    //    business/user-organization-design.md 7.2절) ──────────────────────────

    public Page<PlatformAdminUserDto.Response.UserSummary> findAccounts(
            String loginId,
            String name,
            String email,
            PlatformRole platformRole,
            Pageable pageable
    ) {
        Page<User> accounts = userRepository.searchAccounts(
                blankToNull(loginId),
                blankToNull(name),
                blankToNull(email),
                platformRole,
                pageable
        );
        return accounts.map(this::toUserSummary);
    }

    /**
     * 일반 회원가입/초대 API로는 platform_role을 절대 설정할 수 없다(7.2절) — 이 API가
     * platform_role을 지정할 수 있는 유일한 경로다. 회원 직접 생성({@link #createUser})과
     * 동일하게 임시 비밀번호를 발급하고 다음 로그인 시 변경을 강제한다.
     *
     * <p>"이미 조직에 속한 사용자는 플랫폼 관리자가 될 수 없다"(2026-08-24 결정)는 이 메서드가
     * loginId/email 중복이면 무조건 막아 항상 완전히 새로운 User만 만들 수 있기 때문에 별도
     * 검사 없이 항상 성립한다 — 새로 만든 User는 정의상 어떤 조직 멤버십도 갖고 있지 않다.
     * 기존 사용자에게 나중에 platform_role을 부여하는 경로 자체가 없다(등급 변경은
     * {@link #updateAccountRole}이지만 이미 platform_role이 있는 계정만 대상으로 한다). 반대
     * 방향("플랫폼 관리자는 조직에 소속될 수 없다")은 조직 가입 쪽에서 검사한다
     * ({@code OrganizationErrorCode#ORGANIZATION_MEMBER_IS_PLATFORM_ADMIN}).
     */
    @Transactional
    public PlatformAdminUserDto.Response.CreatedUser createAccount(
            Long actingUserId,
            String actingPlatformRole,
            PlatformAdminAccountDto.Request.CreateAccount request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_ACCOUNT_CREATE");
        if (userRepository.existsByLoginId(request.getLoginId())) {
            throw new ApplicationException(IdentityErrorCode.DUPLICATE_LOGIN_ID);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ApplicationException(IdentityErrorCode.DUPLICATE_EMAIL);
        }

        PlatformRole platformRole = parsePlatformRole(request.getPlatformRole());
        String temporaryPassword = temporaryPasswordGenerator.generate();
        User user = User.builder()
                .loginId(request.getLoginId())
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .locale(request.getLocale())
                .password(passwordEncoder.encode(temporaryPassword))
                .status(UserStatus.ACTIVE)
                .platformRole(platformRole)
                .passwordResetRequired(true)
                .build();
        userRepository.save(user);
        recordUserHistory(user);

        auditLogRecorder.record(
                actingUserId, PlatformAdminAction.CREATE_ACCOUNT, user.getId(), null,
                "loginId=" + user.getLoginId() + ", platformRole=" + platformRole
        );
        return new PlatformAdminUserDto.Response.CreatedUser(toUserSummary(user), temporaryPassword);
    }

    /**
     * 이미 platform_role이 있는 계정의 등급만 바꾼다(부여는 {@link #createAccount}, 해제는
     * {@link #revokeAccount}). 해제 후 재생성하지 않고 등급만 재조정할 수 있게 한다.
     */
    @Transactional
    public PlatformAdminUserDto.Response.UserSummary updateAccountRole(
            Long userId,
            Long actingUserId,
            String actingPlatformRole,
            PlatformAdminAccountDto.Request.UpdateRole request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_ACCOUNT_ROLE_CHANGE");
        if (userId.equals(actingUserId)) {
            throw new ApplicationException(PlatformAdminErrorCode.CANNOT_TARGET_SELF);
        }

        User user = findUserOrThrow(userId);
        if (user.getPlatformRole() == null) {
            throw new ApplicationException(PlatformAdminErrorCode.NOT_A_PLATFORM_ADMIN);
        }
        PlatformRole previousRole = user.getPlatformRole();
        PlatformRole newRole = parsePlatformRole(request.getPlatformRole());
        user.changePlatformRole(newRole);
        recordUserHistory(user);

        auditLogRecorder.record(
                actingUserId, PlatformAdminAction.UPDATE_ACCOUNT_ROLE, userId, null,
                "platformRole: " + previousRole + " -> " + newRole
        );
        return toUserSummary(user);
    }

    /** platform_role만 비워 일반 사용자로 되돌린다. 계정 자체(status)는 건드리지 않는다. */
    @Transactional
    public PlatformAdminUserDto.Response.UserSummary revokeAccount(Long userId, Long actingUserId, String actingPlatformRole) {
        checkAllowed(actingPlatformRole, "ACTION_ACCOUNT_REVOKE");
        if (userId.equals(actingUserId)) {
            throw new ApplicationException(PlatformAdminErrorCode.CANNOT_TARGET_SELF);
        }

        User user = findUserOrThrow(userId);
        if (user.getPlatformRole() == null) {
            throw new ApplicationException(PlatformAdminErrorCode.NOT_A_PLATFORM_ADMIN);
        }
        PlatformRole previousRole = user.getPlatformRole();
        user.revokePlatformRole();
        recordUserHistory(user);

        auditLogRecorder.record(
                actingUserId, PlatformAdminAction.REVOKE_ACCOUNT, userId, null, "platformRole was " + previousRole
        );
        return toUserSummary(user);
    }

    /**
     * signstage-docs business/menu-and-action-permission-management-review.md 10장 —
     * 지금까지 서비스마다 흩어져 있던 {@code Set.of(...).contains(...)}/하드코딩된 등급 비교를
     * {@code RolePermissionService.isAllowed(...)} 호출로 옮긴다. 실제 허용 역할은 배포가
     * role_permissions에 시딩한 값을 따른다({@code V202609051200} 마이그레이션).
     */
    private void checkAllowed(String actingPlatformRole, String permissionKey) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, permissionKey)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    private PlatformRole parsePlatformRole(String platformRole) {
        try {
            return PlatformRole.valueOf(platformRole);
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
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
                user.getLanguageCode(),
                user.getLocale(),
                user.getTimeZoneId(),
                user.getStatus().name(),
                user.getPlatformRole() != null ? user.getPlatformRole().name() : null,
                user.isLocked(),
                user.isPasswordResetRequired(),
                user.getCreatedAt()
        );
    }

    private PlatformAdminUserDto.Response.OrganizationMembership toOrganizationMembership(Member member) {
        return new PlatformAdminUserDto.Response.OrganizationMembership(
                member.getOrganization().getId(),
                member.getOrganization().getName(),
                member.getOrganization().getCode(),
                member.getRole().name(),
                member.getStatus().name(),
                member.getJoinedAt()
        );
    }

    private PlatformAdminLoginHistoryDto.Response.LoginHistoryEntry toLoginHistoryEntry(LoginHistory loginHistory) {
        return new PlatformAdminLoginHistoryDto.Response.LoginHistoryEntry(
                loginHistory.getId(),
                loginHistory.getLoginIdInput(),
                loginHistory.getStatus().name(),
                loginHistory.getIpAddress(),
                loginHistory.getUserAgent(),
                loginHistory.getCreatedAt()
        );
    }
}
