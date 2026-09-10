package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.core.money.CurrencyPolicy;
import com.eformworks.signstage.backend.core.money.MoneyCalculator;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyDto;
import com.eformworks.signstage.backend.feature.ceremony.dto.UnitProductDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanDiscountPeriod;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanUnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyAssignment;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistoryUnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductPurchase;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductPurchaseLine;
import com.eformworks.signstage.backend.feature.ceremony.entity.DiscountType;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductPricePeriod;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanDiscountPeriodRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanUnitProductRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyAssignmentRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEffectDefinitionOptionRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryUnitProductRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyUnitProductPurchaseLineRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyUnitProductPurchaseRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.UnitProductPricePeriodRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.UnitProductRepository;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.error.OrganizationErrorCode;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminCeremonyDiscountDto;
import com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminCeremonyPurchaseDto;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 행사 마스터(Ceremony). signstage-docs business/ceremony-feature-migration-review.md
 * 4.1/4.6/4.7절, business/billing-catalog-unit-product-model-redesign-review.md(2026-09-10,
 * 2단계) 참고.
 *
 * <p>조직 스코핑은 JWT 클레임이 아니라 매 요청마다 organization_members를 직접 조회해
 * 판단한다(기존 {@code MemberService}와 같은 패턴). package-private 헬퍼 일부는
 * {@link CeremonyEventService}가 같은 패키지에서 공유한다.
 *
 * <p>과금 관련 로직은 2단계 재설계로 전면 재작성됐다 — 옛 {@code CapacityAddOn}/
 * {@code OptionalFeature} 카탈로그와 {@code CeremonyCapacityPurchase}/
 * {@code CeremonyOptionalFeaturePurchase} 2종 구매를 {@link UnitProduct} 하나와
 * {@link CeremonyUnitProductPurchase}(+Line) 장바구니형 구매로 통합했다. 플랜은 더 이상 자기
 * 가격을 갖지 않고, "오늘 가격"은 {@link BillingPlanUnitProduct} 구성으로부터 조회 시점에
 * 계산된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CeremonyService {

    private final CeremonyRepository ceremonyRepository;
    private final CeremonyAssignmentRepository ceremonyAssignmentRepository;
    private final CeremonyUnitProductPurchaseRepository ceremonyUnitProductPurchaseRepository;
    private final CeremonyUnitProductPurchaseLineRepository ceremonyUnitProductPurchaseLineRepository;
    private final CeremonyEffectDefinitionOptionRepository ceremonyEffectDefinitionOptionRepository;
    private final CeremonyPlanHistoryRepository ceremonyPlanHistoryRepository;
    private final CeremonyPlanHistoryUnitProductRepository ceremonyPlanHistoryUnitProductRepository;
    private final BillingPlanUnitProductRepository billingPlanUnitProductRepository;
    private final OrganizationRepository organizationRepository;
    private final MemberRepository memberRepository;
    private final BillingPlanRepository billingPlanRepository;
    private final BillingPlanDiscountPeriodRepository billingPlanDiscountPeriodRepository;
    private final UnitProductRepository unitProductRepository;
    private final UnitProductPricePeriodRepository unitProductPricePeriodRepository;
    private final UserRepository userRepository;
    private final PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;
    private final OrganizationDiscountService organizationDiscountService;
    private final MoneyCalculator moneyCalculator;
    private final TaxPolicyResolver taxPolicyResolver;
    private final RolePermissionService rolePermissionService;

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
        // Ceremony.timeZoneId는 organization.getDefaultTimeZoneId()를 그대로 물려받는다 — 아직
        // Ceremony가 없으니 organization에서 같은 값을 미리 계산해 쓴다.
        LocalDate asOfDate = LocalDate.now(ZoneId.of(organization.getDefaultTimeZoneId()));
        BillingPlanDiscountPeriod planPeriod = resolveSellablePlanPeriod(plan, asOfDate);
        checkCurrencyMatches(organization.getBillingCurrencyCode(), resolvePlanCurrency(plan, asOfDate));

        Ceremony ceremony = Ceremony.builder()
                .organization(organization)
                .billingPlan(plan)
                .title(request.getTitle())
                .build();
        ceremonyRepository.save(ceremony);
        recordPlanHistory(ceremony, plan, planPeriod, asOfDate);

        // 생성자는 역할과 무관하게 자동으로 배정된다(4.7절) — 나중에 OPERATOR로 강등돼도
        // 본인이 만든 행사 접근권을 그대로 유지하는 부수 효과가 있다.
        User creator = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ApplicationException(CommonErrorCode.ACCESS_DENIED));
        ceremonyAssignmentRepository.save(
                CeremonyAssignment.builder().ceremony(ceremony).user(creator).build()
        );

        return toSummary(ceremony);
    }

    /**
     * OPERATOR는 배정된 행사만 조회된다 — {@code assignedUserId}를 본인 id로 넘겨 조회 시점에
     * {@link CeremonyAssignment} 조인으로 스코핑한다({@link CeremonyRepositoryCustom#search}).
     */
    public Page<CeremonyDto.Response.CeremonySummary> findCeremonies(
            Long organizationId,
            Long currentUserId,
            String title,
            CeremonyStatus status,
            Pageable pageable
    ) {
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        Long assignedUserId = actingMember.getRole() == MemberRole.OPERATOR ? currentUserId : null;

        Page<Ceremony> ceremonies = ceremonyRepository.search(organizationId, title, status, assignedUserId, null, pageable);
        return ceremonies.map(this::toSummary);
    }

    /**
     * 플랫폼 관리자용 행사 목록 — {@link #findCeremonies}와 달리 조직 멤버십을 요구하지 않는다
     * (플랫폼 관리자는 별도 인가 축이라 organization_members에 없는 게 정상이다,
     * {@link #applyFinalDiscount}/{@link #updateStatusByPlatformAdmin}과 같은 이유). OPERATOR
     * 스코핑 대상이 없어 {@code assignedUserId}는 항상 null이다 — 관리자는 전부 본다. 조회 전용이라
     * {@link #applyFinalDiscount}처럼 등급 검사를 하지 않는다(카탈로그 조회 API들과 같은 관례,
     * PLATFORM_SUPPORT 이상이면 누구나 — 이미 SecurityConfig가 /api/platform-admin/**를 게이트).
     */
    public Page<CeremonyDto.Response.CeremonySummary> findCeremoniesByPlatformAdmin(
            Long organizationId,
            String title,
            CeremonyStatus status,
            Pageable pageable
    ) {
        findOrganizationOrThrow(organizationId);
        Page<Ceremony> ceremonies = ceremonyRepository.search(organizationId, title, status, null, null, pageable);
        return ceremonies.map(this::toSummary);
    }

    /**
     * 플랫폼 관리자용 단건 조회 — {@link #retrieveCeremony}와 달리 조직 멤버십을 요구하지 않는다
     * (위 {@link #findCeremoniesByPlatformAdmin}과 같은 이유). 행사 건별 재량 할인 상세 화면
     * (signstage-docs business/discount-management-screen-separation-review.md 6장 결정 #2)이
     * organizationId를 이미 알고 있는 상태(목록 행에서 넘어옴)로 호출한다.
     */
    public CeremonyDto.Response.CeremonySummary findCeremonyByPlatformAdmin(Long organizationId, Long ceremonyId) {
        return toSummary(findCeremonyInOrganizationOrThrow(organizationId, ceremonyId));
    }

    /**
     * 행사 건별 재량 할인 조직 횡단 목록 — {@code organizationId}/{@code status}/
     * {@code hasFinalDiscount}가 전부 선택 필터다(전부 null이면 전체). 조직명을 같이 내려줘야
     * 하는 화면이라 {@code CeremonyDto.Response.CeremonySummary}가 아니라 전용 DTO를 쓴다 —
     * signstage-docs business/discount-management-screen-separation-review.md 3.1절.
     */
    public Page<PlatformAdminCeremonyDiscountDto.Response.CeremonyDiscountSummary> findCeremonyDiscountsAcrossOrganizations(
            Long organizationId, CeremonyStatus status, Boolean hasFinalDiscount, Pageable pageable
    ) {
        Page<Ceremony> ceremonies = ceremonyRepository.search(organizationId, null, status, null, hasFinalDiscount, pageable);
        return ceremonies.map(ceremony -> new PlatformAdminCeremonyDiscountDto.Response.CeremonyDiscountSummary(
                ceremony.getId(),
                ceremony.getOrganization().getId(),
                ceremony.getOrganization().getName(),
                ceremony.getTitle(),
                ceremony.getStatus().name(),
                ceremony.getFinalDiscount().getDiscountType().name(),
                ceremony.getFinalDiscount().getDiscountValue(),
                ceremony.getCreatedAt()
        ));
    }

    public CeremonyDto.Response.CeremonySummary retrieveCeremony(Long organizationId, Long ceremonyId, Long currentUserId) {
        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCeremonyReadAccess(ceremony, actingMember, currentUserId);
        return toSummary(ceremony);
    }

    /** 행사 수정 화면에서 이름/설명을 바꾼다. 플랜은 생성 시점에 고정이라 여기서 바꾸지 않는다. */
    @Transactional
    public CeremonyDto.Response.CeremonySummary updateCeremony(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId,
            CeremonyDto.Request.UpdateCeremony request
    ) {
        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        checkCeremonyEditable(ceremony);

        ceremony.updateInfo(
                request.getTitle(),
                request.getDescription(),
                request.getOrganizingInstitution(),
                request.getOrganizingDepartment(),
                request.getContactName(),
                request.getContactTitle(),
                request.getContactPhone(),
                request.getContactEmail()
        );
        return toSummary(ceremony);
    }

    /**
     * DRAFT 상태에서만 플랜을 바꿀 수 있다 — 확정 후(IN_PROGRESS/COMPLETED) 시도하면 거부한다.
     * 호출할 때마다 {@link CeremonyPlanHistory}에 이력을 한 행 남긴다(3.2/3.4절).
     */
    @Transactional
    public CeremonyDto.Response.CeremonySummary changePlan(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId,
            CeremonyDto.Request.ChangePlan request
    ) {
        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        checkCeremonyPlanChangeable(ceremony);

        BillingPlan newPlan = billingPlanRepository.findById(request.getBillingPlanId())
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.BILLING_PLAN_NOT_FOUND));
        LocalDate asOfDate = LocalDate.now(ZoneId.of(ceremony.getTimeZoneId()));
        BillingPlanDiscountPeriod newPlanPeriod = resolveSellablePlanPeriod(newPlan, asOfDate);
        checkCurrencyMatches(ceremony.getCurrencyCode(), resolvePlanCurrency(newPlan, asOfDate));

        ceremony.changePlan(newPlan);
        recordPlanHistory(ceremony, newPlan, newPlanPeriod, asOfDate);

        return toSummary(ceremony);
    }

    /**
     * "플랜 확정" — DRAFT → IN_PROGRESS로 단방향 전이한다. 이후 플랜은 고정되고, 서명자/문서/
     * 하위 행사 등록이 열린다(3.1절). 확정을 취소하는 API는 두지 않는다(4.4절).
     */
    @Transactional
    public CeremonyDto.Response.CeremonySummary confirmPlan(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId
    ) {
        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        checkCeremonyPlanChangeable(ceremony);

        ceremony.confirmPlan();

        return toSummary(ceremony);
    }

    /** 최신순 — 가장 앞이 확정(또는 가장 최근 변경) 시점의 스냅샷이다. */
    public List<CeremonyDto.Response.PlanHistorySummary> findPlanHistory(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId
    ) {
        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        return ceremonyPlanHistoryRepository.findAllByCeremonyIdOrderByCreatedAtDesc(ceremonyId).stream()
                .map(this::toPlanHistorySummary)
                .toList();
    }

    /**
     * 단위 상품 추가구매 — 옛 {@code purchaseCapacity}/{@code purchaseOptionalFeature} 통합
     * (signstage-docs business/billing-catalog-unit-product-model-redesign-review.md 결정,
     * 2026-09-10, 3.4절). 한 요청에 여러 줄을 담을 수 있고(장바구니형), 요청 전체가 PENDING
     * 하나로 생겨 승인/반려도 항상 전체 단위로 처리된다. 토글형({@code EVENT_EFFECT_BUNDLE})
     * 단위 상품은 수량이 0/1 관례를 따라야 하고(3.6절), 이미 대기중/승인된 요청이 있으면
     * 재구매할 수 없다 — 그 외 종류(용량 계열)는 여러 번 구매해 누적할 수 있다(옛
     * {@code CeremonyCapacityPurchase}와 같은 동작).
     */
    @Transactional
    public CeremonyDto.Response.UnitProductPurchaseSummary purchaseUnitProducts(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId,
            CeremonyDto.Request.PurchaseUnitProducts request
    ) {
        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        checkCeremonyEditable(ceremony);

        List<Long> requestedIds = request.getLines().stream()
                .map(CeremonyDto.Request.PurchaseUnitProductLine::getUnitProductId)
                .toList();
        if (requestedIds.size() != requestedIds.stream().distinct().count()) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }

        LocalDate asOfDate = LocalDate.now(ZoneId.of(ceremony.getTimeZoneId()));
        Set<Long> purchasableIds = ceremony.getBillingPlan() != null
                ? new HashSet<>(retrievePurchasableUnitProductIds(ceremony))
                : null;

        CeremonyUnitProductPurchase purchase = CeremonyUnitProductPurchase.builder().ceremony(ceremony).build();
        ceremonyUnitProductPurchaseRepository.save(purchase);

        List<CeremonyUnitProductPurchaseLine> lines = new ArrayList<>();
        for (CeremonyDto.Request.PurchaseUnitProductLine line : request.getLines()) {
            UnitProduct unitProduct = unitProductRepository.findById(line.getUnitProductId())
                    .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_NOT_FOUND));
            UnitProductPricePeriod period = resolveSellableUnitProductPeriod(unitProduct, asOfDate);
            checkCurrencyMatches(ceremony.getCurrencyCode(), period.getPriceInfo().getCurrencyCode());

            // 안 A(구매 가능 상품 큐레이션) — 이 Ceremony의 플랜에서 구매 후보로 열어두지 않은
            // 상품은 거부한다. 플랜이 없는 행사(4.8절 예외)는 제한 없이 전부 허용한다.
            if (purchasableIds != null && !purchasableIds.contains(unitProduct.getId())) {
                throw new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_NOT_AVAILABLE_FOR_PLAN);
            }

            if (unitProduct.getType() == UnitProductType.EVENT_EFFECT_BUNDLE) {
                if (line.getQuantity() > 1) {
                    throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
                }
                boolean alreadyRequested = ceremonyUnitProductPurchaseLineRepository
                        .existsByPurchase_CeremonyIdAndUnitProduct_IdAndPurchase_StatusIn(
                                ceremonyId, unitProduct.getId(), List.of(PurchaseStatus.PENDING, PurchaseStatus.APPROVED)
                        );
                if (alreadyRequested) {
                    throw new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_ALREADY_PURCHASED);
                }
            }

            lines.add(ceremonyUnitProductPurchaseLineRepository.save(
                    CeremonyUnitProductPurchaseLine.builder()
                            .purchase(purchase)
                            .unitProduct(unitProduct)
                            .quantity(line.getQuantity())
                            .currencyCode(period.getPriceInfo().getCurrencyCode())
                            .purchasedName(unitProduct.getName())
                            .purchasedSalePrice(period.getPriceInfo().getSalePrice())
                            .purchasedTaxCode(period.getPriceInfo().getTaxCode())
                            .build()
            ));
        }

        return toUnitProductPurchaseSummary(purchase, lines);
    }

    /** 요청자 본인 이력 조회 — 대기중/승인됨/반려됨 전부 보여준다. */
    public List<CeremonyDto.Response.UnitProductPurchaseSummary> findUnitProductPurchases(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId
    ) {
        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        return ceremonyUnitProductPurchaseRepository.findAllByCeremonyIdOrderByCreatedAtDesc(ceremonyId).stream()
                .map(purchase -> toUnitProductPurchaseSummary(
                        purchase,
                        ceremonyUnitProductPurchaseLineRepository.findAllByPurchaseIdOrderByIdAsc(purchase.getId())
                ))
                .toList();
    }

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
        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

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
        return unitProductRepository.findAllById(availableIds).stream()
                .map(unitProduct -> {
                    CeremonyUnitProductPurchaseLine line = approvedLineByProductId.get(unitProduct.getId());
                    // 개별 추가구매(승인됨)한 상품은 구매 시점 스냅샷을 쓰지만, 플랜에 기본 포함된
                    // 상품(추가구매 기록 없음)은 "오늘" 기준 유효한 판매가격 기간을 그대로 보여준다.
                    Optional<UnitProductPricePeriod> effective =
                            unitProductPricePeriodRepository.findEffective(unitProduct.getId(), asOfDate);
                    return new UnitProductDto.Response.UnitProductSummary(
                            unitProduct.getId(),
                            unitProduct.getType().name(),
                            line != null ? line.getPurchasedName() : unitProduct.getName(),
                            unitProduct.getCategory().name(),
                            unitProduct.getExclusivityGroup(),
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
                            effective.map(p -> p.isActive() ? "ON_SALE" : "INACTIVE").orElse("NO_ACTIVE_PERIOD")
                    );
                })
                .toList();
    }

    /**
     * 플랫폼 관리자가 Ceremony 상태를 IN_PROGRESS/COMPLETED 사이에서 양방향으로 강제 변경한다
     * (실수로 완료됐거나 예외 상황 처리용). DRAFT는 대상이 아니다 — 플랜 확정(DRAFT →
     * IN_PROGRESS)은 {@link #confirmPlan}의 단방향 전이로만 이뤄진다(signstage-docs
     * business/ceremony-plan-confirmation-review.md 4.4절). {@code feature.platformadmin.service}에
     * 별도 래퍼를 두지 않고 여기 직접 붙인다 — 과금 카탈로그 작업에서 확립한 관례
     * (PlatformAdminBillingCatalogController → BillingPlanService 등)와 같다. 아래
     * {@code findCeremonyInOrganizationOrThrow}를 그대로 재사용한다.
     */
    @Transactional
    public CeremonyDto.Response.CeremonySummary updateStatusByPlatformAdmin(
            Long organizationId,
            Long ceremonyId,
            Long adminUserId,
            String actingPlatformRole,
            CeremonyDto.Request.UpdateStatus request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_CEREMONY_STATUS_CONTROL");

        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        CeremonyStatus previousStatus = ceremony.getStatus();
        CeremonyStatus newStatus = parseCeremonyStatus(request.getStatus());
        if (newStatus == CeremonyStatus.DRAFT) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
        ceremony.changeStatus(newStatus);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_CEREMONY_STATUS, null, organizationId,
                "ceremonyId=" + ceremonyId + ", status: " + previousStatus + " -> " + newStatus
        );

        return toSummary(ceremony);
    }

    private CeremonyStatus parseCeremonyStatus(String status) {
        try {
            return CeremonyStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    /**
     * 행사 건별 재량 할인 설정 — 플랫폼 관리자(PLATFORM_OPS 이상) 전용이고, 플랜이 확정된
     * (IN_PROGRESS) 행사에만 적용할 수 있다. DRAFT는 "아직 플랜도 안 정해졌는데 할인부터
     * 매길 수 없다"는 이유로, COMPLETED는 "끝난 행사는 더 이상 안 바뀐다"는 기존 원칙으로
     * 막는다 — signstage-docs business/organization-event-discount-pricing-review.md
     * 4.2/4.4/6.2절 참고. {@code feature.platformadmin.service}에 별도 래퍼를 두지 않는 이유는
     * 위 {@link #updateStatusByPlatformAdmin}과 같다.
     */
    @Transactional
    public CeremonyDto.Response.CeremonySummary applyFinalDiscount(
            Long organizationId,
            Long ceremonyId,
            Long adminUserId,
            String actingPlatformRole,
            CeremonyDto.Request.ApplyFinalDiscount request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_CEREMONY_FINAL_DISCOUNT_MANAGE");

        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        checkCeremonyInProgress(ceremony);

        DiscountType previousType = ceremony.getFinalDiscount().getDiscountType();
        BigDecimal previousValue = ceremony.getFinalDiscount().getDiscountValue();
        DiscountType newType = parseDiscountType(request.getDiscountType());

        ceremony.applyFinalDiscount(newType, request.getDiscountValue());

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_CEREMONY_FINAL_DISCOUNT, null, organizationId,
                "ceremonyId=" + ceremonyId + ", finalDiscount: " + previousType + " " + previousValue
                        + " -> " + newType + " " + request.getDiscountValue()
        );

        return toSummary(ceremony);
    }

    /** DRAFT(플랜 미확정)·COMPLETED(완료) 둘 다 막고 IN_PROGRESS만 허용한다. */
    private void checkCeremonyInProgress(Ceremony ceremony) {
        if (ceremony.getStatus() == CeremonyStatus.DRAFT) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_PLAN_NOT_CONFIRMED);
        }
        if (ceremony.getStatus() == CeremonyStatus.COMPLETED) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_ALREADY_COMPLETED);
        }
    }

    private DiscountType parseDiscountType(String discountType) {
        try {
            return DiscountType.valueOf(discountType);
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    // ---- 플랫폼 관리자 — 단위 상품 추가구매 승인 대기열 ----
    // feature.platformadmin.service에 별도 래퍼를 두지 않고 여기 직접 붙인다(위 updateStatusByPlatformAdmin과 같은 이유).

    /** status를 생략하면(null) 전체 상태를 최신순으로 돌려준다(조직 생성 요청 목록과 같은 규약). */
    public Page<PlatformAdminCeremonyPurchaseDto.Response.UnitProductPurchaseRequestSummary> findUnitProductPurchaseRequests(
            PurchaseStatus status,
            Pageable pageable
    ) {
        Page<CeremonyUnitProductPurchase> purchases = status != null
                ? ceremonyUnitProductPurchaseRepository.findAllByStatus(status, pageable)
                : ceremonyUnitProductPurchaseRepository.findAll(pageable);
        Map<Long, String> loginIdsByUserId = resolveUserLoginIds(
                purchases.getContent().stream().flatMap(purchase -> Stream.of(purchase.getCreatedBy(), purchase.getReviewedBy()))
        );
        return purchases.map(purchase -> toUnitProductRequestSummary(purchase, loginIdsByUserId));
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
        return toUnitProductRequestSummary(purchase, resolveUserLoginIds(Stream.of(purchase.getCreatedBy(), purchase.getReviewedBy())));
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
        return toUnitProductRequestSummary(purchase, resolveUserLoginIds(Stream.of(purchase.getCreatedBy(), purchase.getReviewedBy())));
    }

    private CeremonyUnitProductPurchase findPendingUnitProductPurchaseOrThrow(Long purchaseId) {
        CeremonyUnitProductPurchase purchase = ceremonyUnitProductPurchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_PURCHASE_NOT_FOUND));
        if (purchase.getStatus() != PurchaseStatus.PENDING) {
            throw new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_PURCHASE_NOT_PENDING);
        }
        return purchase;
    }

    private Map<Long, String> resolveUserLoginIds(Stream<Long> userIds) {
        List<Long> ids = userIds.filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getLoginId));
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
                .orElseGet(() -> resolveDemoVirtualMemberOrThrow(organizationId, userId));
    }

    /**
     * 데모 조직 우회 — signstage-docs
     * business/demo-account-exhibition-signer-preview-review.md 11.2절(2026-09-09, 결정 번복).
     * 실제 {@code Member} 행이 없을 때만(비용이 큰 조회는 평소 조직 사용자 흐름에 영향이 없도록
     * 실패 경로에서만 시도한다) 호출자가 플랫폼 관리자이고 그 조직이 데모 조직(Organization.isDemo)
     * 이면, 저장하지 않는 가상의 Member를 그 자리에서 만들어 돌려준다 — 이 메서드를 공유하는
     * {@code CeremonyEventService}/{@code CeremonyEventEffectSettingService}/
     * {@code CeremonyEffectRuntimeService}/{@code CeremonyResultService}/{@code SignerService}/
     * {@code TemplateService}/{@code TemplateFieldService} 8개 서비스 전부에 자동 파급된다.
     *
     * <p>가상 Member의 역할은 호출자의 플랫폼 등급에 따라 갈린다 — {@code ACTION_DEMO_CEREMONY_MANAGE}
     * 권한이 있으면(PLATFORM_OPS 이상) 전체 관리가 가능한 OWNER, 없으면(PLATFORM_SUPPORT) 조회만
     * 가능한 VIEWER다. 이 메서드 자체는 두 등급을 구분하지 않고 항상 같은 코드 경로를 타지만,
     * 이미 있는 역할 기반 검사(예: {@code checkCeremonyManageAccess}가 쓰는
     * {@code ACTION_CEREMONY_MANAGE})가 VIEWER는 그대로 걸러내므로 새 검사를 추가하지 않아도
     * 두 등급이 자연스럽게 나뉜다.
     */
    private Member resolveDemoVirtualMemberOrThrow(Long organizationId, Long userId) {
        Organization organization = organizationRepository.findById(organizationId).orElse(null);
        User user = userRepository.findById(userId).orElse(null);
        if (organization != null && organization.isDemo() && user != null && user.getPlatformRole() != null) {
            MemberRole virtualRole = rolePermissionService.isAllowed(user.getPlatformRole().name(), "ACTION_DEMO_CEREMONY_MANAGE")
                    ? MemberRole.OWNER
                    : MemberRole.VIEWER;
            return Member.builder().organization(organization).user(user).role(virtualRole).status(MemberStatus.ACTIVE).build();
        }
        throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
    }

    /** OWNER/ADMIN/VIEWER는 조직의 모든 행사를 조회할 수 있고, OPERATOR는 배정된 행사만 조회할 수 있다. */
    void checkCeremonyReadAccess(Ceremony ceremony, Member actingMember, Long currentUserId) {
        if (actingMember.getRole() == MemberRole.OPERATOR) {
            checkAssigned(ceremony, currentUserId);
        }
    }

    /**
     * 행사 생성/수정(단위 상품 구매, 하위 행사 생성 등) 권한. OWNER/ADMIN은 항상 가능하고,
     * OPERATOR는 배정된 행사만, VIEWER는 불가하다(user-organization-design.md 4.2절
     * "행사(Ceremony) 생성/수정/삭제").
     */
    void checkCeremonyManageAccess(Ceremony ceremony, Member actingMember, Long currentUserId) {
        if (!rolePermissionService.isAllowed(actingMember.getRole().name(), "ACTION_CEREMONY_MANAGE")) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
        // OPERATOR의 "본인/배정 건만" 제약은 소유권 기반 불변식이라 role_permissions로 옮기지
        // 않는다(signstage-docs business/menu-and-action-permission-management-review.md 3장).
        if (actingMember.getRole() == MemberRole.OPERATOR) {
            checkAssigned(ceremony, currentUserId);
        }
    }

    /**
     * 완료(COMPLETED)된 Ceremony 아래에서는 하위 데이터를 더 이상 수정할 수 없다 — 조회만 가능하다.
     * 결과물 생성처럼 완료를 유발하는 호출 자체는 이 체크가 통과한 뒤(아직 IN_PROGRESS일 때)
     * 실행되고, 완료 전이는 그 성공 이후에 일어나므로 스스로를 막지 않는다.
     */
    void checkCeremonyEditable(Ceremony ceremony) {
        if (ceremony.getStatus() == CeremonyStatus.COMPLETED) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_ALREADY_COMPLETED);
        }
    }

    /**
     * 플랜 변경/확정은 DRAFT 상태에서만 가능하다 — signstage-docs
     * business/ceremony-plan-confirmation-review.md 3.1/3.2절.
     */
    private void checkCeremonyPlanChangeable(Ceremony ceremony) {
        if (ceremony.getStatus() != CeremonyStatus.DRAFT) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_PLAN_ALREADY_CONFIRMED);
        }
    }

    /**
     * 서명자/문서/하위 행사 등록은 플랜이 확정된(DRAFT를 벗어난) Ceremony에서만 허용한다.
     * {@link SignerService}/{@link TemplateService}/{@link CeremonyEventService}가
     * {@link #checkCeremonyEditable}과 함께 재사용한다 — signstage-docs
     * business/ceremony-plan-confirmation-review.md 3.3절.
     */
    void checkCeremonyPlanConfirmed(Ceremony ceremony) {
        if (ceremony.getStatus() == CeremonyStatus.DRAFT) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_PLAN_NOT_CONFIRMED);
        }
    }

    /**
     * asOfDate 기준 유효한 할인 기간을 찾아 사용여부까지 확인한다 — 기간이 없거나(카탈로그 등록
     * 실수로 공백이 생긴 경우) 있어도 사용 중지(active=false)면 신규 선택/변경 대상에서 제외한다
     * (signstage-docs business/billing-catalog-price-validity-period-review.md 결정,
     * 2026-09-09 원칙을 플랜 할인 기간에도 그대로 적용).
     */
    private BillingPlanDiscountPeriod resolveSellablePlanPeriod(BillingPlan plan, LocalDate asOfDate) {
        BillingPlanDiscountPeriod period = billingPlanDiscountPeriodRepository.findEffective(plan.getId(), asOfDate)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.BILLING_PLAN_INACTIVE));
        if (!period.isActive()) {
            throw new ApplicationException(CeremonyErrorCode.BILLING_PLAN_INACTIVE);
        }
        return period;
    }

    /** {@link #resolveSellablePlanPeriod}과 같은 원칙 — 단위 상품. */
    private UnitProductPricePeriod resolveSellableUnitProductPeriod(UnitProduct unitProduct, LocalDate asOfDate) {
        UnitProductPricePeriod period = unitProductPricePeriodRepository.findEffective(unitProduct.getId(), asOfDate)
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
    private String resolvePlanCurrency(BillingPlan plan, LocalDate asOfDate) {
        Set<String> currencies = billingPlanUnitProductRepository.findAllByBillingPlanId(plan.getId()).stream()
                .map(BillingPlanUnitProduct::getUnitProduct)
                .map(unitProduct -> unitProductPricePeriodRepository.findEffective(unitProduct.getId(), asOfDate))
                .flatMap(Optional::stream)
                .map(period -> period.getPriceInfo().getCurrencyCode())
                .collect(Collectors.toSet());
        if (currencies.size() > 1) {
            throw new ApplicationException(CeremonyErrorCode.CURRENCY_MISMATCH);
        }
        return currencies.stream().findFirst().orElse(null);
    }

    /**
     * Ceremony 생성 시(최초 플랜 선택)와 {@link #changePlan}에서 매 변경마다 호출한다(3.4절).
     * 그 순간 플랜이 포함하던 단위 상품 구성(포함 수량 × 그 순간 단가)을
     * {@link CeremonyPlanHistoryUnitProduct}로 함께 스냅샷한다 — 카탈로그 관리자가 나중에
     * 플랜의 구성/가격을 바꿔도 이 Ceremony는 영향받지 않아야 한다(signstage-docs
     * business/billing-catalog-unit-product-model-redesign-review.md 5장).
     */
    private void recordPlanHistory(Ceremony ceremony, BillingPlan plan, BillingPlanDiscountPeriod planPeriod, LocalDate asOfDate) {
        // 조직×플랜 할인 오버라이드가 있으면 카탈로그 값 대신 이 값을 스냅샷한다
        // (signstage-docs business/organization-event-discount-pricing-review.md 4.1절,
        // 2026-08-21 재검토) — 그 시점의 값을 CeremonyPlanHistory에 고정해 두므로, 오버라이드를
        // 나중에 바꿔도 이미 만들어진 이 Ceremony에는 영향을 주지 않는다.
        OrganizationDiscountService.EffectiveDiscount discount =
                organizationDiscountService.resolveBillingPlanDiscount(
                        ceremony.getOrganization(), plan.getId(),
                        planPeriod.getDiscount().getDiscountType(), planPeriod.getDiscount().getDiscountValue(),
                        asOfDate
                );
        CeremonyPlanHistory history = ceremonyPlanHistoryRepository.save(
                CeremonyPlanHistory.builder()
                        .ceremony(ceremony)
                        .billingPlan(plan)
                        .catalogDiscountType(planPeriod.getDiscount().getDiscountType())
                        .catalogDiscountValue(planPeriod.getDiscount().getDiscountValue())
                        .discountType(discount.type())
                        .discountValue(discount.value())
                        .build()
        );
        billingPlanUnitProductRepository.findAllByBillingPlanId(plan.getId()).forEach(source -> {
            UnitProduct unitProduct = source.getUnitProduct();
            Optional<UnitProductPricePeriod> effective = unitProductPricePeriodRepository.findEffective(unitProduct.getId(), asOfDate);
            ceremonyPlanHistoryUnitProductRepository.save(
                    CeremonyPlanHistoryUnitProduct.builder()
                            .ceremonyPlanHistory(history)
                            .unitProduct(unitProduct)
                            .includedQuantity(source.getIncludedQuantity())
                            .purchasable(source.isPurchasable())
                            .currencyCode(effective.map(p -> p.getPriceInfo().getCurrencyCode()).orElse(null))
                            .snapshotSalePrice(effective.map(p -> p.getPriceInfo().getSalePrice()).orElse(BigDecimal.ZERO))
                            .snapshotTaxCode(effective.map(p -> p.getPriceInfo().getTaxCode()).orElse("KR_VAT_STANDARD"))
                            .build()
            );
        });
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
        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        return new CeremonyDto.Response.CapacityStatus(
                calculateEffectiveCapacity(ceremony, UnitProductType.SIGNERS),
                calculateEffectiveCapacity(ceremony, UnitProductType.TEMPLATES),
                calculateEffectiveCapacity(ceremony, UnitProductType.TEST_EVENTS),
                calculateEffectiveCapacity(ceremony, UnitProductType.REHEARSAL_EVENTS),
                calculateEffectiveCapacity(ceremony, UnitProductType.MAIN_EVENTS)
        );
    }

    /**
     * 이 Ceremony의 예상 청구 금액 — signstage-docs
     * business/billing-catalog-unit-product-model-redesign-review.md 3.5절 계산식.
     * {@code 플랜 소계 = Σ(포함 단위 상품 snapshot가격 × includedQuantity)} → 플랜 자체 할인을
     * 한 번 적용 → 추가구매(승인분만, 정가 그대로 — 할인 없음)를 더해 subtotal → 행사 건별
     * 재량 할인({@code ceremony.finalDiscount})을 다시 한 번 적용 → 세금은 라인별 비례 배분 +
     * 라인별 taxCode로 계산한다(플랜 소계 자체도 그 안의 단위 상품 줄 수만큼 라인이 늘어난다 —
     * 옛 "플랜 항목 1줄"에서 "플랜에 포함된 단위 상품 줄 수"로 세분화됐을 뿐 알고리즘은 같다).
     *
     * <p>플랜 항목은 라이브 {@code BillingPlanUnitProduct}가 아니라 {@link CeremonyPlanHistory}
     * 최신 스냅샷({@link CeremonyPlanHistoryUnitProduct})을 쓴다 — 이력이 없는 행사(이 기능
     * 배포 전 기존 행사)만 라이브로 폴백한다. 추가구매는 승인(APPROVED)된 건만 반영하고, 그
     * 구매 시점 스냅샷(가격)을 그대로 쓴다.
     */
    public CeremonyDto.Response.EstimatedTotal calculateEstimatedTotal(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId
    ) {
        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        CurrencyPolicy currencyPolicy = ceremony.currencyPolicy();
        LocalDate asOfDate = LocalDate.now(ZoneId.of(ceremony.getTimeZoneId()));

        // ---- 플랜 소계 → 플랜 적용가(할인 한 번) → 줄별 재배분(세금 계산용) ----
        BigDecimal planApplied = BigDecimal.ZERO;
        List<TaxableLine> planTaxableLines = List.of();
        BillingPlan plan = ceremony.getBillingPlan();
        if (plan != null) {
            Optional<CeremonyPlanHistory> snapshot =
                    ceremonyPlanHistoryRepository.findFirstByCeremonyIdOrderByCreatedAtDesc(ceremony.getId());
            List<TaxableLine> rawPlanLines = snapshot
                    .map(history -> ceremonyPlanHistoryUnitProductRepository.findAllByCeremonyPlanHistoryId(history.getId()).stream()
                            .filter(line -> line.getIncludedQuantity() > 0)
                            .map(line -> new TaxableLine(
                                    line.getSnapshotSalePrice().multiply(BigDecimal.valueOf(line.getIncludedQuantity())),
                                    line.getSnapshotTaxCode()
                            ))
                            .toList())
                    // 이력이 없는 경우(플랜 확정 기능 배포 전 기존 행사)만 라이브 값으로 대체한다.
                    .orElseGet(() -> billingPlanUnitProductRepository.findAllByBillingPlanId(plan.getId()).stream()
                            .filter(source -> source.getIncludedQuantity() > 0)
                            .flatMap(source -> unitProductPricePeriodRepository
                                    .findEffective(source.getUnitProduct().getId(), asOfDate)
                                    .map(period -> new TaxableLine(
                                            period.getPriceInfo().getSalePrice().multiply(BigDecimal.valueOf(source.getIncludedQuantity())),
                                            period.getPriceInfo().getTaxCode()
                                    ))
                                    .stream())
                            .toList());

            DiscountType planDiscountType = snapshot.map(CeremonyPlanHistory::getPlanDiscountType)
                    .orElseGet(() -> billingPlanDiscountPeriodRepository.findEffective(plan.getId(), asOfDate)
                            .map(period -> period.getDiscount().getDiscountType()).orElse(DiscountType.PERCENT));
            BigDecimal planDiscountValue = snapshot.map(CeremonyPlanHistory::getPlanDiscountValue)
                    .orElseGet(() -> billingPlanDiscountPeriodRepository.findEffective(plan.getId(), asOfDate)
                            .map(period -> period.getDiscount().getDiscountValue()).orElse(BigDecimal.ZERO));

            BigDecimal planSubtotal = rawPlanLines.stream().map(TaxableLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            planApplied = moneyCalculator.applyDiscount(planSubtotal, planDiscountType, planDiscountValue, currencyPolicy);
            planTaxableLines = allocateProportionally(rawPlanLines, planApplied, currencyPolicy);
        }

        // ---- 추가구매(승인분, 정가 그대로 — 할인 없음) ----
        List<TaxableLine> purchaseLines = ceremonyUnitProductPurchaseLineRepository
                .findAllByPurchase_CeremonyIdOrderByCreatedAtDesc(ceremonyId).stream()
                .filter(line -> line.getPurchase().getStatus() == PurchaseStatus.APPROVED)
                .map(line -> new TaxableLine(
                        line.getPurchasedSalePrice().multiply(BigDecimal.valueOf(line.getQuantity())),
                        line.getPurchasedTaxCode()
                ))
                .toList();
        BigDecimal purchaseTotal = purchaseLines.stream().map(TaxableLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<TaxableLine> taxableLines = new ArrayList<>(planTaxableLines);
        taxableLines.addAll(purchaseLines);

        BigDecimal subtotal = planApplied.add(purchaseTotal);
        BigDecimal netAmount = moneyCalculator.applyDiscount(subtotal, ceremony.getFinalDiscount(), currencyPolicy);
        BigDecimal taxAmount = calculateLineRoundedTax(taxableLines, subtotal, netAmount, currencyPolicy, asOfDate);
        BigDecimal grossAmount = moneyCalculator.normalize(netAmount.add(taxAmount), currencyPolicy);

        return new CeremonyDto.Response.EstimatedTotal(
                planApplied,
                purchaseTotal,
                subtotal,
                ceremony.getFinalDiscount().getDiscountType().name(),
                ceremony.getFinalDiscount().getDiscountValue(),
                ceremony.getCurrencyCode(),
                ceremony.getCurrencyFractionDigits(),
                netAmount,
                taxAmount,
                grossAmount,
                grossAmount
        );
    }

    /**
     * {@code total}을 {@code lines}의 세전 금액 비중대로 재배분한다 — 반올림 오차는 마지막
     * 줄이 흡수한다. 플랜 소계에 플랜 할인을 적용한 뒤, 그 적용가를 원래 단위 상품 줄들에
     * 세금 계산용으로 되돌려 배분하는 데 쓴다({@link #calculateLineRoundedTax}가 행사 건별
     * 최종 할인에 쓰는 것과 같은 알고리즘).
     */
    private List<TaxableLine> allocateProportionally(List<TaxableLine> lines, BigDecimal total, CurrencyPolicy currencyPolicy) {
        BigDecimal weightSum = lines.stream().map(TaxableLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (weightSum.signum() == 0) {
            return lines;
        }
        List<TaxableLine> allocated = new ArrayList<>();
        BigDecimal runningTotal = BigDecimal.ZERO;
        for (int index = 0; index < lines.size(); index++) {
            BigDecimal share;
            if (index == lines.size() - 1) {
                share = total.subtract(runningTotal);
            } else {
                share = moneyCalculator.normalize(
                        lines.get(index).amount().multiply(total).divide(weightSum, 12, currencyPolicy.roundingMode()),
                        currencyPolicy
                );
                runningTotal = runningTotal.add(share);
            }
            allocated.add(new TaxableLine(share, lines.get(index).taxCode()));
        }
        return allocated;
    }

    /** 행사 최종 할인 후 금액을 실제 구매 라인에 비례 배분하고 유효한 세금 정책으로 라인별 반올림한다. */
    private BigDecimal calculateLineRoundedTax(
            List<TaxableLine> lines,
            BigDecimal subtotal,
            BigDecimal netAmount,
            CurrencyPolicy currencyPolicy,
            LocalDate taxPointDate
    ) {
        if (subtotal.signum() == 0) {
            return moneyCalculator.normalize(BigDecimal.ZERO, currencyPolicy);
        }
        BigDecimal allocated = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (int index = 0; index < lines.size(); index++) {
            BigDecimal lineNet;
            if (index == lines.size() - 1) {
                lineNet = netAmount.subtract(allocated);
            } else {
                lineNet = moneyCalculator.normalize(
                        lines.get(index).amount().multiply(netAmount).divide(subtotal, 12, currencyPolicy.roundingMode()),
                        currencyPolicy
                );
                allocated = allocated.add(lineNet);
            }
            BigDecimal rate = taxPolicyResolver.resolve("KR", lines.get(index).taxCode(), taxPointDate).getRatePercent();
            tax = tax.add(moneyCalculator.calculateExclusiveTax(lineNet, rate, currencyPolicy));
        }
        return moneyCalculator.normalize(tax, currencyPolicy);
    }

    private record TaxableLine(BigDecimal amount, String taxCode) {
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
     * 영향받지 않아야 한다. 이 기능(플랜 확정) 배포 전에 만들어져 이력이 없는 행사만 예외로
     * 라이브 값에 fallback한다.
     */
    int calculateEffectiveCapacity(Ceremony ceremony, UnitProductType type) {
        BillingPlan plan = ceremony.getBillingPlan();
        if (plan == null) {
            return Integer.MAX_VALUE;
        }

        int baseValue = ceremonyPlanHistoryRepository.findFirstByCeremonyIdOrderByCreatedAtDesc(ceremony.getId())
                .map(snapshot -> ceremonyPlanHistoryUnitProductRepository.findAllByCeremonyPlanHistoryId(snapshot.getId()).stream()
                        .filter(line -> line.getUnitProduct().getType() == type)
                        .mapToInt(CeremonyPlanHistoryUnitProduct::getIncludedQuantity)
                        .sum())
                // 이력이 없는 경우(플랜 확정 기능 배포 전 기존 행사)만 라이브 값으로 대체한다.
                .orElseGet(() -> billingPlanUnitProductRepository.findAllByBillingPlanId(plan.getId()).stream()
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
     * 스냅샷 기능 배포 전 기존 행사)만 라이브 값으로 대체한다.
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

        Optional<CeremonyPlanHistory> snapshot =
                ceremonyPlanHistoryRepository.findFirstByCeremonyIdOrderByCreatedAtDesc(ceremony.getId());
        List<Long> includedInPlan = snapshot
                .map(history -> ceremonyPlanHistoryUnitProductRepository.findAllByCeremonyPlanHistoryId(history.getId()).stream()
                        .filter(line -> line.getIncludedQuantity() > 0)
                        .filter(line -> line.getUnitProduct().getType() == UnitProductType.EVENT_EFFECT_BUNDLE)
                        .map(line -> line.getUnitProduct().getId())
                        .toList())
                .orElseGet(() -> billingPlanUnitProductRepository.findAllByBillingPlanId(ceremony.getBillingPlan().getId()).stream()
                        .filter(source -> source.getIncludedQuantity() > 0)
                        .filter(source -> source.getUnitProduct().getType() == UnitProductType.EVENT_EFFECT_BUNDLE)
                        .map(source -> source.getUnitProduct().getId())
                        .toList());

        return Stream.concat(purchased.stream(), includedInPlan.stream()).distinct().toList();
    }

    /**
     * 이 Ceremony의 플랜에서 구매 가능한(안 A 큐레이션, {@code purchasable=true}) 단위 상품 id
     * 목록. 라이브 {@code BillingPlanUnitProduct} 대신 이 Ceremony의 최신
     * {@link CeremonyPlanHistory} 스냅샷을 우선 쓴다 — 카탈로그 관리자가 나중에 플랜의 구매
     * 가능 상품 구성을 바꿔도 영향받지 않아야 한다. 이력이 없는 경우만 라이브 값으로 대체한다.
     * 호출부가 {@code ceremony.getBillingPlan() != null}을 먼저 확인해야 한다 — 플랜 없는
     * 행사는 제한 자체가 없다.
     */
    List<Long> retrievePurchasableUnitProductIds(Ceremony ceremony) {
        Optional<CeremonyPlanHistory> snapshot =
                ceremonyPlanHistoryRepository.findFirstByCeremonyIdOrderByCreatedAtDesc(ceremony.getId());
        return snapshot
                .map(history -> ceremonyPlanHistoryUnitProductRepository.findAllByCeremonyPlanHistoryId(history.getId()).stream()
                        .filter(CeremonyPlanHistoryUnitProduct::isPurchasable)
                        .map(line -> line.getUnitProduct().getId())
                        .toList())
                .orElseGet(() -> billingPlanUnitProductRepository.findAllByBillingPlanId(ceremony.getBillingPlan().getId()).stream()
                        .filter(BillingPlanUnitProduct::isPurchasable)
                        .map(source -> source.getUnitProduct().getId())
                        .toList());
    }

    /**
     * 이 Ceremony가 실제로 구매 요청할 수 있는 단위 상품 카탈로그만 필터링해 돌려준다(안 A) —
     * {@link #retrieveApplicableUnitProducts}와 같은 목적으로, 구매 화면의 선택 목록이 이
     * 목록으로 채워야 플랜에서 열어두지 않은 상품을 골라 제출한 뒤에야 거부당하는 UX를
     * 피할 수 있다. 플랜이 없는 행사(4.8절 예외)는 활성 상품 전체를 제한 없이 돌려준다.
     */
    public List<UnitProductDto.Response.UnitProductSummary> retrievePurchasableUnitProducts(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId
    ) {
        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        if (ceremony.getBillingPlan() == null) {
            return unitProductRepository.findAll().stream().map(this::toUnitProductSummaryForPurchase).toList();
        }

        List<Long> availableIds = retrievePurchasableUnitProductIds(ceremony);
        if (availableIds.isEmpty()) {
            return List.of();
        }
        return unitProductRepository.findAllById(availableIds).stream().map(this::toUnitProductSummaryForPurchase).toList();
    }

    private UnitProductDto.Response.UnitProductSummary toUnitProductSummaryForPurchase(UnitProduct unitProduct) {
        Optional<UnitProductPricePeriod> effective =
                unitProductPricePeriodRepository.findEffective(unitProduct.getId(), LocalDate.now());
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
                ceremonyUnitProductPurchaseLineRepository.countByUnitProduct_IdAndPurchase_Status(unitProduct.getId(), PurchaseStatus.APPROVED),
                List.of(),
                unitProduct.getCreatedAt(),
                effective.map(UnitProductPricePeriod::getEffectiveFrom).orElse(null),
                effective.map(UnitProductPricePeriod::getEffectiveTo).orElse(null),
                effective.map(p -> p.isActive() ? "ON_SALE" : "INACTIVE").orElse("NO_ACTIVE_PERIOD")
        );
    }

    private void checkAssigned(Ceremony ceremony, Long currentUserId) {
        if (!ceremonyAssignmentRepository.existsByCeremonyIdAndUserId(ceremony.getId(), currentUserId)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    private void checkCanCreateCeremony(Member actingMember) {
        if (!rolePermissionService.isAllowed(actingMember.getRole().name(), "ACTION_CEREMONY_CREATE")) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    private void checkCurrencyMatches(String ceremonyCurrencyCode, String itemCurrencyCode) {
        if (itemCurrencyCode != null && !Objects.equals(ceremonyCurrencyCode, itemCurrencyCode)) {
            throw new ApplicationException(CeremonyErrorCode.CURRENCY_MISMATCH);
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
                ceremony.getCurrencyCode(),
                ceremony.getCurrencyFractionDigits(),
                ceremony.getTimeZoneId(),
                ceremony.getTitle(),
                ceremony.getDescription(),
                ceremony.getStatus().name(),
                ceremony.getOrganizingInstitution(),
                ceremony.getOrganizingDepartment(),
                ceremony.getContactName(),
                ceremony.getContactTitle(),
                ceremony.getContactPhone(),
                ceremony.getContactEmail(),
                ceremony.getFinalDiscount().getDiscountType().name(),
                ceremony.getFinalDiscount().getDiscountValue(),
                ceremony.getCreatedBy(),
                ceremony.getCreatedAt()
        );
    }

    private CeremonyDto.Response.PlanHistorySummary toPlanHistorySummary(CeremonyPlanHistory history) {
        List<CeremonyDto.Response.PlanHistoryLineSummary> lines = ceremonyPlanHistoryUnitProductRepository
                .findAllByCeremonyPlanHistoryId(history.getId()).stream()
                .map(line -> new CeremonyDto.Response.PlanHistoryLineSummary(
                        line.getUnitProduct().getId(),
                        line.getUnitProduct().getType().name(),
                        line.getUnitProduct().getName(),
                        line.getIncludedQuantity(),
                        line.isPurchasable(),
                        line.getCurrencyCode(),
                        line.getSnapshotSalePrice(),
                        line.getSnapshotTaxCode()
                ))
                .toList();
        return new CeremonyDto.Response.PlanHistorySummary(
                history.getId(),
                history.getBillingPlan().getId(),
                history.getPlanName(),
                history.getPlanDiscountType().name(),
                history.getPlanDiscountValue(),
                lines,
                history.getCreatedBy(),
                history.getCreatedAt()
        );
    }

    private CeremonyDto.Response.UnitProductPurchaseSummary toUnitProductPurchaseSummary(
            CeremonyUnitProductPurchase purchase,
            List<CeremonyUnitProductPurchaseLine> lines
    ) {
        return new CeremonyDto.Response.UnitProductPurchaseSummary(
                purchase.getId(),
                purchase.getCeremony().getId(),
                lines.stream().map(this::toUnitProductPurchaseLineSummary).toList(),
                purchase.getStatus().name(),
                purchase.getRejectionReason(),
                purchase.getReviewedAt(),
                purchase.getCreatedAt()
        );
    }

    private CeremonyDto.Response.UnitProductPurchaseLineSummary toUnitProductPurchaseLineSummary(CeremonyUnitProductPurchaseLine line) {
        return new CeremonyDto.Response.UnitProductPurchaseLineSummary(
                line.getId(),
                line.getUnitProduct().getId(),
                line.getUnitProduct().getType().name(),
                line.getQuantity(),
                line.getCurrencyCode(),
                line.getPurchasedName(),
                line.getPurchasedSalePrice(),
                line.getPurchasedTaxCode()
        );
    }

    private PlatformAdminCeremonyPurchaseDto.Response.UnitProductPurchaseRequestSummary toUnitProductRequestSummary(
            CeremonyUnitProductPurchase purchase,
            Map<Long, String> loginIdsByUserId
    ) {
        List<CeremonyDto.Response.UnitProductPurchaseLineSummary> lines = ceremonyUnitProductPurchaseLineRepository
                .findAllByPurchaseIdOrderByIdAsc(purchase.getId()).stream()
                .map(this::toUnitProductPurchaseLineSummary)
                .toList();
        return new PlatformAdminCeremonyPurchaseDto.Response.UnitProductPurchaseRequestSummary(
                purchase.getId(),
                purchase.getCreatedBy(),
                loginIdsByUserId.get(purchase.getCreatedBy()),
                purchase.getCeremony().getOrganization().getId(),
                purchase.getCeremony().getId(),
                purchase.getCeremony().getTitle(),
                lines,
                purchase.getStatus().name(),
                purchase.getRejectionReason(),
                purchase.getReviewedBy() != null ? loginIdsByUserId.get(purchase.getReviewedBy()) : null,
                purchase.getReviewedAt(),
                purchase.getCreatedAt()
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
