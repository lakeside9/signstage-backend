package com.eformworks.signstage.backend.feature.notification.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.notification.dto.NotificationDto;
import com.eformworks.signstage.backend.feature.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 내 알림 API — 로그인한 사용자 본인 알림만 다룬다. */
@Tag(name = "Notification", description = "앱 안 알림 API")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "내 알림 목록 조회", description = "최신순. unreadOnly=true면 읽지 않은 알림만.")
    @GetMapping
    public ApiResponse<PageResponse<NotificationDto.Response.NotificationSummary>> findMine(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(
                PageResponse.from(notificationService.findMine(currentUser.userId(), unreadOnly, pageable)),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "읽지 않은 알림 개수")
    @GetMapping("/unread-count")
    public ApiResponse<NotificationDto.Response.UnreadCount> countUnread(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.success(notificationService.countUnread(currentUser.userId()), traceIdProvider.getTraceId());
    }

    @Operation(summary = "알림 읽음 처리")
    @PostMapping("/{notificationId}/read")
    public ApiResponse<NotificationDto.Response.NotificationSummary> markRead(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long notificationId
    ) {
        return ApiResponse.success(notificationService.markRead(currentUser.userId(), notificationId), traceIdProvider.getTraceId());
    }

    @Operation(summary = "모든 알림 읽음 처리")
    @PostMapping("/read-all")
    public ApiResponse<NotificationDto.Response.UnreadCount> markAllRead(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.success(notificationService.markAllRead(currentUser.userId()), traceIdProvider.getTraceId());
    }
}
