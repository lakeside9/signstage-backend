package com.eformworks.signstage.backend.feature.support.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.support.dto.AnnouncementDto;
import com.eformworks.signstage.backend.feature.support.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공지사항 관리. PLATFORM_SUPPORT 이상만 도달할 수 있고(SecurityConfig가
 * {@code /api/platform-admin/**} 전체를 게이트), 실제 등록·수정·삭제는
 * {@code ACTION_ANNOUNCEMENT_MANAGE}가 허용된 등급(PLATFORM_OPS 이상)만 서비스에서 한 번 더 검사한다.
 */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자 공지사항 관리 API")
@RestController
@RequestMapping("/api/platform-admin/announcements")
@RequiredArgsConstructor
public class PlatformAdminAnnouncementController {

    private final AnnouncementService announcementService;
    private final TraceIdProvider traceIdProvider;

    @Operation(
            summary = "공지사항 목록 조회",
            description = "keyword(title/content 부분일치), active 둘 다 선택 필터다(생략하면 그 조건 없이 전체)."
    )
    @GetMapping
    public ApiResponse<PageResponse<AnnouncementDto.Response.AnnouncementSummary>> findAnnouncements(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<AnnouncementDto.Response.AnnouncementSummary> response = announcementService.findAnnouncements(keyword, active, pageable);
        return ApiResponse.success(PageResponse.from(response), traceIdProvider.getTraceId());
    }

    @Operation(summary = "공지사항 상세 조회")
    @GetMapping("/{announcementId}")
    public ApiResponse<AnnouncementDto.Response.AnnouncementSummary> findAnnouncement(@PathVariable Long announcementId) {
        return ApiResponse.success(announcementService.findAnnouncement(announcementId), traceIdProvider.getTraceId());
    }

    @Operation(summary = "공지사항 등록", description = "ACTION_ANNOUNCEMENT_MANAGE가 허용된 등급만 호출할 수 있다(PLATFORM_OPS 이상).")
    @PostMapping
    public ApiResponse<AnnouncementDto.Response.AnnouncementSummary> createAnnouncement(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody AnnouncementDto.Request.CreateAnnouncement request
    ) {
        AnnouncementDto.Response.AnnouncementSummary response =
                announcementService.createAnnouncement(currentUser.platformRole(), currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "공지사항 수정", description = "ACTION_ANNOUNCEMENT_MANAGE가 허용된 등급만 호출할 수 있다(PLATFORM_OPS 이상).")
    @PutMapping("/{announcementId}")
    public ApiResponse<AnnouncementDto.Response.AnnouncementSummary> updateAnnouncement(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long announcementId,
            @Valid @RequestBody AnnouncementDto.Request.UpdateAnnouncement request
    ) {
        AnnouncementDto.Response.AnnouncementSummary response =
                announcementService.updateAnnouncement(announcementId, currentUser.platformRole(), currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "공지사항 삭제", description = "ACTION_ANNOUNCEMENT_MANAGE가 허용된 등급만 호출할 수 있다(PLATFORM_OPS 이상).")
    @DeleteMapping("/{announcementId}")
    public ApiResponse<Void> deleteAnnouncement(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long announcementId
    ) {
        announcementService.deleteAnnouncement(announcementId, currentUser.platformRole(), currentUser.userId());
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }
}
