package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityAddOn;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityType;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyAssignment;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyCapacityPurchase;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyOptionalFeaturePurchase;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeature;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanOptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CapacityAddOnRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyAssignmentRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyCapacityPurchaseRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyOptionalFeaturePurchaseRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.error.OrganizationErrorCode;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 행사 마스터(Ceremony). signstage-docs business/ceremony-feature-migration-review.md
 * 4.1/4.6/4.7절, business/ceremony-billing-options-review.md 4.10/4.11절 참고.
 *
 * <p>조직 스코핑은 JWT 클레임이 아니라 매 요청마다 organization_members를 직접 조회해
 * 판단한다(기존 {@code MemberService}와 같은 패턴). package-private 헬퍼 일부는
 * {@link CeremonyEventService}가 같은 패키지에서 공유한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CeremonyService {

    private final CeremonyRepository ceremonyRepository;
    private final CeremonyAssignmentRepository ceremonyAssignmentRepository;
    private final CeremonyCapacityPurchaseRepository ceremonyCapacityPurchaseRepository;
    private final CeremonyOptionalFeaturePurchaseRepository ceremonyOptionalFeaturePurchaseRepository;
    private final OrganizationRepository organizationRepository;
    private final MemberRepository memberRepository;
    private final BillingPlanRepository billingPlanRepository;
    private final BillingPlanOptionalFeatureRepository billingPlanOptionalFeatureRepository;
    private final CapacityAddOnRepository capacityAddOnRepository;
    private final OptionalFeatureRepository optionalFeatureRepository;
    private final UserRepository userRepository;

    @Transactional
    public CeremonyDto.Response.CeremonySummary createCeremony(
            Long organizationId,
            Long currentUserId,
            CeremonyDto.Request.CreateCeremony request
    ) {
        Organization organization = findOrganizationOrThrow(organizationId);
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCanCreateCeremony(actingMember);

        BillingPlan plan = billingPlanRepository.findById(request.getBillingPlanId())
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.BILLING_PLAN_NOT_FOUND));

        Ceremony ceremony = Ceremony.builder()
                .organization(organization)
                .billingPlan(plan)
                .title(request.getTitle())
                .build();
        ceremonyRepository.save(ceremony);

        // 생성자는 역할과 무관하게 자동으로 배정된다(4.7절) — 나중에 OPERATOR로 강등돼도
        // 본인이 만든 행사 접근권을 그대로 유지하는 부수 효과가 있다.
        User creator = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ApplicationException(CommonErrorCode.ACCESS_DENIED));
        ceremonyAssignmentRepository.save(
                CeremonyAssignment.builder().ceremony(ceremony).user(creator).build()
        );

        return toSummary(ceremony);
    }

    public List<CeremonyDto.Response.CeremonySummary> findCeremonies(Long organizationId, Long currentUserId) {
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);

        List<Ceremony> ceremonies;
        if (actingMember.getRole() == MemberRole.OPERATOR) {
            ceremonies = ceremonyAssignmentRepository.findAllByUserId(currentUserId).stream()
                    .map(CeremonyAssignment::getCeremony)
                    .filter(ceremony -> ceremony.getOrganization().getId().equals(organizationId))
                    .toList();
        } else {
            ceremonies = ceremonyRepository.findAllByOrganizationId(organizationId);
        }
        return ceremonies.stream().map(this::toSummary).toList();
    }

    public CeremonyDto.Response.CeremonySummary retrieveCeremony(Long organizationId, Long ceremonyId, Long currentUserId) {
        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCeremonyReadAccess(ceremony, actingMember, currentUserId);
        return toSummary(ceremony);
    }

    @Transactional
    public CeremonyDto.Response.CapacityPurchaseSummary purchaseCapacity(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId,
            CeremonyDto.Request.PurchaseCapacity request
    ) {
        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCeremonyManageAccess(ceremony, actingMember, currentUserId);

        CapacityAddOn addOn = capacityAddOnRepository.findById(request.getCapacityAddOnId())
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CAPACITY_ADDON_NOT_FOUND));

        CeremonyCapacityPurchase purchase = CeremonyCapacityPurchase.builder()
                .ceremony(ceremony)
                .capacityAddOn(addOn)
                .quantity(request.getQuantity())
                .purchasedSalePrice(addOn.getSalePrice())
                .purchasedDiscountType(addOn.getDiscountType())
                .purchasedDiscountValue(addOn.getDiscountValue())
                .build();
        ceremonyCapacityPurchaseRepository.save(purchase);

        return toCapacitySummary(purchase);
    }

    @Transactional
    public CeremonyDto.Response.OptionalFeaturePurchaseSummary purchaseOptionalFeature(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId,
            CeremonyDto.Request.PurchaseOptionalFeature request
    ) {
        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCeremonyManageAccess(ceremony, actingMember, currentUserId);

        OptionalFeature feature = optionalFeatureRepository.findById(request.getOptionalFeatureId())
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.OPTIONAL_FEATURE_NOT_FOUND));

        if (ceremonyOptionalFeaturePurchaseRepository.existsByCeremonyIdAndOptionalFeatureId(ceremonyId, feature.getId())) {
            throw new ApplicationException(CeremonyErrorCode.OPTIONAL_FEATURE_ALREADY_PURCHASED);
        }

        CeremonyOptionalFeaturePurchase purchase = CeremonyOptionalFeaturePurchase.builder()
                .ceremony(ceremony)
                .optionalFeature(feature)
                .purchasedSalePrice(feature.getSalePrice())
                .purchasedDiscountType(feature.getDiscountType())
                .purchasedDiscountValue(feature.getDiscountValue())
                .build();
        ceremonyOptionalFeaturePurchaseRepository.save(purchase);

        return toOptionalFeatureSummary(purchase);
    }

    // ---- CeremonyEventService와 공유하는 package-private 헬퍼 ----

    Ceremony findCeremonyInOrganizationOrThrow(Long organizationId, Long ceremonyId) {
        Ceremony ceremony = ceremonyRepository.findById(ceremonyId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CEREMONY_NOT_FOUND));
        if (!ceremony.getOrganization().getId().equals(organizationId)) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_NOT_FOUND);
        }
        return ceremony;
    }

    Member findActiveMemberOrThrow(Long organizationId, Long userId) {
        return memberRepository.findByOrganizationIdAndUserIdAndStatus(organizationId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApplicationException(CommonErrorCode.ACCESS_DENIED));
    }

    /** OWNER/ADMIN/VIEWER는 조직의 모든 행사를 조회할 수 있고, OPERATOR는 배정된 행사만 조회할 수 있다. */
    void checkCeremonyReadAccess(Ceremony ceremony, Member actingMember, Long currentUserId) {
        if (actingMember.getRole() == MemberRole.OPERATOR) {
            checkAssigned(ceremony, currentUserId);
        }
    }

    /**
     * 행사 생성/수정(용량·옵션 구매, 하위 행사 생성 등) 권한. OWNER/ADMIN은 항상 가능하고,
     * OPERATOR는 배정된 행사만, VIEWER는 불가하다(user-organization-design.md 4.2절
     * "행사(Ceremony) 생성/수정/삭제").
     */
    void checkCeremonyManageAccess(Ceremony ceremony, Member actingMember, Long currentUserId) {
        if (actingMember.getRole() == MemberRole.VIEWER) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
        if (actingMember.getRole() == MemberRole.OPERATOR) {
            checkAssigned(ceremony, currentUserId);
        }
    }

    /**
     * 필수옵션(용량) 유효 한도 = 플랜 기본값 + Σ(구매수량 × addon.unitAmount). 플랜이 없는
     * 행사(4.8 예외 — 이 기능 배포 전 기존 행사)는 한도 강제 자체를 적용하지 않는다(사실상 무제한).
     */
    int calculateEffectiveCapacity(Ceremony ceremony, CapacityType capacityType) {
        BillingPlan plan = ceremony.getBillingPlan();
        if (plan == null) {
            return Integer.MAX_VALUE;
        }

        int baseValue = switch (capacityType) {
            case SIGNERS -> plan.getMaxSigners();
            case TEMPLATES -> plan.getMaxTemplates();
            case TEST_EVENTS -> plan.getMaxTestEvents();
            case MAIN_EVENTS -> plan.getMaxMainEvents();
        };

        int purchasedAmount = ceremonyCapacityPurchaseRepository
                .findAllByCeremonyIdAndCapacityAddOn_CapacityType(ceremony.getId(), capacityType).stream()
                .mapToInt(purchase -> purchase.getQuantity() * purchase.getCapacityAddOn().getUnitAmount())
                .sum();

        return baseValue + purchasedAmount;
    }

    /** Ceremony가 "구매한"(플랜 기본 포함 또는 추가구매) 선택옵션 id 집합(4.11절). */
    List<Long> retrievePurchasedOptionalFeatureIds(Ceremony ceremony) {
        List<Long> purchased = ceremonyOptionalFeaturePurchaseRepository.findAllByCeremonyId(ceremony.getId()).stream()
                .map(purchase -> purchase.getOptionalFeature().getId())
                .toList();
        if (ceremony.getBillingPlan() == null) {
            return purchased;
        }
        List<Long> includedInPlan = billingPlanOptionalFeatureRepository
                .findAllByBillingPlanId(ceremony.getBillingPlan().getId()).stream()
                .map(mapping -> mapping.getOptionalFeature().getId())
                .toList();
        return Stream.concat(purchased.stream(), includedInPlan.stream()).distinct().toList();
    }

    private void checkAssigned(Ceremony ceremony, Long currentUserId) {
        if (!ceremonyAssignmentRepository.existsByCeremonyIdAndUserId(ceremony.getId(), currentUserId)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    private void checkCanCreateCeremony(Member actingMember) {
        if (actingMember.getRole() == MemberRole.VIEWER) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    private Organization findOrganizationOrThrow(Long organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApplicationException(OrganizationErrorCode.ORGANIZATION_NOT_FOUND));
    }

    private CeremonyDto.Response.CeremonySummary toSummary(Ceremony ceremony) {
        return new CeremonyDto.Response.CeremonySummary(
                ceremony.getId(),
                ceremony.getOrganization().getId(),
                ceremony.getBillingPlan() != null ? ceremony.getBillingPlan().getId() : null,
                ceremony.getTitle(),
                ceremony.getCreatedBy(),
                ceremony.getCreatedAt()
        );
    }

    private CeremonyDto.Response.CapacityPurchaseSummary toCapacitySummary(CeremonyCapacityPurchase purchase) {
        return new CeremonyDto.Response.CapacityPurchaseSummary(
                purchase.getId(),
                purchase.getCeremony().getId(),
                purchase.getCapacityAddOn().getId(),
                purchase.getQuantity(),
                purchase.getPurchasedSalePrice(),
                purchase.getPurchasedDiscountType().name(),
                purchase.getPurchasedDiscountValue(),
                purchase.getCreatedAt()
        );
    }

    private CeremonyDto.Response.OptionalFeaturePurchaseSummary toOptionalFeatureSummary(
            CeremonyOptionalFeaturePurchase purchase
    ) {
        return new CeremonyDto.Response.OptionalFeaturePurchaseSummary(
                purchase.getId(),
                purchase.getCeremony().getId(),
                purchase.getOptionalFeature().getId(),
                purchase.getPurchasedSalePrice(),
                purchase.getPurchasedDiscountType().name(),
                purchase.getPurchasedDiscountValue(),
                purchase.getCreatedAt()
        );
    }
}
