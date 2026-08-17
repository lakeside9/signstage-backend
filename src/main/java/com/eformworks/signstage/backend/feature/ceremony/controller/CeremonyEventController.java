package com.eformworks.signstage.backend.feature.ceremony.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyEventDto;
import com.eformworks.signstage.backend.feature.ceremony.service.CeremonyEventService;
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
}
