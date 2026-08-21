package com.eformworks.signstage.backend.feature.platformadmin.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyStatus;
import com.eformworks.signstage.backend.feature.ceremony.service.CeremonyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 행사 마스터(Ceremony) 완료 상태·건별 재량 할인을 플랫폼 관리자가 다룬다. PLATFORM_SUPPORT
 * 이상만 도달할 수 있고(SecurityConfig에서 /api/platform-admin/** 전체를 게이트), 실제 변경은
 * PLATFORM_OPS 이상만 서비스에서 한 번 더 검사한다 — 다른 PlatformAdminXxxController와 같은
 * 패턴이다. 조직 하위 리소스 URL 중첩은 {@link PlatformAdminMemberController}와 같은 관례다.
 */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자 행사 상태/할인 API")
@RestController
@RequestMapping("/api/platform-admin/organizations/{organizationId}/ceremonies")
@RequiredArgsConstructor
public class PlatformAdminCeremonyController {

    private final CeremonyService ceremonyService;
    private final TraceIdProvider traceIdProvider;

    @Operation(
            summary = "행사 목록 조회(플랫폼 관리자)",
            description = "조직 멤버십과 무관하게 그 조직의 모든 행사를 본다. title은 부분 일치, status는 정확히 일치. "
                    + "조회 전용이라 등급 검사 없이 PLATFORM_SUPPORT 이상이면 누구나 호출할 수 있다."
    )
    @GetMapping
    public ApiResponse<PageResponse<CeremonyDto.Response.CeremonySummary>> findCeremonies(
            @PathVariable Long organizationId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) CeremonyStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<CeremonyDto.Response.CeremonySummary> response =
                ceremonyService.findCeremoniesByPlatformAdmin(organizationId, title, status, pageable);
        return ApiResponse.success(PageResponse.from(response), traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "행사 상태 강제 변경",
            description = "IN_PROGRESS/COMPLETED 양방향 변경. PLATFORM_OPS 이상만 호출할 수 있다."
    )
    @PutMapping("/{ceremonyId}/status")
    public ApiResponse<CeremonyDto.Response.CeremonySummary> updateCeremonyStatus(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @Valid @RequestBody CeremonyDto.Request.UpdateStatus request
    ) {
        CeremonyDto.Response.CeremonySummary response = ceremonyService.updateStatusByPlatformAdmin(
                organizationId, ceremonyId, currentUser.userId(), currentUser.platformRole(), request
        );
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "행사 건별 재량 할인 설정",
            description = "품목 할인과 별개로 이 행사 건에만 적용하는 추가 할인. 플랜이 확정된(IN_PROGRESS) 행사에만 "
                    + "적용할 수 있다(DRAFT/COMPLETED는 거부). PLATFORM_OPS 이상만 호출할 수 있다."
    )
    @PutMapping("/{ceremonyId}/final-discount")
    public ApiResponse<CeremonyDto.Response.CeremonySummary> applyFinalDiscount(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @Valid @RequestBody CeremonyDto.Request.ApplyFinalDiscount request
    ) {
        CeremonyDto.Response.CeremonySummary response = ceremonyService.applyFinalDiscount(
                organizationId, ceremonyId, currentUser.userId(), currentUser.platformRole(), request
        );
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
