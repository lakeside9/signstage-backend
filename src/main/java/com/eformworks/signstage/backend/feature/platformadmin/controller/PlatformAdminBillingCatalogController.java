package com.eformworks.signstage.backend.feature.platformadmin.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.BillingPlanDto;
import com.eformworks.signstage.backend.feature.ceremony.dto.CapacityAddOnDto;
import com.eformworks.signstage.backend.feature.ceremony.dto.OptionalFeatureDto;
import com.eformworks.signstage.backend.feature.ceremony.service.BillingPlanService;
import com.eformworks.signstage.backend.feature.ceremony.service.CapacityAddOnService;
import com.eformworks.signstage.backend.feature.ceremony.service.OptionalFeatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 행사 과금 카탈로그(플랜/선택옵션/용량 추가구매 상품) 등록·수정. PLATFORM_SUPPORT 이상만 도달할 수 있고
 * (SecurityConfig에서 /api/platform-admin/** 전체를 게이트), 실제 등록·수정은 PLATFORM_OPS 이상만
 * 서비스에서 한 번 더 검사한다 — 다른 PlatformAdminXxxController와 같은 패턴이다.
 * signstage-docs business/ceremony-billing-options-review.md 참고.
 */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자 행사 과금 카탈로그 API")
@RestController
@RequestMapping("/api/platform-admin")
@RequiredArgsConstructor
public class PlatformAdminBillingCatalogController {

    private final BillingPlanService billingPlanService;
    private final OptionalFeatureService optionalFeatureService;
    private final CapacityAddOnService capacityAddOnService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "과금 플랜 등록", description = "PLATFORM_OPS 이상만 호출할 수 있다.")
    @PostMapping("/billing-plans")
    public ApiResponse<BillingPlanDto.Response.BillingPlanSummary> createPlan(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody BillingPlanDto.Request.CreatePlan request
    ) {
        BillingPlanDto.Response.BillingPlanSummary response =
                billingPlanService.createPlan(currentUser.platformRole(), currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "과금 플랜 수정", description = "PLATFORM_OPS 이상만 호출할 수 있다. 선택옵션 구성은 생성 후 불변이라 여기서 바꿀 수 없다.")
    @PutMapping("/billing-plans/{id}")
    public ApiResponse<BillingPlanDto.Response.BillingPlanSummary> updatePlan(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody BillingPlanDto.Request.UpdatePlan request
    ) {
        BillingPlanDto.Response.BillingPlanSummary response =
                billingPlanService.updatePlan(id, currentUser.platformRole(), currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "선택옵션 등록", description = "PLATFORM_OPS 이상만 호출할 수 있다.")
    @PostMapping("/optional-features")
    public ApiResponse<OptionalFeatureDto.Response.OptionalFeatureSummary> createOptionalFeature(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody OptionalFeatureDto.Request.CreateOptionalFeature request
    ) {
        OptionalFeatureDto.Response.OptionalFeatureSummary response =
                optionalFeatureService.createOptionalFeature(currentUser.platformRole(), currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "선택옵션 수정", description = "PLATFORM_OPS 이상만 호출할 수 있다. code는 생성 후 불변이라 여기서 바꿀 수 없다.")
    @PutMapping("/optional-features/{id}")
    public ApiResponse<OptionalFeatureDto.Response.OptionalFeatureSummary> updateOptionalFeature(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody OptionalFeatureDto.Request.UpdateOptionalFeature request
    ) {
        OptionalFeatureDto.Response.OptionalFeatureSummary response =
                optionalFeatureService.updateOptionalFeature(id, currentUser.platformRole(), currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "용량 추가구매 상품 등록", description = "PLATFORM_OPS 이상만 호출할 수 있다.")
    @PostMapping("/capacity-addons")
    public ApiResponse<CapacityAddOnDto.Response.CapacityAddOnSummary> createCapacityAddOn(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody CapacityAddOnDto.Request.CreateCapacityAddOn request
    ) {
        CapacityAddOnDto.Response.CapacityAddOnSummary response =
                capacityAddOnService.createCapacityAddOn(currentUser.platformRole(), currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "용량 추가구매 상품 수정", description = "PLATFORM_OPS 이상만 호출할 수 있다. capacityType은 생성 후 불변이라 여기서 바꿀 수 없다.")
    @PutMapping("/capacity-addons/{id}")
    public ApiResponse<CapacityAddOnDto.Response.CapacityAddOnSummary> updateCapacityAddOn(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody CapacityAddOnDto.Request.UpdateCapacityAddOn request
    ) {
        CapacityAddOnDto.Response.CapacityAddOnSummary response =
                capacityAddOnService.updateCapacityAddOn(id, currentUser.platformRole(), currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
