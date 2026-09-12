package com.eformworks.signstage.backend.feature.ceremony.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyInquiryDto;
import com.eformworks.signstage.backend.feature.ceremony.service.CeremonyInquiryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 행사별 1:1 문의(파트너 쪽). {@code Ceremony} 직속이라 서명자/문서 양식과 같은 접근 검사
 * 규약을 쓴다 — signstage-docs business/partner-support-center-review.md 5.3절.
 */
@Tag(name = "Ceremony", description = "행사별 1:1 문의(파트너) API")
@RestController
@RequestMapping("/api/organizations/{organizationId}/ceremonies/{ceremonyId}/inquiries")
@RequiredArgsConstructor
public class CeremonyInquiryController {

    private final CeremonyInquiryService ceremonyInquiryService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "문의 등록", description = "제목 + 첫 메시지를 함께 등록한다.")
    @PostMapping
    public ApiResponse<CeremonyInquiryDto.Response.InquiryDetail> createInquiry(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @Valid @RequestBody CeremonyInquiryDto.Request.CreateInquiry request
    ) {
        CeremonyInquiryDto.Response.InquiryDetail response =
                ceremonyInquiryService.createInquiry(organizationId, ceremonyId, currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "이 행사의 문의 목록 조회", description = "최근 갱신순(답변 대기가 위로 오도록)이다.")
    @GetMapping
    public ApiResponse<List<CeremonyInquiryDto.Response.InquirySummary>> findInquiries(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId
    ) {
        List<CeremonyInquiryDto.Response.InquirySummary> response =
                ceremonyInquiryService.findInquiries(organizationId, ceremonyId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "문의 상세 조회", description = "메시지 전체를 시간순으로 포함한다.")
    @GetMapping("/{inquiryId}")
    public ApiResponse<CeremonyInquiryDto.Response.InquiryDetail> findInquiry(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long inquiryId
    ) {
        CeremonyInquiryDto.Response.InquiryDetail response =
                ceremonyInquiryService.findInquiry(organizationId, ceremonyId, inquiryId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "메시지 추가",
            description = "종료(CLOSED)된 문의에는 추가할 수 없다 — 계속 물어보려면 새 문의를 등록해야 한다."
    )
    @PostMapping("/{inquiryId}/messages")
    public ApiResponse<CeremonyInquiryDto.Response.InquiryDetail> addMessage(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long inquiryId,
            @Valid @RequestBody CeremonyInquiryDto.Request.AddMessage request
    ) {
        CeremonyInquiryDto.Response.InquiryDetail response =
                ceremonyInquiryService.addMessage(organizationId, ceremonyId, inquiryId, currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "문의 종료", description = "종료 후에는 재오픈할 수 없다.")
    @PutMapping("/{inquiryId}/close")
    public ApiResponse<CeremonyInquiryDto.Response.InquiryDetail> closeInquiry(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long inquiryId
    ) {
        CeremonyInquiryDto.Response.InquiryDetail response =
                ceremonyInquiryService.closeInquiry(organizationId, ceremonyId, inquiryId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
