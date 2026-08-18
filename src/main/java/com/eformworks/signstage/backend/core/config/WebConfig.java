package com.eformworks.signstage.backend.core.config;

import com.eformworks.signstage.backend.core.web.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 서블릿 인터셉터 등록. accessKey 소지만으로 접근하는 JWT-free API(포털/프로젝터)에
 * 레이트 리밋을 건다.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/portal/**", "/api/projector/**");
    }
}
