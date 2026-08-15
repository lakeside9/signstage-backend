package com.eformworks.signstage.backend.core.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * created_by/created_at/updated_by/updated_at 감사 컬럼을 공통으로 가지는 Entity의 상위 클래스.
 * 규칙은 signstage-docs의 database/audit-columns.md를 따른다.
 *
 * <p>{@code createdBy}/{@code updatedBy}는 {@code core.jpa.SecurityAuditorAware}가 채운다.
 * 인증된 요청(SecurityContext에 CurrentUser가 있는 경우)에서만 채워지고, 그렇지 않으면
 * (예: 조직 최초 생성처럼 인증된 행위자가 아직 없는 요청) 비워둔다.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedBy
    @Column(updatable = false)
    private Long createdBy;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedBy
    private Long updatedBy;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    /**
     * AuditorAware가 채울 인증된 행위자가 아직 없는 시점(예: 조직 최초 생성 시 소유자 계정
     * 자신이 생성 주체가 되는 경우)에 생성자를 직접 지정해야 할 때만 사용한다.
     * 이미 값이 채워져 있으면 덮어쓰지 않는다.
     */
    public void assignCreatedBy(Long userId) {
        if (this.createdBy == null) {
            this.createdBy = userId;
        }
    }
}
