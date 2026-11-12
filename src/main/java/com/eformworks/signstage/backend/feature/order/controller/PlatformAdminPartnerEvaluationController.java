package com.eformworks.signstage.backend.feature.order.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.order.dto.MarginSimulationDto;
import com.eformworks.signstage.backend.feature.order.dto.PartnerEvaluationDto;
import com.eformworks.signstage.backend.feature.order.service.MarginSimulationService;
import com.eformworks.signstage.backend.feature.order.service.PartnerEvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 파트너 평가·영업 마진 시뮬레이션 API(signstage-docs business/platform-order-partner-subcontract-review.md 11장 8단계). */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자의 파트너 평가·마진 시뮬레이션 API")
@RestController
@RequestMapping("/api/platform-admin")
@RequiredArgsConstructor
public class PlatformAdminPartnerEvaluationController {

    private final PartnerEvaluationService partnerEvaluationService;
    private final MarginSimulationService marginSimulationService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "파트너 평가 조회", description = "발주 가능한 파트너와 발주 이력이 있는 파트너의 응답·검수 지표. 플랫폼 내부용이다.")
    @GetMapping("/partner-evaluations")
    public ApiResponse<List<PartnerEvaluationDto.Response.PartnerEvaluation>> findPartnerEvaluations() {
        return ApiResponse.success(partnerEvaluationService.findEvaluations(), traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "영업 마진 시뮬레이션",
            description = "견적 입력으로 고객 금액과 기본·파트너별 발주 금액, 마진을 계산한다. 저장하지 않는다."
    )
    @PostMapping("/margin-simulations")
    public ApiResponse<MarginSimulationDto.Response.Simulation> simulateMargin(
            @Valid @RequestBody MarginSimulationDto.Request.Simulate request
    ) {
        return ApiResponse.success(marginSimulationService.simulate(request), traceIdProvider.getTraceId());
    }
}
