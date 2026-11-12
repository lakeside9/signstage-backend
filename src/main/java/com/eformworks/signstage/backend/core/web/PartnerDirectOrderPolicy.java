package com.eformworks.signstage.backend.core.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 파트너 직접 수주 기능을 켤지 — signstage-docs business/platform-order-partner-subcontract-review.md
 * D29 안 A. 서버는 {@link PartnerDirectOrderInterceptor}로 API를 막고, 프론트는
 * {@code GET /api/organizations/me/features}로 같은 값을 받아 화면을 숨긴다.
 */
@Component
public class PartnerDirectOrderPolicy {

    private final boolean enabled;

    public PartnerDirectOrderPolicy(@Value("${signstage.partner-direct-order.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
