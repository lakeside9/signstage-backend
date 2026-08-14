package com.eformworks.signstage.backend.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * core.jpa.BaseEntity의 @CreatedDate/@LastModifiedDate(및 추후 @CreatedBy/@LastModifiedBy)를
 * 활성화한다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
