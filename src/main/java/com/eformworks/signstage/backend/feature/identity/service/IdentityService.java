package com.eformworks.signstage.backend.feature.identity.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.security.JwtProvider;
import com.eformworks.signstage.backend.feature.identity.dto.IdentityDto;
import com.eformworks.signstage.backend.feature.identity.error.IdentityErrorCode;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.identity.repository.entity.LoginHistoryStatus;
import com.eformworks.signstage.backend.feature.identity.repository.entity.User;
import com.eformworks.signstage.backend.feature.identity.repository.entity.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * signstage-docs business/login-security.md 5장의 로그인 흐름을 구현한다.
 * 이번 최소 구현 범위는 플랫폼 관리자(platformRole 보유자) 로그인만 지원한다 —
 * organization_members가 아직 없어 조직 선택 흐름(5.2절)은 다루지 않는다.
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

        if (user.getPlatformRole() == null) {
            // 조직 기반 로그인은 이번 범위 밖이다(위 클래스 설명 참고).
            throw new ApplicationException(IdentityErrorCode.ORGANIZATION_LOGIN_NOT_SUPPORTED);
        }

        String accessToken = jwtProvider.createPlatformAccessToken(user);
        IdentityDto.Response.PlatformAdminInfo platformAdmin = new IdentityDto.Response.PlatformAdminInfo(
                user.getId(),
                user.getLoginId(),
                user.getName(),
                user.getPlatformRole().name()
        );

        return IdentityDto.Response.Login.success(accessToken, platformAdmin);
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
}
