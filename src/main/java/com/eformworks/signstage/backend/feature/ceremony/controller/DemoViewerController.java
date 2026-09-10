package com.eformworks.signstage.backend.feature.ceremony.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.DemoScenarioDto;
import com.eformworks.signstage.backend.feature.ceremony.service.DemoScenarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 데모 체험 계정(VIEWER) 전용 API — signstage-docs
 * business/demo-account-exhibition-signer-preview-review.md 4.2절. 일반 사용자 JWT로 인증하고
 * (로그인 응답의 {@code isDemoViewer}로 프런트가 이미 이 계정임을 안다), organizationId를
 * URL로 받지 않는다 — 호출자가 어느 데모 조직 소속인지는 서비스가 스스로 찾는다.
 */
@Tag(name = "DemoViewer", description = "데모 체험 계정 전용 API")
@RestController
@RequestMapping("/api/demo-viewer")
@RequiredArgsConstructor
public class DemoViewerController {

    private final DemoScenarioService demoScenarioService;
    private final TraceIdProvider traceIdProvider;

    @Operation(
            summary = "데모 시나리오 목록 조회",
            description = "데모 조직 소속 STARTED 이벤트를 최신순으로 자동 나열한다(5.4절) — 별도 노출 큐레이션이 없다."
    )
    @GetMapping("/scenarios")
    public ApiResponse<List<DemoScenarioDto.Response.DemoScenario>> findScenarios(
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        List<DemoScenarioDto.Response.DemoScenario> response = demoScenarioService.findScenarios(currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
