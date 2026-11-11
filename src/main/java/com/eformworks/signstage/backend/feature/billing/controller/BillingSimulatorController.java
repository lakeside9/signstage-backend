package com.eformworks.signstage.backend.feature.billing.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.billing.dto.BillingSimulatorDto;
import com.eformworks.signstage.backend.feature.billing.service.BillingSimulatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "BillingSimulator", description = "파트너사 과금 시뮬레이터 API")
@RestController
@RequiredArgsConstructor
public class BillingSimulatorController {
    private final BillingSimulatorService service;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "과금 시뮬레이터 카탈로그 조회")
    @GetMapping("/api/organizations/{organizationId}/billing-simulator")
    public ApiResponse<BillingSimulatorDto.Response.Catalog> catalog(
            @PathVariable Long organizationId, @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.success(service.catalog(organizationId, user.userId()), traceIdProvider.getTraceId());
    }
}
