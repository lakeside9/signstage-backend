package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.i18n.InternationalizationDefaults;
import com.eformworks.signstage.backend.feature.billing.dto.UnitProductDto;
import com.eformworks.signstage.backend.feature.billing.entity.SalesChannel;
import com.eformworks.signstage.backend.feature.billing.service.BillingCatalogQueryService;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductPricePeriod;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductCartLine;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductPurchase;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductPurchaseLine;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyUnitProductCartLineRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyUnitProductPurchaseLineRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyUnitProductPurchaseRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 행사 단위 상품 추가구매 — 장바구니 담기·수정·삭제, 구매 요청(자가-체크아웃), 구매 내역
 * 조회. signstage-docs business/unit-product-purchase-self-checkout-review.md 참고.
 * 2026-11-11 {@code CeremonyService}에서 분리했다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CeremonyUnitProductPurchaseService {

    private final CeremonyUnitProductPurchaseRepository ceremonyUnitProductPurchaseRepository;
    private final CeremonyUnitProductPurchaseLineRepository ceremonyUnitProductPurchaseLineRepository;
    private final CeremonyUnitProductCartLineRepository ceremonyUnitProductCartLineRepository;
    private final BillingCatalogQueryService billingCatalogQueryService;
    private final CeremonyAccessService ceremonyAccessService;
    private final CeremonyCatalogService ceremonyCatalogService;

    /** 장바구니 조회 — 담은 순서대로, 항목마다 지금 카탈로그 기준 표시 정보(이름/가격 등)를 같이 돌려준다. */
    public List<CeremonyDto.Response.CartLineSummary> retrieveCart(Long organizationId, Long ceremonyId, Long currentUserId) {
        Ceremony ceremony = ceremonyAccessService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyAccessService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyAccessService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        return ceremonyUnitProductCartLineRepository.findAllByCeremonyIdOrderByIdAsc(ceremonyId).stream()
                .map(line -> new CeremonyDto.Response.CartLineSummary(
                        line.getUnitProduct().getId(), line.getQuantity(), toUnitProductSummaryForPurchase(line.getUnitProduct())
                ))
                .toList();
    }

    /**
     * "추가 구매하기" — 장바구니에 담는다("구매 확정"이 아니다). 같은 항목을 다시 담으면
     * 새 줄을 만들지 않고 수량을 더한다(signstage-docs
     * business/unit-product-purchase-self-checkout-review.md 6장 결정, 2026-09-11).
     *
     * <p>플랜이 확정(DRAFT → IN_PROGRESS)된 행사에서만 담을 수 있다(2026-09-11 사용자 요청) —
     * 플랜을 고르기만 하고 아직 확정 전인 상태에서 미리 장바구니를 채워두는 걸 막는다.
     * {@link CeremonyAccessService#checkCeremonyPlanConfirmed}는 배포 전 레거시(plan 없음, 영원히 IN_PROGRESS)
     * 행사는 그대로 통과시킨다 — DRAFT인지만 본다.
     */
    @Transactional
    public List<CeremonyDto.Response.CartLineSummary> addToCart(
            Long organizationId, Long ceremonyId, Long currentUserId, CeremonyDto.Request.AddToCart request
    ) {
        Ceremony ceremony = ceremonyAccessService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyAccessService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyAccessService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyAccessService.checkPartnerDirectCeremony(ceremony);
        ceremonyAccessService.checkCeremonyEditable(ceremony);
        ceremonyAccessService.checkCeremonyPlanConfirmed(ceremony);

        UnitProduct unitProduct = billingCatalogQueryService.findUnitProductOrThrow(request.getUnitProductId(), SalesChannel.PARTNER_DIRECT);
        checkPurchasable(ceremony, unitProduct);

        Optional<CeremonyUnitProductCartLine> existingLine =
                ceremonyUnitProductCartLineRepository.findByCeremonyIdAndUnitProductId(ceremonyId, unitProduct.getId());
        int resultingQuantity = existingLine.map(CeremonyUnitProductCartLine::getQuantity).orElse(0) + request.getQuantity();
        checkPurchaseQuantity(ceremony, unitProduct, resultingQuantity);

        existingLine.ifPresentOrElse(
                existing -> existing.addQuantity(request.getQuantity()),
                () -> ceremonyUnitProductCartLineRepository.save(
                        CeremonyUnitProductCartLine.builder()
                                .ceremony(ceremony)
                                .unitProduct(unitProduct)
                                .quantity(request.getQuantity())
                                .build()
                )
        );

        return retrieveCart(organizationId, ceremonyId, currentUserId);
    }

    /** 장바구니 검토 화면에서 수량을 직접 고쳐 쓸 때. */
    @Transactional
    public List<CeremonyDto.Response.CartLineSummary> updateCartLine(
            Long organizationId, Long ceremonyId, Long currentUserId, Long unitProductId, CeremonyDto.Request.UpdateCartLine request
    ) {
        Ceremony ceremony = ceremonyAccessService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyAccessService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyAccessService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyAccessService.checkPartnerDirectCeremony(ceremony);
        ceremonyAccessService.checkCeremonyEditable(ceremony);

        CeremonyUnitProductCartLine line = ceremonyUnitProductCartLineRepository
                .findByCeremonyIdAndUnitProductId(ceremonyId, unitProductId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CART_LINE_NOT_FOUND));
        checkPurchaseQuantity(ceremony, line.getUnitProduct(), request.getQuantity());
        line.changeQuantity(request.getQuantity());

        return retrieveCart(organizationId, ceremonyId, currentUserId);
    }

    /** 장바구니에서 항목을 뺀다. */
    @Transactional
    public List<CeremonyDto.Response.CartLineSummary> removeCartLine(
            Long organizationId, Long ceremonyId, Long currentUserId, Long unitProductId
    ) {
        Ceremony ceremony = ceremonyAccessService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyAccessService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyAccessService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyAccessService.checkPartnerDirectCeremony(ceremony);
        ceremonyAccessService.checkCeremonyEditable(ceremony);

        CeremonyUnitProductCartLine line = ceremonyUnitProductCartLineRepository
                .findByCeremonyIdAndUnitProductId(ceremonyId, unitProductId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CART_LINE_NOT_FOUND));
        ceremonyUnitProductCartLineRepository.delete(line);

        return retrieveCart(organizationId, ceremonyId, currentUserId);
    }

    /**
     * "구매하기" — 장바구니에 담긴 내용을 그대로 읽어 구매를 만든다(더 이상 요청 바디로 라인을
     * 받지 않는다). 옛 {@code purchaseCapacity}/{@code purchaseOptionalFeature} 통합
     * (signstage-docs business/billing-catalog-unit-product-model-redesign-review.md 결정,
     * 2026-09-10, 3.4절)에 이어, 장바구니형 2단계 자가-체크아웃(같은 문서
     * business/unit-product-purchase-self-checkout-review.md 4장 결정, 2026-09-11)으로
     * 재정의됐다 — 담긴 줄이 전부 플랫폼 이용료 대상({@link UnitProduct#isPlatformUsageFee},
     * 2026-09-12부터 카테고리 파생이 아니라 저장된 값 — signstage-docs
     * business/onsite-support-negotiation-and-billing-classification-review.md 3.1절)이면
     * 관리자 승인 없이 그 즉시 APPROVED로
     * 반영되고({@link CeremonyUnitProductPurchase#autoApprove}), 아니면(레거시 플랜 없는 행사가
     * 장비·인력을 담은 경우) 예전처럼 PENDING으로 남아 관리자 승인을 기다린다. 성공하면 장바구니는
     * 비워진다. 토글형({@code EVENT_EFFECT_BUNDLE}) 단위 상품은 수량이 0/1 관례를 따라야 하고
     * (3.6절), 이미 대기중/승인된 요청이 있으면 재구매할 수 없다 — 그 외 종류(용량 계열)는 여러
     * 번 구매해 누적할 수 있다(옛 {@code CeremonyCapacityPurchase}와 같은 동작).
     */
    @Transactional
    public CeremonyDto.Response.UnitProductPurchaseSummary purchaseUnitProducts(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyAccessService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyAccessService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyAccessService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyAccessService.checkPartnerDirectCeremony(ceremony);
        ceremonyAccessService.checkCeremonyEditable(ceremony);
        // 플랜이 확정(DRAFT → IN_PROGRESS)된 행사에서만 구매할 수 있다(2026-09-11 사용자
        // 요청) — addToCart에서 이미 막지만, 이 요청 이전에 이미 담겨 있던 장바구니로
        // 구매를 시도하는 경우까지 방어한다. checkCeremonyPlanConfirmed는 배포 전 레거시
        // (plan 없음, 영원히 IN_PROGRESS) 행사는 그대로 통과시킨다 — DRAFT인지만 본다
        // (signstage-docs business/ceremony-registration-flow-and-billing-tab-separation-review.md 4장).
        ceremonyAccessService.checkCeremonyPlanConfirmed(ceremony);

        List<CeremonyUnitProductCartLine> cartLines = ceremonyUnitProductCartLineRepository
                .findAllByCeremonyIdOrderByIdAsc(ceremonyId);
        if (cartLines.isEmpty()) {
            throw new ApplicationException(CeremonyErrorCode.CART_EMPTY);
        }

        LocalDate asOfDate = LocalDate.now(ZoneId.of(ceremony.getTimeZoneId()));
        CeremonyUnitProductPurchase purchase = CeremonyUnitProductPurchase.builder().ceremony(ceremony).build();
        ceremonyUnitProductPurchaseRepository.save(purchase);

        List<CeremonyUnitProductPurchaseLine> lines = new ArrayList<>();
        for (CeremonyUnitProductCartLine cartLine : cartLines) {
            UnitProduct unitProduct = cartLine.getUnitProduct();
            // 담을 때 이미 확인했지만, 담긴 뒤 카탈로그·플랜 구성이 바뀌었을 수 있어 구매
            // 시점에 다시 한번 확인한다(가격·통화도 이 시점 값을 스냅샷으로 쓴다).
            checkPurchasable(ceremony, unitProduct);
            UnitProductPricePeriod period = ceremonyCatalogService.resolveSellableUnitProductPeriod(unitProduct, asOfDate);
            ceremonyCatalogService.checkCurrencyMatches(ceremony.getCurrencyCode(), period.getPriceInfo().getCurrencyCode());

            // 담을 때/수량 수정 때 이미 검사하지만(addToCart/updateCartLine), 그 사이 다른 요청으로
            // 이미 구매됐을 수 있어 구매 확정 시점에 다시 한번 확인한다(checkPurchasable 재검증과
            // 같은 이유).
            checkPurchaseQuantity(ceremony, unitProduct, cartLine.getQuantity());

            lines.add(ceremonyUnitProductPurchaseLineRepository.save(
                    CeremonyUnitProductPurchaseLine.builder()
                            .purchase(purchase)
                            .unitProduct(unitProduct)
                            .quantity(cartLine.getQuantity())
                            .currencyCode(period.getPriceInfo().getCurrencyCode())
                            .purchasedName(unitProduct.getName())
                            .purchasedSalePrice(period.getPriceInfo().getSalePrice())
                            .purchasedTaxCode(period.getPriceInfo().getTaxCode())
                            .build()
            ));
        }

        boolean allSystemUsageFee = lines.stream().allMatch(line -> line.getUnitProduct().isPlatformUsageFee());
        if (allSystemUsageFee) {
            purchase.autoApprove();
        }
        ceremonyUnitProductCartLineRepository.deleteAllByCeremonyId(ceremonyId);

        return toUnitProductPurchaseSummary(purchase, lines);
    }

    /**
     * 안 A(구매 가능 상품 큐레이션) — 이 Ceremony의 플랜에서 구매 후보로 열어두지 않은 상품은
     * 거부한다. 플랜이 없는 행사(4.8절 예외)는 제한 없이 전부 허용한다. 장바구니 담기·구매
     * 양쪽에서 같은 기준으로 쓴다.
     */
    private void checkPurchasable(Ceremony ceremony, UnitProduct unitProduct) {
        if (ceremony.getBillingPlan() == null) {
            return;
        }
        Set<Long> purchasableIds = new HashSet<>(ceremonyCatalogService.retrievePurchasableUnitProductIds(ceremony));
        if (!purchasableIds.contains(unitProduct.getId())) {
            throw new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_NOT_AVAILABLE_FOR_PLAN);
        }
    }

    /**
     * 담기/수량 수정/구매 확정 세 지점이 공유하는 수량 검사 — 토글형 상한, 판매 단위 배수,
     * 최대 구매 수량 세 가지를 본다.
     *
     * <p><b>1. 토글형</b>({@link UnitProductType#isToggle()}, 지금은 EVENT_EFFECT_BUNDLE만)은
     * 수량으로 여러 개를 담는 게 아니라 행사당 1회만 "가졌다/안 가졌다"로 다룬다(2026-09-11
     * 사용자 지적 — "이벤트 효과 묶음의 경우 행사에 한번 구매하면 됩니다. 수량으로 추가할
     * 내용이 아닙니다"). 관리자가 조정할 수 없는 타입 자체의 규칙이라 {@link
     * UnitProduct#getMaxPurchaseQuantity()}는 아예 보지 않는다({@code UnitProduct} 생성자/
     * {@code updateInfo}가 토글형이면 항상 null로 정규화해두므로 사실 봐도 null이다).
     *
     * <p><b>2. 판매 단위 수량</b>({@link UnitProduct#getSaleUnitQuantity()}, 기본값 1=제약
     * 없음)의 배수가 아닌 {@code resultingQuantity}는 거부한다(2026-09-14 사용자 요청 —
     * "템플릿 문서 10개를 1묶음 10,000원에 팔고 싶다"). 판매가는 여전히 개당 단가라 이 검사
     * 외에는 금액 계산에 아무 영향이 없다 — quantity가 항상 배수로 들어오도록 강제만 한다.
     *
     * <p><b>3. 그 외 수량 상한</b>은 카탈로그에 설정된 {@code maxPurchaseQuantity}(nullable=무제한) —
     * "이 행사에서 지금까지 담은/구매(PENDING+APPROVED) 수량 합"이 이 값을 넘으면 거부한다
     * (2026-09-12 사용자 요청 — "단위 상품을 구매할 수 있는 최대 수량을 관리하려고 합니다").
     * 플랜에 기본 포함된 수량은 이 합계에 넣지 않는다 — 추가구매로 "더 살 수 있는 양"만 상한을
     * 적용하는 게 자연스럽고, 플랜을 바꿀 때마다 이미 산 게 상한을 넘기는 애매한 상황을
     * 피한다.
     *
     * <p>{@code purchaseUnitProducts}(구매 확정)만 검사하던 것을 {@code addToCart}/
     * {@code updateCartLine}(장바구니 담기/수량 수정)까지 넓혔다(2026-09-11) — 전에는 장바구니
     * 단계에선 상한을 넘겨 담아도 막지 않다가 마지막 구매 확정 시점에야 거부돼서, 사용자가
     * 장바구니를 다 채운 뒤에야 실패를 알게 됐다.
     *
     * @param resultingQuantity 이 검사 뒤에 실제로 반영될 "장바구니 줄" 수량(추가/수정 전이
     *                          아니라 후 값) — 과거 구매분과는 별개로 더한다
     */
    private void checkPurchaseQuantity(Ceremony ceremony, UnitProduct unitProduct, int resultingQuantity) {
        if (unitProduct.getType().isToggle()) {
            if (resultingQuantity > 1) {
                throw new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_TOGGLE_QUANTITY_INVALID);
            }
            boolean alreadyRequested = ceremonyUnitProductPurchaseLineRepository
                    .existsByPurchase_CeremonyIdAndUnitProduct_IdAndPurchase_StatusIn(
                            ceremony.getId(), unitProduct.getId(), List.of(PurchaseStatus.PENDING, PurchaseStatus.APPROVED)
                    );
            if (alreadyRequested) {
                throw new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_ALREADY_PURCHASED);
            }
            return;
        }

        if (resultingQuantity % unitProduct.getSaleUnitQuantity() != 0) {
            throw new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_SALE_UNIT_QUANTITY_INVALID);
        }

        Integer maxQuantity = unitProduct.getMaxPurchaseQuantity();
        if (maxQuantity == null) {
            return;
        }
        int alreadyPurchased = ceremonyUnitProductPurchaseLineRepository
                .findAllByPurchase_CeremonyIdAndUnitProduct_IdAndPurchase_StatusIn(
                        ceremony.getId(), unitProduct.getId(), List.of(PurchaseStatus.PENDING, PurchaseStatus.APPROVED)
                )
                .stream()
                .mapToInt(CeremonyUnitProductPurchaseLine::getQuantity)
                .sum();
        if (alreadyPurchased + resultingQuantity > maxQuantity) {
            throw new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_MAX_QUANTITY_EXCEEDED);
        }
    }

    /** 요청자 본인 이력 조회 — 대기중/승인됨/반려됨 전부 보여준다. */
    public List<CeremonyDto.Response.UnitProductPurchaseSummary> findUnitProductPurchases(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyAccessService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyAccessService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyAccessService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        return ceremonyUnitProductPurchaseRepository.findAllByCeremonyIdOrderByCreatedAtDesc(ceremonyId).stream()
                .map(purchase -> toUnitProductPurchaseSummary(
                        purchase,
                        ceremonyUnitProductPurchaseLineRepository.findAllByPurchaseIdOrderByIdAsc(purchase.getId())
                ))
                .toList();
    }

    /**
     * 이 Ceremony가 실제로 구매 요청할 수 있는 단위 상품 카탈로그만 필터링해 돌려준다(안 A) —
     * {@link CeremonyCatalogService#retrieveApplicableUnitProducts}와 같은 목적으로, 구매 화면의 선택 목록이 이
     * 목록으로 채워야 플랜에서 열어두지 않은 상품을 골라 제출한 뒤에야 거부당하는 UX를
     * 피할 수 있다. 플랜이 없는 행사(4.8절 예외)는 시스템 사용료 활성 상품 전체를 제한 없이
     * 돌려준다 — 장비·인력은 플랜 유무와 무관하게 항상 제외한다(위 {@link
     * #retrievePurchasableUnitProductIds} 참고).
     */
    public List<UnitProductDto.Response.UnitProductSummary> retrievePurchasableUnitProducts(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyAccessService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyAccessService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyAccessService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        if (ceremony.getBillingPlan() == null) {
            return billingCatalogQueryService.findAllUnitProducts().stream()
                    .filter(unitProduct -> !unitProduct.isPlatformOrder() && unitProduct.isPlatformUsageFee())
                    .map(this::toUnitProductSummaryForPurchase)
                    .toList();
        }

        List<Long> availableIds = ceremonyCatalogService.retrievePurchasableUnitProductIds(ceremony);
        if (availableIds.isEmpty()) {
            return List.of();
        }
        // 추가구매 후보는 직접 수주 경로 상품뿐이다(플랫폼 수주 행사의 범위 변경은 수주 쪽에서 한다).
        return billingCatalogQueryService.findUnitProducts(availableIds).stream()
                .filter(unitProduct -> !unitProduct.isPlatformOrder())
                .map(this::toUnitProductSummaryForPurchase)
                .toList();
    }

    private UnitProductDto.Response.UnitProductSummary toUnitProductSummaryForPurchase(UnitProduct unitProduct) {
        Optional<UnitProductPricePeriod> effective =
                billingCatalogQueryService.findEffectiveUnitProductPricePeriod(unitProduct.getId(), InternationalizationDefaults.today());
        return new UnitProductDto.Response.UnitProductSummary(
                unitProduct.getId(),
                unitProduct.getType().name(),
                unitProduct.getName(),
                unitProduct.getDescription(),
                unitProduct.getCategory().name(),
                unitProduct.getExclusivityGroup(),
                unitProduct.getMaxPurchaseQuantity(),
                unitProduct.getSaleUnitQuantity(),
                unitProduct.isPlatformUsageFee(),
                effective.map(p -> p.getPriceInfo().getCurrencyCode()).orElse(null),
                effective.map(p -> p.getPriceInfo().getSupplyPrice()).orElse(null),
                effective.map(p -> p.getPriceInfo().getSalePrice()).orElse(null),
                effective.map(p -> p.getPriceInfo().getTaxCode()).orElse(null),
                effective.map(UnitProductPricePeriod::isActive).orElse(null),
                ceremonyUnitProductPurchaseLineRepository.countByUnitProduct_IdAndPurchase_Status(unitProduct.getId(), PurchaseStatus.APPROVED),
                List.of(),
                unitProduct.getCreatedAt(),
                effective.map(UnitProductPricePeriod::getEffectiveFrom).orElse(null),
                effective.map(UnitProductPricePeriod::getEffectiveTo).orElse(null),
                effective.map(p -> p.isActive() ? "ON_SALE" : "INACTIVE").orElse("NO_ACTIVE_PERIOD"),
                unitProduct.getDisplayOrder(),
                // canDelete는 카탈로그 관리 화면 전용 필드다(UnitProductService#toSummary만 실제
                // 6곳 조회로 계산한다) — 이 조직 사용자 화면(구매 후보 목록)은 어차피 이 값을
                // 쓰지 않으므로 보수적으로 false를 채운다.
                false,
                unitProduct.getSalesChannel().name()
        );
    }

    private CeremonyDto.Response.UnitProductPurchaseSummary toUnitProductPurchaseSummary(
            CeremonyUnitProductPurchase purchase,
            List<CeremonyUnitProductPurchaseLine> lines
    ) {
        return new CeremonyDto.Response.UnitProductPurchaseSummary(
                purchase.getId(),
                purchase.getCeremony().getId(),
                lines.stream().map(CeremonyDto.Response.UnitProductPurchaseLineSummary::from).toList(),
                purchase.getStatus().name(),
                purchase.getRejectionReason(),
                purchase.getReviewedAt(),
                purchase.getCancellationReason(),
                purchase.getCancelledAt(),
                purchase.getCreatedAt()
        );
    }
}
