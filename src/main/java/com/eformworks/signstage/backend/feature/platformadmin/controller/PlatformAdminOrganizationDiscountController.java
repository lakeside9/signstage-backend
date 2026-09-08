package com.eformworks.signstage.backend.feature.platformadmin.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.OrganizationDiscountDto;
import com.eformworks.signstage.backend.feature.ceremony.service.OrganizationDiscountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 조직×품목 세밀 할인 오버라이드 관리 API — 행 하나가 기간 하나(다중 버전, 안 B). signstage-docs
 * business/organization-discount-override-security-and-validity-period-review.md 결정
 * #4(2026-09-08) 참고. PLATFORM_SUPPORT 이상만 도달할 수 있고(SecurityConfig에서
 * /api/platform-admin/** 전체를 게이트), 실제 변경(POST/PUT/DELETE)은 동적 RBAC
 * ({@code ACTION_ORGANIZATION_DISCOUNT_MANAGE})으로 서비스에서 한 번 더 검사한다. 조회(GET)는
 * 별도 등급 검사 없이 플랫폼 관리자 화면 누구나 볼 수 있다(카탈로그 조회 API들과 같은 관례).
 */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자 조직별 할인 오버라이드 API")
@RestController
@RequestMapping("/api/platform-admin/organizations/{organizationId}/billing-discounts")
@RequiredArgsConstructor
public class PlatformAdminOrganizationDiscountController {

    private final OrganizationDiscountService organizationDiscountService;
    private final TraceIdProvider traceIdProvider;

