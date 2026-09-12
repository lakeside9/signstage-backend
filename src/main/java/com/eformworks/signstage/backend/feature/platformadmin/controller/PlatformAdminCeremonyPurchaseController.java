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
 * 플랫폼 관리자의 행사 단위 상품 추가구매 요청 승인/반려 API다(signstage-docs
 * business/billing-catalog-unit-product-model-redesign-review.md 결정, 2026-09-10) — 옛
 * 용량/선택옵션 2종 승인 큐를 하나로 합쳤다(장바구니형 요청이라 승인/반려도 요청 전체 단위다).
 * 조회는 PLATFORM_SUPPORT 이상, 승인/반려는 PLATFORM_OPS 이상만 서비스에서 한 번 더 검사한다 —
 * {@link PlatformAdminOrganizationRequestController}와 같은 규약.
 */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자의 행사 추가구매 승인/반려 API")
@RestController
@RequestMapping("/api/platform-admin")
@RequiredArgsConstructor
public class PlatformAdminCeremonyPurchaseController {

    private final CeremonyService ceremonyService;
    private final TraceIdProvider traceIdProvider;

    @Operation(
            summary = "단위 상품 추가구매 요청 목록 조회",
            description = "status/organizationId/ceremonyId 전부 선택 필터다(생략하면 그 조건 없이 전체 최신순). "
                    + "ceremonyId로 좁히면 신규 \"행사 이력\" 화면(signstage-docs "
                    + "business/unit-product-purchase-self-checkout-review.md 8.6절 결정, 2026-09-11)의 구매 이력 조회로 쓸 수 있다."
    )
    @GetMapping("/unit-product-purchases")
    public ApiResponse<PageResponse<PlatformAdminCeremonyPurchaseDto.Response.UnitProductPurchaseRequestSummary>> findUnitProductPurchaseRequests(
            @RequestParam(required = false) PurchaseStatus status,
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) Long ceremonyId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<PlatformAdminCeremonyPurchaseDto.Response.UnitProductPurchaseRequestSummary> result =
                ceremonyService.findUnitProductPurchaseRequests(status, organizationId, ceremonyId, pageable);
        return ApiResponse.success(PageResponse.from(result), traceIdProvider.getTraceId());
    }

    @Operation(summary = "단위 상품 추가구매 요청 승인", description = "PLATFORM_OPS 이상만 호출할 수 있다. 요청에 담긴 줄 전체가 함께 승인된다.")
    @PostMapping("/unit-product-purchases/{purchaseId}/approve")
    public ApiResponse<PlatformAdminCeremonyPurchaseDto.Response.UnitProductPurchaseRequestSummary> approveUnitProductPurchase(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long purchaseId
    ) {
        PlatformAdminCeremonyPurchaseDto.Response.UnitProductPurchaseRequestSummary response = ceremonyService
                .approveUnitProductPurchase(purchaseId, currentUser.userId(), currentUser.platformRole());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "단위 상품 추가구매 요청 반려", description = "반려 사유를 남긴다. PLATFORM_OPS 이상만 호출할 수 있다. 요청에 담긴 줄 전체가 함께 반려된다.")
    @PutMapping("/unit-product-purchases/{purchaseId}/reject")
    public ApiResponse<PlatformAdminCeremonyPurchaseDto.Response.UnitProductPurchaseRequestSummary> rejectUnitProductPurchase(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long purchaseId,
            @Valid @RequestBody PlatformAdminCeremonyPurchaseDto.Request.Reject request
    ) {
        PlatformAdminCeremonyPurchaseDto.Response.UnitProductPurchaseRequestSummary response = ceremonyService
                .rejectUnitProductPurchase(purchaseId, currentUser.userId(), currentUser.platformRole(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "단위 상품 추가구매 요청 취소",
            description = "이미 승인(APPROVED)된 구매를 취소한다. 취소 사유를 남긴다. PLATFORM_OPS 이상만 호출할 수 있다. "
                    + "이벤트 효과 묶음이 포함돼 있고 STARTED가 아닌 하위 행사에 적용돼 있으면 자동으로 해제된다."
    )
    @PutMapping("/unit-product-purchases/{purchaseId}/cancel")
    public ApiResponse<PlatformAdminCeremonyPurchaseDto.Response.UnitProductPurchaseRequestSummary> cancelUnitProductPurchase(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long purchaseId,
            @Valid @RequestBody PlatformAdminCeremonyPurchaseDto.Request.Cancel request
    ) {
        PlatformAdminCeremonyPurchaseDto.Response.UnitProductPurchaseRequestSummary response = ceremonyService
                .cancelUnitProductPurchase(purchaseId, currentUser.userId(), currentUser.platformRole(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
