package com.eformworks.signstage.backend.feature.identity.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.security.JwtProvider;
import com.eformworks.signstage.backend.feature.identity.dto.IdentityDto;
import com.eformworks.signstage.backend.feature.identity.error.IdentityErrorCode;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.identity.entity.LoginHistoryStatus;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.entity.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * signstage-docs business/login-security.md 5장의 로그인 흐름을 구현한다.
 * platformRole 보유자(플랫폼 관리자)와 일반 사용자 모두 로그인할 수 있다. 다만 아직
 * organizationId를 JWT 클레임에 싣는 조직 선택 흐름(user-organization-design.md 5.2절)은
 * 구현하지 않았다 — 일반 사용자는 조직 소속 여부와 무관하게 항상 같은 형태의 토큰을 받는다.
 *
 * <p>로그인 이력 기록과 실패 카운터/잠금 갱신은 {@link LoginAttemptRecorder}가
 * 별도 트랜잭션으로 처리한다. 이 클래스 자체는 그 값들을 직접 쓰지 않는다 —
 * 실패 시 예외를 던지는 흐름과 같은 트랜잭션에서 기록하면, 예외 때문에 트랜잭션이
 * 롤백되면서 방금 남긴 실패 이력까지 함께 사라지기 때문이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IdentityService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final LoginAttemptRecorder loginAttemptRecorder;

    public IdentityDto.Response.Login login(IdentityDto.Request.Login request, String ipAddress, String userAgent) {
        User user = userRepository.findByLoginId(request.getLoginId()).orElse(null);

        if (user == null) {
            loginAttemptRecorder.recordFailure(null, request.getLoginId(), LoginHistoryStatus.FAILED_NOT_FOUND, ipAddress, userAgent);
            throw new ApplicationException(IdentityErrorCode.INVALID_CREDENTIAL);
        }

        if (user.isLocked()) {
            loginAttemptRecorder.recordFailure(user.getId(), request.getLoginId(), LoginHistoryStatus.FAILED_LOCKED, ipAddress, userAgent);
            throw new ApplicationException(IdentityErrorCode.ACCOUNT_LOCKED);
        }

        if (user.getStatus() == UserStatus.PENDING) {
            loginAttemptRecorder.recordFailure(user.getId(), request.getLoginId(), LoginHistoryStatus.FAILED_PENDING_APPROVAL, ipAddress, userAgent);
            throw new ApplicationException(IdentityErrorCode.ACCOUNT_PENDING_APPROVAL);
        }

        if (user.getStatus() == UserStatus.DISABLED) {
            loginAttemptRecorder.recordFailure(user.getId(), request.getLoginId(), LoginHistoryStatus.FAILED_DISABLED, ipAddress, userAgent);
            throw new ApplicationException(IdentityErrorCode.ACCOUNT_DISABLED);
        }

        if (user.getStatus() == UserStatus.WITHDRAWN) {
            loginAttemptRecorder.recordFailure(user.getId(), request.getLoginId(), LoginHistoryStatus.FAILED_WITHDRAWN, ipAddress, userAgent);
            throw new ApplicationException(IdentityErrorCode.INVALID_CREDENTIAL);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginAttemptRecorder.recordInvalidPassword(user.getId(), request.getLoginId(), ipAddress, userAgent);
            throw new ApplicationException(IdentityErrorCode.INVALID_CREDENTIAL);
        }

        loginAttemptRecorder.recordSuccess(user.getId(), request.getLoginId(), ipAddress, userAgent);

        if (user.isPasswordResetRequired()) {
            String passwordResetToken = jwtProvider.createPasswordResetToken(user.getId());
            return IdentityDto.Response.Login.passwordChangeRequired(passwordResetToken);
        }

        if (user.getPlatformRole() != null) {
            String accessToken = jwtProvider.createPlatformAccessToken(user);
            IdentityDto.Response.PlatformAdminInfo platformAdmin = new IdentityDto.Response.PlatformAdminInfo(
                    user.getId(),
                    user.getLoginId(),
                    user.getName(),
                    user.getPlatformRole().name()
            );
            return IdentityDto.Response.Login.success(accessToken, platformAdmin);
        }

        // 일반 사용자(조직 소속 여부와 무관): platformRole이 없으므로 관리자 콘솔 토큰이 아닌
        // 일반 액세스 토큰을 발급한다. feature.organization의 API들은 이미 JWT 클레임이 아니라
        // organization_members를 직접 조회해 권한을 판단하므로, 이 토큰만으로 그대로 호출할 수 있다.
        String accessToken = jwtProvider.createUserAccessToken(user);
        return IdentityDto.Response.Login.success(accessToken, null);
    }

    /**
     * 일반 사용자 가입. status=PENDING으로 생성되며, 관리자 승인(PENDING→ACTIVE) 전까지는
     * 로그인할 수 없다(signstage-docs business/user-organization-design.md 5.1절 (a)).
     * platform_role은 이 경로로 절대 설정되지 않는다. loginId는 요청으로 받지 않고 이메일을
     * 그대로 쓴다(2026-08-16 결정) — 중복 검사도 그 값 하나로 두 컬럼(loginId/email)을 각각 본다.
     */
    @Transactional
    public IdentityDto.Response.Signup signup(IdentityDto.Request.Signup request) {
        if (userRepository.existsByLoginId(request.getEmail())) {
            throw new ApplicationException(IdentityErrorCode.DUPLICATE_LOGIN_ID);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ApplicationException(IdentityErrorCode.DUPLICATE_EMAIL);
        }

        User user = User.builder()
                .loginId(request.getEmail())
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .locale(request.getLocale())
                .password(passwordEncoder.encode(request.getPassword()))
                .status(UserStatus.PENDING)
                .build();
        userRepository.save(user);

        return new IdentityDto.Response.Signup(user.getId(), user.getLoginId(), user.getStatus().name());
    }

    @Transactional
    public void forcePasswordChange(IdentityDto.Request.ForcePasswordChange request) {
        Long userId = jwtProvider.parsePasswordResetToken(request.getPasswordResetToken());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationException(IdentityErrorCode.INVALID_RESET_TOKEN));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new ApplicationException(IdentityErrorCode.INVALID_CREDENTIAL);
        }

        user.changePassword(passwordEncoder.encode(request.getNewPassword()));
    }

    public IdentityDto.Response.Me getMe(Long userId) {
        return toMeResponse(findUserOrThrow(userId));
    }

    @Transactional
    public IdentityDto.Response.Me updateMe(Long userId, IdentityDto.Request.UpdateMe request) {
        User user = findUserOrThrow(userId);

        if (!user.getEmail().equals(request.getEmail())
                && userRepository.existsByEmailAndIdNot(request.getEmail(), userId)) {
            throw new ApplicationException(IdentityErrorCode.DUPLICATE_EMAIL);
        }

        user.changeProfile(request.getName(), request.getEmail(), request.getPhone(), request.getLocale());
        return toMeResponse(user);
    }

    @Transactional
    public void changeMyPassword(Long userId, IdentityDto.Request.ChangeMyPassword request) {
        User user = findUserOrThrow(userId);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new ApplicationException(IdentityErrorCode.INVALID_CREDENTIAL);
        }

        user.changePassword(passwordEncoder.encode(request.getNewPassword()));
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationException(IdentityErrorCode.INVALID_CREDENTIAL));
    }

    private IdentityDto.Response.Me toMeResponse(User user) {
        return new IdentityDto.Response.Me(
                user.getId(),
                user.getLoginId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getLocale(),
                user.getPlatformRole() != null ? user.getPlatformRole().name() : null
        );
    }
}