    @Operation(
            summary = "조직에 걸린 할인 오버라이드 전체 조회",
            description = "플랜/선택옵션/용량 추가구매 세 종류를, 각 품목의 모든 기간(과거/현재/예정)과 함께 한 번에 반환한다."
    )
    @GetMapping
    public ApiResponse<OrganizationDiscountDto.Response.OrganizationDiscountOverview> findDiscounts(
            @PathVariable Long organizationId
    ) {
        return ApiResponse.success(organizationDiscountService.findDiscounts(organizationId), traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "조직×플랜 할인 오버라이드 기간 생성",
            description = "이 조직이 이 플랜을 쓸 때 카탈로그 할인 대신 적용할 기간 하나를 새로 만든다. 겹치는 기간이 있으면 거부된다."
    )
    @PostMapping("/plans/{billingPlanId}")
    public ApiResponse<OrganizationDiscountDto.Response.BillingPlanDiscountSummary> createBillingPlanDiscountPeriod(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long billingPlanId,
            @Valid @RequestBody OrganizationDiscountDto.Request.SetDiscount request
    ) {
        OrganizationDiscountDto.Response.BillingPlanDiscountSummary response = organizationDiscountService.createBillingPlanDiscountPeriod(
                organizationId, billingPlanId, currentUser.platformRole(), currentUser.userId(), request
        );
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "조직×플랜 할인 오버라이드 기간 수정", description = "이미 있는 기간 하나의 할인값/시작일/종료일을 고친다.")
    @PutMapping("/plans/{billingPlanId}/periods/{periodId}")
    public ApiResponse<OrganizationDiscountDto.Response.BillingPlanDiscountSummary> updateBillingPlanDiscountPeriod(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long billingPlanId,
            @PathVariable Long periodId,
            @Valid @RequestBody OrganizationDiscountDto.Request.SetDiscount request
    ) {
        OrganizationDiscountDto.Response.BillingPlanDiscountSummary response = organizationDiscountService.updateBillingPlanDiscountPeriod(
                organizationId, billingPlanId, periodId, currentUser.platformRole(), currentUser.userId(), request
        );
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "조직×플랜 할인 오버라이드 기간 제거", description = "만료된 기간은 자동으로 지워지지 않는다 — 필요하면 이 API로 직접 지운다.")
    @DeleteMapping("/plans/{billingPlanId}/periods/{periodId}")
    public ApiResponse<Void> removeBillingPlanDiscountPeriod(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long billingPlanId,
            @PathVariable Long periodId
    ) {
        organizationDiscountService.removeBillingPlanDiscountPeriod(
                organizationId, billingPlanId, periodId, currentUser.platformRole(), currentUser.userId()
        );
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "조직×플랜 할인 오버라이드 변경 이력 조회",
            description = "최신순. 기간 생성/수정 시점마다, 그리고 제거 시점에(그 직전 값) 한 건씩 쌓인다."
    )
    @GetMapping("/plans/{billingPlanId}/history")
    public ApiResponse<List<OrganizationDiscountDto.Response.BillingPlanDiscountHistorySummary>> findBillingPlanDiscountHistory(
            @PathVariable Long organizationId,
            @PathVariable Long billingPlanId
    ) {
        return ApiResponse.success(
                organizationDiscountService.findBillingPlanDiscountHistory(organizationId, billingPlanId), traceIdProvider.getTraceId()
        );
    }

    @Operation(
            summary = "조직×선택옵션 할인 오버라이드 기간 생성",
            description = "이 조직이 이 선택옵션을 살 때 카탈로그 할인 대신 적용할 기간 하나를 새로 만든다. 겹치는 기간이 있으면 거부된다."
    )
    @PostMapping("/optional-features/{optionalFeatureId}")
    public ApiResponse<OrganizationDiscountDto.Response.OptionalFeatureDiscountSummary> createOptionalFeatureDiscountPeriod(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long optionalFeatureId,
            @Valid @RequestBody OrganizationDiscountDto.Request.SetDiscount request
    ) {
        OrganizationDiscountDto.Response.OptionalFeatureDiscountSummary response =
                organizationDiscountService.createOptionalFeatureDiscountPeriod(
                        organizationId, optionalFeatureId, currentUser.platformRole(), currentUser.userId(), request
                );
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "조직×선택옵션 할인 오버라이드 기간 수정", description = "이미 있는 기간 하나의 할인값/시작일/종료일을 고친다.")
    @PutMapping("/optional-features/{optionalFeatureId}/periods/{periodId}")
    public ApiResponse<OrganizationDiscountDto.Response.OptionalFeatureDiscountSummary> updateOptionalFeatureDiscountPeriod(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long optionalFeatureId,
            @PathVariable Long periodId,
            @Valid @RequestBody OrganizationDiscountDto.Request.SetDiscount request
    ) {
        OrganizationDiscountDto.Response.OptionalFeatureDiscountSummary response =
                organizationDiscountService.updateOptionalFeatureDiscountPeriod(
                        organizationId, optionalFeatureId, periodId, currentUser.platformRole(), currentUser.userId(), request
                );
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "조직×선택옵션 할인 오버라이드 기간 제거", description = "만료된 기간은 자동으로 지워지지 않는다 — 필요하면 이 API로 직접 지운다.")
    @DeleteMapping("/optional-features/{optionalFeatureId}/periods/{periodId}")
    public ApiResponse<Void> removeOptionalFeatureDiscountPeriod(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long optionalFeatureId,
            @PathVariable Long periodId
    ) {
        organizationDiscountService.removeOptionalFeatureDiscountPeriod(
                organizationId, optionalFeatureId, periodId, currentUser.platformRole(), currentUser.userId()
        );
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "조직×선택옵션 할인 오버라이드 변경 이력 조회",
            description = "최신순. 기간 생성/수정 시점마다, 그리고 제거 시점에(그 직전 값) 한 건씩 쌓인다."
    )
    @GetMapping("/optional-features/{optionalFeatureId}/history")
    public ApiResponse<List<OrganizationDiscountDto.Response.OptionalFeatureDiscountHistorySummary>> findOptionalFeatureDiscountHistory(
            @PathVariable Long organizationId,
            @PathVariable Long optionalFeatureId
    ) {
        return ApiResponse.success(
                organizationDiscountService.findOptionalFeatureDiscountHistory(organizationId, optionalFeatureId), traceIdProvider.getTraceId()
        );
    }

    @Operation(
            summary = "조직×용량 추가구매 할인 오버라이드 기간 생성",
            description = "이 조직이 이 용량 추가구매 상품을 살 때 카탈로그 할인 대신 적용할 기간 하나를 새로 만든다. 겹치는 기간이 있으면 거부된다."
    )
    @PostMapping("/capacity-addons/{capacityAddOnId}")
    public ApiResponse<OrganizationDiscountDto.Response.CapacityAddOnDiscountSummary> createCapacityAddOnDiscountPeriod(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long capacityAddOnId,
            @Valid @RequestBody OrganizationDiscountDto.Request.SetDiscount request
    ) {
        OrganizationDiscountDto.Response.CapacityAddOnDiscountSummary response =
                organizationDiscountService.createCapacityAddOnDiscountPeriod(
                        organizationId, capacityAddOnId, currentUser.platformRole(), currentUser.userId(), request
                );
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "조직×용량 추가구매 할인 오버라이드 기간 수정", description = "이미 있는 기간 하나의 할인값/시작일/종료일을 고친다.")
    @PutMapping("/capacity-addons/{capacityAddOnId}/periods/{periodId}")
    public ApiResponse<OrganizationDiscountDto.Response.CapacityAddOnDiscountSummary> updateCapacityAddOnDiscountPeriod(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long capacityAddOnId,
            @PathVariable Long periodId,
            @Valid @RequestBody OrganizationDiscountDto.Request.SetDiscount request
    ) {
        OrganizationDiscountDto.Response.CapacityAddOnDiscountSummary response =
                organizationDiscountService.updateCapacityAddOnDiscountPeriod(
                        organizationId, capacityAddOnId, periodId, currentUser.platformRole(), currentUser.userId(), request
                );
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "조직×용량 추가구매 할인 오버라이드 기간 제거", description = "만료된 기간은 자동으로 지워지지 않는다 — 필요하면 이 API로 직접 지운다.")
    @DeleteMapping("/capacity-addons/{capacityAddOnId}/periods/{periodId}")
    public ApiResponse<Void> removeCapacityAddOnDiscountPeriod(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long capacityAddOnId,
            @PathVariable Long periodId
    ) {
        organizationDiscountService.removeCapacityAddOnDiscountPeriod(
                organizationId, capacityAddOnId, periodId, currentUser.platformRole(), currentUser.userId()
        );
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "조직×용량 추가구매 할인 오버라이드 변경 이력 조회",
            description = "최신순. 기간 생성/수정 시점마다, 그리고 제거 시점에(그 직전 값) 한 건씩 쌓인다."
    )
    @GetMapping("/capacity-addons/{capacityAddOnId}/history")
    public ApiResponse<List<OrganizationDiscountDto.Response.CapacityAddOnDiscountHistorySummary>> findCapacityAddOnDiscountHistory(
            @PathVariable Long organizationId,
            @PathVariable Long capacityAddOnId
    ) {
        return ApiResponse.success(
                organizationDiscountService.findCapacityAddOnDiscountHistory(organizationId, capacityAddOnId), traceIdProvider.getTraceId()
        );
    }
}
