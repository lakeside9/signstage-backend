package com.eformworks.signstage.backend.feature.platformadmin.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.OrganizationDiscountDto;
import com.eformworks.signstage.backend.feature.ceremony.service.OrganizationDiscountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 조직×품목 세밀 할인 오버라이드(안 A) 관리 API. signstage-docs
 * business/organization-event-discount-pricing-review.md 4.1절(2026-08-21 재검토) 참고.
 * PLATFORM_SUPPORT 이상만 도달할 수 있고(SecurityConfig에서 /api/platform-admin/** 전체를
 * 게이트), 실제 변경(PUT/DELETE)은 PLATFORM_OPS 이상만 서비스에서 한 번 더 검사한다 —
 * {@link PlatformAdminCeremonyController}와 같은 패턴. 조회(GET)는 별도 등급 검사 없이
 * 플랫폼 관리자 화면 누구나 볼 수 있다(카탈로그 조회 API들과 같은 관례).
 */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자 조직별 할인 오버라이드 API")
@RestController
@RequestMapping("/api/platform-admin/organizations/{organizationId}/billing-discounts")
@RequiredArgsConstructor
public class PlatformAdminOrganizationDiscountController {

    private final OrganizationDiscountService organizationDiscountService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "조직에 걸린 할인 오버라이드 전체 조회", description = "플랜/선택옵션/용량 추가구매 세 종류를 한 번에 반환한다.")
    @GetMapping
    public ApiResponse<OrganizationDiscountDto.Response.OrganizationDiscountOverview> findDiscounts(
            @PathVariable Long organizationId
    ) {
        return ApiResponse.success(organizationDiscountService.findDiscounts(organizationId), traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "조직×플랜 할인 오버라이드 설정",
            description = "이 조직이 이 플랜을 쓸 때 카탈로그 할인 대신 적용할 값. PLATFORM_OPS 이상만 호출할 수 있다."
    )
    @PutMapping("/plans/{billingPlanId}")
    public ApiResponse<OrganizationDiscountDto.Response.BillingPlanDiscountSummary> setBillingPlanDiscount(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long billingPlanId,
            @Valid @RequestBody OrganizationDiscountDto.Request.SetDiscount request
    ) {
        OrganizationDiscountDto.Response.BillingPlanDiscountSummary response = organizationDiscountService.setBillingPlanDiscount(
                organizationId, billingPlanId, currentUser.platformRole(), currentUser.userId(), request
        );
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "조직×플랜 할인 오버라이드 제거", description = "제거하면 이후 카탈로그 자체의 할인값을 다시 쓴다.")
    @DeleteMapping("/plans/{billingPlanId}")
    public ApiResponse<Void> removeBillingPlanDiscount(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long billingPlanId
    ) {
        organizationDiscountService.removeBillingPlanDiscount(organizationId, billingPlanId, currentUser.platformRole(), currentUser.userId());
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "조직×선택옵션 할인 오버라이드 설정",
            description = "이 조직이 이 선택옵션을 살 때 카탈로그 할인 대신 적용할 값. PLATFORM_OPS 이상만 호출할 수 있다."
    )
    @PutMapping("/optional-features/{optionalFeatureId}")
    public ApiResponse<OrganizationDiscountDto.Response.OptionalFeatureDiscountSummary> setOptionalFeatureDiscount(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long optionalFeatureId,
            @Valid @RequestBody OrganizationDiscountDto.Request.SetDiscount request
    ) {
        OrganizationDiscountDto.Response.OptionalFeatureDiscountSummary response = organizationDiscountService.setOptionalFeatureDiscount(
                organizationId, optionalFeatureId, currentUser.platformRole(), currentUser.userId(), request
        );
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "조직×선택옵션 할인 오버라이드 제거", description = "제거하면 이후 카탈로그 자체의 할인값을 다시 쓴다.")
    @DeleteMapping("/optional-features/{optionalFeatureId}")
    public ApiResponse<Void> removeOptionalFeatureDiscount(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long optionalFeatureId
    ) {
        organizationDiscountService.removeOptionalFeatureDiscount(
                organizationId, optionalFeatureId, currentUser.platformRole(), currentUser.userId()
        );
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "조직×용량 추가구매 할인 오버라이드 설정",
            description = "이 조직이 이 용량 추가구매 상품을 살 때 카탈로그 할인 대신 적용할 값. PLATFORM_OPS 이상만 호출할 수 있다."
    )
    @PutMapping("/capacity-addons/{capacityAddOnId}")
    public ApiResponse<OrganizationDiscountDto.Response.CapacityAddOnDiscountSummary> setCapacityAddOnDiscount(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long capacityAddOnId,
            @Valid @RequestBody OrganizationDiscountDto.Request.SetDiscount request
    ) {
        OrganizationDiscountDto.Response.CapacityAddOnDiscountSummary response = organizationDiscountService.setCapacityAddOnDiscount(
                organizationId, capacityAddOnId, currentUser.platformRole(), currentUser.userId(), request
        );
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "조직×용량 추가구매 할인 오버라이드 제거", description = "제거하면 이후 카탈로그 자체의 할인값을 다시 쓴다.")
    @DeleteMapping("/capacity-addons/{capacityAddOnId}")
    public ApiResponse<Void> removeCapacityAddOnDiscount(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long capacityAddOnId
    ) {
        organizationDiscountService.removeCapacityAddOnDiscount(
                organizationId, capacityAddOnId, currentUser.platformRole(), currentUser.userId()
        );
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }
}
