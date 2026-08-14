package com.eformworks.signstage.backend.feature.identity.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.identity.dto.IdentityDto;
import com.eformworks.signstage.backend.feature.identity.service.IdentityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Identity", description = "인증(로그인) API")
@RestController
@RequestMapping("/api/identity")
@RequiredArgsConstructor
public class IdentityController {

    private final IdentityService identityService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "로그인", description = "성공 시 비밀번호 변경이 필요하면 passwordResetToken을, 아니면 accessToken을 반환한다.")
    @SecurityRequirements(value = {})
    @PostMapping("/login")
    public ApiResponse<IdentityDto.Response.Login> login(
            @Valid @RequestBody IdentityDto.Request.Login request,
            HttpServletRequest httpRequest
    ) {
        IdentityDto.Response.Login response = identityService.login(
                request,
                resolveClientIp(httpRequest),
                httpRequest.getHeader("User-Agent")
        );
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "강제 비밀번호 변경", description = "로그인 응답의 passwordResetToken으로만 호출할 수 있다.")
    @SecurityRequirements(value = {})
    @PostMapping("/force-password-change")
    public ApiResponse<Void> forcePasswordChange(@Valid @RequestBody IdentityDto.Request.ForcePasswordChange request) {
        identityService.forcePasswordChange(request);
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
