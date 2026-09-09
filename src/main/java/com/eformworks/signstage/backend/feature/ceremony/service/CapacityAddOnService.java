package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.CapacityAddOnDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityAddOn;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityAddOnHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityAddOnPricePeriod;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityAddOnPricePeriodHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityType;
import com.eformworks.signstage.backend.feature.ceremony.entity.DiscountType;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CapacityAddOnHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CapacityAddOnPricePeriodHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CapacityAddOnPricePeriodRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CapacityAddOnRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyCapacityPurchaseRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 필수옵션(용량 한도) 추가구매 상품 카탈로그(예: "서명자 +10명"). signstage-docs
 * business/ceremony-billing-options-review.md 4.7/4.9절 참고. 등록은 플랫폼 관리자 전용,
 * 조회는 인증된 사용자 누구나 가능하다.
 *
 * <p>가격정보/사용여부는 이 서비스가 {@link CapacityAddOnPricePeriod} 기간 단위 CRUD로 관리한다
 * (signstage-docs business/billing-catalog-price-validity-period-review.md 결정, 2026-09-09,
 * 다중버전 채택) — {@code BillingPlanService}의 기간 CRUD와 같은 패턴.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CapacityAddOnService {

    private final CapacityAddOnRepository capacityAddOnRepository;
    private final CapacityAddOnHistoryRepository capacityAddOnHistoryRepository;
    private final CapacityAddOnPricePeriodRepository capacityAddOnPricePeriodRepository;
    private final CapacityAddOnPricePeriodHistoryRepository capacityAddOnPricePeriodHistoryRepository;
    private final CeremonyCapacityPurchaseRepository ceremonyCapacityPurchaseRepository;
    private final PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;
    private final RolePermissionService rolePermissionService;

    @Transactional
    public CapacityAddOnDto.Response.CapacityAddOnSummary createCapacityAddOn(
            String actingPlatformRole,
            Long adminUserId,
            CapacityAddOnDto.Request.CreateCapacityAddOn request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");

        CapacityType capacityType = parseCapacityType(request.getCapacityType());
        CapacityType secondaryCapacityType = parseOptionalCapacityType(request.getSecondaryCapacityType());
        checkSecondaryCapacityValid(capacityType, secondaryCapacityType, request.getSecondaryUnitAmount());
        checkPeriodValid(request.getEffectiveFrom(), request.getEffectiveTo());

        CapacityAddOn capacityAddOn = CapacityAddOn.builder()
                .capacityType(capacityType)
                .unitAmount(request.getUnitAmount())
                .secondaryCapacityType(secondaryCapacityType)
                .secondaryUnitAmount(secondaryCapacityType == null ? null : request.getSecondaryUnitAmount())
                .build();
        capacityAddOnRepository.save(capacityAddOn);
        recordAddOnHistory(capacityAddOn);

        CapacityAddOnPricePeriod period = CapacityAddOnPricePeriod.builder()
                .capacityAddOn(capacityAddOn)
                .currencyCode(request.getCurrencyCode())
                .supplyPrice(request.getSupplyPrice())
                .salePrice(request.getSalePrice())
                .discountType(parseDiscountType(request.getDiscountType()))
                .discountValue(request.getDiscountValue())
                .taxCode(request.getTaxCode())
                .active(request.getActive())
                .effectiveFrom(request.getEffectiveFrom())
                .effectiveTo(request.getEffectiveTo())
                .build();
        capacityAddOnPricePeriodRepository.save(period);
        recordPeriodHistory(capacityAddOn, period, false);

        platformAdminAuditLogRecorder.record(
                adminUserId,
                PlatformAdminAction.CREATE_CAPACITY_ADDON,
                null,
                null,
                "capacityAddOnId=" + capacityAddOn.getId() + ", capacityType=" + capacityAddOn.getCapacityType()
        );

        return toSummary(capacityAddOn);
    }

    @Transactional
    public CapacityAddOnDto.Response.CapacityAddOnSummary updateCapacityAddOn(
            Long capacityAddOnId,
            String actingPlatformRole,
            Long adminUserId,
            CapacityAddOnDto.Request.UpdateCapacityAddOn request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");

        CapacityAddOn capacityAddOn = capacityAddOnRepository.findById(capacityAddOnId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CAPACITY_ADDON_NOT_FOUND));

        String detail = "capacityAddOnId=" + capacityAddOnId
                + ", unitAmount: " + capacityAddOn.getUnitAmount() + " -> " + request.getUnitAmount();

        // secondaryCapacityType은 생성 후 불변이라 수정 요청에 없다 — 원래 묶음 상품이 아니었으면
        // (secondaryCapacityType == null) 보조 수량 입력은 조용히 무시한다(묶음으로 바꾸려면 새
        // 상품을 만들어야 한다). 원래 묶음 상품이었다면 보조 수량은 계속 필수다.
        if (capacityAddOn.getSecondaryCapacityType() != null && request.getSecondaryUnitAmount() == null) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
        Integer secondaryUnitAmount = capacityAddOn.getSecondaryCapacityType() == null
                ? null
                : request.getSecondaryUnitAmount();

        capacityAddOn.updateInfo(request.getUnitAmount(), secondaryUnitAmount);
        recordAddOnHistory(capacityAddOn);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_CAPACITY_ADDON, null, null, detail
        );

        return toSummary(capacityAddOn);
    }

    /** 새 판매가격 기간을 추가한다. */
    @Transactional
    public CapacityAddOnDto.Response.CapacityAddOnPeriodSummary createPeriod(
            Long capacityAddOnId,
            String actingPlatformRole,
            Long adminUserId,
            CapacityAddOnDto.Request.CreatePeriod request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");
        CapacityAddOn capacityAddOn = capacityAddOnRepository.findById(capacityAddOnId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CAPACITY_ADDON_NOT_FOUND));
        checkPeriodValid(request.getEffectiveFrom(), request.getEffectiveTo());
        checkNoOverlap(capacityAddOnId, null, request.getEffectiveFrom(), request.getEffectiveTo());

        CapacityAddOnPricePeriod period = CapacityAddOnPricePeriod.builder()
                .capacityAddOn(capacityAddOn)
                .currencyCode(request.getCurrencyCode())
                .supplyPrice(request.getSupplyPrice())
                .salePrice(request.getSalePrice())
                .discountType(parseDiscountType(request.getDiscountType()))
                .discountValue(request.getDiscountValue())
                .taxCode(request.getTaxCode())
                .active(request.getActive())
                .effectiveFrom(request.getEffectiveFrom())
                .effectiveTo(request.getEffectiveTo())
                .build();
        capacityAddOnPricePeriodRepository.save(period);
        recordPeriodHistory(capacityAddOn, period, false);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_CAPACITY_ADDON, null, null,
                "capacityAddOnId=" + capacityAddOnId + ", 판매가격 기간 생성: " + describe(period)
        );
        return toPeriodSummary(period);
    }

    /** 이미 있는 판매가격 기간 하나를 고친다. */
    @Transactional
    public CapacityAddOnDto.Response.CapacityAddOnPeriodSummary updatePeriod(
            Long capacityAddOnId,
            Long periodId,
            String actingPlatformRole,
            Long adminUserId,
            CapacityAddOnDto.Request.UpdatePeriod request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");
        capacityAddOnRepository.findById(capacityAddOnId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CAPACITY_ADDON_NOT_FOUND));
        CapacityAddOnPricePeriod period = capacityAddOnPricePeriodRepository
                .findByIdAndCapacityAddOnId(periodId, capacityAddOnId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CATALOG_PRICE_PERIOD_NOT_FOUND));
        checkPeriodValid(request.getEffectiveFrom(), request.getEffectiveTo());
        checkNoOverlap(capacityAddOnId, periodId, request.getEffectiveFrom(), request.getEffectiveTo());

        String previous = describe(period);
        period.update(
                request.getCurrencyCode(), request.getSupplyPrice(), request.getSalePrice(),
                parseDiscountType(request.getDiscountType()), request.getDiscountValue(), request.getTaxCode(),
                request.getActive(), request.getEffectiveFrom(), request.getEffectiveTo()
        );
        recordPeriodHistory(period.getCapacityAddOn(), period, false);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_CAPACITY_ADDON, null, null,
                "capacityAddOnId=" + capacityAddOnId + ", periodId=" + periodId + ", 판매가격 기간: "
                        + previous + " -> " + describe(period)
        );
        return toPeriodSummary(period);
    }

    /** 판매가격 기간을 제거한다 — 마지막 남은 기간 하나는 지울 수 없다. */
    @Transactional
    public void removePeriod(Long capacityAddOnId, Long periodId, String actingPlatformRole, Long adminUserId) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");
        capacityAddOnRepository.findById(capacityAddOnId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CAPACITY_ADDON_NOT_FOUND));
        CapacityAddOnPricePeriod period = capacityAddOnPricePeriodRepository
                .findByIdAndCapacityAddOnId(periodId, capacityAddOnId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CATALOG_PRICE_PERIOD_NOT_FOUND));
        if (capacityAddOnPricePeriodRepository.countByCapacityAddOnId(capacityAddOnId) <= 1) {
            throw new ApplicationException(CeremonyErrorCode.CATALOG_PRICE_PERIOD_REQUIRED);
        }

        recordPeriodHistory(period.getCapacityAddOn(), period, true);
        capacityAddOnPricePeriodRepository.delete(period);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_CAPACITY_ADDON, null, null,
                "capacityAddOnId=" + capacityAddOnId + ", periodId=" + periodId + ", 판매가격 기간 제거: " + describe(period)
        );
    }

    /** 이 상품의 판매가격 기간 전체(과거/현재/예정) — 오래된 순. */
    public List<CapacityAddOnDto.Response.CapacityAddOnPeriodSummary> findAddOnPeriods(Long capacityAddOnId) {
        if (!capacityAddOnRepository.existsById(capacityAddOnId)) {
            throw new ApplicationException(CeremonyErrorCode.CAPACITY_ADDON_NOT_FOUND);
        }
        return capacityAddOnPricePeriodRepository.findAllByCapacityAddOnIdOrderByEffectiveFromAsc(capacityAddOnId).stream()
                .map(this::toPeriodSummary)
                .toList();
    }

    /** 판매가격 기간의 생성/수정/삭제 이력 — 최신순. */
    public List<CapacityAddOnDto.Response.CapacityAddOnPeriodHistorySummary> findAddOnPeriodHistory(Long capacityAddOnId) {
        if (!capacityAddOnRepository.existsById(capacityAddOnId)) {
            throw new ApplicationException(CeremonyErrorCode.CAPACITY_ADDON_NOT_FOUND);
        }
        return capacityAddOnPricePeriodHistoryRepository.findAllByCapacityAddOnIdOrderByCreatedAtDesc(capacityAddOnId).stream()
                .map(h -> new CapacityAddOnDto.Response.CapacityAddOnPeriodHistorySummary(
                        h.getId(),
                        h.getPriceInfo().getCurrencyCode(),
                        h.getPriceInfo().getSupplyPrice(),
                        h.getPriceInfo().getSalePrice(),
                        h.getPriceInfo().getDiscount().getDiscountType().name(),
                        h.getPriceInfo().getDiscount().getDiscountValue(),
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

    public List<CapacityAddOnDto.Response.CapacityAddOnSummary> findCapacityAddOns() {
        return capacityAddOnRepository.findAll().stream()
                .map(this::toSummary)
                .toList();
    }

    /** 최신순 — 생성 시점 1건 + 이후 수정할 때마다 1건씩(단위수량이 바뀔 때). */
    public List<CapacityAddOnDto.Response.CapacityAddOnHistorySummary> findAddOnHistory(Long capacityAddOnId) {
        if (!capacityAddOnRepository.existsById(capacityAddOnId)) {
            throw new ApplicationException(CeremonyErrorCode.CAPACITY_ADDON_NOT_FOUND);
        }
        return capacityAddOnHistoryRepository.findAllByCapacityAddOnIdOrderByCreatedAtDesc(capacityAddOnId).stream()
                .map(this::toHistorySummary)
                .toList();
    }

    /** 생성 시(최초 상태)와 {@link #updateCapacityAddOn}에서 매 변경마다 호출한다. */
    private void recordAddOnHistory(CapacityAddOn capacityAddOn) {
        capacityAddOnHistoryRepository.save(CapacityAddOnHistory.builder().capacityAddOn(capacityAddOn).build());
    }

    /** 판매가격 기간 설정(생성/수정) 시점마다, 그리고 {@link #removePeriod}에서 제거 시점마다 호출한다. */
    private void recordPeriodHistory(CapacityAddOn capacityAddOn, CapacityAddOnPricePeriod period, boolean removed) {
        capacityAddOnPricePeriodHistoryRepository.save(
                CapacityAddOnPricePeriodHistory.builder().capacityAddOn(capacityAddOn).period(period).removed(removed).build()
        );
    }

    private CapacityType parseCapacityType(String capacityType) {
        try {
            return CapacityType.valueOf(capacityType);
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    /** 묶음 상품이 아니면(요청에 없으면) null — {@link #parseCapacityType}과 달리 생략을 허용한다. */
    private CapacityType parseOptionalCapacityType(String secondaryCapacityType) {
        if (secondaryCapacityType == null || secondaryCapacityType.isBlank()) {
            return null;
        }
        return parseCapacityType(secondaryCapacityType);
    }

    /**
     * 묶음 상품 등록 규칙 — signstage-docs business/ceremony-billing-options-review.md 4.7절
     * 후속(2026-08-21): 보조 유형/수량은 함께 있거나 함께 없어야 하고, 보조 유형은 주 유형과
     * 달라야 한다(같은 용량을 두 번 늘리는 건 의미가 없다 — 그냥 unitAmount를 늘리면 된다).
     */
    private void checkSecondaryCapacityValid(CapacityType capacityType, CapacityType secondaryCapacityType, Integer secondaryUnitAmount) {
        boolean hasSecondaryType = secondaryCapacityType != null;
        boolean hasSecondaryAmount = secondaryUnitAmount != null;
        if (hasSecondaryType != hasSecondaryAmount) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
        if (hasSecondaryType && secondaryCapacityType == capacityType) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    private DiscountType parseDiscountType(String discountType) {
        try {
            return DiscountType.valueOf(discountType);
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    private void checkPeriodValid(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new ApplicationException(CeremonyErrorCode.DISCOUNT_PERIOD_INVALID);
        }
    }

    private void checkNoOverlap(Long capacityAddOnId, Long excludePeriodId, LocalDate newFrom, LocalDate newTo) {
        boolean overlaps = capacityAddOnPricePeriodRepository
                .findAllByCapacityAddOnIdOrderByEffectiveFromAsc(capacityAddOnId).stream()
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
        LocalDate today = LocalDate.now();
        if (today.isBefore(effectiveFrom)) {
            return "PENDING";
        }
        if (effectiveTo != null && today.isAfter(effectiveTo)) {
            return "EXPIRED";
        }
        return active ? "ON_SALE" : "INACTIVE";
    }

    private String describe(CapacityAddOnPricePeriod period) {
        return period.getPriceInfo().getSalePrice() + " " + period.getPriceInfo().getCurrencyCode()
                + " (" + period.getEffectiveFrom() + " ~ " + (period.getEffectiveTo() == null ? "무기한" : period.getEffectiveTo()) + ")";
    }

    private CapacityAddOnDto.Response.CapacityAddOnSummary toSummary(CapacityAddOn capacityAddOn) {
        Optional<CapacityAddOnPricePeriod> effective =
                capacityAddOnPricePeriodRepository.findEffective(capacityAddOn.getId(), LocalDate.now());
        return new CapacityAddOnDto.Response.CapacityAddOnSummary(
                capacityAddOn.getId(),
                capacityAddOn.getCapacityType().name(),
                capacityAddOn.getUnitAmount(),
                capacityAddOn.getSecondaryCapacityType() == null ? null : capacityAddOn.getSecondaryCapacityType().name(),
                capacityAddOn.getSecondaryUnitAmount(),
                effective.map(p -> p.getPriceInfo().getCurrencyCode()).orElse(null),
                effective.map(p -> p.getPriceInfo().getSupplyPrice()).orElse(null),
                effective.map(p -> p.getPriceInfo().getSalePrice()).orElse(null),
                effective.map(p -> p.getPriceInfo().getDiscount().getDiscountType().name()).orElse(null),
                effective.map(p -> p.getPriceInfo().getDiscount().getDiscountValue()).orElse(null),
                effective.map(p -> p.getPriceInfo().getTaxCode()).orElse(null),
                effective.map(CapacityAddOnPricePeriod::isActive).orElse(null),
                ceremonyCapacityPurchaseRepository.countByCapacityAddOnIdAndStatus(
                        capacityAddOn.getId(), PurchaseStatus.APPROVED
                ),
                capacityAddOn.getCreatedAt(),
                effective.map(CapacityAddOnPricePeriod::getEffectiveFrom).orElse(null),
                effective.map(CapacityAddOnPricePeriod::getEffectiveTo).orElse(null),
                effective.map(p -> computeStatus(p.isActive(), p.getEffectiveFrom(), p.getEffectiveTo())).orElse("NO_ACTIVE_PERIOD")
        );
    }

    private CapacityAddOnDto.Response.CapacityAddOnHistorySummary toHistorySummary(CapacityAddOnHistory history) {
        return new CapacityAddOnDto.Response.CapacityAddOnHistorySummary(
                history.getId(),
                history.getCapacityType().name(),
                history.getUnitAmount(),
                history.getSecondaryCapacityType() == null ? null : history.getSecondaryCapacityType().name(),
                history.getSecondaryUnitAmount(),
                history.getCreatedBy(),
                history.getCreatedAt()
        );
    }

    private CapacityAddOnDto.Response.CapacityAddOnPeriodSummary toPeriodSummary(CapacityAddOnPricePeriod period) {
        return new CapacityAddOnDto.Response.CapacityAddOnPeriodSummary(
                period.getId(),
                period.getPriceInfo().getCurrencyCode(),
                period.getPriceInfo().getSupplyPrice(),
                period.getPriceInfo().getSalePrice(),
                period.getPriceInfo().getDiscount().getDiscountType().name(),
                period.getPriceInfo().getDiscount().getDiscountValue(),
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
