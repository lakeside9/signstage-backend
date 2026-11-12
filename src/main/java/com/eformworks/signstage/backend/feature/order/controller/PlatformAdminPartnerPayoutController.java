package com.eformworks.signstage.backend.feature.order.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.order.dto.PartnerPayoutDto;
import com.eformworks.signstage.backend.feature.order.entity.PartnerPayoutStatus;
import com.eformworks.signstage.backend.feature.order.service.PartnerPayoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 파트너 월 합산 지급 API(signstage-docs business/platform-order-partner-subcontract-review.md D11·D12). */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자의 파트너 지급 API")
@RestController
@RequestMapping("/api/platform-admin/partner-payouts")
@RequiredArgsConstructor
public class PlatformAdminPartnerPayoutController {

    private final PartnerPayoutService partnerPayoutService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "지급 목록 조회", description = "settlementMonth는 검수 승인월(yyyy-MM)이다.")
    @GetMapping
    public ApiResponse<PageResponse<PartnerPayoutDto.Response.PayoutSummary>> findPayouts(
            @RequestParam(required = false) String settlementMonth,
            @RequestParam(required = false) PartnerPayoutStatus status,
            @RequestParam(required = false) Long organizationId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(
                PageResponse.from(partnerPayoutService.findPayouts(settlementMonth, status, organizationId, pageable)),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "월 정산 생성", description = "승인월의 미정산 검수를 파트너별로 합산해 지급 건을 만든다. 지난달까지만 된다.")
    @PostMapping
    public ApiResponse<List<PartnerPayoutDto.Response.PayoutSummary>> createPayouts(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody PartnerPayoutDto.Request.CreatePayouts request
    ) {
        return ApiResponse.success(
                partnerPayoutService.createPayouts(request.getSettlementMonth(), currentUser.userId(), currentUser.platformRole()),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "파트너 원장 조회", description = "미지급 잔액(공급가)과 원장 항목.")
    @GetMapping("/ledger")
    public ApiResponse<PartnerPayoutDto.Response.Ledger> retrieveLedger(
            @RequestParam Long organizationId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(partnerPayoutService.retrieveLedger(organizationId, pageable), traceIdProvider.getTraceId());
    }

    @Operation(summary = "지급 상세 조회")
    @GetMapping("/{partnerPayoutId}")
    public ApiResponse<PartnerPayoutDto.Response.PayoutDetail> retrievePayout(@PathVariable Long partnerPayoutId) {
        return ApiResponse.success(partnerPayoutService.retrievePayout(partnerPayoutId), traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "고액 지급 승인",
            description = "기준 금액 이상 지급 건을 승인한다. ACTION_PARTNER_PAYOUT_LARGE_APPROVE(PLATFORM_SUPER) 권한이 필요하고, "
                    + "정산을 만든 사람은 승인할 수 없다."
    )
    @PostMapping("/{partnerPayoutId}/approve")
    public ApiResponse<PartnerPayoutDto.Response.PayoutDetail> approvePayout(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long partnerPayoutId
    ) {
        return ApiResponse.success(
                partnerPayoutService.approvePayout(partnerPayoutId, currentUser.userId(), currentUser.platformRole()),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "지급 완료 기록", description = "시스템 밖에서 이체한 뒤 지급일·이체 금액을 기록한다. 고액 지급은 승인 후에만 기록한다.")
    @PostMapping("/{partnerPayoutId}/mark-paid")
    public ApiResponse<PartnerPayoutDto.Response.PayoutDetail> markPaid(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long partnerPayoutId,
            @Valid @RequestBody PartnerPayoutDto.Request.MarkPaid request
    ) {
        return ApiResponse.success(
                partnerPayoutService.markPaid(partnerPayoutId, currentUser.userId(), currentUser.platformRole(), request),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "지급 취소", description = "이체 전 지급 건만 취소한다. 묶였던 검수는 다시 정산할 수 있다.")
    @PostMapping("/{partnerPayoutId}/cancel")
    public ApiResponse<PartnerPayoutDto.Response.PayoutDetail> cancelPayout(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long partnerPayoutId,
            @Valid @RequestBody PartnerPayoutDto.Request.Cancel request
    ) {
        return ApiResponse.success(
                partnerPayoutService.cancelPayout(partnerPayoutId, currentUser.userId(), currentUser.platformRole(), request.getReason()),
                traceIdProvider.getTraceId()
        );
    }
}
