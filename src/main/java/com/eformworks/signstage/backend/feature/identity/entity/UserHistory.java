package com.eformworks.signstage.backend.feature.identity.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원(User) 정보 변경 이력. append-only다 — 카탈로그 *History류와 같은 패턴
 * (signstage-docs business/ceremony-billing-options-review.md 8장).
 *
 * <p>가입, 본인 프로필 수정({@code IdentityService#updateMe}), 관리자의 회원
 * 생성/상태 변경/강제 비밀번호 재설정/강제 탈퇴/관리자 계정 생성/등급 변경/권한 해제
 * ({@code PlatformAdminUserService}) — 회원 본인이 바꾸든 관리자가 바꾸든 이 지점 전부가
 * 그 순간의 전체 상태를 스냅샷 한 행씩 남긴다(2026-08-30 요청). 비밀번호(해시)는 절대
 * 스냅샷하지 않는다 — 이 테이블은 "회원 정보"이지 인증 정보가 아니다.
 *
 * <p><b>탈퇴 PII 마스킹과의 관계</b>: {@code User#withdraw}가 살아있는 {@code users} 행의
 * 이름/이메일/전화번호를 지우는 것과 같은 원칙을, 회원이 탈퇴할 때는 그 회원의 기존
 * {@code UserHistory} 행들에도 그대로 적용한다({@code UserHistoryRepository#maskPiiForUser})
 * — 그러지 않으면 이력 테이블이 "삭제된 PII를 되살릴 수 있는" 우회로가 되어 탈퇴 마스킹의
 * 취지(signstage-docs business/user-organization-design.md 8.2절)가 무의미해진다.
 */
@Entity
@Table(name = "user_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "login_id", nullable = false, length = 50)
    private String loginId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false, length = 10)
    private String locale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_role", length = 20)
    private PlatformRole platformRole;

    @Column(name = "is_password_reset_required", nullable = false)
    private boolean passwordResetRequired;

    @Builder
    private UserHistory(User user) {
        this.user = user;
        this.loginId = user.getLoginId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.phone = user.getPhone();
        this.locale = user.getLocale();
        this.status = user.getStatus();
        this.platformRole = user.getPlatformRole();
        this.passwordResetRequired = user.isPasswordResetRequired();
    }
}
