package com.eformworks.signstage.backend.feature.platformadmin.entity;

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
 * 플랫폼 관리자 제어 행위 기록. append-only 로그라 core.jpa.BaseEntity(4종 감사 컬럼)를
 * 상속하지 않고 created_at만 가진다 — signstage-docs database/audit-columns.md 2장
 * "예외 2", feature.identity.entity.LoginHistory와 같은 패턴이다.
 * admin_user_id/organization_id는 FK를 걸지 않는다(같은 이유).
 */
@Entity
@Table(name = "platform_admin_audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Immutable
public class PlatformAdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_user_id", nullable = false)
    private Long adminUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    private PlatformAdminAction action;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(length = 500)
    private String detail;

    @Column(name = "request_path", length = 255)
    private String requestPath;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private PlatformAdminAuditLog(
            Long adminUserId,
            PlatformAdminAction action,
            Long targetUserId,
            Long organizationId,
            String detail,
            String requestPath
    ) {
        this.adminUserId = adminUserId;
        this.action = action;
        this.targetUserId = targetUserId;
        this.organizationId = organizationId;
        this.detail = detail;
        this.requestPath = requestPath;
    }
}
