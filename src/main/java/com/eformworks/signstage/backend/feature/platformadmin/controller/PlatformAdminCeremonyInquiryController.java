package com.eformworks.signstage.backend.feature.platformadmin.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.ceremony.entity.InquiryStatus;
import com.eformworks.signstage.backend.feature.ceremony.service.CeremonyInquiryService;
import com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminCeremonyInquiryDto;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 플랫폼 관리자의 행사별 1:1 문의 조회/답변/종료 API — signstage-docs
 * business/partner-support-center-review.md 5.3절. 조회는 PLATFORM_SUPPORT 이상,
 * 답변/종료는 {@code ACTION_CEREMONY_INQUIRY_MANAGE}가 허용된 등급(PLATFORM_OPS 이상)만
 * 서비스에서 한 번 더 검사한다 — {@link PlatformAdminCeremonyPurchaseController}와 같은 규약.
 */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자 행사별 1:1 문의 API")
@RestController
@RequestMapping("/api/platform-admin/ceremony-inquiries")
@RequiredArgsConstructor
public class PlatformAdminCeremonyInquiryController {

    private final CeremonyInquiryService ceremonyInquiryService;
    private final TraceIdProvider traceIdProvider;

    @Operation(
            summary = "행사별 1:1 문의 조직 횡단 목록 조회",
            description = "status/organizationId/ceremonyId/requesterKeyword/ceremonyTitle 전부 선택 필터다."
    )
    @GetMapping
    public ApiResponse<PageResponse<PlatformAdminCeremonyInquiryDto.Response.InquirySummary>> findInquiries(
            @RequestParam(required = false) InquiryStatus status,
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) Long ceremonyId,
            @RequestParam(required = false) String requesterKeyword,
            @RequestParam(required = false) String ceremonyTitle,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<PlatformAdminCeremonyInquiryDto.Response.InquirySummary> response =
                ceremonyInquiryService.findInquiriesAcrossOrganizations(
                        status, organizationId, ceremonyId, requesterKeyword, ceremonyTitle, pageable
                );
        return ApiResponse.success(PageResponse.from(response), traceIdProvider.getTraceId());
    }

    @Operation(summary = "행사별 1:1 문의 상세 조회")
    @GetMapping("/{inquiryId}")
    public ApiResponse<PlatformAdminCeremonyInquiryDto.Response.InquiryDetail> findInquiry(@PathVariable Long inquiryId) {
        return ApiResponse.success(ceremonyInquiryService.findInquiryByPlatformAdmin(inquiryId), traceIdProvider.getTraceId());
    }

    @Operation(summary = "답변", description = "ACTION_CEREMONY_INQUIRY_MANAGE가 허용된 등급만 호출할 수 있다(PLATFORM_OPS 이상).")
    @PostMapping("/{inquiryId}/messages")
    public ApiResponse<PlatformAdminCeremonyInquiryDto.Response.InquiryDetail> reply(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long inquiryId,
            @Valid @RequestBody PlatformAdminCeremonyInquiryDto.Request.Reply request
    ) {
        PlatformAdminCeremonyInquiryDto.Response.InquiryDetail response =
                ceremonyInquiryService.replyAsAdmin(inquiryId, currentUser.platformRole(), currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "종료", description = "ACTION_CEREMONY_INQUIRY_MANAGE가 허용된 등급만 호출할 수 있다(PLATFORM_OPS 이상).")
    @PutMapping("/{inquiryId}/close")
    public ApiResponse<PlatformAdminCeremonyInquiryDto.Response.InquiryDetail> close(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long inquiryId
    ) {
        PlatformAdminCeremonyInquiryDto.Response.InquiryDetail response =
                ceremonyInquiryService.closeInquiryAsAdmin(inquiryId, currentUser.platformRole(), currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
