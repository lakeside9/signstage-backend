package com.eformworks.signstage.backend.feature.support.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.support.dto.AnnouncementDto;
import com.eformworks.signstage.backend.feature.support.entity.SupportAudience;
import com.eformworks.signstage.backend.feature.support.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공지사항 조회 — 조직 스코프가 없는 전역 카탈로그다(인증된 사용자면 누구나). v1은 플랫폼
 * 전체 공개만 지원한다 — signstage-docs business/partner-support-center-review.md 9장 결정.
 */
@Tag(name = "Support", description = "공지사항 조회 API")
@RestController
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;
    private final TraceIdProvider traceIdProvider;

    @Operation(
            summary = "공지사항 목록 조회",
            description = "파트너용 활성 공지만 고정 우선 + 최신순으로 페이지 단위로 반환한다. keyword(title/content 부분일치)는 "
                    + "선택 필터다(고객용은 /api/public/announcements)."
    )
    @GetMapping("/api/announcements")
    public ApiResponse<PageResponse<AnnouncementDto.Response.AnnouncementSummary>> findPublicAnnouncements(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(
                PageResponse.from(announcementService.findAnnouncements(SupportAudience.PARTNER, keyword, true, pageable)),
                traceIdProvider.getTraceId());
    }

    @Operation(summary = "공지사항 상세 조회")
    @GetMapping("/api/announcements/{announcementId}")
    public ApiResponse<AnnouncementDto.Response.AnnouncementSummary> findPublicAnnouncement(
            @PathVariable Long announcementId
    ) {
        return ApiResponse.success(announcementService.findPublicAnnouncement(SupportAudience.PARTNER, announcementId), traceIdProvider.getTraceId());
    }
}
