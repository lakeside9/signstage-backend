package com.eformworks.signstage.backend.feature.billing.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.billing.dto.BillingPlanDto;
import com.eformworks.signstage.backend.feature.billing.dto.UnitProductDto;
import com.eformworks.signstage.backend.feature.billing.entity.SalesChannel;
import com.eformworks.signstage.backend.feature.billing.service.BillingPlanService;
import com.eformworks.signstage.backend.feature.billing.service.UnitProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 행사 과금 카탈로그(플랜/단위 상품) 조회. 조직 스코프가 없는 전역 카탈로그라
 * {@code /api/organizations/{organizationId}/...} 아래에 두지 않는다 — 인증된 사용자면 누구나
 * 조회할 수 있다(행사 생성 화면에서 플랜/단위 상품을 고를 때 필요). signstage-docs
 * business/billing-catalog-unit-product-model-redesign-review.md 참고 — 옛
 * {@code /api/optional-features}/{@code /api/capacity-addons}는 2단계 전환으로 제거됐다.
 *
 * <p>{@code salesChannel}로 판매 경로를 거를 수 있다(생략하면 전부). 플랫폼 수주 행사의 플랜 이름을
 * 파트너 화면에서도 보여줘야 해서 조직 사용자에게도 숨기지 않는다 — 고르는 화면이 경로를 지정하고,
 * 저장할 때 서버가 경로를 다시 확인한다(signstage-docs
 * business/platform-order-unit-product-composition-review.md U03).
 */
@Tag(name = "BillingCatalog", description = "행사 과금 카탈로그 조회 API")
@RestController
@RequiredArgsConstructor
public class BillingCatalogController {

    private final BillingPlanService billingPlanService;
    private final UnitProductService unitProductService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "과금 플랜 목록 조회")
    @GetMapping("/api/billing-plans")
    public ApiResponse<List<BillingPlanDto.Response.BillingPlanSummary>> findPlans(
            @RequestParam(required = false) SalesChannel salesChannel
    ) {
        return ApiResponse.success(billingPlanService.findPlans(salesChannel), traceIdProvider.getTraceId());
    }

    @Operation(summary = "단위 상품 목록 조회")
    @GetMapping("/api/unit-products")
    public ApiResponse<List<UnitProductDto.Response.UnitProductSummary>> findUnitProducts(
            @RequestParam(required = false) SalesChannel salesChannel
    ) {
        return ApiResponse.success(unitProductService.findUnitProducts(salesChannel), traceIdProvider.getTraceId());
    }
}
