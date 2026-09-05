package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.CapacityAddOnDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityAddOn;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityAddOnHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityType;
import com.eformworks.signstage.backend.feature.ceremony.entity.DiscountType;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CapacityAddOnHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CapacityAddOnRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyCapacityPurchaseRepository;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 필수옵션(용량 한도) 추가구매 상품 카탈로그(예: "서명자 +10명"). signstage-docs
 * business/ceremony-billing-options-review.md 4.7/4.9절 참고. 등록은 플랫폼 관리자 전용,
 * 조회는 인증된 사용자 누구나 가능하다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CapacityAddOnService {

    private static final Set<String> CATALOG_MANAGE_ALLOWED_ROLES = Set.of("PLATFORM_OPS", "PLATFORM_SUPER");

    private final CapacityAddOnRepository capacityAddOnRepository;
    private final CapacityAddOnHistoryRepository capacityAddOnHistoryRepository;
    private final CeremonyCapacityPurchaseRepository ceremonyCapacityPurchaseRepository;
    private final PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;

    @Transactional
    public CapacityAddOnDto.Response.CapacityAddOnSummary createCapacityAddOn(
            String actingPlatformRole,
            Long adminUserId,
            CapacityAddOnDto.Request.CreateCapacityAddOn request
    ) {
        if (!CATALOG_MANAGE_ALLOWED_ROLES.contains(actingPlatformRole)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }

        CapacityType capacityType = parseCapacityType(request.getCapacityType());
        CapacityType secondaryCapacityType = parseOptionalCapacityType(request.getSecondaryCapacityType());
        checkSecondaryCapacityValid(capacityType, secondaryCapacityType, request.getSecondaryUnitAmount());

        CapacityAddOn capacityAddOn = CapacityAddOn.builder()
                .capacityType(capacityType)
                .unitAmount(request.getUnitAmount())
                .secondaryCapacityType(secondaryCapacityType)
                .secondaryUnitAmount(secondaryCapacityType == null ? null : request.getSecondaryUnitAmount())
                .currencyCode(request.getCurrencyCode())
                .supplyPrice(request.getSupplyPrice())
                .salePrice(request.getSalePrice())
                .discountType(parseDiscountType(request.getDiscountType()))
                .discountValue(request.getDiscountValue())
                .taxCode(request.getTaxCode())
                .build();
        capacityAddOnRepository.save(capacityAddOn);
        recordAddOnHistory(capacityAddOn);

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
        if (!CATALOG_MANAGE_ALLOWED_ROLES.contains(actingPlatformRole)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }

        CapacityAddOn capacityAddOn = capacityAddOnRepository.findById(capacityAddOnId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CAPACITY_ADDON_NOT_FOUND));

        String detail = "capacityAddOnId=" + capacityAddOnId
                + ", salePrice: " + capacityAddOn.getSalePrice() + " -> " + request.getSalePrice()
                + ", active: " + capacityAddOn.isActive() + " -> " + request.getActive();

        // secondaryCapacityType은 생성 후 불변이라 수정 요청에 없다 — 원래 묶음 상품이 아니었으면
        // (secondaryCapacityType == null) 보조 수량 입력은 조용히 무시한다(묶음으로 바꾸려면 새
        // 상품을 만들어야 한다). 원래 묶음 상품이었다면 보조 수량은 계속 필수다.
        if (capacityAddOn.getSecondaryCapacityType() != null && request.getSecondaryUnitAmount() == null) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
        Integer secondaryUnitAmount = capacityAddOn.getSecondaryCapacityType() == null
                ? null
                : request.getSecondaryUnitAmount();

        capacityAddOn.updateInfo(
                request.getUnitAmount(),
                secondaryUnitAmount,
                request.getCurrencyCode(),
                request.getSupplyPrice(),
                request.getSalePrice(),
                parseDiscountType(request.getDiscountType()),
                request.getDiscountValue(),
                request.getTaxCode(),
                request.getActive()
        );
        recordAddOnHistory(capacityAddOn);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_CAPACITY_ADDON, null, null, detail
        );

        return toSummary(capacityAddOn);
    }

    public List<CapacityAddOnDto.Response.CapacityAddOnSummary> findCapacityAddOns() {
        return capacityAddOnRepository.findAll().stream()
                .map(this::toSummary)
                .toList();
    }

    /** 최신순 — 생성 시점 1건 + 이후 수정할 때마다 1건씩(값 또는 사용여부가 바뀔 때). */
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

    private CapacityAddOnDto.Response.CapacityAddOnSummary toSummary(CapacityAddOn capacityAddOn) {
        return new CapacityAddOnDto.Response.CapacityAddOnSummary(
                capacityAddOn.getId(),
                capacityAddOn.getCapacityType().name(),
                capacityAddOn.getUnitAmount(),
                capacityAddOn.getSecondaryCapacityType() == null ? null : capacityAddOn.getSecondaryCapacityType().name(),
                capacityAddOn.getSecondaryUnitAmount(),
                capacityAddOn.getCurrencyCode(),
                capacityAddOn.getSupplyPrice(),
                capacityAddOn.getSalePrice(),
                capacityAddOn.getDiscountType().name(),
                capacityAddOn.getDiscountValue(),
                capacityAddOn.getTaxCode(),
                capacityAddOn.isActive(),
                ceremonyCapacityPurchaseRepository.countByCapacityAddOnIdAndStatus(
                        capacityAddOn.getId(), PurchaseStatus.APPROVED
                ),
                capacityAddOn.getCreatedAt()
        );
    }

    private CapacityAddOnDto.Response.CapacityAddOnHistorySummary toHistorySummary(CapacityAddOnHistory history) {
        return new CapacityAddOnDto.Response.CapacityAddOnHistorySummary(
                history.getId(),
                history.getCapacityType().name(),
                history.getUnitAmount(),
                history.getSecondaryCapacityType() == null ? null : history.getSecondaryCapacityType().name(),
                history.getSecondaryUnitAmount(),
                history.getCurrencyCode(),
                history.getSupplyPrice(),
                history.getSalePrice(),
                history.getDiscountType().name(),
                history.getDiscountValue(),
                history.getTaxCode(),
                history.isActive(),
                history.getCreatedBy(),
                history.getCreatedAt()
        );
    }
}
