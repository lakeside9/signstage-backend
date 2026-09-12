package com.eformworks.signstage.backend.feature.support.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.support.dto.AnnouncementDto;
import com.eformworks.signstage.backend.feature.support.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @Operation(summary = "공지사항 목록 조회", description = "활성 공지만 고정 우선 + 최신순으로 반환한다.")
    @GetMapping("/api/announcements")
    public ApiResponse<List<AnnouncementDto.Response.AnnouncementSummary>> findPublicAnnouncements() {
        return ApiResponse.success(announcementService.findPublicAnnouncements(), traceIdProvider.getTraceId());
    }

    @Operation(summary = "공지사항 상세 조회")
    @GetMapping("/api/announcements/{announcementId}")
    public ApiResponse<AnnouncementDto.Response.AnnouncementSummary> findPublicAnnouncement(
            @PathVariable Long announcementId
    ) {
        return ApiResponse.success(announcementService.findPublicAnnouncement(announcementId), traceIdProvider.getTraceId());
    }
}
