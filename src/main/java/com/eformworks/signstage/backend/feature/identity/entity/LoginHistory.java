package com.eformworks.signstage.backend.feature.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.hibernate.annotations.Immutable;

/**
 * 로그인 시도 이력. append-only 로그라 core.jpa.BaseEntity(4종 감사 컬럼)를 상속하지 않고
 * created_at만 가진다 — signstage-docs database/audit-columns.md 2장 "예외 2" 참고.
 * user_id는 FK를 걸지 않는다(대량 쓰기 테이블) — signstage-docs business/login-security.md 3.4절 참고.
 */
@Entity
@Table(name = "login_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Immutable
public class LoginHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "login_id_input", nullable = false, length = 50)
    private String loginIdInput;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LoginHistoryStatus status;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private LoginHistory(Long userId, String loginIdInput, LoginHistoryStatus status, String ipAddress, String userAgent) {
        this.userId = userId;
        this.loginIdInput = loginIdInput;
        this.status = status;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }
}
