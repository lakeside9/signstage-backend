package com.eformworks.signstage.backend.feature.ceremony.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyOnsiteSupportRequestDto;
import com.eformworks.signstage.backend.feature.ceremony.service.CeremonyOnsiteSupportRequestService;
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
 * 현장지원 요청(관리자 견적, 파트너 쪽) — signstage-docs
 * business/onsite-support-negotiation-and-billing-classification-review.md 3.2절. 파트너가
 * 일시·장소를 적어 요청하고, 관리자가 매긴 금액을 수락/거부한다. {@code Ceremony} 직속이라
 * 서명자/문서 양식과 같은 접근 검사 규약을 쓴다.
 */
@Tag(name = "Ceremony", description = "현장지원 요청(관리자 견적, 파트너) API")
@RestController
@RequestMapping("/api/organizations/{organizationId}/ceremonies/{ceremonyId}/onsite-support-requests")
@RequiredArgsConstructor
public class CeremonyOnsiteSupportRequestController {

    private final CeremonyOnsiteSupportRequestService ceremonyOnsiteSupportRequestService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "현장지원 요청 등록", description = "일시·장소를 입력한다. 아직 견적은 없는 상태(REQUESTED)로 생긴다.")
    @PostMapping
    public ApiResponse<CeremonyOnsiteSupportRequestDto.Response.RequestSummary> createRequest(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @Valid @RequestBody CeremonyOnsiteSupportRequestDto.Request.CreateRequest request
    ) {
        CeremonyOnsiteSupportRequestDto.Response.RequestSummary response =
                ceremonyOnsiteSupportRequestService.createRequest(organizationId, ceremonyId, currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "이 행사의 현장지원 요청 목록 조회", description = "최신순이다.")
    @GetMapping
    public ApiResponse<List<CeremonyOnsiteSupportRequestDto.Response.RequestSummary>> findRequests(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId
    ) {
        List<CeremonyOnsiteSupportRequestDto.Response.RequestSummary> response =
                ceremonyOnsiteSupportRequestService.findRequests(organizationId, ceremonyId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "견적 수락",
            description = "견적(QUOTED)이 매겨진 요청만 수락할 수 있다. 수락하면 그 금액으로 구매 1건이 즉시 생성되고 " +
                    "\"플랫폼 이용료\"에 반영된다."
    )
    @PutMapping("/{requestId}/accept")
    public ApiResponse<CeremonyOnsiteSupportRequestDto.Response.RequestSummary> acceptRequest(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long requestId
    ) {
        CeremonyOnsiteSupportRequestDto.Response.RequestSummary response =
                ceremonyOnsiteSupportRequestService.acceptRequest(organizationId, ceremonyId, requestId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "견적 거부", description = "종결이다 — 재협상은 없다. 다시 필요하면 새 요청을 등록한다.")
    @PutMapping("/{requestId}/decline")
    public ApiResponse<CeremonyOnsiteSupportRequestDto.Response.RequestSummary> declineRequest(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long requestId
    ) {
        CeremonyOnsiteSupportRequestDto.Response.RequestSummary response =
                ceremonyOnsiteSupportRequestService.declineRequest(organizationId, ceremonyId, requestId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
