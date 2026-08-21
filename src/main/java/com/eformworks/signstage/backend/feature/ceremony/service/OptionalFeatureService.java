package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.OptionalFeatureDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.DiscountType;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeature;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeatureCode;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeatureHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyOptionalFeaturePurchaseRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OptionalFeatureHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 선택옵션 카탈로그(서명 하이라이트/폭죽/화상참석 등). signstage-docs
 * business/ceremony-billing-options-review.md 4.6/4.7절 참고. 등록은 플랫폼 관리자 전용,
 * 조회는 인증된 사용자 누구나 가능하다(행사 생성 화면에서 옵션을 고를 때 필요).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OptionalFeatureService {

    private static final Set<String> CATALOG_MANAGE_ALLOWED_ROLES = Set.of("PLATFORM_OPS", "PLATFORM_SUPER");

    private final OptionalFeatureRepository optionalFeatureRepository;
    private final OptionalFeatureHistoryRepository optionalFeatureHistoryRepository;
    private final CeremonyOptionalFeaturePurchaseRepository ceremonyOptionalFeaturePurchaseRepository;
    private final PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;

    @Transactional
    public OptionalFeatureDto.Response.OptionalFeatureSummary createOptionalFeature(
            String actingPlatformRole,
            Long adminUserId,
            OptionalFeatureDto.Request.CreateOptionalFeature request
    ) {
        if (!CATALOG_MANAGE_ALLOWED_ROLES.contains(actingPlatformRole)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }

        OptionalFeatureCode code = parseCode(request.getCode());
        if (optionalFeatureRepository.existsByCode(code)) {
            throw new ApplicationException(CeremonyErrorCode.OPTIONAL_FEATURE_CODE_DUPLICATE);
        }

        OptionalFeature optionalFeature = OptionalFeature.builder()
                .code(code)
                .name(request.getName())
                .supplyPrice(request.getSupplyPrice())
                .salePrice(request.getSalePrice())
                .discountType(parseDiscountType(request.getDiscountType()))
                .discountValue(request.getDiscountValue())
                .projectorEffect(request.getProjectorEffect())
                .exclusivityGroup(request.getExclusivityGroup())
                .build();
        optionalFeatureRepository.save(optionalFeature);
        recordFeatureHistory(optionalFeature);

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
        if (!CATALOG_MANAGE_ALLOWED_ROLES.contains(actingPlatformRole)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }

        OptionalFeature optionalFeature = optionalFeatureRepository.findById(optionalFeatureId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.OPTIONAL_FEATURE_NOT_FOUND));

        String detail = "optionalFeatureId=" + optionalFeatureId
                + ", salePrice: " + optionalFeature.getSalePrice() + " -> " + request.getSalePrice()
                + ", active: " + optionalFeature.isActive() + " -> " + request.getActive();

        optionalFeature.updateInfo(
                request.getName(),
                request.getSupplyPrice(),
                request.getSalePrice(),
                parseDiscountType(request.getDiscountType()),
                request.getDiscountValue(),
                request.getActive(),
                request.getProjectorEffect(),
                request.getExclusivityGroup()
        );
        recordFeatureHistory(optionalFeature);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_OPTIONAL_FEATURE, null, null, detail
        );

        return toSummary(optionalFeature);
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

    private OptionalFeatureDto.Response.OptionalFeatureSummary toSummary(OptionalFeature optionalFeature) {
        return new OptionalFeatureDto.Response.OptionalFeatureSummary(
                optionalFeature.getId(),
                optionalFeature.getCode().name(),
                optionalFeature.getName(),
                optionalFeature.getSupplyPrice(),
                optionalFeature.getSalePrice(),
                optionalFeature.getDiscountType().name(),
                optionalFeature.getDiscountValue(),
                optionalFeature.isActive(),
                optionalFeature.isProjectorEffect(),
                optionalFeature.getExclusivityGroup(),
                ceremonyOptionalFeaturePurchaseRepository.countByOptionalFeatureIdAndStatus(
                        optionalFeature.getId(), PurchaseStatus.APPROVED
                ),
                optionalFeature.getCreatedAt()
        );
    }

    private OptionalFeatureDto.Response.OptionalFeatureHistorySummary toHistorySummary(OptionalFeatureHistory history) {
        return new OptionalFeatureDto.Response.OptionalFeatureHistorySummary(
                history.getId(),
                history.getCode().name(),
                history.getName(),
                history.getSupplyPrice(),
                history.getSalePrice(),
                history.getDiscountType().name(),
                history.getDiscountValue(),
                history.isActive(),
                history.isProjectorEffect(),
                history.getExclusivityGroup(),
                history.getCreatedBy(),
                history.getCreatedAt()
        );
    }
}
