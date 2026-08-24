package com.eformworks.signstage.backend.feature.ceremony.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.SignerPortalDto;
import com.eformworks.signstage.backend.feature.ceremony.dto.StrokeDataDto;
import com.eformworks.signstage.backend.feature.ceremony.service.SignerPortalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @Operation(
            summary = "서명용(CONTRACT) 문서 배경 조회",
            description = "문서 전체와 배치된 모든 서명란을 돌려준다(본인 것만이 아니다). CONTRACT 매핑이 없으면 data가 null이다."
    )
    @SecurityRequirements(value = {})
    @GetMapping("/contract")
    public ApiResponse<SignerPortalDto.Response.PortalContractDocument> retrieveContract(
            @PathVariable String eventAccessKey,
            @PathVariable String signerAccessKey
    ) {
        SignerPortalDto.Response.PortalContractDocument response =
                signerPortalService.retrieveContract(eventAccessKey, signerAccessKey);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "서명용 문서 페이지 렌더링", description = "CONTRACT 매핑이 없으면 404다.")
    @SecurityRequirements(value = {})
    @GetMapping("/contract/pages/{pageIndex}")
    public ResponseEntity<byte[]> renderContractPage(
            @PathVariable String eventAccessKey,
            @PathVariable String signerAccessKey,
            @PathVariable int pageIndex,
            @RequestParam(defaultValue = "1.5") float scale
    ) {
        byte[] png = signerPortalService.renderContractPage(eventAccessKey, signerAccessKey, pageIndex, scale);
        // ProjectorController와 같은 이유(TemplateService 캐시 + accessKey 기반 공개 자원이라
        // 공유 캐시까지 허용).
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(png);
    }

    @Operation(
            summary = "실시간 스트로크 캐치업 조회",
            description = "본인 것만이 아니라 이벤트 전체 획이다 — 같은 문서에 이미 서명한 다른 사람도 함께 보여주기 위해서다. "
                    + "그 이후의 획은 WebSocket(SIGNATURE_STROKE_SUBMITTED)으로 이어받는다."
    )
    @SecurityRequirements(value = {})
    @GetMapping("/strokes")
    public ApiResponse<List<StrokeDataDto.Response.StrokeSummary>> findStrokes(
            @PathVariable String eventAccessKey,
            @PathVariable String signerAccessKey
    ) {
        List<StrokeDataDto.Response.StrokeSummary> response = signerPortalService.findStrokes(eventAccessKey, signerAccessKey);
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

    @Operation(
            summary = "서명란 지우기(재서명)",
            description = "행사 진행 중(STARTED)이고 아직 완료 전인 서명자만 자기 서명란을 지우고 다시 그릴 수 있다. "
                    + "완료 후에는 관리자의 재서명 요청(REPLACE)을 거쳐야 한다."
    )
    @SecurityRequirements(value = {})
    @DeleteMapping("/fields/{templateFieldId}/strokes")
    public ApiResponse<Void> clearFieldStroke(
            @PathVariable String eventAccessKey,
            @PathVariable String signerAccessKey,
            @PathVariable Long templateFieldId
    ) {
        signerPortalService.clearFieldStroke(eventAccessKey, signerAccessKey, templateFieldId);
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }
}
