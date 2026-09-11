package com.eformworks.signstage.backend.feature.ceremony.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyDto;
import com.eformworks.signstage.backend.feature.ceremony.dto.CustomerQuoteDto;
import com.eformworks.signstage.backend.feature.ceremony.dto.UnitProductDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyStatus;
import com.eformworks.signstage.backend.feature.ceremony.service.CeremonyService;
import com.eformworks.signstage.backend.feature.ceremony.service.CustomerQuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
 * 행사 마스터(Ceremony). OWNER/ADMIN은 조직의 모든 행사를, OPERATOR는 본인이 배정된 행사만
 * 다룰 수 있다(user-organization-design.md 4.2절).
 *
 * <p>생성된 Ceremony는 DRAFT로 시작한다 — 확정 전엔 플랜을 자유롭게 고르거나 바꿀 수 있고
 * (서명자/문서/하위 행사는 등록할 수 없다), "플랜 확정"으로 IN_PROGRESS로 넘어가면 그때부터
 * 반대가 된다(signstage-docs business/ceremony-plan-confirmation-review.md). 생성 시 플랜
 * 선택은 더 이상 필수가 아니다(2026-09-10, signstage-docs
 * business/ceremony-registration-flow-and-billing-tab-separation-review.md — 옛
 * {@code ceremony-billing-options-review.md} 4.10절 "생성 시 필수" 결정을 뒤집었다) — 생략하면
 * 플랜 없이 DRAFT로 만들어지고, 확정하려면 그 전에 플랜을 선택해야 한다.
 */
@Tag(name = "Ceremony", description = "행사 마스터 API")
@RestController
@RequestMapping("/api/organizations/{organizationId}/ceremonies")
@RequiredArgsConstructor
public class CeremonyController {

    private final CeremonyService ceremonyService;
    private final CustomerQuoteService customerQuoteService;
    private final TraceIdProvider traceIdProvider;

    @Operation(
            summary = "행사 생성",
            description = "billingPlanId는 생략할 수 있다 — 생략하면 플랜 없이 DRAFT로 만들어지고 나중에 /plan으로 "
                    + "선택하면 된다. 생성자는 자동으로 배정된다. 생성 직후엔 DRAFT 상태라 플랜을 자유롭게 "
                    + "고르거나 바꿀 수 있고, 확정(/plan/confirm)해야 서명자/문서/하위 행사를 등록할 수 있다(확정하려면 "
                    + "플랜이 먼저 선택돼 있어야 한다)."
    )
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
            description = "이름/설명, 주관 기관/부서, 담당자 정보를 바꾼다. 플랜은 여기서 바꿀 수 없다(/plan을 쓴다). "
                    + "완료된 행사는 수정할 수 없다."
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

