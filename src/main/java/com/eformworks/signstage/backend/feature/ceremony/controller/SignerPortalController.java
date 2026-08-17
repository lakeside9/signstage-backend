package com.eformworks.signstage.backend.feature.ceremony.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.SignerPortalDto;
import com.eformworks.signstage.backend.feature.ceremony.service.SignerPortalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서명자 포털. JWT 없이 {@code eventAccessKey}/{@code signerAccessKey} 소지만으로 접근한다
 * (signstage-docs business/ceremony-feature-migration-review.md 2.3/4.5절 결정) —
 * {@code SecurityConfig}에서 {@code /api/portal/**}를 permitAll로 열어둔다.
 */
@Tag(name = "SignerPortal", description = "서명자 포털 API (인증 불필요)")
@RestController
@RequestMapping("/api/portal/events/{eventAccessKey}/signers/{signerAccessKey}")
@RequiredArgsConstructor
public class SignerPortalController {

    private final SignerPortalService signerPortalService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "포털 컨텍스트 조회", description = "배정된 필수 서명란과 각 서명란의 서명 여부를 함께 돌려준다.")
    @SecurityRequirements(value = {})
    @GetMapping
    public ApiResponse<SignerPortalDto.Response.PortalContext> retrievePortalContext(
            @PathVariable String eventAccessKey,
            @PathVariable String signerAccessKey
    ) {
        SignerPortalDto.Response.PortalContext response =
                signerPortalService.retrievePortalContext(eventAccessKey, signerAccessKey);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "서명 스트로크 제출", description = "본인에게 배정된, 이 하위 행사에 매핑된 서명란만 제출할 수 있다.")
    @SecurityRequirements(value = {})
    @PostMapping("/strokes")
    public ApiResponse<SignerPortalDto.Response.StrokeSubmitted> submitStroke(
            @PathVariable String eventAccessKey,
            @PathVariable String signerAccessKey,
            @Valid @RequestBody SignerPortalDto.Request.SubmitStroke request
    ) {
        SignerPortalDto.Response.StrokeSubmitted response =
                signerPortalService.submitStroke(eventAccessKey, signerAccessKey, request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "서명 완료 처리", description = "배정된 필수 서명란 전부에 스트로크가 있어야 완료할 수 있다.")
    @SecurityRequirements(value = {})
    @PostMapping("/complete")
    public ApiResponse<Void> completeSignature(
            @PathVariable String eventAccessKey,
            @PathVariable String signerAccessKey
    ) {
        signerPortalService.completeSignature(eventAccessKey, signerAccessKey);
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }
}
