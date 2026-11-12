package com.eformworks.signstage.backend.feature.order.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.order.dto.PartnerOffsetDto;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOffsetStatus;
import com.eformworks.signstage.backend.feature.order.service.PartnerOffsetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

/** 파트너 귀책 상계 API(signstage-docs business/platform-order-partner-subcontract-review.md D13). */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자의 파트너 귀책 상계 API")
@RestController
@RequestMapping("/api/platform-admin/partner-offsets")
@RequiredArgsConstructor
public class PlatformAdminPartnerOffsetController {

    private final PartnerOffsetService partnerOffsetService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "귀책 상계 목록 조회")
    @GetMapping
    public ApiResponse<PageResponse<PartnerOffsetDto.Response.OffsetSummary>> findOffsets(
            @RequestParam(required = false) PartnerOffsetStatus status,
            @RequestParam(required = false) Long organizationId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(
                PageResponse.from(partnerOffsetService.findOffsets(status, organizationId, pageable)),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(
            summary = "귀책 상계 등록",
            description = "수락된 발주에 파트너 귀책 금액(고객 환불 귀책분 등)을 등록한다. 다음 정산에서 지급액에서 뺀다. "
                    + "ACTION_PARTNER_PAYMENT_APPROVE 권한이 필요하다."
    )
    @PostMapping
    public ApiResponse<PartnerOffsetDto.Response.OffsetSummary> registerOffset(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody PartnerOffsetDto.Request.Register request
    ) {
        return ApiResponse.success(
                partnerOffsetService.registerOffset(currentUser.userId(), currentUser.platformRole(), request),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "귀책 상계 취소", description = "정산에 반영되기 전의 상계만 취소한다. 원장에 취소(+)가 남는다.")
    @PostMapping("/{partnerOffsetId}/cancel")
    public ApiResponse<PartnerOffsetDto.Response.OffsetSummary> cancelOffset(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long partnerOffsetId,
            @Valid @RequestBody PartnerOffsetDto.Request.Cancel request
    ) {
        return ApiResponse.success(
                partnerOffsetService.cancelOffset(partnerOffsetId, currentUser.userId(), currentUser.platformRole(), request.getReason()),
                traceIdProvider.getTraceId()
        );
    }
}
