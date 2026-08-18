package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.CapacityAddOnDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityAddOn;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityType;
import com.eformworks.signstage.backend.feature.ceremony.entity.DiscountType;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CapacityAddOnRepository;
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

        CapacityAddOn capacityAddOn = CapacityAddOn.builder()
                .capacityType(parseCapacityType(request.getCapacityType()))
                .unitAmount(request.getUnitAmount())
                .supplyPrice(request.getSupplyPrice())
                .salePrice(request.getSalePrice())
                .discountType(parseDiscountType(request.getDiscountType()))
                .discountValue(request.getDiscountValue())
                .build();
        capacityAddOnRepository.save(capacityAddOn);

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
                + ", salePrice: " + capacityAddOn.getSalePrice() + " -> " + request.getSalePrice();

        capacityAddOn.updateInfo(
                request.getUnitAmount(),
                request.getSupplyPrice(),
                request.getSalePrice(),
                parseDiscountType(request.getDiscountType()),
                request.getDiscountValue()
        );

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

    private CapacityType parseCapacityType(String capacityType) {
        try {
            return CapacityType.valueOf(capacityType);
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

    private CapacityAddOnDto.Response.CapacityAddOnSummary toSummary(CapacityAddOn capacityAddOn) {
        return new CapacityAddOnDto.Response.CapacityAddOnSummary(
                capacityAddOn.getId(),
                capacityAddOn.getCapacityType().name(),
                capacityAddOn.getUnitAmount(),
                capacityAddOn.getSupplyPrice(),
                capacityAddOn.getSalePrice(),
                capacityAddOn.getDiscountType().name(),
                capacityAddOn.getDiscountValue(),
                capacityAddOn.getCreatedAt()
        );
    }
}
