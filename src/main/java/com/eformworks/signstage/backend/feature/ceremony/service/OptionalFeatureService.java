package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.OptionalFeatureDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectDefinition;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectDefinitionOption;
import com.eformworks.signstage.backend.feature.ceremony.entity.DiscountType;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeature;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeatureCategory;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeatureCode;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeatureHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeaturePricePeriod;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeaturePricePeriodHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEffectDefinitionOptionRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEffectDefinitionRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyOptionalFeaturePurchaseRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OptionalFeatureHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OptionalFeaturePricePeriodHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OptionalFeaturePricePeriodRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OptionalFeatureRepository;
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
 * 선택옵션 카탈로그(이벤트 효과 묶음/화상참석 등). signstage-docs
 * business/ceremony-billing-options-review.md 4.6/4.7절 참고. 등록은 플랫폼 관리자 전용,
 * 조회는 인증된 사용자 누구나 가능하다(행사 생성 화면에서 옵션을 고를 때 필요).
 *
 * <p>{@code code='EVENT_EFFECT_BUNDLE'}은 {@code optional_features.code}의 UNIQUE 제약을
 * 받지 않는다(2026-09-08 결정, {@code capacity_addons.capacity_type}과 같은 패턴) — "3종",
 * "5종"처럼 관리자가 계속 새 묶음 상품을 만들 수 있어야 해서다. 이 코드일 때만
 * {@code effectDefinitionIds}로 묶음에 포함될 이벤트 효과를 지정한다.
 *
 * <p>가격정보/사용여부는 이 서비스가 {@link OptionalFeaturePricePeriod} 기간 단위 CRUD로 관리한다
 * (signstage-docs business/billing-catalog-price-validity-period-review.md 결정, 2026-09-09,
 * 다중버전 채택) — {@code BillingPlanService}의 기간 CRUD와 같은 패턴.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OptionalFeatureService {

    private final OptionalFeatureRepository optionalFeatureRepository;
    private final OptionalFeatureHistoryRepository optionalFeatureHistoryRepository;
    private final OptionalFeaturePricePeriodRepository optionalFeaturePricePeriodRepository;
    private final OptionalFeaturePricePeriodHistoryRepository optionalFeaturePricePeriodHistoryRepository;
    private final CeremonyOptionalFeaturePurchaseRepository ceremonyOptionalFeaturePurchaseRepository;
    private final CeremonyEffectDefinitionRepository ceremonyEffectDefinitionRepository;
    private final CeremonyEffectDefinitionOptionRepository ceremonyEffectDefinitionOptionRepository;
    private final PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;
    private final RolePermissionService rolePermissionService;

    @Transactional
    public OptionalFeatureDto.Response.OptionalFeatureSummary createOptionalFeature(
            String actingPlatformRole,
            Long adminUserId,
            OptionalFeatureDto.Request.CreateOptionalFeature request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");

        OptionalFeatureCode code = parseCode(request.getCode());
        if (code != OptionalFeatureCode.EVENT_EFFECT_BUNDLE && optionalFeatureRepository.existsByCode(code)) {
            throw new ApplicationException(CeremonyErrorCode.OPTIONAL_FEATURE_CODE_DUPLICATE);
        }
        checkEffectDefinitionIdsAllowed(code, request.getEffectDefinitionIds());
        checkPeriodValid(request.getEffectiveFrom(), request.getEffectiveTo());

        OptionalFeature optionalFeature = OptionalFeature.builder()
                .code(code)
                .name(request.getName())
                .exclusivityGroup(request.getExclusivityGroup())
                .category(parseCategory(request.getCategory()))
                .build();
        optionalFeatureRepository.save(optionalFeature);
        recordFeatureHistory(optionalFeature);

        OptionalFeaturePricePeriod period = OptionalFeaturePricePeriod.builder()
                .optionalFeature(optionalFeature)
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
        optionalFeaturePricePeriodRepository.save(period);
        recordPeriodHistory(optionalFeature, period, false);

        if (request.getEffectDefinitionIds() != null) {
            replaceEffectDefinitions(optionalFeature, request.getEffectDefinitionIds());
        }

        platformAdminAuditLogRecorder.record(
                adminUserId,
                PlatformAdminAction.CREATE_OPTIONAL_FEATURE,
                null,
                null,
                "optionalFeatureId=" + optionalFeature.getId() + ", code=" + optionalFeature.getCode()
        );

        return toSummary(optionalFeature);
    }

    @Transactional
    public OptionalFeatureDto.Response.OptionalFeatureSummary updateOptionalFeature(
            Long optionalFeatureId,
            String actingPlatformRole,
            Long adminUserId,
            OptionalFeatureDto.Request.UpdateOptionalFeature request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");

        OptionalFeature optionalFeature = optionalFeatureRepository.findById(optionalFeatureId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.OPTIONAL_FEATURE_NOT_FOUND));
        checkEffectDefinitionIdsAllowed(optionalFeature.getCode(), request.getEffectDefinitionIds());

        String detail = "optionalFeatureId=" + optionalFeatureId
                + ", name: " + optionalFeature.getName() + " -> " + request.getName();

        optionalFeature.updateInfo(request.getName(), request.getExclusivityGroup(), parseCategory(request.getCategory()));
        recordFeatureHistory(optionalFeature);
        if (request.getEffectDefinitionIds() != null) {
            replaceEffectDefinitions(optionalFeature, request.getEffectDefinitionIds());
        }

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_OPTIONAL_FEATURE, null, null, detail
        );

        return toSummary(optionalFeature);
    }

    /** 새 판매가격 기간을 추가한다. */
    @Transactional
    public OptionalFeatureDto.Response.OptionalFeaturePeriodSummary createPeriod(
            Long optionalFeatureId,
            String actingPlatformRole,
            Long adminUserId,
            OptionalFeatureDto.Request.CreatePeriod request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");
        OptionalFeature optionalFeature = optionalFeatureRepository.findById(optionalFeatureId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.OPTIONAL_FEATURE_NOT_FOUND));
        checkPeriodValid(request.getEffectiveFrom(), request.getEffectiveTo());
        checkNoOverlap(optionalFeatureId, null, request.getEffectiveFrom(), request.getEffectiveTo());

        OptionalFeaturePricePeriod period = OptionalFeaturePricePeriod.builder()
                .optionalFeature(optionalFeature)
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
        optionalFeaturePricePeriodRepository.save(period);
        recordPeriodHistory(optionalFeature, period, false);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_OPTIONAL_FEATURE, null, null,
                "optionalFeatureId=" + optionalFeatureId + ", 판매가격 기간 생성: " + describe(period)
        );
        return toPeriodSummary(period);
    }

    /** 이미 있는 판매가격 기간 하나를 고친다. */
    @Transactional
    public OptionalFeatureDto.Response.OptionalFeaturePeriodSummary updatePeriod(
            Long optionalFeatureId,
            Long periodId,
            String actingPlatformRole,
            Long adminUserId,
            OptionalFeatureDto.Request.UpdatePeriod request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");
        OptionalFeature optionalFeature = optionalFeatureRepository.findById(optionalFeatureId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.OPTIONAL_FEATURE_NOT_FOUND));
        OptionalFeaturePricePeriod period = optionalFeaturePricePeriodRepository
                .findByIdAndOptionalFeatureId(periodId, optionalFeatureId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CATALOG_PRICE_PERIOD_NOT_FOUND));
        checkPeriodValid(request.getEffectiveFrom(), request.getEffectiveTo());
        checkNoOverlap(optionalFeatureId, periodId, request.getEffectiveFrom(), request.getEffectiveTo());

        String previous = describe(period);
        period.update(
                request.getCurrencyCode(), request.getSupplyPrice(), request.getSalePrice(),
                parseDiscountType(request.getDiscountType()), request.getDiscountValue(), request.getTaxCode(),
                request.getActive(), request.getEffectiveFrom(), request.getEffectiveTo()
        );
        recordPeriodHistory(optionalFeature, period, false);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_OPTIONAL_FEATURE, null, null,
                "optionalFeatureId=" + optionalFeatureId + ", periodId=" + periodId + ", 판매가격 기간: "
                        + previous + " -> " + describe(period)
        );
        return toPeriodSummary(period);
    }

    /** 판매가격 기간을 제거한다 — 마지막 남은 기간 하나는 지울 수 없다. */
    @Transactional
    public void removePeriod(Long optionalFeatureId, Long periodId, String actingPlatformRole, Long adminUserId) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");
        optionalFeatureRepository.findById(optionalFeatureId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.OPTIONAL_FEATURE_NOT_FOUND));
        OptionalFeaturePricePeriod period = optionalFeaturePricePeriodRepository
                .findByIdAndOptionalFeatureId(periodId, optionalFeatureId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CATALOG_PRICE_PERIOD_NOT_FOUND));
        if (optionalFeaturePricePeriodRepository.countByOptionalFeatureId(optionalFeatureId) <= 1) {
            throw new ApplicationException(CeremonyErrorCode.CATALOG_PRICE_PERIOD_REQUIRED);
        }

        recordPeriodHistory(period.getOptionalFeature(), period, true);
        optionalFeaturePricePeriodRepository.delete(period);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_OPTIONAL_FEATURE, null, null,
                "optionalFeatureId=" + optionalFeatureId + ", periodId=" + periodId + ", 판매가격 기간 제거: " + describe(period)
        );
    }

    /** 이 선택옵션의 판매가격 기간 전체(과거/현재/예정) — 오래된 순. */
    public List<OptionalFeatureDto.Response.OptionalFeaturePeriodSummary> findFeaturePeriods(Long optionalFeatureId) {
        if (!optionalFeatureRepository.existsById(optionalFeatureId)) {
            throw new ApplicationException(CeremonyErrorCode.OPTIONAL_FEATURE_NOT_FOUND);
        }
        return optionalFeaturePricePeriodRepository.findAllByOptionalFeatureIdOrderByEffectiveFromAsc(optionalFeatureId).stream()
                .map(this::toPeriodSummary)
                .toList();
    }

    /** 판매가격 기간의 생성/수정/삭제 이력 — 최신순. */
    public List<OptionalFeatureDto.Response.OptionalFeaturePeriodHistorySummary> findFeaturePeriodHistory(Long optionalFeatureId) {
        if (!optionalFeatureRepository.existsById(optionalFeatureId)) {
            throw new ApplicationException(CeremonyErrorCode.OPTIONAL_FEATURE_NOT_FOUND);
        }
        return optionalFeaturePricePeriodHistoryRepository.findAllByOptionalFeatureIdOrderByCreatedAtDesc(optionalFeatureId).stream()
                .map(h -> new OptionalFeatureDto.Response.OptionalFeaturePeriodHistorySummary(
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

    /** {@code EVENT_EFFECT_BUNDLE}이 아닌 종류에 효과 목록을 지정하려는 요청을 막는다. */
    private void checkEffectDefinitionIdsAllowed(OptionalFeatureCode code, List<Long> effectDefinitionIds) {
        if (code != OptionalFeatureCode.EVENT_EFFECT_BUNDLE
                && effectDefinitionIds != null && !effectDefinitionIds.isEmpty()) {
            throw new ApplicationException(CeremonyErrorCode.OPTIONAL_FEATURE_EFFECT_BUNDLE_ONLY);
        }
    }

    /**
     * 이 묶음이 여는 이벤트 효과를 통째로 교체한다(delete-all-then-recreate,
     * {@code CeremonyEventEffectSettingService#applyEffectSelections}와 같은 패턴). 존재하지
     * 않는 효과 id가 섞여 있으면 전체를 거부한다(부분 성공 없음).
     */
    private void replaceEffectDefinitions(OptionalFeature optionalFeature, List<Long> effectDefinitionIds) {
        ceremonyEffectDefinitionOptionRepository.deleteAllByOptionalFeatureId(optionalFeature.getId());
        if (effectDefinitionIds.isEmpty()) {
            return;
        }

        List<CeremonyEffectDefinition> definitions = ceremonyEffectDefinitionRepository.findAllById(effectDefinitionIds);
        if (definitions.size() != effectDefinitionIds.size()) {
            throw new ApplicationException(CeremonyErrorCode.EFFECT_DEFINITION_NOT_FOUND);
        }

        definitions.forEach(definition -> ceremonyEffectDefinitionOptionRepository.save(
                CeremonyEffectDefinitionOption.builder()
                        .effectDefinition(definition)
                        .optionalFeature(optionalFeature)
                        .build()
        ));
    }

    public List<OptionalFeatureDto.Response.OptionalFeatureSummary> findOptionalFeatures() {
        return optionalFeatureRepository.findAll().stream()
                .map(this::toSummary)
                .toList();
    }

    /** 최신순 — 생성 시점 1건 + 이후 수정할 때마다 1건씩(이름/배타그룹/분류가 바뀔 때). */
    public List<OptionalFeatureDto.Response.OptionalFeatureHistorySummary> findFeatureHistory(Long optionalFeatureId) {
        if (!optionalFeatureRepository.existsById(optionalFeatureId)) {
            throw new ApplicationException(CeremonyErrorCode.OPTIONAL_FEATURE_NOT_FOUND);
        }
        return optionalFeatureHistoryRepository.findAllByOptionalFeatureIdOrderByCreatedAtDesc(optionalFeatureId).stream()
                .map(this::toHistorySummary)
                .toList();
    }

    /** 생성 시(최초 상태)와 {@link #updateOptionalFeature}에서 매 변경마다 호출한다. */
    private void recordFeatureHistory(OptionalFeature optionalFeature) {
        optionalFeatureHistoryRepository.save(OptionalFeatureHistory.builder().optionalFeature(optionalFeature).build());
    }

    /** 판매가격 기간 설정(생성/수정) 시점마다, 그리고 {@link #removePeriod}에서 제거 시점마다 호출한다. */
    private void recordPeriodHistory(OptionalFeature optionalFeature, OptionalFeaturePricePeriod period, boolean removed) {
        optionalFeaturePricePeriodHistoryRepository.save(
                OptionalFeaturePricePeriodHistory.builder().optionalFeature(optionalFeature).period(period).removed(removed).build()
        );
    }

    private OptionalFeatureCode parseCode(String code) {
        try {
            return OptionalFeatureCode.valueOf(code);
        } catch (IllegalArgumentException e) {
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

    private OptionalFeatureCategory parseCategory(String category) {
        try {
            return OptionalFeatureCategory.valueOf(category);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    private void checkPeriodValid(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new ApplicationException(CeremonyErrorCode.DISCOUNT_PERIOD_INVALID);
        }
    }

    private void checkNoOverlap(Long optionalFeatureId, Long excludePeriodId, LocalDate newFrom, LocalDate newTo) {
        boolean overlaps = optionalFeaturePricePeriodRepository
                .findAllByOptionalFeatureIdOrderByEffectiveFromAsc(optionalFeatureId).stream()
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

    private String describe(OptionalFeaturePricePeriod period) {
        return period.getPriceInfo().getSalePrice() + " " + period.getPriceInfo().getCurrencyCode()
                + " (" + period.getEffectiveFrom() + " ~ " + (period.getEffectiveTo() == null ? "무기한" : period.getEffectiveTo()) + ")";
    }

    private OptionalFeatureDto.Response.OptionalFeatureSummary toSummary(OptionalFeature optionalFeature) {
        Optional<OptionalFeaturePricePeriod> effective =
                optionalFeaturePricePeriodRepository.findEffective(optionalFeature.getId(), LocalDate.now());
        return new OptionalFeatureDto.Response.OptionalFeatureSummary(
                optionalFeature.getId(),
                optionalFeature.getCode().name(),
                optionalFeature.getName(),
                effective.map(p -> p.getPriceInfo().getCurrencyCode()).orElse(null),
                effective.map(p -> p.getPriceInfo().getSupplyPrice()).orElse(null),
                effective.map(p -> p.getPriceInfo().getSalePrice()).orElse(null),
                effective.map(p -> p.getPriceInfo().getDiscount().getDiscountType().name()).orElse(null),
                effective.map(p -> p.getPriceInfo().getDiscount().getDiscountValue()).orElse(null),
                effective.map(p -> p.getPriceInfo().getTaxCode()).orElse(null),
                effective.map(OptionalFeaturePricePeriod::isActive).orElse(null),
                optionalFeature.getExclusivityGroup(),
                optionalFeature.getCategory().name(),
                ceremonyOptionalFeaturePurchaseRepository.countByOptionalFeatureIdAndStatus(
                        optionalFeature.getId(), PurchaseStatus.APPROVED
                ),
                ceremonyEffectDefinitionOptionRepository.findAllByOptionalFeatureId(optionalFeature.getId()).stream()
                        .map(mapping -> mapping.getEffectDefinition().getId())
                        .toList(),
                optionalFeature.getCreatedAt(),
                effective.map(OptionalFeaturePricePeriod::getEffectiveFrom).orElse(null),
                effective.map(OptionalFeaturePricePeriod::getEffectiveTo).orElse(null),
                effective.map(p -> computeStatus(p.isActive(), p.getEffectiveFrom(), p.getEffectiveTo())).orElse("NO_ACTIVE_PERIOD")
        );
    }

    private OptionalFeatureDto.Response.OptionalFeatureHistorySummary toHistorySummary(OptionalFeatureHistory history) {
        return new OptionalFeatureDto.Response.OptionalFeatureHistorySummary(
                history.getId(),
                history.getCode().name(),
                history.getName(),
                history.getExclusivityGroup(),
                history.getCategory().name(),
                history.getCreatedBy(),
                history.getCreatedAt()
        );
    }

    private OptionalFeatureDto.Response.OptionalFeaturePeriodSummary toPeriodSummary(OptionalFeaturePricePeriod period) {
        return new OptionalFeatureDto.Response.OptionalFeaturePeriodSummary(
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
