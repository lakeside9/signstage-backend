package com.eformworks.signstage.backend.feature.platformadmin.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.BillingPlanDto;
import com.eformworks.signstage.backend.feature.ceremony.dto.UnitProductDto;
import com.eformworks.signstage.backend.feature.ceremony.service.BillingPlanService;
import com.eformworks.signstage.backend.feature.ceremony.service.UnitProductService;
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
 * 행사 과금 카탈로그(플랜/단위 상품) 등록·수정. PLATFORM_SUPPORT 이상만 도달할 수 있고
 * (SecurityConfig에서 /api/platform-admin/** 전체를 게이트), 실제 등록·수정은 PLATFORM_OPS 이상만
 * 서비스에서 한 번 더 검사한다 — 다른 PlatformAdminXxxController와 같은 패턴이다.
 * signstage-docs business/billing-catalog-unit-product-model-redesign-review.md 참고 — 옛
 * {@code /optional-features}/{@code /capacity-addons} 엔드포인트는 2단계 전환으로 제거됐다.
 */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자 행사 과금 카탈로그 API")
@RestController
@RequestMapping("/api/platform-admin")
@RequiredArgsConstructor
public class PlatformAdminBillingCatalogController {

    private final BillingPlanService billingPlanService;
    private final UnitProductService unitProductService;
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
            description = "PLATFORM_OPS 이상만 호출할 수 있다. 단위 상품 구성(unitProducts)도 여기서 통째로 교체할 수 있다."
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

    // === 단위 상품(UnitProduct) — 옛 선택옵션/용량 추가구매 상품 API를 통합했다
    // (signstage-docs business/billing-catalog-unit-product-model-redesign-review.md, 2026-09-10).

    @Operation(summary = "단위 상품 등록", description = "PLATFORM_OPS 이상만 호출할 수 있다.")
    @PostMapping("/unit-products")
    public ApiResponse<UnitProductDto.Response.UnitProductSummary> createUnitProduct(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody UnitProductDto.Request.CreateUnitProduct request
    ) {
        UnitProductDto.Response.UnitProductSummary response =
                unitProductService.createUnitProduct(currentUser.platformRole(), currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "단위 상품 수정", description = "PLATFORM_OPS 이상만 호출할 수 있다. type은 생성 후 불변이라 여기서 바꿀 수 없다.")
    @PutMapping("/unit-products/{id}")
    public ApiResponse<UnitProductDto.Response.UnitProductSummary> updateUnitProduct(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody UnitProductDto.Request.UpdateUnitProduct request
    ) {
        UnitProductDto.Response.UnitProductSummary response =
                unitProductService.updateUnitProduct(id, currentUser.platformRole(), currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "단위 상품 변경 이력 조회", description = "최신순. 생성 시점 1건 + 이후 이름/분류/배타그룹이 바뀔 때마다 1건씩 쌓인다.")
    @GetMapping("/unit-products/{id}/history")
    public ApiResponse<List<UnitProductDto.Response.UnitProductHistorySummary>> findUnitProductHistory(
            @PathVariable Long id
    ) {
        return ApiResponse.success(unitProductService.findUnitProductHistory(id), traceIdProvider.getTraceId());
    }

    @Operation(summary = "단위 상품 판매가격 기간 추가", description = "PLATFORM_OPS 이상만 호출할 수 있다. 기간이 다른 기간과 겹치면 거부된다.")
    @PostMapping("/unit-products/{id}/periods")
    public ApiResponse<UnitProductDto.Response.UnitProductPeriodSummary> createUnitProductPeriod(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody UnitProductDto.Request.CreatePeriod request
    ) {
        UnitProductDto.Response.UnitProductPeriodSummary response =
                unitProductService.createPeriod(id, currentUser.platformRole(), currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "단위 상품 판매가격 기간 수정", description = "PLATFORM_OPS 이상만 호출할 수 있다.")
    @PutMapping("/unit-products/{id}/periods/{periodId}")
    public ApiResponse<UnitProductDto.Response.UnitProductPeriodSummary> updateUnitProductPeriod(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @PathVariable Long periodId,
            @Valid @RequestBody UnitProductDto.Request.UpdatePeriod request
    ) {
        UnitProductDto.Response.UnitProductPeriodSummary response =
                unitProductService.updatePeriod(id, periodId, currentUser.platformRole(), currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "단위 상품 판매가격 기간 삭제", description = "PLATFORM_OPS 이상만 호출할 수 있다. 마지막 남은 기간은 지울 수 없다.")
    @DeleteMapping("/unit-products/{id}/periods/{periodId}")
    public ApiResponse<Void> removeUnitProductPeriod(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @PathVariable Long periodId
    ) {
        unitProductService.removePeriod(id, periodId, currentUser.platformRole(), currentUser.userId());
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }

    @Operation(summary = "단위 상품 판매가격 기간 목록 조회", description = "오래된 순 — 과거/현재/예정 기간을 전부 보여준다.")
    @GetMapping("/unit-products/{id}/periods")
    public ApiResponse<List<UnitProductDto.Response.UnitProductPeriodSummary>> findUnitProductPeriods(
            @PathVariable Long id
    ) {
        return ApiResponse.success(unitProductService.findUnitProductPeriods(id), traceIdProvider.getTraceId());
    }

    @Operation(summary = "단위 상품 판매가격 기간 변경 이력 조회", description = "최신순 — 기간 생성/수정/삭제 이벤트를 전부 보여준다.")
    @GetMapping("/unit-products/{id}/periods/history")
    public ApiResponse<List<UnitProductDto.Response.UnitProductPeriodHistorySummary>> findUnitProductPeriodHistory(
            @PathVariable Long id
    ) {
        return ApiResponse.success(unitProductService.findUnitProductPeriodHistory(id), traceIdProvider.getTraceId());
    }
}
