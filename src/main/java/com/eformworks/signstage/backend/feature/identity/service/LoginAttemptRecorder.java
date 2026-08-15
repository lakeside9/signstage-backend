package com.eformworks.signstage.backend.feature.identity.service;

import com.eformworks.signstage.backend.feature.identity.repository.LoginHistoryRepository;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.identity.entity.LoginHistory;
import com.eformworks.signstage.backend.feature.identity.entity.LoginHistoryStatus;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 시도 기록(login_history)과 그에 따른 실패 카운터/잠금 갱신을 담당한다.
 *
 * <p>{@code IdentityService.login()}은 실패 시 기록을 남긴 뒤 {@code ApplicationException}을
 * 던지는데, 같은 트랜잭션 안에서 기록하면 그 예외 때문에 트랜잭션 전체가 롤백되어
 * 정작 남겨야 할 실패 이력이 사라진다. 이를 막기 위해 이 클래스의 메서드는 전부
 * {@code REQUIRES_NEW}로 별도 트랜잭션에서 즉시 커밋한다.
 */
@Component
@RequiredArgsConstructor
class LoginAttemptRecorder {

    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final long LOCK_MINUTES = 15;

    private final LoginHistoryRepository loginHistoryRepository;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(Long userId, String loginIdInput, String ipAddress, String userAgent) {
        save(userId, loginIdInput, LoginHistoryStatus.SUCCESS, ipAddress, userAgent);
        userRepository.findById(userId).ifPresent(User::resetFailedLoginCount);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordInvalidPassword(Long userId, String loginIdInput, String ipAddress, String userAgent) {
        save(userId, loginIdInput, LoginHistoryStatus.FAILED_INVALID_PASSWORD, ipAddress, userAgent);
        userRepository.findById(userId).ifPresent(user -> {
            user.increaseFailedLoginCount();
            if (user.getFailedLoginCount() >= MAX_FAILED_LOGIN_ATTEMPTS) {
                user.lockUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long userId, String loginIdInput, LoginHistoryStatus status, String ipAddress, String userAgent) {
        save(userId, loginIdInput, status, ipAddress, userAgent);
    }

    private void save(Long userId, String loginIdInput, LoginHistoryStatus status, String ipAddress, String userAgent) {
        loginHistoryRepository.save(LoginHistory.builder()
                .userId(userId)
                .loginIdInput(loginIdInput)
                .status(status)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build());
    }
}
