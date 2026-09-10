package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.core.i18n.InternationalizationDefaults;
import com.eformworks.signstage.backend.feature.ceremony.dto.UnitProductDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.ProductPriceInfo;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductCategory;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductPricePeriod;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductPricePeriodHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.UnitProductHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.UnitProductPricePeriodHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.UnitProductPricePeriodRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.UnitProductRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카탈로그 단위 상품(서명자/템플릿 문서/테스트·리허설·본 행사/태블릿/현장지원/온라인지원/이벤트 효과 묶음)
 * — signstage-docs business/billing-catalog-unit-product-model-redesign-review.md 결정
 * (2026-09-10)에 따라 {@code OptionalFeatureService}와 {@code CapacityAddOnService}를 하나로
 * 합친 자리다. 등록은 플랫폼 관리자 전용, 조회는 인증된 사용자 누구나 가능하다(행사 생성 화면에서
 * 상품을 고를 때 필요).
 *
 * <p>{@code type}은 {@code unit_products.type}에 UNIQUE 제약을 두지 않는다(같은 문서 §3.1 결정)
 * — {@code EVENT_EFFECT_BUNDLE}처럼 관리자가 계속 새 상품 행을 만들 수 있어야 하는 종류가 있어서,
 * 옛 두 서비스에 있던 코드/유형 중복 검사를 이 서비스는 아예 두지 않는다.
 *
 * <p>가격정보는 이 서비스가 {@link UnitProductPricePeriod} 기간 단위 CRUD로 관리한다
 * (signstage-docs business/billing-catalog-price-validity-period-review.md 결정, 2026-09-09,
 * 다중버전 채택) — {@code BillingPlanService}의 기간 CRUD와 같은 패턴. {@link ProductPriceInfo}는
 * 할인 필드가 없다 — 단위 상품은 할인을 갖지 않는다(할인은 오직 BillingPlan에만 있다, 같은 문서
 * §3.5 결정).
 *
 * <p><b>1단계(추가 전용) 범위 안내</b> — 이 서비스는 아직 {@code Ceremony*Purchase}/
 * {@code CeremonyEffectDefinitionOption}과 연결되지 않았다(둘 다 옛 {@code OptionalFeature}/
 * {@code CapacityAddOn}을 참조하는 2단계 전환 대상이라서다). 그래서 지금은
 * <ul>
 *   <li>{@code usageCount}가 항상 0이다 — 아직 어떤 구매도 {@code UnitProduct}를 참조하지
 *       않기 때문에 실제로 맞는 값이다. 2단계에서 구매 엔티티가 전환되면 실제 집계로 바뀐다.</li>
 *   <li>{@code effectDefinitionIds}는 읽기 전용으로 항상 빈 배열을 내려주고, 쓰기(생성/수정
 *       요청에 값이 오는 경우)는 거부한다 — {@code EVENT_EFFECT_BUNDLE} 연결은
 *       {@code CeremonyEffectDefinitionOption}의 외래키가 {@code unit_product_id}로 바뀌는
 *       2단계에서 함께 연결한다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UnitProductService {

    private final UnitProductRepository unitProductRepository;
    private final UnitProductHistoryRepository unitProductHistoryRepository;
    private final UnitProductPricePeriodRepository unitProductPricePeriodRepository;
    private final UnitProductPricePeriodHistoryRepository unitProductPricePeriodHistoryRepository;
    private final PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;
    private final RolePermissionService rolePermissionService;

    @Transactional
    public UnitProductDto.Response.UnitProductSummary createUnitProduct(
            String actingPlatformRole,
            Long adminUserId,
            UnitProductDto.Request.CreateUnitProduct request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");

        UnitProductType type = parseType(request.getType());
        checkEffectDefinitionIdsAllowed(request.getEffectDefinitionIds());
        LocalDate effectiveFrom = resolveEffectiveFrom(request.getEffectiveFrom());
        checkPeriodValid(effectiveFrom, request.getEffectiveTo());

        UnitProduct unitProduct = UnitProduct.builder()
                .type(type)
                .name(request.getName())
                .category(parseCategory(request.getCategory()))
                .exclusivityGroup(request.getExclusivityGroup())
                .build();
        unitProductRepository.save(unitProduct);
        recordProductHistory(unitProduct);

        UnitProductPricePeriod period = UnitProductPricePeriod.builder()
                .unitProduct(unitProduct)
                .currencyCode(request.getCurrencyCode())
                .supplyPrice(request.getSupplyPrice())
                .salePrice(request.getSalePrice())
                .taxCode(request.getTaxCode())
                .active(request.getActive())
                .effectiveFrom(effectiveFrom)
                .effectiveTo(request.getEffectiveTo())
                .build();
        unitProductPricePeriodRepository.save(period);
        recordPeriodHistory(unitProduct, period, false);

        platformAdminAuditLogRecorder.record(
                adminUserId,
                PlatformAdminAction.CREATE_UNIT_PRODUCT,
                null,
                null,
                "unitProductId=" + unitProduct.getId() + ", type=" + unitProduct.getType()
        );

        return toSummary(unitProduct);
    }

    @Transactional
    public UnitProductDto.Response.UnitProductSummary updateUnitProduct(
            Long unitProductId,
            String actingPlatformRole,
            Long adminUserId,
            UnitProductDto.Request.UpdateUnitProduct request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");

        UnitProduct unitProduct = unitProductRepository.findById(unitProductId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_NOT_FOUND));
        checkEffectDefinitionIdsAllowed(request.getEffectDefinitionIds());

        String detail = "unitProductId=" + unitProductId
                + ", name: " + unitProduct.getName() + " -> " + request.getName();

        unitProduct.updateInfo(request.getName(), parseCategory(request.getCategory()), request.getExclusivityGroup());
        recordProductHistory(unitProduct);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_UNIT_PRODUCT, null, null, detail
        );

        return toSummary(unitProduct);
    }

    /** 새 판매가격 기간을 추가한다. */
    @Transactional
    public UnitProductDto.Response.UnitProductPeriodSummary createPeriod(
            Long unitProductId,
            String actingPlatformRole,
            Long adminUserId,
            UnitProductDto.Request.CreatePeriod request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");
        UnitProduct unitProduct = unitProductRepository.findById(unitProductId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_NOT_FOUND));
        LocalDate effectiveFrom = resolveEffectiveFrom(request.getEffectiveFrom());
        checkPeriodValid(effectiveFrom, request.getEffectiveTo());
        checkNoOverlap(unitProductId, null, effectiveFrom, request.getEffectiveTo());

        UnitProductPricePeriod period = UnitProductPricePeriod.builder()
                .unitProduct(unitProduct)
                .currencyCode(request.getCurrencyCode())
                .supplyPrice(request.getSupplyPrice())
                .salePrice(request.getSalePrice())
                .taxCode(request.getTaxCode())
                .active(request.getActive())
                .effectiveFrom(effectiveFrom)
                .effectiveTo(request.getEffectiveTo())
                .build();
        unitProductPricePeriodRepository.save(period);
        recordPeriodHistory(unitProduct, period, false);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_UNIT_PRODUCT, null, null,
                "unitProductId=" + unitProductId + ", 판매가격 기간 생성: " + describe(period)
        );
        return toPeriodSummary(period);
    }

    /** 이미 있는 판매가격 기간 하나를 고친다. */
    @Transactional
    public UnitProductDto.Response.UnitProductPeriodSummary updatePeriod(
            Long unitProductId,
            Long periodId,
            String actingPlatformRole,
            Long adminUserId,
            UnitProductDto.Request.UpdatePeriod request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");
        unitProductRepository.findById(unitProductId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_NOT_FOUND));
        UnitProductPricePeriod period = unitProductPricePeriodRepository
                .findByIdAndUnitProductId(periodId, unitProductId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CATALOG_PRICE_PERIOD_NOT_FOUND));
        checkPeriodValid(request.getEffectiveFrom(), request.getEffectiveTo());
        checkNoOverlap(unitProductId, periodId, request.getEffectiveFrom(), request.getEffectiveTo());

        String previous = describe(period);
        period.update(
                request.getCurrencyCode(), request.getSupplyPrice(), request.getSalePrice(),
                request.getTaxCode(), request.getActive(), request.getEffectiveFrom(), request.getEffectiveTo()
        );
        recordPeriodHistory(period.getUnitProduct(), period, false);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_UNIT_PRODUCT, null, null,
                "unitProductId=" + unitProductId + ", periodId=" + periodId + ", 판매가격 기간: "
                        + previous + " -> " + describe(period)
        );
        return toPeriodSummary(period);
    }

    /** 판매가격 기간을 제거한다 — 마지막 남은 기간 하나는 지울 수 없다. */
    @Transactional
    public void removePeriod(Long unitProductId, Long periodId, String actingPlatformRole, Long adminUserId) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");
        unitProductRepository.findById(unitProductId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_NOT_FOUND));
        UnitProductPricePeriod period = unitProductPricePeriodRepository
                .findByIdAndUnitProductId(periodId, unitProductId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CATALOG_PRICE_PERIOD_NOT_FOUND));
        if (unitProductPricePeriodRepository.countByUnitProductId(unitProductId) <= 1) {
            throw new ApplicationException(CeremonyErrorCode.CATALOG_PRICE_PERIOD_REQUIRED);
        }

        recordPeriodHistory(period.getUnitProduct(), period, true);
        unitProductPricePeriodRepository.delete(period);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_UNIT_PRODUCT, null, null,
                "unitProductId=" + unitProductId + ", periodId=" + periodId + ", 판매가격 기간 제거: " + describe(period)
        );
    }

    /** 이 단위 상품의 판매가격 기간 전체(과거/현재/예정) — 오래된 순. */
    public List<UnitProductDto.Response.UnitProductPeriodSummary> findUnitProductPeriods(Long unitProductId) {
        if (!unitProductRepository.existsById(unitProductId)) {
            throw new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_NOT_FOUND);
        }
        return unitProductPricePeriodRepository.findAllByUnitProductIdOrderByEffectiveFromAsc(unitProductId).stream()
                .map(this::toPeriodSummary)
                .toList();
    }

    /** 판매가격 기간의 생성/수정/삭제 이력 — 최신순. */
    public List<UnitProductDto.Response.UnitProductPeriodHistorySummary> findUnitProductPeriodHistory(Long unitProductId) {
        if (!unitProductRepository.existsById(unitProductId)) {
            throw new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_NOT_FOUND);
        }
        return unitProductPricePeriodHistoryRepository.findAllByUnitProductIdOrderByCreatedAtDesc(unitProductId).stream()
                .map(h -> new UnitProductDto.Response.UnitProductPeriodHistorySummary(
                        h.getId(),
                        h.getPriceInfo().getCurrencyCode(),
                        h.getPriceInfo().getSupplyPrice(),
                        h.getPriceInfo().getSalePrice(),
                        h.getPriceInfo().getTaxCode(),
                        h.isActive(),
                        h.getEffectiveFrom(),
                        h.getEffectiveTo(),
                        h.isRemoved(),
                        h.getCreatedBy(),
                        h.getCreatedAt()
                ))
                .toList();
    }

    /**
     * 1단계에서는 어떤 종류든 이벤트 효과 목록 지정을 받지 않는다 — 클래스 javadoc 참고. 2단계에서
     * {@code CeremonyEffectDefinitionOption}이 {@code unit_product_id}를 참조하게 되면 옛
     * {@code OptionalFeatureService#checkEffectDefinitionIdsAllowed}처럼
     * {@code type != EVENT_EFFECT_BUNDLE}일 때만 거부하는 로직으로 바뀐다.
     */
    private void checkEffectDefinitionIdsAllowed(List<Long> effectDefinitionIds) {
        if (effectDefinitionIds != null && !effectDefinitionIds.isEmpty()) {
            throw new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_EFFECT_BUNDLE_ONLY);
        }
    }

    public List<UnitProductDto.Response.UnitProductSummary> findUnitProducts() {
        return unitProductRepository.findAll().stream()
                .map(this::toSummary)
                .toList();
    }

    /** 최신순 — 생성 시점 1건 + 이후 수정할 때마다 1건씩(이름/분류/배타그룹이 바뀔 때). */
    public List<UnitProductDto.Response.UnitProductHistorySummary> findUnitProductHistory(Long unitProductId) {
        if (!unitProductRepository.existsById(unitProductId)) {
            throw new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_NOT_FOUND);
        }
        return unitProductHistoryRepository.findAllByUnitProductIdOrderByCreatedAtDesc(unitProductId).stream()
                .map(this::toHistorySummary)
                .toList();
    }

    /** 생성 시(최초 상태)와 {@link #updateUnitProduct}에서 매 변경마다 호출한다. */
    private void recordProductHistory(UnitProduct unitProduct) {
        unitProductHistoryRepository.save(UnitProductHistory.builder().unitProduct(unitProduct).build());
    }

    /** 판매가격 기간 설정(생성/수정) 시점마다, 그리고 {@link #removePeriod}에서 제거 시점마다 호출한다. */
    private void recordPeriodHistory(UnitProduct unitProduct, UnitProductPricePeriod period, boolean removed) {
        unitProductPricePeriodHistoryRepository.save(
                UnitProductPricePeriodHistory.builder().unitProduct(unitProduct).period(period).removed(removed).build()
        );
    }

    private UnitProductType parseType(String type) {
        try {
            return UnitProductType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    private UnitProductCategory parseCategory(String category) {
        try {
            return UnitProductCategory.valueOf(category);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    /**
     * effectiveFrom 생략(null) 시 "오늘"로 채운다 — signstage-docs
     * business/organization-discount-override-security-and-validity-period-review.md 결정
     * #5(2026-09-10) — 단위 상품 카탈로그는 조직/행사 스코프가 없어 플랫폼 기본 타임존
     * (Asia/Seoul)을 쓴다. 이미 있는 기간을 고치는 {@code UpdatePeriod}는 이 헬퍼를 쓰지
     * 않는다 — 편집 중인 기간의 시작일을 묵시적으로 오늘로 되돌리면 안 되므로 여전히 필수
     * 입력이다.
     */
    private LocalDate resolveEffectiveFrom(LocalDate requested) {
        return requested != null ? requested : InternationalizationDefaults.today();
    }

    private void checkPeriodValid(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new ApplicationException(CeremonyErrorCode.DISCOUNT_PERIOD_INVALID);
        }
    }

    private void checkNoOverlap(Long unitProductId, Long excludePeriodId, LocalDate newFrom, LocalDate newTo) {
        boolean overlaps = unitProductPricePeriodRepository
                .findAllByUnitProductIdOrderByEffectiveFromAsc(unitProductId).stream()
                .filter(p -> excludePeriodId == null || !p.getId().equals(excludePeriodId))
                .anyMatch(p -> rangesOverlap(newFrom, newTo, p.getEffectiveFrom(), p.getEffectiveTo()));
        if (overlaps) {
            throw new ApplicationException(CeremonyErrorCode.CATALOG_PRICE_PERIOD_OVERLAPPING);
        }
    }

    private boolean rangesOverlap(LocalDate aFrom, LocalDate aTo, LocalDate bFrom, LocalDate bTo) {
        boolean aStartsBeforeBEnds = bTo == null || !aFrom.isAfter(bTo);
        boolean bStartsBeforeAEnds = aTo == null || !bFrom.isAfter(aTo);
        return aStartsBeforeBEnds && bStartsBeforeAEnds;
    }

    private String computeStatus(boolean active, LocalDate effectiveFrom, LocalDate effectiveTo) {
        LocalDate today = InternationalizationDefaults.today();
        if (today.isBefore(effectiveFrom)) {
            return "PENDING";
        }
        if (effectiveTo != null && today.isAfter(effectiveTo)) {
            return "EXPIRED";
        }
        return active ? "ON_SALE" : "INACTIVE";
    }

    private String describe(UnitProductPricePeriod period) {
        return period.getPriceInfo().getSalePrice() + " " + period.getPriceInfo().getCurrencyCode()
                + " (" + period.getEffectiveFrom() + " ~ " + (period.getEffectiveTo() == null ? "무기한" : period.getEffectiveTo()) + ")";
    }

    private UnitProductDto.Response.UnitProductSummary toSummary(UnitProduct unitProduct) {
        Optional<UnitProductPricePeriod> effective =
                unitProductPricePeriodRepository.findEffective(unitProduct.getId(), InternationalizationDefaults.today());
        return new UnitProductDto.Response.UnitProductSummary(
                unitProduct.getId(),
                unitProduct.getType().name(),
                unitProduct.getName(),
                unitProduct.getCategory().name(),
                unitProduct.getExclusivityGroup(),
                effective.map(p -> p.getPriceInfo().getCurrencyCode()).orElse(null),
                effective.map(p -> p.getPriceInfo().getSupplyPrice()).orElse(null),
                effective.map(p -> p.getPriceInfo().getSalePrice()).orElse(null),
                effective.map(p -> p.getPriceInfo().getTaxCode()).orElse(null),
                effective.map(UnitProductPricePeriod::isActive).orElse(null),
                // 1단계 임시값 — 클래스 javadoc 참고. 아직 어떤 구매도 UnitProduct를 참조하지 않아
                // 실제로 0이 맞다. 2단계에서 CeremonyUnitProductPurchaseLine 집계로 바뀐다.
                0L,
                Collections.emptyList(),
                unitProduct.getCreatedAt(),
                effective.map(UnitProductPricePeriod::getEffectiveFrom).orElse(null),
                effective.map(UnitProductPricePeriod::getEffectiveTo).orElse(null),
                effective.map(p -> computeStatus(p.isActive(), p.getEffectiveFrom(), p.getEffectiveTo())).orElse("NO_ACTIVE_PERIOD")
        );
    }

    private UnitProductDto.Response.UnitProductHistorySummary toHistorySummary(UnitProductHistory history) {
        return new UnitProductDto.Response.UnitProductHistorySummary(
                history.getId(),
                history.getType().name(),
                history.getName(),
                history.getCategory().name(),
                history.getExclusivityGroup(),
                history.getCreatedBy(),
                history.getCreatedAt()
        );
    }

    private UnitProductDto.Response.UnitProductPeriodSummary toPeriodSummary(UnitProductPricePeriod period) {
        return new UnitProductDto.Response.UnitProductPeriodSummary(
                period.getId(),
                period.getPriceInfo().getCurrencyCode(),
                period.getPriceInfo().getSupplyPrice(),
                period.getPriceInfo().getSalePrice(),
                period.getPriceInfo().getTaxCode(),
                period.isActive(),
                period.getEffectiveFrom(),
                period.getEffectiveTo(),
                computeStatus(period.isActive(), period.getEffectiveFrom(), period.getEffectiveTo()),
                period.getCreatedAt()
        );
    }

    /** signstage-docs business/menu-and-action-permission-management-review.md 10장 참고. */
    private void checkAllowed(String actingPlatformRole, String permissionKey) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, permissionKey)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }
}
