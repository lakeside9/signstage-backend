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

    @Operation(
            summary = "과금 플랜 수정",
            description = "PLATFORM_OPS 이상만 호출할 수 있다. 선택옵션 구성(optionalFeatureIds)과 구매 가능 용량 "
                    + "추가구매 상품 구성(capacityAddOnIds)도 여기서 통째로 교체할 수 있다."
    )
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

    @Operation(summary = "과금 플랜 변경 이력 조회", description = "최신순. 생성 시점 1건 + 이후 이름/한도 구성이 바뀔 때마다 1건씩 쌓인다.")
    @GetMapping("/billing-plans/{id}/history")
    public ApiResponse<List<BillingPlanDto.Response.BillingPlanHistorySummary>> findPlanHistory(@PathVariable Long id) {
        return ApiResponse.success(billingPlanService.findPlanHistory(id), traceIdProvider.getTraceId());
    }

    @Operation(summary = "과금 플랜 판매가격 기간 추가", description = "PLATFORM_OPS 이상만 호출할 수 있다. 기간이 다른 기간과 겹치면 거부된다.")
    @PostMapping("/billing-plans/{id}/periods")
    public ApiResponse<BillingPlanDto.Response.BillingPlanPeriodSummary> createPlanPeriod(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody BillingPlanDto.Request.CreatePeriod request
    ) {
        BillingPlanDto.Response.BillingPlanPeriodSummary response =
                billingPlanService.createPeriod(id, currentUser.platformRole(), currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "과금 플랜 판매가격 기간 수정", description = "PLATFORM_OPS 이상만 호출할 수 있다.")
    @PutMapping("/billing-plans/{id}/periods/{periodId}")
    public ApiResponse<BillingPlanDto.Response.BillingPlanPeriodSummary> updatePlanPeriod(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @PathVariable Long periodId,
            @Valid @RequestBody BillingPlanDto.Request.UpdatePeriod request
    ) {
        BillingPlanDto.Response.BillingPlanPeriodSummary response =
                billingPlanService.updatePeriod(id, periodId, currentUser.platformRole(), currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "과금 플랜 판매가격 기간 삭제", description = "PLATFORM_OPS 이상만 호출할 수 있다. 마지막 남은 기간은 지울 수 없다.")
    @DeleteMapping("/billing-plans/{id}/periods/{periodId}")
    public ApiResponse<Void> removePlanPeriod(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @PathVariable Long periodId
    ) {
        billingPlanService.removePeriod(id, periodId, currentUser.platformRole(), currentUser.userId());
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }

    @Operation(summary = "과금 플랜 판매가격 기간 목록 조회", description = "오래된 순 — 과거/현재/예정 기간을 전부 보여준다.")
    @GetMapping("/billing-plans/{id}/periods")
    public ApiResponse<List<BillingPlanDto.Response.BillingPlanPeriodSummary>> findPlanPeriods(@PathVariable Long id) {
        return ApiResponse.success(billingPlanService.findPlanPeriods(id), traceIdProvider.getTraceId());
    }

    @Operation(summary = "과금 플랜 판매가격 기간 변경 이력 조회", description = "최신순 — 기간 생성/수정/삭제 이벤트를 전부 보여준다.")
    @GetMapping("/billing-plans/{id}/periods/history")
    public ApiResponse<List<BillingPlanDto.Response.BillingPlanPeriodHistorySummary>> findPlanPeriodHistory(@PathVariable Long id) {
        return ApiResponse.success(billingPlanService.findPlanPeriodHistory(id), traceIdProvider.getTraceId());
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

    @Operation(summary = "선택옵션 변경 이력 조회", description = "최신순. 생성 시점 1건 + 이후 이름/배타그룹/분류가 바뀔 때마다 1건씩 쌓인다.")
    @GetMapping("/optional-features/{id}/history")
    public ApiResponse<List<OptionalFeatureDto.Response.OptionalFeatureHistorySummary>> findOptionalFeatureHistory(
            @PathVariable Long id
    ) {
        return ApiResponse.success(optionalFeatureService.findFeatureHistory(id), traceIdProvider.getTraceId());
    }

    @Operation(summary = "선택옵션 판매가격 기간 추가", description = "PLATFORM_OPS 이상만 호출할 수 있다. 기간이 다른 기간과 겹치면 거부된다.")
    @PostMapping("/optional-features/{id}/periods")
    public ApiResponse<OptionalFeatureDto.Response.OptionalFeaturePeriodSummary> createOptionalFeaturePeriod(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody OptionalFeatureDto.Request.CreatePeriod request
    ) {
        OptionalFeatureDto.Response.OptionalFeaturePeriodSummary response =
                optionalFeatureService.createPeriod(id, currentUser.platformRole(), currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "선택옵션 판매가격 기간 수정", description = "PLATFORM_OPS 이상만 호출할 수 있다.")
    @PutMapping("/optional-features/{id}/periods/{periodId}")
    public ApiResponse<OptionalFeatureDto.Response.OptionalFeaturePeriodSummary> updateOptionalFeaturePeriod(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @PathVariable Long periodId,
            @Valid @RequestBody OptionalFeatureDto.Request.UpdatePeriod request
    ) {
        OptionalFeatureDto.Response.OptionalFeaturePeriodSummary response =
                optionalFeatureService.updatePeriod(id, periodId, currentUser.platformRole(), currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "선택옵션 판매가격 기간 삭제", description = "PLATFORM_OPS 이상만 호출할 수 있다. 마지막 남은 기간은 지울 수 없다.")
    @DeleteMapping("/optional-features/{id}/periods/{periodId}")
    public ApiResponse<Void> removeOptionalFeaturePeriod(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @PathVariable Long periodId
    ) {
        optionalFeatureService.removePeriod(id, periodId, currentUser.platformRole(), currentUser.userId());
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }

    @Operation(summary = "선택옵션 판매가격 기간 목록 조회", description = "오래된 순 — 과거/현재/예정 기간을 전부 보여준다.")
    @GetMapping("/optional-features/{id}/periods")
    public ApiResponse<List<OptionalFeatureDto.Response.OptionalFeaturePeriodSummary>> findOptionalFeaturePeriods(
            @PathVariable Long id
    ) {
        return ApiResponse.success(optionalFeatureService.findFeaturePeriods(id), traceIdProvider.getTraceId());
    }

    @Operation(summary = "선택옵션 판매가격 기간 변경 이력 조회", description = "최신순 — 기간 생성/수정/삭제 이벤트를 전부 보여준다.")
    @GetMapping("/optional-features/{id}/periods/history")
    public ApiResponse<List<OptionalFeatureDto.Response.OptionalFeaturePeriodHistorySummary>> findOptionalFeaturePeriodHistory(
            @PathVariable Long id
    ) {
        return ApiResponse.success(optionalFeatureService.findFeaturePeriodHistory(id), traceIdProvider.getTraceId());
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

    @Operation(summary = "용량 추가구매 상품 변경 이력 조회", description = "최신순. 생성 시점 1건 + 이후 단위수량이 바뀔 때마다 1건씩 쌓인다.")
    @GetMapping("/capacity-addons/{id}/history")
    public ApiResponse<List<CapacityAddOnDto.Response.CapacityAddOnHistorySummary>> findCapacityAddOnHistory(
            @PathVariable Long id
    ) {
        return ApiResponse.success(capacityAddOnService.findAddOnHistory(id), traceIdProvider.getTraceId());
    }

    @Operation(summary = "용량 추가구매 상품 판매가격 기간 추가", description = "PLATFORM_OPS 이상만 호출할 수 있다. 기간이 다른 기간과 겹치면 거부된다.")
    @PostMapping("/capacity-addons/{id}/periods")
    public ApiResponse<CapacityAddOnDto.Response.CapacityAddOnPeriodSummary> createCapacityAddOnPeriod(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody CapacityAddOnDto.Request.CreatePeriod request
    ) {
        CapacityAddOnDto.Response.CapacityAddOnPeriodSummary response =
                capacityAddOnService.createPeriod(id, currentUser.platformRole(), currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "용량 추가구매 상품 판매가격 기간 수정", description = "PLATFORM_OPS 이상만 호출할 수 있다.")
    @PutMapping("/capacity-addons/{id}/periods/{periodId}")
    public ApiResponse<CapacityAddOnDto.Response.CapacityAddOnPeriodSummary> updateCapacityAddOnPeriod(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @PathVariable Long periodId,
            @Valid @RequestBody CapacityAddOnDto.Request.UpdatePeriod request
    ) {
        CapacityAddOnDto.Response.CapacityAddOnPeriodSummary response =
                capacityAddOnService.updatePeriod(id, periodId, currentUser.platformRole(), currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "용량 추가구매 상품 판매가격 기간 삭제", description = "PLATFORM_OPS 이상만 호출할 수 있다. 마지막 남은 기간은 지울 수 없다.")
    @DeleteMapping("/capacity-addons/{id}/periods/{periodId}")
    public ApiResponse<Void> removeCapacityAddOnPeriod(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @PathVariable Long periodId
    ) {
        capacityAddOnService.removePeriod(id, periodId, currentUser.platformRole(), currentUser.userId());
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }

    @Operation(summary = "용량 추가구매 상품 판매가격 기간 목록 조회", description = "오래된 순 — 과거/현재/예정 기간을 전부 보여준다.")
    @GetMapping("/capacity-addons/{id}/periods")
    public ApiResponse<List<CapacityAddOnDto.Response.CapacityAddOnPeriodSummary>> findCapacityAddOnPeriods(
            @PathVariable Long id
    ) {
        return ApiResponse.success(capacityAddOnService.findAddOnPeriods(id), traceIdProvider.getTraceId());
    }

    @Operation(summary = "용량 추가구매 상품 판매가격 기간 변경 이력 조회", description = "최신순 — 기간 생성/수정/삭제 이벤트를 전부 보여준다.")
    @GetMapping("/capacity-addons/{id}/periods/history")
    public ApiResponse<List<CapacityAddOnDto.Response.CapacityAddOnPeriodHistorySummary>> findCapacityAddOnPeriodHistory(
            @PathVariable Long id
    ) {
        return ApiResponse.success(capacityAddOnService.findAddOnPeriodHistory(id), traceIdProvider.getTraceId());
    }
}
