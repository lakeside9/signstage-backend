package com.eformworks.signstage.backend.feature.order.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.order.dto.PartnerPayoutDto;
import com.eformworks.signstage.backend.feature.order.service.PartnerPayoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 파트너가 받는 지급 내역 API. ACTION_PARTNER_PAYOUT_VIEW 권한(OWNER·ADMIN)이 필요하다. */
@Tag(name = "PartnerPayout", description = "파트너 지급 내역 API")
@RestController
@RequestMapping("/api/organizations/{organizationId}/partner-payouts")
@RequiredArgsConstructor
public class PartnerPayoutController {

    private final PartnerPayoutService partnerPayoutService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "지급 내역 목록 조회")
    @GetMapping
    public ApiResponse<PageResponse<PartnerPayoutDto.Response.PayoutSummary>> findReceivedPayouts(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(
                PageResponse.from(partnerPayoutService.findReceivedPayouts(organizationId, currentUser.userId(), pageable)),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "원장 조회", description = "미지급 잔액(공급가)과 원장 항목.")
    @GetMapping("/ledger")
    public ApiResponse<PartnerPayoutDto.Response.Ledger> retrieveReceivedLedger(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(
                partnerPayoutService.retrieveReceivedLedger(organizationId, currentUser.userId(), pageable),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "지급 상세 조회")
    @GetMapping("/{partnerPayoutId}")
    public ApiResponse<PartnerPayoutDto.Response.PayoutDetail> retrieveReceivedPayout(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long partnerPayoutId
    ) {
        return ApiResponse.success(
                partnerPayoutService.retrieveReceivedPayout(organizationId, partnerPayoutId, currentUser.userId()),
                traceIdProvider.getTraceId()
        );
    }
}
