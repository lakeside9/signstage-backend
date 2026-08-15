package com.eformworks.signstage.backend.core.jpa;

import com.eformworks.signstage.backend.core.security.CurrentUser;
import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * BaseEntity의 @CreatedBy/@LastModifiedBy를 채운다.
 * 규칙은 signstage-docs database/audit-columns.md 6장을 따른다.
 *
 * <p>SecurityContext에 인증된 {@link CurrentUser}가 없으면(비로그인 요청, 배치 등)
 * 빈 값을 반환한다. 그 경우 각 Entity/Service가 필요에 따라 {@link BaseEntity#assignCreatedBy}로
 * 직접 채운다(예: 조직 최초 생성 시 소유자 자신을 생성 주체로 지정).
 */
@Component
public class SecurityAuditorAware implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .filter(CurrentUser.class::isInstance)
                .map(principal -> ((CurrentUser) principal).userId());
    }
}
