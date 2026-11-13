package com.eformworks.signstage.backend.feature.support.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.support.dto.AnnouncementDto;
import com.eformworks.signstage.backend.feature.support.dto.FaqDto;
import com.eformworks.signstage.backend.feature.support.entity.SupportAudience;
import com.eformworks.signstage.backend.feature.support.service.AnnouncementService;
import com.eformworks.signstage.backend.feature.support.service.FaqService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 화면의 고객용 FAQ·공지사항(2026-11-13) — 로그인 없이 조회한다({@code SecurityConfig}가 {@code /api/public/**}를 연다).
 * 파트너용 글은 여기서 보이지 않는다.
 */
@Tag(name = "Public", description = "로그인 없이 쓰는 공개 API")
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicSupportController {

    private final FaqService faqService;
    private final AnnouncementService announcementService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "고객용 FAQ 목록", description = "고객용 활성 FAQ만 표시 순서대로 반환한다.")
    @GetMapping("/faqs")
    public ApiResponse<List<FaqDto.Response.FaqSummary>> findCustomerFaqs() {
        return ApiResponse.success(faqService.findPublicFaqs(SupportAudience.CUSTOMER), traceIdProvider.getTraceId());
    }

    @Operation(summary = "고객용 공지사항 목록", description = "고객용 활성 공지만 고정 우선 + 최신순으로 반환한다.")
    @GetMapping("/announcements")
    public ApiResponse<List<AnnouncementDto.Response.AnnouncementSummary>> findCustomerAnnouncements() {
        return ApiResponse.success(announcementService.findPublicAnnouncements(SupportAudience.CUSTOMER), traceIdProvider.getTraceId());
    }

    @Operation(summary = "고객용 공지사항 상세")
    @GetMapping("/announcements/{announcementId}")
    public ApiResponse<AnnouncementDto.Response.AnnouncementSummary> findCustomerAnnouncement(@PathVariable Long announcementId) {
        return ApiResponse.success(
                announcementService.findPublicAnnouncement(SupportAudience.CUSTOMER, announcementId),
                traceIdProvider.getTraceId()
        );
    }
}
