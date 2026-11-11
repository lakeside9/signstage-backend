package com.eformworks.signstage.backend.feature.ceremony.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.billing.dto.BillingPlanDto;
import com.eformworks.signstage.backend.feature.ceremony.service.CeremonyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 과금 플랜별 사용 행사 조회. 행사 목록이라 billing이 아닌 ceremony에 둔다(backend-coding-convention.md
 * 3.4절) — 경로는 과금 카탈로그 관리 화면의 하위 리소스 그대로다. PLATFORM_SUPPORT 이상만 도달할 수
 * 있다(SecurityConfig에서 /api/platform-admin/** 전체를 게이트).
 */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자 과금 플랜 사용 행사 API")
@RestController
@RequestMapping("/api/platform-admin/billing-plans/{id}/ceremonies")
@RequiredArgsConstructor
public class PlatformAdminBillingPlanCeremonyController {

    private final CeremonyService ceremonyService;
    private final TraceIdProvider traceIdProvider;

    @Operation(
            summary = "이 플랜을 쓰는 행사 조직 횡단 목록 조회",
            description = "조직 멤버십과 무관하게 이 플랜을 쓰는 행사 전체를 본다. 카탈로그 관리 화면(항상 \"오늘\" 가격만 "
                    + "보여준다)만으로는 특정 행사가 실제로 어떤 값에 고정돼 있는지 알 수 없다는 문제의 발견성을 개선한다 "
                    + "(signstage-docs business/ceremony-plan-price-snapshot-consistency-review.md 3.5절). 조회 전용이라 "
                    + "등급 검사 없다."
    )
    @GetMapping
    public ApiResponse<PageResponse<BillingPlanDto.Response.CeremonyUsingPlanSummary>> findCeremoniesByPlan(
            @PathVariable Long id,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<BillingPlanDto.Response.CeremonyUsingPlanSummary> response = ceremonyService.findCeremoniesByBillingPlan(id, pageable);
        return ApiResponse.success(PageResponse.from(response), traceIdProvider.getTraceId());
    }
}
