package com.eformworks.signstage.backend.feature.order.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.order.dto.OrderInspectionDto;
import com.eformworks.signstage.backend.feature.order.entity.InspectionDisputeStatus;
import com.eformworks.signstage.backend.feature.order.entity.InspectionStatus;
import com.eformworks.signstage.backend.feature.order.service.OrderInspectionService;
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

/** 발주 검수 API(signstage-docs business/platform-order-partner-subcontract-review.md D11·D13). */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자의 검수 API")
@RestController
@RequestMapping("/api/platform-admin/order-inspections")
@RequiredArgsConstructor
public class PlatformAdminOrderInspectionController {

    private final OrderInspectionService orderInspectionService;
    private final TraceIdProvider traceIdProvider;

    @Operation(
            summary = "검수 목록 조회",
            description = "조회 전에 완료된 행사의 검수를 만들고 기한이 지난 대기 검수를 승인 간주한다(매일 배치와 같은 처리)."
    )
    @GetMapping
    public ApiResponse<PageResponse<OrderInspectionDto.Response.InspectionSummary>> findInspections(
            @RequestParam(required = false) InspectionStatus status,
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) InspectionDisputeStatus disputeStatus,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        orderInspectionService.syncInspections();
        return ApiResponse.success(
                PageResponse.from(orderInspectionService.findInspections(status, organizationId, disputeStatus, pageable)),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "감액 유형 조회", description = "계약 기준표의 감액 유형과 기준 비율(발주 금액 대비 %).")
    @GetMapping("/deduction-types")
    public ApiResponse<List<OrderInspectionDto.Response.DeductionTypeOption>> findDeductionTypes() {
        return ApiResponse.success(orderInspectionService.findDeductionTypes(), traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "감액 이의 조정",
            description = "이의 제기 중인 검수의 최종 감액을 정한다. 지급 대상 금액 차이는 원장에 조정으로 남는다. "
                    + "ACTION_INSPECTION_DISPUTE_RESOLVE(PLATFORM_SUPER) 권한이 필요하다."
    )
    @PostMapping("/{inspectionId}/resolve-dispute")
    public ApiResponse<OrderInspectionDto.Response.InspectionSummary> resolveDispute(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long inspectionId,
            @Valid @RequestBody OrderInspectionDto.Request.ResolveDispute request
    ) {
        return ApiResponse.success(
                orderInspectionService.resolveDispute(inspectionId, currentUser.userId(), currentUser.platformRole(), request),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(
            summary = "검수 승인",
            description = "하자·지체 메모와 감액(사유·유형 필수)을 기록한다. ACTION_ORDER_INSPECTION 권한이 필요하고, "
                    + "발주 금액의 30%를 넘는 감액은 ACTION_INSPECTION_LARGE_DEDUCTION(PLATFORM_SUPER)이 필요하다."
    )
    @PostMapping("/{inspectionId}/approve")
    public ApiResponse<OrderInspectionDto.Response.InspectionSummary> approveInspection(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long inspectionId,
            @Valid @RequestBody OrderInspectionDto.Request.Approve request
    ) {
        return ApiResponse.success(
                orderInspectionService.approveInspection(inspectionId, currentUser.userId(), currentUser.platformRole(), request),
                traceIdProvider.getTraceId()
        );
    }
}
