package com.eformworks.signstage.backend.feature.organization.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.organization.dto.PartnerSubcontractProfileDto;
import com.eformworks.signstage.backend.feature.organization.service.PartnerSubcontractProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 플랫폼 관리자의 파트너 외주 정보 조회·정산 계좌 확인 API(D22). */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자의 파트너 외주 정보 API")
@RestController
@RequestMapping("/api/platform-admin/organizations/{organizationId}/partner-profile")
@RequiredArgsConstructor
public class PlatformAdminPartnerSubcontractProfileController {

    private final PartnerSubcontractProfileService partnerSubcontractProfileService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "파트너 외주 정보 조회")
    @GetMapping
    public ApiResponse<PartnerSubcontractProfileDto.Response.Profile> retrieveProfile(@PathVariable Long organizationId) {
        PartnerSubcontractProfileDto.Response.Profile response =
                partnerSubcontractProfileService.retrieveProfileByPlatformAdmin(organizationId);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "정산 계좌 예금주 확인", description = "ACTION_PARTNER_PROFILE_VERIFY 권한이 필요하다.")
    @PostMapping("/account-verification")
    public ApiResponse<PartnerSubcontractProfileDto.Response.Profile> verifySettlementAccount(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId
    ) {
        PartnerSubcontractProfileDto.Response.Profile response = partnerSubcontractProfileService
                .verifySettlementAccount(organizationId, currentUser.userId(), currentUser.platformRole());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
