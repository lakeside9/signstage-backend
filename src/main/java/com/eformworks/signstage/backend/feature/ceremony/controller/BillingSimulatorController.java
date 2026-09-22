package com.eformworks.signstage.backend.feature.ceremony.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.ceremony.service.BillingSimulatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BillingSimulatorController {
    private final BillingSimulatorService service;
    private final TraceIdProvider traceIdProvider;

    @GetMapping("/api/organizations/{organizationId}/billing-simulator")
    public ApiResponse<BillingSimulatorService.Catalog> catalog(
            @PathVariable Long organizationId, @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.success(service.catalog(organizationId, user.userId()), traceIdProvider.getTraceId());
    }
}
