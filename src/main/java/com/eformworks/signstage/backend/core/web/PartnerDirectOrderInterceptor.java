package com.eformworks.signstage.backend.core.web;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 파트너 직접 수주 기능을 끈다 — signstage-docs business/platform-order-partner-subcontract-review.md
 * D01(2026-11-12 결정: 플랫폼 수주 단독, 파트너 직접 수주 보류)·D29 안 A(코드 유지, 설정으로 끔).
 *
 * <p>{@code signstage.partner-direct-order.enabled=false}(기본값)이면 파트너가 쓰던 행사 직접 등록,
 * 플랜 선택·확정, 장바구니·추가구매, 고객 견적·마진, 현장지원 요청, 마진 정책, 구독 신청·해지 요청,
 * 과금 시뮬레이터를 거부한다. 조회 API는 열어 둔다(플랫폼 수주 행사 화면이 한도·구매 내역을 읽는다).
 *
 * <p>플랫폼 관리자 토큰은 통과시킨다 — 데모 조직 행사는 플랫폼 관리자가 같은 API로 만들고 운영한다.
 * 다시 열 때는 설정만 바꾸고, 함께 정할 규칙(고객 귀속, 경로 고정, 채널 가격, 정산 화면 분리)은 D29를 따른다.
 */
@Component
public class PartnerDirectOrderInterceptor implements HandlerInterceptor {

    private static final String CEREMONY = "/api/organizations/*/ceremonies/*";

    /** 메서드와 무관하게 막는 경로. */
    private static final List<String> BLOCKED_ANY_METHOD = List.of(
            "/api/organizations/*/billing-simulator"
    );

    /** 조회(GET)는 열고 쓰기만 막는 경로. */
    private static final List<String> BLOCKED_WRITE = List.of(
            "/api/organizations/*/ceremonies",
            CEREMONY + "/plan",
            CEREMONY + "/plan/confirm",
            CEREMONY + "/unit-product-cart/**",
            CEREMONY + "/unit-product-purchases",
            CEREMONY + "/customer-margin",
            CEREMONY + "/customer-quotes/**",
            CEREMONY + "/onsite-support-requests/**",
            "/api/organizations/*/margin-policy/**",
            "/api/organizations/*/subscriptions/**"
    );

    private final boolean enabled;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public PartnerDirectOrderInterceptor(@Value("${signstage.partner-direct-order.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (enabled || isPlatformAdmin()) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean write = !HttpMethod.GET.matches(request.getMethod());
        boolean blocked = BLOCKED_ANY_METHOD.stream().anyMatch(pattern -> pathMatcher.match(pattern, path))
                || (write && BLOCKED_WRITE.stream().anyMatch(pattern -> pathMatcher.match(pattern, path)));
        if (blocked) {
            throw new ApplicationException(CommonErrorCode.PARTNER_DIRECT_ORDER_DISABLED);
        }
        return true;
    }

    private static boolean isPlatformAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getPrincipal() instanceof CurrentUser currentUser
                && currentUser.platformRole() != null;
    }
}
