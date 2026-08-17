package com.eformworks.signstage.backend.core.config;

import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.JwtAuthenticationFilter;
import com.eformworks.signstage.backend.core.security.JwtProvider;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 인증(Authorization: Bearer)이 필요한 API와 그렇지 않은 API를 여기서 나눈다.
 * 목록은 각 컨트롤러의 {@code @SecurityRequirements(value = {})} 표기(backend-coding-convention.md
 * 14.2절)와 항상 같이 맞춘다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final TraceIdProvider traceIdProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/identity/signup", "/api/identity/login", "/api/identity/force-password-change").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**").permitAll()
                        // 서명자 포털은 JWT를 쓰지 않는다 — eventAccessKey/signerAccessKey 소지만으로
                        // 접근하고, 짝이 맞는지는 서비스 레이어(SignerPortalService)가 매 요청 검증한다
                        // (signstage-docs business/ceremony-feature-migration-review.md 2.3/4.5절 결정).
                        .requestMatchers("/api/portal/**").permitAll()
                        // platform_role 보유자만 통과. 등급별 세부 권한(예: 회원 상태 변경은 PLATFORM_OPS 이상)은
                        // 서비스 레이어에서 CurrentUser.platformRole()로 한 번 더 검사한다
                        // (signstage-docs backend/signup-approval-implementation-plan.md 4.2절).
                        .requestMatchers("/api/platform-admin/**")
                        .hasAnyRole("PLATFORM_SUPPORT", "PLATFORM_OPS", "PLATFORM_SUPER")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                writeErrorResponse(response, CommonErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeErrorResponse(response, CommonErrorCode.ACCESS_DENIED))
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * ApiResponse와 같은 모양의 JSON을 직접 만든다. 이 시점은 서블릿 필터 단계라
     * Jackson ObjectMapper 빈(스프링부트 4.1부터 별도 모듈 tools.jackson 계열로
     * 옮겨져 여기서 그대로 재사용하기 애매하다)에 기대지 않는다.
     */
    private void writeErrorResponse(HttpServletResponse response, CommonErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        String body = "{\"code\":\"%s\",\"message\":\"%s\",\"data\":null,\"traceId\":\"%s\"}".formatted(
                errorCode.getCode(),
                errorCode.getMessage(),
                traceIdProvider.getTraceId()
        );
        response.getWriter().write(body);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
