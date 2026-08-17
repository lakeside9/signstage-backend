package com.eformworks.signstage.backend.core.web;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 포털 API(accessKey 소지만으로 접근하는 JWT-free API) 요청을 IP 기준으로 제한한다
 * (signstage-docs business/ceremony-feature-migration-review.md §4.5). 초과 시 던지는
 * {@link ApplicationException}은 {@code GlobalExceptionHandler}가 그대로 429 JSON으로 만든다.
 *
 * <p>리버스 프록시 뒤에 있을 때 {@code X-Forwarded-For}를 신뢰하는 문제는 아직 다루지 않는다
 * — 이 프로젝트에 리버스 프록시 설정이 없어 {@code request.getRemoteAddr()}로 충분하다.
 */
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!rateLimiter.tryAcquire(request.getRemoteAddr())) {
            throw new ApplicationException(CommonErrorCode.TOO_MANY_REQUESTS);
        }
        return true;
    }
}
