package com.eformworks.signstage.backend.feature.platformadmin.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyStatus;
import com.eformworks.signstage.backend.feature.ceremony.service.CeremonyService;
import com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminCeremonyDiscountDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 행사 건별 재량 할인 조직 횡단 목록 API — 조직 상세를 거치지 않는 별도 목록 화면 전용
 * (signstage-docs business/discount-management-screen-separation-review.md). 단건 조회/수정은
 * 기존 {@link PlatformAdminCeremonyController}(조직 하위 중첩)를 그대로 재사용한다(같은 문서
 * 6장 결정 #2) — 이 컨트롤러는 읽기 전용 목록 하나만 새로 추가한다. 조회 전용이라 등급 검사
 * 없이 PLATFORM_SUPPORT 이상이면 누구나 호출할 수 있다(다른 조회 API들과 같은 관례).
 */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자 행사 건별 재량 할인 횡단 목록 API")
@RestController
@RequestMapping("/api/platform-admin/ceremonies")
@RequiredArgsConstructor
public class PlatformAdminCeremonyDiscountController {

    private final CeremonyService ceremonyService;
    private final TraceIdProvider traceIdProvider;

    @Operation(
            summary = "행사 건별 재량 할인 조직 횡단 목록 조회",
            description = "organizationId/status/hasFinalDiscount 전부 선택 필터다. hasFinalDiscount를 생략하면 전체를,"
                    + " true면 할인이 설정된 행사만, false면 설정 안 된 행사만 반환한다."
    )
    @GetMapping
    public ApiResponse<PageResponse<PlatformAdminCeremonyDiscountDto.Response.CeremonyDiscountSummary>> findCeremonyDiscounts(
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) CeremonyStatus status,
            @RequestParam(required = false) Boolean hasFinalDiscount,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<PlatformAdminCeremonyDiscountDto.Response.CeremonyDiscountSummary> response =
                ceremonyService.findCeremonyDiscountsAcrossOrganizations(organizationId, status, hasFinalDiscount, pageable);
        return ApiResponse.success(PageResponse.from(response), traceIdProvider.getTraceId());
    }
}
