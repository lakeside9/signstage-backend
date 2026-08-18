package com.eformworks.signstage.backend.feature.platformadmin.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import com.eformworks.signstage.backend.feature.ceremony.service.CeremonyService;
import com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminCeremonyPurchaseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 플랫폼 관리자의 행사 용량/선택옵션 추가구매 요청 승인/반려 API다(signstage-docs
 * business/ceremony-billing-options-review.md). 조회는 PLATFORM_SUPPORT 이상, 승인/반려는
 * PLATFORM_OPS 이상만 서비스에서 한 번 더 검사한다 — {@link PlatformAdminOrganizationRequestController}와
 * 같은 규약.
 */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자의 행사 추가구매 승인/반려 API")
@RestController
@RequestMapping("/api/platform-admin")
@RequiredArgsConstructor
public class PlatformAdminCeremonyPurchaseController {

    private final CeremonyService ceremonyService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "용량 추가구매 요청 목록 조회", description = "status로 필터링할 수 있다. 생략하면 전체 상태를 최신순으로 반환한다.")
    @GetMapping("/capacity-purchases")
    public ApiResponse<PageResponse<PlatformAdminCeremonyPurchaseDto.Response.CapacityPurchaseRequestSummary>> findCapacityPurchaseRequests(
            @RequestParam(required = false) PurchaseStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<PlatformAdminCeremonyPurchaseDto.Response.CapacityPurchaseRequestSummary> result =
                ceremonyService.findCapacityPurchaseRequests(status, pageable);
        return ApiResponse.success(PageResponse.from(result), traceIdProvider.getTraceId());
    }

    @Operation(summary = "용량 추가구매 요청 승인", description = "PLATFORM_OPS 이상만 호출할 수 있다.")
    @PostMapping("/capacity-purchases/{purchaseId}/approve")
    public ApiResponse<PlatformAdminCeremonyPurchaseDto.Response.CapacityPurchaseRequestSummary> approveCapacityPurchase(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long purchaseId
    ) {
        PlatformAdminCeremonyPurchaseDto.Response.CapacityPurchaseRequestSummary response = ceremonyService
                .approveCapacityPurchase(purchaseId, currentUser.userId(), currentUser.platformRole());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "용량 추가구매 요청 반려", description = "반려 사유를 남긴다. PLATFORM_OPS 이상만 호출할 수 있다.")
    @PutMapping("/capacity-purchases/{purchaseId}/reject")
    public ApiResponse<PlatformAdminCeremonyPurchaseDto.Response.CapacityPurchaseRequestSummary> rejectCapacityPurchase(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long purchaseId,
            @Valid @RequestBody PlatformAdminCeremonyPurchaseDto.Request.Reject request
    ) {
        PlatformAdminCeremonyPurchaseDto.Response.CapacityPurchaseRequestSummary response = ceremonyService
                .rejectCapacityPurchase(purchaseId, currentUser.userId(), currentUser.platformRole(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "선택옵션 추가구매 요청 목록 조회", description = "status로 필터링할 수 있다. 생략하면 전체 상태를 최신순으로 반환한다.")
    @GetMapping("/optional-feature-purchases")
    public ApiResponse<PageResponse<PlatformAdminCeremonyPurchaseDto.Response.OptionalFeaturePurchaseRequestSummary>> findOptionalFeaturePurchaseRequests(
            @RequestParam(required = false) PurchaseStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<PlatformAdminCeremonyPurchaseDto.Response.OptionalFeaturePurchaseRequestSummary> result =
                ceremonyService.findOptionalFeaturePurchaseRequests(status, pageable);
        return ApiResponse.success(PageResponse.from(result), traceIdProvider.getTraceId());
    }

    @Operation(summary = "선택옵션 추가구매 요청 승인", description = "PLATFORM_OPS 이상만 호출할 수 있다.")
    @PostMapping("/optional-feature-purchases/{purchaseId}/approve")
    public ApiResponse<PlatformAdminCeremonyPurchaseDto.Response.OptionalFeaturePurchaseRequestSummary> approveOptionalFeaturePurchase(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long purchaseId
    ) {
        PlatformAdminCeremonyPurchaseDto.Response.OptionalFeaturePurchaseRequestSummary response = ceremonyService
                .approveOptionalFeaturePurchase(purchaseId, currentUser.userId(), currentUser.platformRole());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "선택옵션 추가구매 요청 반려", description = "반려 사유를 남긴다. PLATFORM_OPS 이상만 호출할 수 있다.")
    @PutMapping("/optional-feature-purchases/{purchaseId}/reject")
    public ApiResponse<PlatformAdminCeremonyPurchaseDto.Response.OptionalFeaturePurchaseRequestSummary> rejectOptionalFeaturePurchase(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long purchaseId,
            @Valid @RequestBody PlatformAdminCeremonyPurchaseDto.Request.Reject request
    ) {
        PlatformAdminCeremonyPurchaseDto.Response.OptionalFeaturePurchaseRequestSummary response = ceremonyService
                .rejectOptionalFeaturePurchase(purchaseId, currentUser.userId(), currentUser.platformRole(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
