package com.eformworks.signstage.backend.feature.organization.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.organization.dto.OrganizationCreationRequestDto;
import com.eformworks.signstage.backend.feature.organization.service.OrganizationCreationRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 일반 사용자가 조직 생성을 요청/취소/조회하는 API다. signstage-docs
 * business/organization-creation-approval-review.md 참고 — 조직은 이 요청이 승인돼야
 * 만들어진다. 승인/반려는 플랫폼 관리자 콘솔(feature.platformadmin)에서 다룬다.
 */
@Tag(name = "Organization", description = "조직 생성 요청 API")
@RestController
@RequestMapping("/api/organizations/requests")
@RequiredArgsConstructor
public class OrganizationCreationRequestController {

    private final OrganizationCreationRequestService organizationCreationRequestService;
    private final TraceIdProvider traceIdProvider;

    @Operation(
            summary = "조직 생성 요청",
            description = "즉시 생성되지 않는다 — 플랫폼 관리자 승인이 있어야 조직이 만들어진다. "
                    + "동시 PENDING 요청은 1건만 가능하고, 최초 요청을 포함해 최대 5회까지 제출할 수 있다."
    )
    @PostMapping
    public ApiResponse<OrganizationCreationRequestDto.Response.RequestSummary> submit(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody OrganizationCreationRequestDto.Request.Create request
    ) {
        OrganizationCreationRequestDto.Response.RequestSummary response =
                organizationCreationRequestService.submit(currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "내 조직 생성 요청 목록 조회", description = "PENDING/APPROVED/REJECTED/CANCELLED 전부 최신순으로 반환한다.")
    @GetMapping
    public ApiResponse<List<OrganizationCreationRequestDto.Response.RequestSummary>> findMyRequests(
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        List<OrganizationCreationRequestDto.Response.RequestSummary> response =
                organizationCreationRequestService.findMyRequests(currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "조직 생성 요청 취소", description = "PENDING 상태의 본인 요청만 취소할 수 있다.")
    @DeleteMapping("/{requestId}")
    public ApiResponse<Void> cancel(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long requestId
    ) {
        organizationCreationRequestService.cancel(requestId, currentUser.userId());
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }
}
