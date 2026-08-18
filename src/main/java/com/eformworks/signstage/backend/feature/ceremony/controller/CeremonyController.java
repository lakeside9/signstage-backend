package com.eformworks.signstage.backend.feature.ceremony.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyStatus;
import com.eformworks.signstage.backend.feature.ceremony.service.CeremonyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 행사 마스터(Ceremony). OWNER/ADMIN은 조직의 모든 행사를, OPERATOR는 본인이 배정된 행사만
 * 다룰 수 있다(user-organization-design.md 4.2절). signstage-docs
 * business/ceremony-billing-options-review.md 4.10절 — 생성 시 플랜 선택이 필수다.
 */
@Tag(name = "Ceremony", description = "행사 마스터 API")
@RestController
@RequestMapping("/api/organizations/{organizationId}/ceremonies")
@RequiredArgsConstructor
public class CeremonyController {

    private final CeremonyService ceremonyService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "행사 생성", description = "billingPlanId는 필수다. 생성자는 자동으로 배정된다.")
    @PostMapping
    public ApiResponse<CeremonyDto.Response.CeremonySummary> createCeremony(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @Valid @RequestBody CeremonyDto.Request.CreateCeremony request
    ) {
        CeremonyDto.Response.CeremonySummary response =
                ceremonyService.createCeremony(organizationId, currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "행사 목록 조회",
            description = "OPERATOR는 본인이 배정된 행사만 조회된다. title은 부분 일치, status는 정확히 일치."
    )
    @GetMapping
    public ApiResponse<PageResponse<CeremonyDto.Response.CeremonySummary>> findCeremonies(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) CeremonyStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<CeremonyDto.Response.CeremonySummary> response =
                ceremonyService.findCeremonies(organizationId, currentUser.userId(), title, status, pageable);
        return ApiResponse.success(PageResponse.from(response), traceIdProvider.getTraceId());
    }

    @Operation(summary = "행사 상세 조회")
    @GetMapping("/{ceremonyId}")
    public ApiResponse<CeremonyDto.Response.CeremonySummary> retrieveCeremony(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId
    ) {
        CeremonyDto.Response.CeremonySummary response =
                ceremonyService.retrieveCeremony(organizationId, ceremonyId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "행사 정보 수정",
            description = "이름/설명만 바꾼다. 플랜은 생성 시점에 고정이라 여기서 바꿀 수 없다. 완료된 행사는 수정할 수 없다."
    )
    @PutMapping("/{ceremonyId}")
    public ApiResponse<CeremonyDto.Response.CeremonySummary> updateCeremony(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @Valid @RequestBody CeremonyDto.Request.UpdateCeremony request
    ) {
        CeremonyDto.Response.CeremonySummary response =
                ceremonyService.updateCeremony(organizationId, ceremonyId, currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "필수옵션(용량) 추가구매", description = "예: 서명자 +10명. 한 번 구매하면 취소할 수 없다.")
    @PostMapping("/{ceremonyId}/capacity-purchases")
    public ApiResponse<CeremonyDto.Response.CapacityPurchaseSummary> purchaseCapacity(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @Valid @RequestBody CeremonyDto.Request.PurchaseCapacity request
    ) {
        CeremonyDto.Response.CapacityPurchaseSummary response =
                ceremonyService.purchaseCapacity(organizationId, ceremonyId, currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "선택옵션 구매", description = "행사 마스터 단위 구매다. 실제 적용은 이벤트 단위로 별도 선택한다.")
    @PostMapping("/{ceremonyId}/optional-feature-purchases")
    public ApiResponse<CeremonyDto.Response.OptionalFeaturePurchaseSummary> purchaseOptionalFeature(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @Valid @RequestBody CeremonyDto.Request.PurchaseOptionalFeature request
    ) {
        CeremonyDto.Response.OptionalFeaturePurchaseSummary response =
                ceremonyService.purchaseOptionalFeature(organizationId, ceremonyId, currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
