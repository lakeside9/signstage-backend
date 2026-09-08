package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.OptionalFeatureDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityType;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectDefinition;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectDefinitionOption;
import com.eformworks.signstage.backend.feature.ceremony.entity.DiscountType;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeature;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeatureCategory;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeatureCode;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeatureHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEffectDefinitionOptionRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEffectDefinitionRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyOptionalFeaturePurchaseRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OptionalFeatureHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import java.util.List;
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
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OptionalFeatureService {

    private final OptionalFeatureRepository optionalFeatureRepository;
    private final OptionalFeatureHistoryRepository optionalFeatureHistoryRepository;
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

        OptionalFeature optionalFeature = OptionalFeature.builder()
                .code(code)
                .name(request.getName())
                .currencyCode(request.getCurrencyCode())
                .supplyPrice(request.getSupplyPrice())
                .salePrice(request.getSalePrice())
                .discountType(parseDiscountType(request.getDiscountType()))
                .discountValue(request.getDiscountValue())
                .taxCode(request.getTaxCode())
                .projectorEffect(request.getProjectorEffect())
                .exclusivityGroup(request.getExclusivityGroup())
                .category(parseCategory(request.getCategory()))
                .pairedCapacityType(parseOptionalCapacityType(request.getPairedCapacityType()))
                .build();
        optionalFeatureRepository.save(optionalFeature);
        recordFeatureHistory(optionalFeature);
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
                + ", salePrice: " + optionalFeature.getPriceInfo().getSalePrice() + " -> " + request.getSalePrice()
                + ", active: " + optionalFeature.isActive() + " -> " + request.getActive();

        optionalFeature.updateInfo(
                request.getName(),
                request.getCurrencyCode(),
                request.getSupplyPrice(),
                request.getSalePrice(),
                parseDiscountType(request.getDiscountType()),
                request.getDiscountValue(),
                request.getTaxCode(),
                request.getActive(),
                request.getProjectorEffect(),
                request.getExclusivityGroup(),
                parseCategory(request.getCategory()),
                parseOptionalCapacityType(request.getPairedCapacityType())
        );
        recordFeatureHistory(optionalFeature);
        if (request.getEffectDefinitionIds() != null) {
            replaceEffectDefinitions(optionalFeature, request.getEffectDefinitionIds());
        }

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_OPTIONAL_FEATURE, null, null, detail
        );

        return toSummary(optionalFeature);
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

    /** 최신순 — 생성 시점 1건 + 이후 수정할 때마다 1건씩(값 또는 사용여부가 바뀔 때). */
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

    /**
     * 짝이 되는 용량 추가구매 종류 — 완결형 상품이면(요청에 생략) null. 짝이 되는
     * {@code CapacityAddOn}이 아직 카탈로그에 없어도 저장은 막지 않는다(경고만, signstage-docs
     * business/optional-feature-capacity-addon-pairing-review.md 결정 #3) — 그 경고는
     * 프런트가 이미 불러온 용량 추가구매 목록과 대조해 표시한다.
     */
    private CapacityType parseOptionalCapacityType(String pairedCapacityType) {
        if (pairedCapacityType == null || pairedCapacityType.isBlank()) {
            return null;
        }
        try {
            return CapacityType.valueOf(pairedCapacityType);
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    private OptionalFeatureDto.Response.OptionalFeatureSummary toSummary(OptionalFeature optionalFeature) {
        return new OptionalFeatureDto.Response.OptionalFeatureSummary(
                optionalFeature.getId(),
                optionalFeature.getCode().name(),
                optionalFeature.getName(),
                optionalFeature.getPriceInfo().getCurrencyCode(),
                optionalFeature.getPriceInfo().getSupplyPrice(),
                optionalFeature.getPriceInfo().getSalePrice(),
                optionalFeature.getPriceInfo().getDiscount().getDiscountType().name(),
                optionalFeature.getPriceInfo().getDiscount().getDiscountValue(),
                optionalFeature.getPriceInfo().getTaxCode(),
                optionalFeature.isActive(),
                optionalFeature.isProjectorEffect(),
                optionalFeature.getExclusivityGroup(),
                optionalFeature.getCategory().name(),
                optionalFeature.getPairedCapacityType() == null ? null : optionalFeature.getPairedCapacityType().name(),
                ceremonyOptionalFeaturePurchaseRepository.countByOptionalFeatureIdAndStatus(
                        optionalFeature.getId(), PurchaseStatus.APPROVED
                ),
                ceremonyEffectDefinitionOptionRepository.findAllByOptionalFeatureId(optionalFeature.getId()).stream()
                        .map(mapping -> mapping.getEffectDefinition().getId())
                        .toList(),
                optionalFeature.getCreatedAt()
        );
    }

    private OptionalFeatureDto.Response.OptionalFeatureHistorySummary toHistorySummary(OptionalFeatureHistory history) {
        return new OptionalFeatureDto.Response.OptionalFeatureHistorySummary(
                history.getId(),
                history.getCode().name(),
                history.getName(),
                history.getPriceInfo().getCurrencyCode(),
                history.getPriceInfo().getSupplyPrice(),
                history.getPriceInfo().getSalePrice(),
                history.getPriceInfo().getDiscount().getDiscountType().name(),
                history.getPriceInfo().getDiscount().getDiscountValue(),
                history.getPriceInfo().getTaxCode(),
                history.isActive(),
                history.isProjectorEffect(),
                history.getExclusivityGroup(),
                history.getCategory().name(),
                history.getPairedCapacityType() == null ? null : history.getPairedCapacityType().name(),
                history.getCreatedBy(),
                history.getCreatedAt()
        );
    }

    /** signstage-docs business/menu-and-action-permission-management-review.md 10장 참고. */
    private void checkAllowed(String actingPlatformRole, String permissionKey) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, permissionKey)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }
}
