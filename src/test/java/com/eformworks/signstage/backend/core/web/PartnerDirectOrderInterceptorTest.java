package com.eformworks.signstage.backend.core.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@link PartnerDirectOrderInterceptor} 단위 테스트 — 파트너 직접 수주 보류(D29 안 A).
 */
class PartnerDirectOrderInterceptorTest {

    private final PartnerDirectOrderInterceptor disabled = new PartnerDirectOrderInterceptor(new PartnerDirectOrderPolicy(false));

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("꺼져 있으면 파트너의 행사 직접 등록·플랜 확정을 거부한다")
    void preHandle_blocksDirectOrderWrites() {
        authenticate(null);

        assertBlocked("POST", "/api/organizations/1/ceremonies");
        assertBlocked("POST", "/api/organizations/1/ceremonies/2/plan/confirm");
        assertBlocked("PUT", "/api/organizations/1/ceremonies/2/unit-product-cart/lines/3");
        assertBlocked("GET", "/api/organizations/1/billing-simulator");
    }

    @Test
    @DisplayName("조회와 플랫폼 수주 행사 운영 API는 열어 둔다")
    void preHandle_allowsReadsAndCeremonyOperations() {
        authenticate(null);

        assertAllowed("GET", "/api/organizations/1/ceremonies");
        assertAllowed("GET", "/api/organizations/1/ceremonies/2/unit-product-purchases");
        assertAllowed("PUT", "/api/organizations/1/ceremonies/2");
        assertAllowed("POST", "/api/organizations/1/partner-orders/3/accept");
    }

    @Test
    @DisplayName("플랫폼 관리자와 켜진 설정은 통과시킨다")
    void preHandle_allowsPlatformAdminOrEnabled() {
        authenticate("PLATFORM_SUPER");
        assertAllowed("POST", "/api/organizations/1/ceremonies");

        authenticate(null);
        assertThat(new PartnerDirectOrderInterceptor(new PartnerDirectOrderPolicy(true))
                .preHandle(request("POST", "/api/organizations/1/ceremonies"), new MockHttpServletResponse(), new Object()))
                .isTrue();
    }

    private void assertBlocked(String method, String uri) {
        assertThatThrownBy(() -> disabled.preHandle(request(method, uri), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.PARTNER_DIRECT_ORDER_DISABLED);
    }

    private void assertAllowed(String method, String uri) {
        assertThat(disabled.preHandle(request(method, uri), new MockHttpServletResponse(), new Object())).isTrue();
    }

    private static MockHttpServletRequest request(String method, String uri) {
        return new MockHttpServletRequest(method, uri);
    }

    private static void authenticate(String platformRole) {
        CurrentUser user = new CurrentUser(10L, "user", platformRole);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
}
