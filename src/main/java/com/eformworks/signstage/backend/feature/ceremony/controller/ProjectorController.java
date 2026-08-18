package com.eformworks.signstage.backend.feature.ceremony.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.ProjectorDto;
import com.eformworks.signstage.backend.feature.ceremony.dto.StrokeDataDto;
import com.eformworks.signstage.backend.feature.ceremony.service.ProjectorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 프로젝터 화면(전시용 화면). JWT 없이 {@code eventAccessKey} 소지만으로 접근한다
 * (서명자 포털과 같은 인가 모델) — {@code SecurityConfig}에서 {@code /api/projector/**}를
 * permitAll로 열어두고, {@code WebConfig}의 {@code RateLimitInterceptor}도 이 경로에 건다.
 */
@Tag(name = "Projector", description = "공개 프로젝터 화면 API (인증 불필요)")
@RestController
@RequestMapping("/api/projector/events/{eventAccessKey}")
@RequiredArgsConstructor
public class ProjectorController {

    private final ProjectorService projectorService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "프로젝터 컨텍스트 조회", description = "매핑된 전시용(EXHIBITION) 문서 정보와 서명란을 돌려준다.")
    @SecurityRequirements(value = {})
    @GetMapping
    public ApiResponse<ProjectorDto.Response.ProjectorContext> retrieveContext(
            @PathVariable String eventAccessKey
    ) {
        ProjectorDto.Response.ProjectorContext response = projectorService.retrieveContext(eventAccessKey);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "전시용 문서 페이지 렌더링", description = "EXHIBITION 매핑이 없으면 404다.")
    @SecurityRequirements(value = {})
    @GetMapping("/pages/{pageIndex}")
    public ResponseEntity<byte[]> renderPage(
            @PathVariable String eventAccessKey,
            @PathVariable int pageIndex,
            @RequestParam(defaultValue = "1.5") float scale
    ) {
        byte[] png = projectorService.renderPage(eventAccessKey, pageIndex, scale);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
    }

    @Operation(summary = "실시간 스트로크 캐치업 조회", description = "그 이후의 획은 WebSocket(SIGNATURE_STROKE_SUBMITTED)으로 이어받는다.")
    @SecurityRequirements(value = {})
    @GetMapping("/strokes")
    public ApiResponse<List<StrokeDataDto.Response.StrokeSummary>> findStrokes(
            @PathVariable String eventAccessKey
    ) {
        List<StrokeDataDto.Response.StrokeSummary> response = projectorService.findStrokes(eventAccessKey);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
