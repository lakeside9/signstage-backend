package com.eformworks.signstage.backend.core.config;

import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.JwtAuthenticationFilter;
import com.eformworks.signstage.backend.core.security.JwtProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
                        .requestMatchers("/api/identity/login", "/api/identity/force-password-change").permitAll()
                        // 조직 최초 생성(POST /api/organizations)은 소유자 계정을 함께 만드는 가입 경로라
                        // 인증 없이 호출한다(signstage-docs business/user-organization-design.md 5.1절 (a)).
                        .requestMatchers(HttpMethod.POST, "/api/organizations").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(CommonErrorCode.UNAUTHORIZED.getHttpStatus().value());
                            response.setContentType("application/json;charset=UTF-8");
                            // ApiResponse와 같은 모양의 JSON을 직접 만든다. 이 시점은 서블릿 필터 단계라
                            // Jackson ObjectMapper 빈(스프링부트 4.1부터 별도 모듈 tools.jackson 계열로
                            // 옮겨져 여기서 그대로 재사용하기 애매하다)에 기대지 않는다.
                            String body = "{\"code\":\"%s\",\"message\":\"%s\",\"data\":null,\"traceId\":\"%s\"}".formatted(
                                    CommonErrorCode.UNAUTHORIZED.getCode(),
                                    CommonErrorCode.UNAUTHORIZED.getMessage(),
                                    traceIdProvider.getTraceId()
                            );
                            response.getWriter().write(body);
                        })
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
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
