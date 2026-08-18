package com.eformworks.signstage.backend.feature.ceremony.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyEventDto;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyEventLogDto;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyResultDto;
import com.eformworks.signstage.backend.feature.ceremony.dto.StrokeDataDto;
import com.eformworks.signstage.backend.feature.ceremony.service.CeremonyEventService;
import com.eformworks.signstage.backend.feature.ceremony.service.CeremonyResultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 하위 행사(CeremonyEvent, TEST/MAIN). 생성은 플랜의 유효 한도(기본값 + 추가구매)를
 * 초과하면 하드 블록된다(signstage-docs business/ceremony-billing-options-review.md 4.5절).
 * 이번 라운드는 DRAFT 생성까지만 다루고 READY/START/FINISH 전이 API는 아직 없다.
 */
@Tag(name = "Ceremony", description = "하위 행사(CeremonyEvent) API")
@RestController
@RequestMapping("/api/organizations/{organizationId}/ceremonies/{ceremonyId}/events")
@RequiredArgsConstructor
public class CeremonyEventController {

    private final CeremonyEventService ceremonyEventService;
    private final CeremonyResultService ceremonyResultService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "하위 행사 생성", description = "eventType은 TEST/MAIN. 플랜 한도를 넘으면 거부된다.")
    @PostMapping
    public ApiResponse<CeremonyEventDto.Response.CeremonyEventSummary> createCeremonyEvent(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @Valid @RequestBody CeremonyEventDto.Request.CreateCeremonyEvent request
    ) {
        CeremonyEventDto.Response.CeremonyEventSummary response = ceremonyEventService
                .createCeremonyEvent(organizationId, ceremonyId, currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "하위 행사 목록 조회")
    @GetMapping
    public ApiResponse<List<CeremonyEventDto.Response.CeremonyEventSummary>> findCeremonyEvents(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId
    ) {
        List<CeremonyEventDto.Response.CeremonyEventSummary> response =
                ceremonyEventService.findCeremonyEvents(organizationId, ceremonyId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "하위 행사 상세 조회")
    @GetMapping("/{eventId}")
    public ApiResponse<CeremonyEventDto.Response.CeremonyEventSummary> retrieveCeremonyEvent(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long eventId
    ) {
        CeremonyEventDto.Response.CeremonyEventSummary response = ceremonyEventService
                .retrieveCeremonyEvent(organizationId, ceremonyId, eventId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "하위 행사 수정",
            description = "이름/장소/일정/설명만 바꾼다. 구분(TEST/MAIN)은 여기서 바꿀 수 없다. "
                    + "시작되었거나 종료된 하위 행사는 수정할 수 없다."
    )
    @PutMapping("/{eventId}")
    public ApiResponse<CeremonyEventDto.Response.CeremonyEventSummary> updateCeremonyEvent(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long eventId,
            @Valid @RequestBody CeremonyEventDto.Request.UpdateCeremonyEvent request
    ) {
        CeremonyEventDto.Response.CeremonyEventSummary response = ceremonyEventService
                .updateCeremonyEvent(organizationId, ceremonyId, eventId, currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "하위 행사 삭제",
            description = "시작되었거나 종료된 하위 행사는 삭제할 수 없다."
    )
    @DeleteMapping("/{eventId}")
    public ApiResponse<Void> deleteCeremonyEvent(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long eventId
    ) {
        ceremonyEventService.deleteCeremonyEvent(organizationId, ceremonyId, eventId, currentUser.userId());
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "적용 선택옵션 교체",
            description = "행사 마스터가 구매한 선택옵션의 부분집합만 지정할 수 있다. 목록 전체를 교체한다."
    )
    @PutMapping("/{eventId}/optional-features")
    public ApiResponse<CeremonyEventDto.Response.CeremonyEventSummary> updateOptionalFeatures(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long eventId,
            @Valid @RequestBody CeremonyEventDto.Request.UpdateOptionalFeatures request
    ) {
        CeremonyEventDto.Response.CeremonyEventSummary response = ceremonyEventService
                .updateOptionalFeatures(organizationId, ceremonyId, eventId, currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "문서 매핑", description = "DRAFT/READY일 때만 가능하다. STARTED/FINISHED는 잠긴 상태다.")
    @PostMapping("/{eventId}/templates")
    public ApiResponse<CeremonyEventDto.Response.CeremonyTemplateSummary> mapTemplate(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long eventId,
            @Valid @RequestBody CeremonyEventDto.Request.MapTemplate request
    ) {
        CeremonyEventDto.Response.CeremonyTemplateSummary response = ceremonyEventService
                .mapTemplate(organizationId, ceremonyId, eventId, currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "매핑된 문서 목록 조회")
    @GetMapping("/{eventId}/templates")
    public ApiResponse<List<CeremonyEventDto.Response.CeremonyTemplateSummary>> findMappedTemplates(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long eventId
    ) {
        List<CeremonyEventDto.Response.CeremonyTemplateSummary> response = ceremonyEventService
                .findMappedTemplates(organizationId, ceremonyId, eventId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "READY 전이",
            description = "CONTRACT/EXHIBITION 각 1개 이상 매핑, 필수 서명란 전원 배정, "
                    + "CONTRACT/EXHIBITION 필수 서명자 구성 일치가 조건이다."
    )
    @PostMapping("/{eventId}/ready")
    public ApiResponse<CeremonyEventDto.Response.CeremonyEventSummary> transitionToReady(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long eventId
    ) {
        CeremonyEventDto.Response.CeremonyEventSummary response = ceremonyEventService
                .transitionToReady(organizationId, ceremonyId, eventId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "START 전이", description = "READY 상태여야 한다.")
    @PostMapping("/{eventId}/start")
    public ApiResponse<CeremonyEventDto.Response.CeremonyEventSummary> transitionToStart(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long eventId
    ) {
        CeremonyEventDto.Response.CeremonyEventSummary response = ceremonyEventService
                .transitionToStart(organizationId, ceremonyId, eventId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "FINISH 전이",
            description = "STARTED 상태여야 하고, 필수 서명자 전원이 서명을 완료해야 한다. "
                    + "관리자가 명시적으로 호출한다(자동 전이 없음)."
    )
    @PostMapping("/{eventId}/finish")
    public ApiResponse<CeremonyEventDto.Response.CeremonyEventSummary> transitionToFinish(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long eventId
    ) {
        CeremonyEventDto.Response.CeremonyEventSummary response = ceremonyEventService
                .transitionToFinish(organizationId, ceremonyId, eventId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "서명 재요청(REPLACE)",
            description = "관리자가 한 서명자의 이 이벤트 서명 진행 상황 전체를 초기화한다(완료 여부와 무관). STARTED 상태여야 한다."
    )
    @PostMapping("/{eventId}/signers/{signerId}/replace-signature")
    public ApiResponse<Void> replaceSignerSignature(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long eventId,
            @PathVariable Long signerId
    ) {
        ceremonyEventService.replaceSignerSignature(organizationId, ceremonyId, eventId, signerId, currentUser.userId());
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }

    @Operation(summary = "감사 로그 조회")
    @GetMapping("/{eventId}/logs")
    public ApiResponse<List<CeremonyEventLogDto.Response.CeremonyEventLogSummary>> findEventLogs(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long eventId
    ) {
        List<CeremonyEventLogDto.Response.CeremonyEventLogSummary> response = ceremonyEventService
                .findEventLogs(organizationId, ceremonyId, eventId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "결과물 생성",
            description = "FINISHED 상태여야 하고, 이벤트당 1회만 생성할 수 있다. "
                    + "매핑된 CONTRACT/EXHIBITION 문서마다 서명이 그려진 PDF를 만든다."
    )
    @PostMapping("/{eventId}/results")
    public ApiResponse<List<CeremonyResultDto.Response.CeremonyResultSummary>> generateResults(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long eventId
    ) {
        List<CeremonyResultDto.Response.CeremonyResultSummary> response = ceremonyResultService
                .generateResults(organizationId, ceremonyId, eventId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "결과물 목록 조회")
    @GetMapping("/{eventId}/results")
    public ApiResponse<List<CeremonyResultDto.Response.CeremonyResultSummary>> findResults(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long eventId
    ) {
        List<CeremonyResultDto.Response.CeremonyResultSummary> response = ceremonyResultService
                .findResults(organizationId, ceremonyId, eventId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "실시간 스트로크 캐치업 조회",
            description = "행사제어 화면이 늦게 들어와도 이미 그려진 획을 이어서 볼 수 있게 한다. "
                    + "그 이후의 획은 WebSocket(SIGNATURE_STROKE_SUBMITTED)으로 이어받는다."
    )
    @GetMapping("/{eventId}/strokes")
    public ApiResponse<List<StrokeDataDto.Response.StrokeSummary>> findStrokes(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long eventId
    ) {
        List<StrokeDataDto.Response.StrokeSummary> response = ceremonyEventService
                .findStrokes(organizationId, ceremonyId, eventId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "결과물 파일 다운로드",
            description = "관리자 콘솔 전용 경로다(JWT+조직 소속 검증) — 서명자 포털용 다운로드는 없다."
    )
    @GetMapping("/{eventId}/results/{resultId}/file")
    public ResponseEntity<Resource> downloadResultFile(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long eventId,
            @PathVariable Long resultId
    ) {
        CeremonyResultService.DownloadedResult downloaded = ceremonyResultService
                .downloadResultFile(organizationId, ceremonyId, eventId, resultId, currentUser.userId());

        String encodedFilename = URLEncoder.encode(downloaded.originalFilename(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .body(downloaded.resource());
    }
}
