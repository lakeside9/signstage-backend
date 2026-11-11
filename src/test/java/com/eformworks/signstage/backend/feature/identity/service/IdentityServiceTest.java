package com.eformworks.signstage.backend.feature.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.security.JwtProvider;
import com.eformworks.signstage.backend.feature.identity.dto.IdentityDto;
import com.eformworks.signstage.backend.feature.identity.entity.LoginHistoryStatus;
import com.eformworks.signstage.backend.feature.identity.entity.PlatformRole;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.entity.UserStatus;
import com.eformworks.signstage.backend.feature.identity.error.IdentityErrorCode;
import com.eformworks.signstage.backend.feature.identity.repository.UserHistoryRepository;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.identity.port.UserMembershipPort;
import io.jsonwebtoken.JwtException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link IdentityService} 로그인·강제 비밀번호 변경 흐름 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class IdentityServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserHistoryRepository userHistoryRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private LoginAttemptRecorder loginAttemptRecorder;
    @Mock
    private UserMembershipPort userMembershipPort;

    @InjectMocks
    private IdentityService identityService;

    private static final String IP_ADDRESS = "127.0.0.1";
    private static final String USER_AGENT = "test";

    @Test
    @DisplayName("없는 아이디로 로그인하면 실패 이력을 남기고 INVALID_CREDENTIAL을 던진다")
    void login_unknownLoginId_isRejected() {
        given(userRepository.findByLoginId("nobody")).willReturn(Optional.empty());

        assertThatThrownBy(() -> identityService.login(new IdentityDto.Request.Login("nobody", "pw"), IP_ADDRESS, USER_AGENT))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(IdentityErrorCode.INVALID_CREDENTIAL);
        verify(loginAttemptRecorder).recordFailure(null, "nobody", LoginHistoryStatus.FAILED_NOT_FOUND, IP_ADDRESS, USER_AGENT);
    }

    @Test
    @DisplayName("플랫폼 관리자는 platformRole을 담은 관리자 토큰을 받는다")
    void login_platformAdmin_receivesPlatformAccessToken() {
        User admin = user("admin", PlatformRole.PLATFORM_OPS);
        given(userRepository.findByLoginId("admin")).willReturn(Optional.of(admin));
        given(passwordEncoder.matches("pw", "encoded")).willReturn(true);
        given(jwtProvider.createPlatformAccessToken(1L, "admin", "PLATFORM_OPS")).willReturn("platform-token");

        IdentityDto.Response.Login response =
                identityService.login(new IdentityDto.Request.Login("admin", "pw"), IP_ADDRESS, USER_AGENT);

        assertThat(response.getAccessToken()).isEqualTo("platform-token");
        assertThat(response.getPlatformAdmin().getPlatformRole()).isEqualTo("PLATFORM_OPS");
        verify(jwtProvider, never()).createUserAccessToken(any(), any());
    }

    @Test
    @DisplayName("재설정 토큰이 유효하지 않으면 INVALID_RESET_TOKEN으로 변환한다")
    void forcePasswordChange_invalidToken_isConvertedToInvalidResetToken() {
        given(jwtProvider.parsePasswordResetToken("bad-token")).willThrow(new JwtException("invalid"));

        assertThatThrownBy(() -> identityService.forcePasswordChange(
                new IdentityDto.Request.ForcePasswordChange("bad-token", "current", "new-password")
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(IdentityErrorCode.INVALID_RESET_TOKEN);
        verify(userRepository, never()).findById(any());
    }

    private User user(String loginId, PlatformRole platformRole) {
        User user = User.builder()
                .loginId(loginId)
                .name(loginId)
                .password("encoded")
                .status(UserStatus.ACTIVE)
                .platformRole(platformRole)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}
