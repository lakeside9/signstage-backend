package com.eformworks.signstage.backend.feature.identity.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 계정. 조직 소속 여부와 무관하게 존재한다(signstage-docs business/user-organization-design.md 3장 참고).
 * 이번 최소 구현 범위(플랫폼 관리자 로그인)에서는 platformRole만 사용하고,
 * organization_members 등 조직 관련 필드/연관관계는 아직 다루지 않는다.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, unique = true, length = 50)
    private String loginId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false, length = 10)
    private String locale;

    @Column(nullable = false, length = 255)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_role", length = 20)
    private PlatformRole platformRole;

    @Column(name = "is_password_reset_required", nullable = false)
    private boolean passwordResetRequired;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Builder
    private User(
            String loginId,
            String name,
            String email,
            String phone,
            String locale,
            String password,
            UserStatus status,
            PlatformRole platformRole,
            boolean passwordResetRequired
    ) {
        this.loginId = loginId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.locale = locale != null ? locale : "ko-KR";
        this.password = password;
        this.status = status != null ? status : UserStatus.ACTIVE;
        this.platformRole = platformRole;
        this.passwordResetRequired = passwordResetRequired;
        this.failedLoginCount = 0;
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    public void increaseFailedLoginCount() {
        this.failedLoginCount++;
    }

    public void resetFailedLoginCount() {
        this.failedLoginCount = 0;
        this.lockedUntil = null;
    }

    public void lockUntil(LocalDateTime until) {
        this.lockedUntil = until;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
        this.passwordResetRequired = false;
    }

    public void changeProfile(String name, String email, String phone, String locale) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.locale = locale != null ? locale : this.locale;
    }

    /**
     * 플랫폼 관리자가 회원가입 승인/거절, 계정 비활성화·재활성화를 처리할 때 사용한다
     * (signstage-docs business/user-organization-design.md 5.1절 (a), platform-admin-member-management.md 4.2절).
     */
    public void changeStatus(UserStatus status) {
        this.status = status;
    }

    /**
     * 플랫폼 관리자가 이 사용자의 비밀번호를 강제로 재설정하게 만든다. 다음 로그인 시
     * 5.3절 강제 비밀번호 변경 흐름을 그대로 타게 된다(platform-admin-member-management.md 4.2절).
     * 관리자는 비밀번호 자체를 조회/대신 설정하지 않고 이 플래그만 켠다.
     */
    public void requirePasswordReset() {
        this.passwordResetRequired = true;
    }

    /**
     * PLATFORM_SUPER가 다른 플랫폼 관리자의 권한을 해제할 때 사용한다. 계정 자체를 막지는
     * 않는다 — platform_role만 비워 일반 사용자로 되돌린다(signstage-docs
     * business/user-organization-design.md 7.2절).
     */
    public void revokePlatformRole() {
        this.platformRole = null;
    }
}
