package com.eformworks.signstage.backend.feature.platformadmin.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.OrganizationDiscountDto;
import com.eformworks.signstage.backend.feature.ceremony.service.OrganizationDiscountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 조직×플랜 할인 오버라이드 조직 횡단 목록 API — 조직 상세를 거치지 않는 별도 목록 화면 전용
 * (signstage-docs business/discount-management-screen-separation-review.md). 상세/생성/수정/
 * 삭제/이력은 기존 {@link PlatformAdminOrganizationDiscountController}(조직 하위 중첩)를
 * 그대로 재사용한다(같은 문서 6장 결정 #2) — 이 컨트롤러는 읽기 전용 목록만 새로 추가한다.
 * 조회 전용이라 등급 검사 없이 PLATFORM_SUPPORT 이상이면 누구나 호출할 수 있다. 옛
 * 선택옵션/용량 추가구매 오버라이드 목록 엔드포인트는 2단계 전환으로 제거됐다(signstage-docs
 * business/billing-catalog-unit-product-model-redesign-review.md 4장).
 */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자 조직×플랜 할인 오버라이드 횡단 목록 API")
@RestController
@RequiredArgsConstructor
public class PlatformAdminOrganizationDiscountListController {

    private final OrganizationDiscountService organizationDiscountService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "조직×플랜 할인 오버라이드 조직 횡단 목록", description = "organizationId는 선택 필터다. 생략하면 전체 조직을 반환한다.")
    @GetMapping("/api/platform-admin/billing-discounts/plans")
    public ApiResponse<PageResponse<OrganizationDiscountDto.Response.BillingPlanDiscountSummary>> findBillingPlanDiscounts(
            @RequestParam(required = false) Long organizationId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<OrganizationDiscountDto.Response.BillingPlanDiscountSummary> response =
                organizationDiscountService.findBillingPlanDiscountsAcrossOrganizations(organizationId, pageable);
        return ApiResponse.success(PageResponse.from(response), traceIdProvider.getTraceId());
    }
}
