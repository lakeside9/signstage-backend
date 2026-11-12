package com.eformworks.signstage.backend.core.config;

import com.eformworks.signstage.backend.core.web.PartnerDirectOrderInterceptor;
import com.eformworks.signstage.backend.core.web.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 서블릿 인터셉터 등록. accessKey 소지만으로 접근하는 JWT-free API(포털/프로젝터)에
 * 레이트 리밋을 걸고, 보류 중인 파트너 직접 수주 API를 끈다({@link PartnerDirectOrderInterceptor}).
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final PartnerDirectOrderInterceptor partnerDirectOrderInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/portal/**", "/api/projector/**");
        registry.addInterceptor(partnerDirectOrderInterceptor).addPathPatterns("/api/organizations/**");
    }
}
