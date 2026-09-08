package com.eformworks.signstage.backend.feature.identity.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import com.eformworks.signstage.backend.core.i18n.InternationalizationDefaults;
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

    /** 탈퇴 시(PII 마스킹) NULL로 비워진다 — {@link #withdraw(String)} 참고. */
    @Column(unique = true, length = 255)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode;

    @Column(nullable = false, length = 10)
    private String locale;

    @Column(name = "time_zone_id", nullable = false, length = 50)
    private String timeZoneId;

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
            String languageCode,
            String locale,
            String timeZoneId,
            String password,
            UserStatus status,
            PlatformRole platformRole,
            boolean passwordResetRequired
    ) {
        this.loginId = loginId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.languageCode = InternationalizationDefaults.languageCodeOrDefault(languageCode);
        this.locale = InternationalizationDefaults.formatLocaleOrDefault(locale);
        this.timeZoneId = InternationalizationDefaults.timeZoneIdOrDefault(timeZoneId);
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

    public void changeProfile(
            String name,
            String email,
            String phone,
            String languageCode,
            String locale,
            String timeZoneId
    ) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.languageCode = InternationalizationDefaults.languageCodeOrDefault(languageCode);
        this.locale = InternationalizationDefaults.formatLocaleOrDefault(locale);
        this.timeZoneId = InternationalizationDefaults.timeZoneIdOrDefault(timeZoneId);
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

    /**
     * PLATFORM_SUPER가 이미 platform_role이 있는 계정의 등급만 바꿀 때 사용한다(부여/해제는
     * {@link #revokePlatformRole()}). null을 넘기지 않는다 — null로 비우는 것은 해제 전용
     * 메서드의 역할이라 이 메서드는 등급 값만 다룬다.
     */
    public void changePlatformRole(PlatformRole platformRole) {
        this.platformRole = platformRole;
    }

    /**
     * 관리자가 회원을 강제 탈퇴시킬 때 쓰는 논리적 삭제(soft delete) + PII 마스킹이다
     * (signstage-docs business/user-organization-design.md 8.2절). {@code id}와 그 id를
     * 참조하는 관계(organization_members 등)는 그대로 남기고, 다시 로그인할 수 없도록
     * loginId/이름/이메일/전화번호/비밀번호를 지운다.
     *
     * @param unusablePasswordHash 아무도 평문을 알 수 없는, 그러나 유효한 형식의 해시
     *                             (호출부가 {@code passwordEncoder.encode(임의값)}으로 만들어 넘긴다)
     */
    public void withdraw(String unusablePasswordHash) {
        this.loginId = "withdrawn_user_" + this.id;
        this.name = "(탈퇴한 사용자)";
        this.email = null;
        this.phone = null;
        this.password = unusablePasswordHash;
        this.status = UserStatus.WITHDRAWN;
    }
}
