package com.eformworks.signstage.backend.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * core.jpa.BaseEntity의 @CreatedDate/@LastModifiedDate/@CreatedBy/@LastModifiedBy를 활성화한다.
 * @CreatedBy/@LastModifiedBy는 core.jpa.SecurityAuditorAware가 채운다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
