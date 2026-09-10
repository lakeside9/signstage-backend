package com.eformworks.signstage.backend.feature.ceremony.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.BillingPlanDto;
import com.eformworks.signstage.backend.feature.ceremony.dto.UnitProductDto;
import com.eformworks.signstage.backend.feature.ceremony.service.BillingPlanService;
import com.eformworks.signstage.backend.feature.ceremony.service.UnitProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 행사 과금 카탈로그(플랜/단위 상품) 조회. 조직 스코프가 없는 전역 카탈로그라
 * {@code /api/organizations/{organizationId}/...} 아래에 두지 않는다 — 인증된 사용자면 누구나
 * 조회할 수 있다(행사 생성 화면에서 플랜/단위 상품을 고를 때 필요). signstage-docs
 * business/billing-catalog-unit-product-model-redesign-review.md 참고 — 옛
 * {@code /api/optional-features}/{@code /api/capacity-addons}는 2단계 전환으로 제거됐다.
 */
@Tag(name = "Ceremony", description = "행사 과금 카탈로그 조회 API")
@RestController
@RequiredArgsConstructor
public class BillingCatalogController {

    private final BillingPlanService billingPlanService;
    private final UnitProductService unitProductService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "과금 플랜 목록 조회")
    @GetMapping("/api/billing-plans")
    public ApiResponse<List<BillingPlanDto.Response.BillingPlanSummary>> findPlans() {
        return ApiResponse.success(billingPlanService.findPlans(), traceIdProvider.getTraceId());
    }

    @Operation(summary = "단위 상품 목록 조회")
    @GetMapping("/api/unit-products")
    public ApiResponse<List<UnitProductDto.Response.UnitProductSummary>> findUnitProducts() {
        return ApiResponse.success(unitProductService.findUnitProducts(), traceIdProvider.getTraceId());
    }
}
