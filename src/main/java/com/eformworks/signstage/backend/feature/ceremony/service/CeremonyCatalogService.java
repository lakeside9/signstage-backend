package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.billing.dto.UnitProductDto;
import com.eformworks.signstage.backend.feature.billing.service.BillingCatalogQueryService;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlanDiscountPeriod;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlanUnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductPricePeriod;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEvent;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistoryUnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductPurchaseLine;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEffectDefinitionOptionRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryUnitProductRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyUnitProductPurchaseLineRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 행사에 적용할 과금 카탈로그 판정 — 지금 고르거나 살 수 있는 플랜·단위 상품인지(판매 기간·
 * 사용 여부·통화·배타 그룹), 플랜 확정 스냅샷을 기준으로 한 유효 한도와 적용·추가구매 가능
 * 단위 상품. 2026-11-11 {@code CeremonyService}에서 분리했다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CeremonyCatalogService {

    private final CeremonyUnitProductPurchaseLineRepository ceremonyUnitProductPurchaseLineRepository;
    private final CeremonyEffectDefinitionOptionRepository ceremonyEffectDefinitionOptionRepository;
    private final CeremonyPlanHistoryRepository ceremonyPlanHistoryRepository;
    private final CeremonyPlanHistoryUnitProductRepository ceremonyPlanHistoryUnitProductRepository;
    private final BillingCatalogQueryService billingCatalogQueryService;
    private final CeremonyAccessService ceremonyAccessService;

    /**
     * 이 Ceremony가 하위 행사에 실제로 적용할 수 있는 단위 상품(플랜 포함분 + 승인된 추가구매)
     * 카탈로그만 필터링해 돌려준다 — {@link #retrieveApplicableUnitProductIds}와 같은 계산을
     * 쓴다. {@code type=EVENT_EFFECT_BUNDLE}로 범위를 좁힌다 — CeremonyEvent에 켜고 끄는
     * 개념이 있는 종류는 지금 이것뿐이다(signstage-docs
     * business/billing-catalog-unit-product-model-redesign-review.md 3.6절). 하위 행사
     * 등록/수정/상세 세 화면이 전부 이 목록으로 체크박스를 채운다.
     *
     * <p>개별 추가구매(승인됨)한 상품은 이름/가격을 구매 시점 스냅샷({@code purchasedName}/
     * {@code purchasedSalePrice} 등)으로 보여준다 — 카탈로그 관리자가 나중에 이름을 바꿔도
     * 이미 구매한 상품의 표시는 안 바뀐다. 플랜에 기본 포함된 상품(추가구매 기록이 없음)은
     * 스냅샷 대상이 아니라 카탈로그 값을 그대로 보여준다.
     */
    public List<UnitProductDto.Response.UnitProductSummary> retrieveApplicableUnitProducts(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyAccessService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyAccessService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyAccessService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        List<Long> availableIds = retrieveApplicableUnitProductIds(ceremony);
        if (availableIds.isEmpty()) {
            return List.of();
        }

        Map<Long, CeremonyUnitProductPurchaseLine> approvedLineByProductId = ceremonyUnitProductPurchaseLineRepository
                .findAllByPurchase_CeremonyIdOrderByCreatedAtDesc(ceremony.getId()).stream()
                .filter(line -> line.getPurchase().getStatus() == PurchaseStatus.APPROVED)
                .collect(Collectors.toMap(line -> line.getUnitProduct().getId(), line -> line, (a, b) -> a));

        Map<Long, List<Long>> effectDefinitionIdsByProductId = ceremonyEffectDefinitionOptionRepository
                .findAllByUnitProductIdIn(availableIds).stream()
                .collect(Collectors.groupingBy(
                        mapping -> mapping.getUnitProduct().getId(),
                        Collectors.mapping(mapping -> mapping.getEffectDefinition().getId(), Collectors.toList())
                ));

        LocalDate asOfDate = LocalDate.now(ZoneId.of(ceremony.getTimeZoneId()));
        return billingCatalogQueryService.findUnitProducts(availableIds).stream()
                .map(unitProduct -> {
                    CeremonyUnitProductPurchaseLine line = approvedLineByProductId.get(unitProduct.getId());
                    // 개별 추가구매(승인됨)한 상품은 구매 시점 스냅샷을 쓰지만, 플랜에 기본 포함된
                    // 상품(추가구매 기록 없음)은 "오늘" 기준 유효한 판매가격 기간을 그대로 보여준다.
                    Optional<UnitProductPricePeriod> effective =
                            billingCatalogQueryService.findEffectiveUnitProductPricePeriod(unitProduct.getId(), asOfDate);
                    return new UnitProductDto.Response.UnitProductSummary(
                            unitProduct.getId(),
                            unitProduct.getType().name(),
                            line != null ? line.getPurchasedName() : unitProduct.getName(),
                            unitProduct.getDescription(),
                            unitProduct.getCategory().name(),
                            unitProduct.getExclusivityGroup(),
                            unitProduct.getMaxPurchaseQuantity(),
                            unitProduct.getSaleUnitQuantity(),
                            unitProduct.isPlatformUsageFee(),
                            line != null ? line.getCurrencyCode() : effective.map(p -> p.getPriceInfo().getCurrencyCode()).orElse(null),
                            effective.map(p -> p.getPriceInfo().getSupplyPrice()).orElse(null),
                            line != null ? line.getPurchasedSalePrice() : effective.map(p -> p.getPriceInfo().getSalePrice()).orElse(null),
                            line != null ? line.getPurchasedTaxCode() : effective.map(p -> p.getPriceInfo().getTaxCode()).orElse(null),
                            effective.map(UnitProductPricePeriod::isActive).orElse(null),
                            ceremonyUnitProductPurchaseLineRepository.countByUnitProduct_IdAndPurchase_Status(
                                    unitProduct.getId(), PurchaseStatus.APPROVED
                            ),
                            effectDefinitionIdsByProductId.getOrDefault(unitProduct.getId(), List.of()),
                            unitProduct.getCreatedAt(),
                            effective.map(UnitProductPricePeriod::getEffectiveFrom).orElse(null),
                            effective.map(UnitProductPricePeriod::getEffectiveTo).orElse(null),
                            effective.map(p -> p.isActive() ? "ON_SALE" : "INACTIVE").orElse("NO_ACTIVE_PERIOD"),
                            unitProduct.getDisplayOrder(),
                            // 이 목록에 나오는 상품은 이미 이 행사의 플랜 구성이나 승인된 추가구매로
                            // 참조되고 있다는 뜻이라 정의상 항상 "사용됨"이다 — canDelete는 카탈로그
                            // 관리 화면 전용 필드라 이 조직 사용자 화면에서는 어차피 안 쓰지만, 값
                            // 자체는 정확하게 false로 채운다(UnitProductService#toSummary만 실제
                            // 6곳 조회로 계산한다).
                            false
                    );
                })
                .toList();
    }

    /**
     * 배타 그룹 검사 — 같은 {@link UnitProduct#getExclusivityGroup()}을 가진 단위 상품이
     * 요청에 2개 이상 섞여 있으면 거부한다({@code UNIT_PRODUCT_GROUP_CONFLICT}). 그룹 없음
     * (null)은 검사 대상에서 뺀다(signstage-docs business/ceremony-billing-options-review.md,
     * 2026-08-21). 원래 {@link CeremonyEventService}의 "이벤트에 옵션 적용" 경로 전용이었는데,
     * {@link CustomerQuoteService}의 고객 견적 장비/인력 품목 추가에는 이 검사가 아예 없어
     * 같은 배타 그룹 상품(예: 현장지원 근/중/원거리)을 한 견적에 나란히 담을 수 있던 문제를
     * 고치며 공유 헬퍼로 옮겼다(2026-09-11).
     */
    void checkExclusivityGroups(List<UnitProduct> unitProducts) {
        Set<String> seenGroups = new HashSet<>();
        for (UnitProduct unitProduct : unitProducts) {
            String group = unitProduct.getExclusivityGroup();
            if (group == null) continue;
            if (!seenGroups.add(group)) {
                throw new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_GROUP_CONFLICT);
            }
        }
    }

    /**
     * asOfDate 기준 유효한 할인 기간을 찾아 사용여부까지 확인한다 — 기간이 없거나(카탈로그 등록
     * 실수로 공백이 생긴 경우) 있어도 사용 중지(active=false)면 신규 선택/변경 대상에서 제외한다
     * (signstage-docs business/billing-catalog-price-validity-period-review.md 결정,
     * 2026-09-09 원칙을 플랜 할인 기간에도 그대로 적용).
     */
    BillingPlanDiscountPeriod resolveSellablePlanPeriod(BillingPlan plan, LocalDate asOfDate) {
        BillingPlanDiscountPeriod period = billingCatalogQueryService.findEffectivePlanDiscountPeriod(plan.getId(), asOfDate)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.BILLING_PLAN_INACTIVE));
        if (!period.isActive()) {
            throw new ApplicationException(CeremonyErrorCode.BILLING_PLAN_INACTIVE);
        }
        return period;
    }

    /**
     * {@link #resolveSellablePlanPeriod}과 같은 원칙 — 단위 상품. 기간이 없거나(공백) 있어도
     * 사용 중지(active=false)면 {@code UNIT_PRODUCT_INACTIVE}. package-private으로 열어
     * {@link CustomerQuoteService}의 고객 견적 장비/인력 품목 추가에도 재사용한다(2026-09-11) —
     * 그 전까지는 이 검사가 아예 없어 사용중지 단위 상품도 견적에 그대로 담겼다.
     */
    UnitProductPricePeriod resolveSellableUnitProductPeriod(UnitProduct unitProduct, LocalDate asOfDate) {
        UnitProductPricePeriod period = billingCatalogQueryService.findEffectiveUnitProductPricePeriod(unitProduct.getId(), asOfDate)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_INACTIVE));
        if (!period.isActive()) {
            throw new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_INACTIVE);
        }
        return period;
    }

    /**
     * 플랜의 통화 — 플랜이 포함하는 단위 상품들의 "오늘" 유효한 통화가 전부 같아야 한다(단위
     * 상품은 자기 가격 기간에 통화를 갖고, 플랜 자체는 더 이상 통화를 갖지 않는다). 포함
     * 단위 상품이 하나도 없으면(드문 경우) 통화 검증 자체를 건너뛴다.
     */
    String resolvePlanCurrency(BillingPlan plan, LocalDate asOfDate) {
        Set<String> currencies = billingCatalogQueryService.findPlanUnitProducts(plan.getId()).stream()
                .map(BillingPlanUnitProduct::getUnitProduct)
                .map(unitProduct -> billingCatalogQueryService.findEffectiveUnitProductPricePeriod(unitProduct.getId(), asOfDate))
                .flatMap(Optional::stream)
                .map(period -> period.getPriceInfo().getCurrencyCode())
                .collect(Collectors.toSet());
        if (currencies.size() > 1) {
            throw new ApplicationException(CeremonyErrorCode.CURRENCY_MISMATCH);
        }
        return currencies.stream().findFirst().orElse(null);
    }

    /**
     * 서명자/문서양식/테스트·본행사 등록 화면이 "등록할 수 있는 개수"를 보여주는 데 쓴다 —
     * {@link #calculateEffectiveCapacity}(플랜 기본값 + 승인된 추가구매)를 다섯 가지 종류
     * 전부에 대해 계산해 돌려준다. 플랜이 없는 행사는 무제한이라 Integer.MAX_VALUE를 그대로
     * 돌려준다(프런트가 "무제한"으로 표시).
     */
    public CeremonyDto.Response.CapacityStatus retrieveCapacityStatus(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyAccessService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyAccessService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyAccessService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        return new CeremonyDto.Response.CapacityStatus(
                calculateEffectiveCapacity(ceremony, UnitProductType.SIGNERS),
                calculateEffectiveCapacity(ceremony, UnitProductType.TEMPLATES),
                calculateEffectiveCapacity(ceremony, UnitProductType.TEST_EVENTS),
                calculateEffectiveCapacity(ceremony, UnitProductType.REHEARSAL_EVENTS),
                calculateEffectiveCapacity(ceremony, UnitProductType.MAIN_EVENTS)
        );
    }

    /**
     * 플랜 스냅샷을 쓸지 라이브 값을 쓸지 정한다 — {@link CeremonyQuoteService#buildQuoteCalculation}/
     * {@link #calculateEffectiveCapacity}/{@link #retrieveApplicableUnitProductIds}/
     * {@link #retrievePurchasableUnitProductIds} 4곳이 전부 이 메서드로 스냅샷 조회를 감싼다.
     * DRAFT 상태에선 항상 {@code Optional.empty()}를 돌려줘 각 메서드의 기존 "스냅샷 없음"
     * fallback(라이브 조회)을 그대로 타게 한다 — 스냅샷은 "확정 이후엔 카탈로그가 바뀌어도
     * 안 바뀐다"를 보장하기 위한 것이라, 아직 아무것도 확정되지 않은 DRAFT에는 애초에 그 보장이
     * 필요 없다(2026-09-10, 실사용 중 발견 — DRAFT 행사가 쓰는 플랜에 관리자가 단위 상품을
     * 추가해도(예: 태블릿 0개 포함) 행사 쪽에 남은 옛 스냅샷 때문에 추가구매 후보 목록에
     * 반영되지 않던 버그. DRAFT에서 플랜을 자유롭게 바꿀 수 있다는 것과 같은 원칙이다).
     */
    Optional<CeremonyPlanHistory> findLatestPlanHistoryForSnapshot(Ceremony ceremony) {
        if (ceremony.getStatus() == CeremonyStatus.DRAFT) {
            return Optional.empty();
        }
        return ceremonyPlanHistoryRepository.findFirstByCeremonyIdOrderByCreatedAtDesc(ceremony.getId());
    }

    /**
     * 필수옵션(용량) 유효 한도 = 플랜 기본값(스냅샷) + Σ(APPROVED 구매 줄의 수량). 플랜이
     * 없는 행사(4.8 예외 — 이 기능 배포 전 기존 행사)는 한도 강제 자체를 적용하지 않는다(사실상
     * 무제한). 옛 "묶음 상품의 보조 용량"(secondaryCapacityType) 반영 로직은 사라졌다 — 묶음이
     * 폐지되고 다중 라인 구매로 대체됐기 때문이다(signstage-docs
     * business/billing-catalog-unit-product-model-redesign-review.md 3.7절).
     *
     * <p>플랜 기본값은 라이브 {@code BillingPlanUnitProduct}가 아니라 {@link CeremonyPlanHistory}의
     * 최신 스냅샷을 쓴다 — 카탈로그 관리자가 나중에 플랜 값을 고쳐도 이미 확정/진행 중인 행사는
     * 영향받지 않아야 한다. 이력이 없는 경우(이 기능 배포 전 기존 행사)와 아직 DRAFT인 행사
     * (아무것도 확정 안 됐으니 이 보장 자체가 필요 없다)는 라이브 값에 fallback한다
     * ({@link #findLatestPlanHistoryForSnapshot}).
     */
    int calculateEffectiveCapacity(Ceremony ceremony, UnitProductType type) {
        BillingPlan plan = ceremony.getBillingPlan();
        if (plan == null) {
            return Integer.MAX_VALUE;
        }

        int baseValue = findLatestPlanHistoryForSnapshot(ceremony)
                .map(snapshot -> ceremonyPlanHistoryUnitProductRepository.findAllByCeremonyPlanHistoryId(snapshot.getId()).stream()
                        .filter(line -> line.getUnitProduct().getType() == type)
                        .mapToInt(CeremonyPlanHistoryUnitProduct::getIncludedQuantity)
                        .sum())
                // 이력이 없는 경우(플랜 확정 기능 배포 전 기존 행사)만 라이브 값으로 대체한다.
                .orElseGet(() -> billingCatalogQueryService.findPlanUnitProducts(plan.getId()).stream()
                        .filter(source -> source.getUnitProduct().getType() == type)
                        .mapToInt(BillingPlanUnitProduct::getIncludedQuantity)
                        .sum());

        // 승인(APPROVED)된 요청만 한도에 반영한다 — 대기중/반려된 요청은 아직/영영 쓸 수 없다.
        int purchased = ceremonyUnitProductPurchaseLineRepository
                .findAllByPurchase_CeremonyIdAndUnitProduct_TypeAndPurchase_Status(ceremony.getId(), type, PurchaseStatus.APPROVED)
                .stream()
                .mapToInt(CeremonyUnitProductPurchaseLine::getQuantity)
                .sum();

        return baseValue + purchased;
    }

    /**
     * Ceremony가 "적용 가능한"(플랜 기본 포함 또는 승인된 추가구매) 단위 상품 id 집합 —
     * {@code type=EVENT_EFFECT_BUNDLE}로 좁힌다(CeremonyEvent 단위로 켜고 끄는 개념이 있는
     * 종류는 지금 이것뿐이다, signstage-docs
     * business/billing-catalog-unit-product-model-redesign-review.md 3.6절).
     *
     * <p>"플랜 기본 포함" 쪽은 라이브 {@code BillingPlanUnitProduct} 대신 이 Ceremony의 최신
     * {@link CeremonyPlanHistory} 스냅샷({@link CeremonyPlanHistoryUnitProduct})을 우선 쓴다 —
     * 카탈로그 관리자가 나중에 플랜의 구성을 바꿔도 영향받지 않아야 한다. 이력이 없는 경우(이
     * 스냅샷 기능 배포 전 기존 행사)와 아직 DRAFT인 행사는 라이브 값으로 대체한다
     * ({@link #findLatestPlanHistoryForSnapshot}).
     */
    List<Long> retrieveApplicableUnitProductIds(Ceremony ceremony) {
        List<Long> purchased = ceremonyUnitProductPurchaseLineRepository
                .findAllByPurchase_CeremonyIdOrderByCreatedAtDesc(ceremony.getId()).stream()
                .filter(line -> line.getPurchase().getStatus() == PurchaseStatus.APPROVED)
                .filter(line -> line.getUnitProduct().getType() == UnitProductType.EVENT_EFFECT_BUNDLE)
                .map(line -> line.getUnitProduct().getId())
                .toList();
        if (ceremony.getBillingPlan() == null) {
            return purchased;
        }

        Optional<CeremonyPlanHistory> snapshot = findLatestPlanHistoryForSnapshot(ceremony);
        List<Long> includedInPlan = snapshot
                .map(history -> ceremonyPlanHistoryUnitProductRepository.findAllByCeremonyPlanHistoryId(history.getId()).stream()
                        .filter(line -> line.getIncludedQuantity() > 0)
                        .filter(line -> line.getUnitProduct().getType() == UnitProductType.EVENT_EFFECT_BUNDLE)
                        .map(line -> line.getUnitProduct().getId())
                        .toList())
                .orElseGet(() -> billingCatalogQueryService.findPlanUnitProducts(ceremony.getBillingPlan().getId()).stream()
                        .filter(source -> source.getIncludedQuantity() > 0)
                        .filter(source -> source.getUnitProduct().getType() == UnitProductType.EVENT_EFFECT_BUNDLE)
                        .map(source -> source.getUnitProduct().getId())
                        .toList());

        return Stream.concat(purchased.stream(), includedInPlan.stream()).distinct().toList();
    }

    /**
     * 이 Ceremony의 플랜에서 구매 가능한(안 A 큐레이션) 단위 상품 id 목록. 플랜 구성에 행이
     * 있으면(포함 수량이 0이든 N이든) 그 자체로 추가구매 후보다 — 별도 {@code purchasable}
     * 플래그는 2026-09-10에 폐지했다({@code BillingPlanUnitProduct} javadoc 참고). 라이브
     * {@code BillingPlanUnitProduct} 대신 이 Ceremony의 최신 {@link CeremonyPlanHistory} 스냅샷을
     * 우선 쓴다 — 카탈로그 관리자가 나중에 플랜의 단위 상품 구성을 바꿔도 영향받지 않아야 한다.
     * 이력이 없는 경우와 아직 DRAFT인 행사는 라이브 값으로 대체한다
     * ({@link #findLatestPlanHistoryForSnapshot}) — 2026-09-10, 실사용 중 발견한 버그 수정:
     * DRAFT 행사가 쓰는 플랜에 관리자가 단위 상품을 나중에 추가해도(예: 태블릿을 0개 포함으로
     * 추가) 옛 스냅샷 때문에 추가구매 후보 목록에 반영되지 않던 문제. 호출부가
     * {@code ceremony.getBillingPlan() != null}을 먼저 확인해야 한다 — 플랜 없는 행사는 제한
     * 자체가 없다.
     *
     * <p>시스템 사용료(ESSENTIAL/APPLICATION)만 돌려준다 — 장비·인력(EQUIPMENT/PERSONNEL)은
     * "플랫폼 이용료" 흐름에서 완전히 분리됐다(signstage-docs
     * business/unit-product-purchase-self-checkout-review.md 8.2절 결정, 2026-09-11).
     * 플랜 구성에 장비·인력이 포함돼 있어도 이 메서드는 걸러낸다.
     *
     * <p>토글형({@link UnitProductType#isToggle()}, 지금은 EVENT_EFFECT_BUNDLE)은 플랜이 이미
     * {@code includedQuantity ≥ 1}로 포함하고 있으면 후보에서 뺀다(2026-09-12 사용자 지적) —
     * {@link #retrieveApplicableUnitProductIds}가 "플랜 기본 포함"만으로도 CeremonyEvent에
     * 적용 가능하다고 이미 인정하는 것과 같은 원칙이라, 포함된 걸 또 추가구매하게 두면 같은
     * 효과를 중복으로 사는 셈이 된다. 수량형(태블릿 등)은 기본 포함 수량을 넘겨 더 사는 게
     * 정상 흐름이라 이 필터를 타지 않는다.
     */
    List<Long> retrievePurchasableUnitProductIds(Ceremony ceremony) {
        Optional<CeremonyPlanHistory> snapshot = findLatestPlanHistoryForSnapshot(ceremony);
        return snapshot
                .map(history -> ceremonyPlanHistoryUnitProductRepository.findAllByCeremonyPlanHistoryId(history.getId()).stream()
                        .filter(line -> line.getUnitProduct().isPlatformUsageFee())
                        .filter(line -> !alreadyIncludedToggle(line.getUnitProduct(), line.getIncludedQuantity()))
                        .map(line -> line.getUnitProduct().getId())
                        .toList())
                .orElseGet(() -> billingCatalogQueryService.findPlanUnitProducts(ceremony.getBillingPlan().getId()).stream()
                        .filter(source -> source.getUnitProduct().isPlatformUsageFee())
                        .filter(source -> !alreadyIncludedToggle(source.getUnitProduct(), source.getIncludedQuantity()))
                        .map(source -> source.getUnitProduct().getId())
                        .toList());
    }

    /** {@link #retrievePurchasableUnitProductIds}가 쓰는 판정 — 토글형이면서 이미 1개 이상 포함돼 있는지. */
    private boolean alreadyIncludedToggle(UnitProduct unitProduct, Integer includedQuantity) {
        return unitProduct.getType().isToggle() && includedQuantity != null && includedQuantity >= 1;
    }

    void checkCurrencyMatches(String ceremonyCurrencyCode, String itemCurrencyCode) {
        if (itemCurrencyCode != null && !Objects.equals(ceremonyCurrencyCode, itemCurrencyCode)) {
            throw new ApplicationException(CeremonyErrorCode.CURRENCY_MISMATCH);
        }
    }
}
