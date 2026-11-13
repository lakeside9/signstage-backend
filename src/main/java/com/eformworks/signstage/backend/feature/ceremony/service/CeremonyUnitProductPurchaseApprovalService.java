package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.audit.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyDto;
import com.eformworks.signstage.backend.feature.ceremony.dto.PlatformAdminCeremonyPurchaseDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEvent;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventEffectSetting;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventOptionalFeature;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductPurchase;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductPurchaseLine;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEffectDefinitionOptionRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventEffectSettingRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventOptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyUnitProductPurchaseLineRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyUnitProductPurchaseRepository;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플랫폼 관리자의 단위 상품 추가구매 승인 대기열 — 조회·승인·반려·승인 취소. 승인 취소된
 * 이벤트 효과 묶음은 아직 시작하지 않은 이벤트에서 적용을 해제한다. 2026-11-11
 * {@code CeremonyService}에서 분리했다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CeremonyUnitProductPurchaseApprovalService {

    private final CeremonyUnitProductPurchaseRepository ceremonyUnitProductPurchaseRepository;
    private final CeremonyUnitProductPurchaseLineRepository ceremonyUnitProductPurchaseLineRepository;
    private final CeremonyEffectDefinitionOptionRepository ceremonyEffectDefinitionOptionRepository;
    private final CeremonyEventRepository ceremonyEventRepository;
    private final CeremonyEventOptionalFeatureRepository ceremonyEventOptionalFeatureRepository;
    private final CeremonyEventEffectSettingRepository ceremonyEventEffectSettingRepository;
    private final UserRepository userRepository;
    private final PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;
    private final RolePermissionService rolePermissionService;

    /**
     * status를 생략하면(null) 전체 상태를 최신순으로 돌려준다(조직 생성 요청 목록과 같은 규약).
     * organizationId/ceremonyId도 선택 필터다 — 기존 "승인 큐"(둘 다 null, status=PENDING
     * 기본)와 신규 "행사 이력" 화면(ceremonyId로 좁힘, signstage-docs
     * business/unit-product-purchase-self-checkout-review.md 8.6절 결정, 2026-09-11)이 이
     * 메서드를 같이 쓴다. requesterKeyword/ceremonyTitle도 선택 필터다(2026-09-12 사용자
     * 요청 추가 — 요청자/행사명 검색).
     */
    public Page<PlatformAdminCeremonyPurchaseDto.Response.UnitProductPurchaseRequestSummary> findUnitProductPurchaseRequests(
            PurchaseStatus status,
            Long organizationId,
            Long ceremonyId,
            String requesterKeyword,
            String ceremonyTitle,
            Pageable pageable
    ) {
        Page<CeremonyUnitProductPurchase> purchases =
                ceremonyUnitProductPurchaseRepository.search(status, organizationId, ceremonyId, requesterKeyword, ceremonyTitle, pageable);
        Map<Long, User> usersByUserId = resolveUsers(
                purchases.getContent().stream()
                        .flatMap(purchase -> Stream.of(purchase.getCreatedBy(), purchase.getReviewedBy(), purchase.getCancelledBy()))
        );
        return purchases.map(purchase -> toUnitProductRequestSummary(purchase, usersByUserId));
    }

    @Transactional
    public PlatformAdminCeremonyPurchaseDto.Response.UnitProductPurchaseRequestSummary approveUnitProductPurchase(
            Long purchaseId,
            Long adminUserId,
            String actingPlatformRole
    ) {
        checkAllowed(actingPlatformRole, "ACTION_PURCHASE_APPROVAL");
        CeremonyUnitProductPurchase purchase = findPendingUnitProductPurchaseOrThrow(purchaseId);
        purchase.approve(adminUserId);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.APPROVE_UNIT_PRODUCT_PURCHASE, null,
                purchase.getCeremony().getOrganization().getId(),
                "purchaseId=" + purchaseId + ", ceremonyId=" + purchase.getCeremony().getId()
        );
        return toUnitProductRequestSummary(purchase, resolveUsers(Stream.of(purchase.getCreatedBy(), purchase.getReviewedBy(), purchase.getCancelledBy())));
    }

    @Transactional
    public PlatformAdminCeremonyPurchaseDto.Response.UnitProductPurchaseRequestSummary rejectUnitProductPurchase(
            Long purchaseId,
            Long adminUserId,
            String actingPlatformRole,
            PlatformAdminCeremonyPurchaseDto.Request.Reject request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_PURCHASE_APPROVAL");
        CeremonyUnitProductPurchase purchase = findPendingUnitProductPurchaseOrThrow(purchaseId);
        purchase.reject(adminUserId, request.getRejectionReason());

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.REJECT_UNIT_PRODUCT_PURCHASE, null,
                purchase.getCeremony().getOrganization().getId(),
                "purchaseId=" + purchaseId + ", reason=" + request.getRejectionReason()
        );
        return toUnitProductRequestSummary(purchase, resolveUsers(Stream.of(purchase.getCreatedBy(), purchase.getReviewedBy(), purchase.getCancelledBy())));
    }

    /**
     * 이미 승인(APPROVED)된 구매를 취소한다 — signstage-docs
     * business/ceremony-unit-product-purchase-cancellation-review.md 결정(2026-09-12).
     * 반려와 대칭이다(사유 필수, 같은 권한). 이벤트 효과 묶음이 포함돼 있으면
     * {@link #unapplyEventEffectBundlesIfCancelled}가 하이브리드 원칙(3.6절)으로 자동
     * 해제한다.
     */
    @Transactional
    public PlatformAdminCeremonyPurchaseDto.Response.UnitProductPurchaseRequestSummary cancelUnitProductPurchase(
            Long purchaseId,
            Long adminUserId,
            String actingPlatformRole,
            PlatformAdminCeremonyPurchaseDto.Request.Cancel request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_PURCHASE_APPROVAL");
        CeremonyUnitProductPurchase purchase = findApprovedUnitProductPurchaseOrThrow(purchaseId);
        // 플랫폼 수주 행사의 구매는 수주·발주 범위를 옮긴 0원 기록이다 — 여기서 취소하면 행사 한도만 줄고 수주·발주와 어긋난다(D08).
        if (purchase.getCeremony().isPlatformOrder()) {
            throw new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_PURCHASE_PLATFORM_ORDER_NOT_CANCELLABLE);
        }
        purchase.cancel(adminUserId, request.getCancellationReason());
        unapplyEventEffectBundlesIfCancelled(purchase);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.CANCEL_UNIT_PRODUCT_PURCHASE, null,
                purchase.getCeremony().getOrganization().getId(),
                "purchaseId=" + purchaseId + ", reason=" + request.getCancellationReason()
        );
        return toUnitProductRequestSummary(purchase, resolveUsers(Stream.of(purchase.getCreatedBy(), purchase.getReviewedBy(), purchase.getCancelledBy())));
    }

    private CeremonyUnitProductPurchase findPendingUnitProductPurchaseOrThrow(Long purchaseId) {
        CeremonyUnitProductPurchase purchase = ceremonyUnitProductPurchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_PURCHASE_NOT_FOUND));
        if (purchase.getStatus() != PurchaseStatus.PENDING) {
            throw new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_PURCHASE_NOT_PENDING);
        }
        return purchase;
    }

    private CeremonyUnitProductPurchase findApprovedUnitProductPurchaseOrThrow(Long purchaseId) {
        CeremonyUnitProductPurchase purchase = ceremonyUnitProductPurchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_PURCHASE_NOT_FOUND));
        if (purchase.getStatus() != PurchaseStatus.APPROVED) {
            throw new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_PURCHASE_NOT_APPROVED);
        }
        return purchase;
    }

    /**
     * 취소된 구매에 이벤트 효과 묶음(EVENT_EFFECT_BUNDLE)이 있으면, 그 효과가 적용된 하위
     * 행사 중 {@code STARTED}가 아닌 것만 자동으로 해제한다 — 하이브리드안(signstage-docs
     * business/ceremony-unit-product-purchase-cancellation-review.md 3.6절/5장 결정 1,
     * 2026-09-12). {@code STARTED}(서명 진행 중)인 하위 행사는 그대로 두고, 관리자가 화면의
     * 하위 행사 상태 표시로 직접 인지하고 처리하게 한다.
     */
    private void unapplyEventEffectBundlesIfCancelled(CeremonyUnitProductPurchase purchase) {
        List<Long> cancelledBundleIds = ceremonyUnitProductPurchaseLineRepository
                .findAllByPurchaseIdOrderByIdAsc(purchase.getId()).stream()
                .map(CeremonyUnitProductPurchaseLine::getUnitProduct)
                .filter(unitProduct -> unitProduct.getType() == UnitProductType.EVENT_EFFECT_BUNDLE)
                .map(UnitProduct::getId)
                .toList();
        if (cancelledBundleIds.isEmpty()) {
            return;
        }

        List<CeremonyEvent> events = ceremonyEventRepository
                .findAllByCeremonyIdOrderByDisplayOrderAscIdAsc(purchase.getCeremony().getId());
        for (CeremonyEvent event : events) {
            if (event.getStatus() == CeremonyEventStatus.STARTED) {
                continue;
            }
            List<CeremonyEventOptionalFeature> mappings = ceremonyEventOptionalFeatureRepository
                    .findAllByCeremonyEventId(event.getId());
            List<CeremonyEventOptionalFeature> toRemove = mappings.stream()
                    .filter(mapping -> cancelledBundleIds.contains(mapping.getUnitProduct().getId()))
                    .toList();
            if (toRemove.isEmpty()) {
                continue;
            }
            ceremonyEventOptionalFeatureRepository.deleteAll(toRemove);
            List<Long> remainingUnitProductIds = mappings.stream()
                    .map(mapping -> mapping.getUnitProduct().getId())
                    .filter(id -> !cancelledBundleIds.contains(id))
                    .toList();
            pruneEffectSettingsRequiringUnappliedFeatures(event.getId(), remainingUnitProductIds);
        }
    }

    /**
     * {@code CeremonyEventEffectSettingService#pruneSettingsRequiringUnappliedFeatures}와
     * 같은 로직이다 — 그 서비스는 이미 {@code CeremonyService}에 의존해(순환 의존 위험) 여기서
     * 직접 호출할 수 없어, 같은 두 리포지토리로 동일한 판정을 다시 구현했다.
     */
    private void pruneEffectSettingsRequiringUnappliedFeatures(Long eventId, List<Long> appliedUnitProductIds) {
        for (CeremonyEventEffectSetting setting : ceremonyEventEffectSettingRepository.findAllByEventIdWithDefinition(eventId)) {
            if (!ceremonyEffectDefinitionOptionRepository.existsByEffectDefinitionIdAndUnitProductIdIn(
                    setting.getDefinition().getId(), appliedUnitProductIds
            )) {
                ceremonyEventEffectSettingRepository.delete(setting);
            }
        }
    }

    /**
     * loginId만 돌려주던 {@code resolveUserLoginIds}를 일반화했다(2026-09-12) — 요청자
     * "명"(name)도 보여달라는 요청으로 {@code User} 객체 자체를 돌려주고, 호출부가 필요한
     * 필드(loginId/name)를 그때그때 꺼내 쓴다.
     */
    private Map<Long, User> resolveUsers(Stream<Long> userIds) {
        List<Long> ids = userIds.filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private PlatformAdminCeremonyPurchaseDto.Response.UnitProductPurchaseRequestSummary toUnitProductRequestSummary(
            CeremonyUnitProductPurchase purchase,
            Map<Long, User> usersByUserId
    ) {
        List<CeremonyDto.Response.UnitProductPurchaseLineSummary> lines = ceremonyUnitProductPurchaseLineRepository
                .findAllByPurchaseIdOrderByIdAsc(purchase.getId()).stream()
                .map(CeremonyDto.Response.UnitProductPurchaseLineSummary::from)
                .toList();
        List<PlatformAdminCeremonyPurchaseDto.Response.CeremonyEventStatusSummary> ceremonyEvents =
                ceremonyEventRepository.findAllByCeremonyIdOrderByDisplayOrderAscIdAsc(purchase.getCeremony().getId()).stream()
                        .map(event -> new PlatformAdminCeremonyPurchaseDto.Response.CeremonyEventStatusSummary(
                                event.getEventType().name(), event.getName(), event.getStatus().name(),
                                event.getScheduledStartAt(), event.getActualStartAt()
                        ))
                        .toList();
        User requester = usersByUserId.get(purchase.getCreatedBy());
        User reviewer = purchase.getReviewedBy() != null ? usersByUserId.get(purchase.getReviewedBy()) : null;
        User canceller = purchase.getCancelledBy() != null ? usersByUserId.get(purchase.getCancelledBy()) : null;
        return new PlatformAdminCeremonyPurchaseDto.Response.UnitProductPurchaseRequestSummary(
                purchase.getId(),
                purchase.getCreatedBy(),
                requester != null ? requester.getLoginId() : null,
                // 요청자 "명"(name)도 보여달라는 요청(2026-09-12) — loginId와 별개 필드다.
                requester != null ? requester.getName() : null,
                purchase.getCeremony().getOrganization().getId(),
                purchase.getCeremony().getId(),
                purchase.getCeremony().getTitle(),
                lines,
                purchase.getStatus().name(),
                purchase.getRejectionReason(),
                reviewer != null ? reviewer.getLoginId() : null,
                purchase.getReviewedAt(),
                canceller != null ? canceller.getLoginId() : null,
                purchase.getCancelledAt(),
                purchase.getCancellationReason(),
                ceremonyEvents,
                purchase.getCreatedAt(),
                purchase.getCeremony().isPlatformOrder()
        );
    }

    /**
     * 플랫폼 관리자 축(PLATFORM_SUPPORT/PLATFORM_OPS/PLATFORM_SUPER) 액션 권한 검사 —
     * {@code BillingPlanService#checkAllowed}와 같은 패턴. signstage-docs
     * business/organization-discount-override-security-and-validity-period-review.md 결정
     * #3(2026-09-08) — 행사 상태 강제 변경/행사 건별 재량 할인/구매요청 승인·반려를 하드코딩
     * 역할집합에서 동적 RBAC로 이관했다.
     */
    private void checkAllowed(String actingPlatformRole, String permissionKey) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, permissionKey)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }
}