    @Operation(
            summary = "행사 삭제",
            description = "플랜이 확정되지 않은(DRAFT) 행사만 삭제할 수 있다. 대기중·승인된 추가구매나 확정 견적이 "
                    + "있으면(DRAFT여도 만들 수 있다) 거부한다."
    )
    @DeleteMapping("/{ceremonyId}")
    public ApiResponse<Void> deleteCeremony(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId
    ) {
        ceremonyService.deleteCeremony(organizationId, ceremonyId, currentUser.userId());
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "플랜 변경",
            description = "플랜이 확정되기 전(DRAFT)에만 가능하다. 호출할 때마다 플랜 변경 이력이 한 행씩 남는다."
    )
    @PutMapping("/{ceremonyId}/plan")
    public ApiResponse<CeremonyDto.Response.CeremonySummary> changePlan(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @Valid @RequestBody CeremonyDto.Request.ChangePlan request
    ) {
        CeremonyDto.Response.CeremonySummary response =
                ceremonyService.changePlan(organizationId, ceremonyId, currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "플랜 선택 해제",
            description = "확정되기 전(DRAFT)에만 가능하다. 선택된 플랜이 없으면 실패한다. 변경 이력은 남기지 않는다."
    )
    @DeleteMapping("/{ceremonyId}/plan")
    public ApiResponse<CeremonyDto.Response.CeremonySummary> clearPlan(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId
    ) {
        CeremonyDto.Response.CeremonySummary response =
                ceremonyService.clearPlan(organizationId, ceremonyId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "플랜 확정",
            description = "DRAFT → IN_PROGRESS로 단방향 전이한다. 확정 후에는 플랜을 바꿀 수 없고, "
                    + "서명자/문서/하위 행사를 등록할 수 있다."
    )
    @PostMapping("/{ceremonyId}/plan/confirm")
    public ApiResponse<CeremonyDto.Response.CeremonySummary> confirmPlan(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId
    ) {
        CeremonyDto.Response.CeremonySummary response =
                ceremonyService.confirmPlan(organizationId, ceremonyId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "플랜 변경 이력 조회",
            description = "최신순. 각 행은 그 변경 시점 플랜의 이름/가격/한도 스냅샷이다 — 카탈로그가 나중에 바뀌어도 그대로 보전된다."
    )
    @GetMapping("/{ceremonyId}/plan/history")
    public ApiResponse<List<CeremonyDto.Response.PlanHistorySummary>> findPlanHistory(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId
    ) {
        List<CeremonyDto.Response.PlanHistorySummary> response =
                ceremonyService.findPlanHistory(organizationId, ceremonyId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "장바구니 조회",
            description = "담은 순서대로, 항목마다 지금 카탈로그 기준 표시 정보(이름/가격 등)를 같이 돌려준다."
    )
    @GetMapping("/{ceremonyId}/unit-product-cart")
    public ApiResponse<List<CeremonyDto.Response.CartLineSummary>> retrieveCart(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId
    ) {
        List<CeremonyDto.Response.CartLineSummary> response =
                ceremonyService.retrieveCart(organizationId, ceremonyId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "장바구니에 담기(추가 구매하기)",
            description = "아직 구매를 만들지 않는다 — 서버에 저장된 장바구니에 담길 뿐이다. 같은 항목을 다시 담으면 "
                    + "새 줄이 아니라 기존 줄의 수량에 더해진다."
    )
    @PostMapping("/{ceremonyId}/unit-product-cart/items")
    public ApiResponse<List<CeremonyDto.Response.CartLineSummary>> addToCart(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @Valid @RequestBody CeremonyDto.Request.AddToCart request
    ) {
        List<CeremonyDto.Response.CartLineSummary> response =
                ceremonyService.addToCart(organizationId, ceremonyId, currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "장바구니 줄 수량 수정", description = "장바구니 검토 화면에서 수량을 직접 고쳐 쓸 때 쓴다.")
    @PutMapping("/{ceremonyId}/unit-product-cart/items/{unitProductId}")
    public ApiResponse<List<CeremonyDto.Response.CartLineSummary>> updateCartLine(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long unitProductId,
            @Valid @RequestBody CeremonyDto.Request.UpdateCartLine request
    ) {
        List<CeremonyDto.Response.CartLineSummary> response =
                ceremonyService.updateCartLine(organizationId, ceremonyId, currentUser.userId(), unitProductId, request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "장바구니 줄 삭제")
    @DeleteMapping("/{ceremonyId}/unit-product-cart/items/{unitProductId}")
    public ApiResponse<List<CeremonyDto.Response.CartLineSummary>> removeCartLine(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long unitProductId
    ) {
        List<CeremonyDto.Response.CartLineSummary> response =
                ceremonyService.removeCartLine(organizationId, ceremonyId, currentUser.userId(), unitProductId);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "구매하기(구매 확정)",
            description = "장바구니에 담긴 내용을 그대로 읽어 구매를 만든다 — 더 이상 요청 바디로 라인을 받지 않는다. "
                    + "담긴 줄이 전부 시스템 사용료(서명자·템플릿·테스트/본행사 추가, 이벤트 효과)면 관리자 승인 없이 "
                    + "그 즉시 반영되고(자가-체크아웃), 성공하면 장바구니는 비워진다."
    )
    @PostMapping("/{ceremonyId}/unit-product-purchases")
    public ApiResponse<CeremonyDto.Response.UnitProductPurchaseSummary> purchaseUnitProducts(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId
    ) {
        CeremonyDto.Response.UnitProductPurchaseSummary response =
                ceremonyService.purchaseUnitProducts(organizationId, ceremonyId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "단위 상품 추가구매 이력 조회",
            description = "요청자 본인이 볼 수 있는 이력이다. 대기중(PENDING)/승인됨(APPROVED)/반려됨(REJECTED) 전부 포함한다."
    )
    @GetMapping("/{ceremonyId}/unit-product-purchases")
    public ApiResponse<List<CeremonyDto.Response.UnitProductPurchaseSummary>> findUnitProductPurchases(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId
    ) {
        List<CeremonyDto.Response.UnitProductPurchaseSummary> response =
                ceremonyService.findUnitProductPurchases(organizationId, ceremonyId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "적용 가능한 단위 상품 조회",
            description = "이 행사가 실제로 하위 행사에 적용할 수 있는 단위 상품(플랜 포함분 + 승인된 추가구매, "
                    + "type=EVENT_EFFECT_BUNDLE)만 필터링해 돌려준다. 하위 행사 등록/수정/상세 화면이 이 목록으로 체크박스를 채운다."
    )
    @GetMapping("/{ceremonyId}/applicable-unit-products")
    public ApiResponse<List<UnitProductDto.Response.UnitProductSummary>> findApplicableUnitProducts(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId
    ) {
        List<UnitProductDto.Response.UnitProductSummary> response =
                ceremonyService.retrieveApplicableUnitProducts(organizationId, ceremonyId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "구매 가능한 단위 상품 조회",
            description = "이 행사의 플랜에서 구매 후보로 열어둔(안 A 큐레이션) 단위 상품만 필터링해 돌려준다. "
                    + "추가구매 폼이 이 목록으로 드롭다운을 채운다. 플랜이 없는 행사는 활성 상품 전체를 제한 없이 돌려준다."
    )
    @GetMapping("/{ceremonyId}/purchasable-unit-products")
    public ApiResponse<List<UnitProductDto.Response.UnitProductSummary>> findPurchasableUnitProducts(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId
    ) {
        List<UnitProductDto.Response.UnitProductSummary> response =
                ceremonyService.retrievePurchasableUnitProducts(organizationId, ceremonyId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "용량 한도 조회",
            description = "서명자/문서양식/테스트·본행사 하위 행사 각각 등록할 수 있는 최대 개수(플랜 기본값 + 승인된 추가구매). "
                    + "서명자·문서양식·하위 행사 등록 화면이 \"등록할 수 있는 개수\"를 보여주는 데 쓴다."
    )
    @GetMapping("/{ceremonyId}/capacity-status")
    public ApiResponse<CeremonyDto.Response.CapacityStatus> retrieveCapacityStatus(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId
    ) {
        CeremonyDto.Response.CapacityStatus response =
                ceremonyService.retrieveCapacityStatus(organizationId, ceremonyId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "예상 청구 금액 조회",
            description = "품목 할인 → subtotal → 행사 건별 할인의 2단 순차 차감으로 계산한다(승인된 구매 건만 반영). "
                    + "실제 결제/청구서 발행 기능은 아직 없다 — 지금 계산하면 얼마인지 보여주는 견적용이다."
    )
    @GetMapping("/{ceremonyId}/estimated-total")
    public ApiResponse<CeremonyDto.Response.EstimatedTotal> retrieveEstimatedTotal(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId
    ) {
        CeremonyDto.Response.EstimatedTotal response =
                ceremonyService.calculateEstimatedTotal(organizationId, ceremonyId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "이 행사에 적용되는 마진 조회",
            description = "행사별 override가 있으면 그 값(source=CEREMONY_OVERRIDE), 없으면 조직 기본값"
                    + "(source=ORGANIZATION_DEFAULT), 둘 다 없으면 source=NONE(고객 견적서 생성 불가). 호출자가 OWNER여야 한다."
    )
    @GetMapping("/{ceremonyId}/customer-margin")
    public ApiResponse<CustomerQuoteDto.Response.EffectiveMargin> retrieveEffectiveMargin(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId
    ) {
        CustomerQuoteDto.Response.EffectiveMargin response =
                customerQuoteService.retrieveEffectiveMargin(organizationId, ceremonyId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "이 행사만의 마진 override 설정", description = "조직 기본값을 이 행사에서만 덮어쓴다. 호출자가 OWNER여야 한다.")
    @PutMapping("/{ceremonyId}/customer-margin")
    public ApiResponse<CustomerQuoteDto.Response.EffectiveMargin> updateCeremonyMarginOverride(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @Valid @RequestBody CustomerQuoteDto.Request.UpdateMargin request
    ) {
        CustomerQuoteDto.Response.EffectiveMargin response =
                customerQuoteService.updateCeremonyMarginOverride(organizationId, ceremonyId, currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "이 행사만의 마진 override 해제", description = "해제하면 다시 조직 기본값을 따른다. 호출자가 OWNER여야 한다.")
    @DeleteMapping("/{ceremonyId}/customer-margin")
    public ApiResponse<Void> clearCeremonyMarginOverride(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId
    ) {
        customerQuoteService.clearCeremonyMarginOverride(organizationId, ceremonyId, currentUser.userId());
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "고객 견적서 생성",
            description = "지금 유효한 마진(행사별 override 또는 조직 기본값)으로 시스템 사용료를 계산하고, 장비/인력은 요청에 담긴 "
                    + "고객 단가를 그대로 스냅샷한다(세전 금액). 마진이 설정돼 있지 않으면 실패한다. 호출자가 OWNER여야 한다."
    )
    @PostMapping("/{ceremonyId}/customer-quotes")
    public ApiResponse<CustomerQuoteDto.Response.QuoteDetail> generateCustomerQuote(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @Valid @RequestBody CustomerQuoteDto.Request.GenerateQuote request
    ) {
        CustomerQuoteDto.Response.QuoteDetail response =
                customerQuoteService.generateCustomerQuote(organizationId, ceremonyId, currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "고객 견적서 목록 조회", description = "버전 역순(최신이 먼저). 호출자가 OWNER여야 한다.")
    @GetMapping("/{ceremonyId}/customer-quotes")
    public ApiResponse<List<CustomerQuoteDto.Response.QuoteSummary>> findCustomerQuotes(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId
    ) {
        List<CustomerQuoteDto.Response.QuoteSummary> response =
                customerQuoteService.findCustomerQuotes(organizationId, ceremonyId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "고객 견적서 상세 조회", description = "줄 단위 내역 포함. 호출자가 OWNER여야 한다.")
    @GetMapping("/{ceremonyId}/customer-quotes/{quoteId}")
    public ApiResponse<CustomerQuoteDto.Response.QuoteDetail> findCustomerQuoteDetail(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long quoteId
    ) {
        CustomerQuoteDto.Response.QuoteDetail response =
                customerQuoteService.findCustomerQuoteDetail(organizationId, ceremonyId, quoteId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
