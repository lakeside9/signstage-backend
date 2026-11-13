package com.eformworks.signstage.backend.feature.support.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.support.dto.ContactInquiryDto;
import com.eformworks.signstage.backend.feature.support.entity.ContactInquiryStatus;
import com.eformworks.signstage.backend.feature.support.service.ContactInquiryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 고객 문의 관리 — 처리 상태·메모 기록은 {@code ACTION_CONTACT_INQUIRY_MANAGE} 권한자만 한다. */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자 고객 문의 관리 API")
@RestController
@RequestMapping("/api/platform-admin/contact-inquiries")
@RequiredArgsConstructor
public class PlatformAdminContactInquiryController {

    private final ContactInquiryService contactInquiryService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "고객 문의 목록", description = "status, keyword(회사명·담당자·이메일·내용) 모두 선택 필터다. 최근 접수 순.")
    @GetMapping
    public ApiResponse<PageResponse<ContactInquiryDto.Response.ContactInquirySummary>> findInquiries(
            @RequestParam(required = false) ContactInquiryStatus status,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(
                PageResponse.from(contactInquiryService.findInquiries(status, keyword, pageable)),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "고객 문의 상세")
    @GetMapping("/{inquiryId}")
    public ApiResponse<ContactInquiryDto.Response.ContactInquirySummary> findInquiry(@PathVariable Long inquiryId) {
        return ApiResponse.success(contactInquiryService.findInquiry(inquiryId), traceIdProvider.getTraceId());
    }

    @Operation(summary = "고객 문의 처리 기록", description = "처리 상태와 내부 메모를 남긴다. 고객에게 따로 알리지 않는다.")
    @PutMapping("/{inquiryId}")
    public ApiResponse<ContactInquiryDto.Response.ContactInquirySummary> handle(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long inquiryId,
            @Valid @RequestBody ContactInquiryDto.Request.Handle request
    ) {
        return ApiResponse.success(
                contactInquiryService.handle(inquiryId, currentUser.platformRole(), currentUser.userId(), request),
                traceIdProvider.getTraceId()
        );
    }
}
