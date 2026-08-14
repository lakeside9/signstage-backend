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
 * <p>{@code createdBy}/{@code updatedBy}는 {@code AuditorAware<Long>} 빈이 등록되어야 채워진다.
 * JWT 인증(core.security)이 아직 구현되지 않아 현재는 이 빈이 없고, 두 컬럼은 nullable이므로
 * 당분간 null로 남는다. 인증 구현 후 AuditorAware 빈을 추가하면 자동으로 채워진다.
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
}
