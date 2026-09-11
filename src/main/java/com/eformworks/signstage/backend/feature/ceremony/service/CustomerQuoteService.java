package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.core.money.CurrencyPolicy;
import com.eformworks.signstage.backend.core.money.MoneyCalculator;
import com.eformworks.signstage.backend.feature.ceremony.dto.CustomerQuoteDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyMarginOverride;
import com.eformworks.signstage.backend.feature.ceremony.entity.CustomerQuote;
import com.eformworks.signstage.backend.feature.ceremony.entity.CustomerQuoteLine;
import com.eformworks.signstage.backend.feature.ceremony.entity.DiscountType;
import com.eformworks.signstage.backend.feature.ceremony.entity.MarginInfo;
import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationMarginPolicy;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyMarginOverrideRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CustomerQuoteLineRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CustomerQuoteRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OrganizationMarginPolicyRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.UnitProductRepository;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.error.OrganizationErrorCode;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 파트너 → 실고객 고객 견적서 — signstage-docs
 * business/partner-customer-quote-design-review.md 결정(2026-09-11 구현),
 * business/platform-partner-customer-billing-model-reference.md 4장. 조직 기본 마진
 * ({@link OrganizationMarginPolicy})과 행사별 override({@link CeremonyMarginOverride})
 * 2단 구조로 "시스템 사용료"(플랫폼→파트너 원가 기준선 + 마진) 금액을 계산하고, 장비·인력
 * (태블릿·현장지원 등)은 파트너가 직접 입력한 고객 단가를 그대로 담는다. 마진 설정·행사별
 * override·견적 생성/조회 전부 파트너 OWNER 전용이다({@code ACTION_MARGIN_POLICY_MANAGE}/
 * {@code ACTION_CUSTOMER_QUOTE_MANAGE}) — 플랫폼은 값 자체를 통제하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerQuoteService {

    private static final String ACTION_MARGIN_POLICY_MANAGE = "ACTION_MARGIN_POLICY_MANAGE";
    private static final String ACTION_CUSTOMER_QUOTE_MANAGE = "ACTION_CUSTOMER_QUOTE_MANAGE";

    private final OrganizationMarginPolicyRepository organizationMarginPolicyRepository;
    private final CeremonyMarginOverrideRepository ceremonyMarginOverrideRepository;
    private final CustomerQuoteRepository customerQuoteRepository;
    private final CustomerQuoteLineRepository customerQuoteLineRepository;
    private final UnitProductRepository unitProductRepository;
    private final OrganizationRepository organizationRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final CeremonyService ceremonyService;
    private final MoneyCalculator moneyCalculator;
    private final RolePermissionService rolePermissionService;

    // ==================== 조직 기본 마진 ====================

    public CustomerQuoteDto.Response.MarginPolicy retrieveOrganizationMarginPolicy(Long organizationId, Long currentUserId) {
        checkMarginPolicyAccess(organizationId, currentUserId);
        return organizationMarginPolicyRepository.findByOrganizationId(organizationId)
                .map(policy -> new CustomerQuoteDto.Response.MarginPolicy(
                        policy.getMargin().getMarginType().name(), policy.getMargin().getMarginValue()
                ))
                .orElseGet(() -> new CustomerQuoteDto.Response.MarginPolicy(null, null));
    }

    @Transactional
    public CustomerQuoteDto.Response.MarginPolicy updateOrganizationMarginPolicy(
            Long organizationId, Long currentUserId, CustomerQuoteDto.Request.UpdateMargin request
    ) {
        checkMarginPolicyAccess(organizationId, currentUserId);
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApplicationException(OrganizationErrorCode.ORGANIZATION_NOT_FOUND));
        MarginInfo margin = toMarginInfo(request.getMarginType(), request.getMarginValue());

        OrganizationMarginPolicy policy = organizationMarginPolicyRepository.findByOrganizationId(organizationId)
                .orElseGet(() -> OrganizationMarginPolicy.builder().organization(organization).margin(margin).build());
        policy.updateMargin(margin);
        organizationMarginPolicyRepository.save(policy);

        return new CustomerQuoteDto.Response.MarginPolicy(margin.getMarginType().name(), margin.getMarginValue());
    }

    private void checkMarginPolicyAccess(Long organizationId, Long currentUserId) {
        Member member = memberRepository.findByOrganizationIdAndUserIdAndStatus(organizationId, currentUserId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApplicationException(CommonErrorCode.ACCESS_DENIED));
        if (!rolePermissionService.isAllowed(member.getRole().name(), ACTION_MARGIN_POLICY_MANAGE)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    // ==================== 행사별 마진 override ====================

    public CustomerQuoteDto.Response.EffectiveMargin retrieveEffectiveMargin(Long organizationId, Long ceremonyId, Long currentUserId) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        checkCustomerQuoteAccess(actingMember);

        Optional<CeremonyMarginOverride> override = ceremonyMarginOverrideRepository.findByCeremonyId(ceremony.getId());
        if (override.isPresent()) {
            MarginInfo margin = override.get().getMargin();
            return new CustomerQuoteDto.Response.EffectiveMargin(margin.getMarginType().name(), margin.getMarginValue(), "CEREMONY_OVERRIDE");
        }
        Optional<OrganizationMarginPolicy> policy = organizationMarginPolicyRepository.findByOrganizationId(organizationId);
        if (policy.isPresent()) {
            MarginInfo margin = policy.get().getMargin();
            return new CustomerQuoteDto.Response.EffectiveMargin(margin.getMarginType().name(), margin.getMarginValue(), "ORGANIZATION_DEFAULT");
        }
        return new CustomerQuoteDto.Response.EffectiveMargin(null, null, "NONE");
    }

    @Transactional
    public CustomerQuoteDto.Response.EffectiveMargin updateCeremonyMarginOverride(
            Long organizationId, Long ceremonyId, Long currentUserId, CustomerQuoteDto.Request.UpdateMargin request
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        checkCustomerQuoteAccess(actingMember);

        MarginInfo margin = toMarginInfo(request.getMarginType(), request.getMarginValue());
        CeremonyMarginOverride override = ceremonyMarginOverrideRepository.findByCeremonyId(ceremony.getId())
                .orElseGet(() -> CeremonyMarginOverride.builder().ceremony(ceremony).margin(margin).build());
        override.updateMargin(margin);
        ceremonyMarginOverrideRepository.save(override);

        return new CustomerQuoteDto.Response.EffectiveMargin(margin.getMarginType().name(), margin.getMarginValue(), "CEREMONY_OVERRIDE");
    }

    @Transactional
    public void clearCeremonyMarginOverride(Long organizationId, Long ceremonyId, Long currentUserId) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        checkCustomerQuoteAccess(actingMember);

        CeremonyMarginOverride override = ceremonyMarginOverrideRepository.findByCeremonyId(ceremony.getId())
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CUSTOMER_MARGIN_OVERRIDE_NOT_SET));
        ceremonyMarginOverrideRepository.delete(override);
    }

    // ==================== 고객 견적서 ====================

    /**
     * 플랜이 확정(DRAFT → IN_PROGRESS)된 행사에서만 정산서를 만들 수 있다(2026-09-11 사용자
     * 요청 — 단위 상품 추가구매와 같은 기준, {@link CeremonyService#checkCeremonyPlanConfirmed}).
     * DRAFT 상태에서는 플랜 스냅샷이 아직 없어(2.4절, {@code findLatestPlanHistoryForSnapshot}이
     * 라이브 카탈로그 값으로 대체) {@link CeremonyService#buildQuoteCalculation}이 계산하는
     * 시스템 사용료 원가가 카탈로그 관리자의 변경에 따라 계속 바뀔 수 있는 잠정치다 — 그
     * 위에서 실고객에게 청구할 정산서를 만들면, 정산서 생성 이후 원가가 바뀌어도 그 정산서
     * 자체는 스냅샷이라 조용히 어긋난 기준으로 남는다.
     */
    @Transactional
    public CustomerQuoteDto.Response.QuoteDetail generateCustomerQuote(
            Long organizationId, Long ceremonyId, Long currentUserId, CustomerQuoteDto.Request.GenerateQuote request
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        checkCustomerQuoteAccess(actingMember);
        ceremonyService.checkCeremonyPlanConfirmed(ceremony);
        CurrencyPolicy currencyPolicy = ceremony.currencyPolicy();

        MarginInfo margin = ceremonyMarginOverrideRepository.findByCeremonyId(ceremony.getId())
                .map(CeremonyMarginOverride::getMargin)
                .or(() -> organizationMarginPolicyRepository.findByOrganizationId(organizationId).map(OrganizationMarginPolicy::getMargin))
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.MARGIN_NOT_SET));

        CeremonyService.QuoteCalculation calculation = ceremonyService.buildQuoteCalculation(ceremony);
        BigDecimal systemUsageCostAmount = calculation.lines().stream()
                .filter(line -> line.category().isSystemUsageFee())
                .map(CeremonyService.QuoteLineDetail::netAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 장비/인력은 더 이상 승인된 구매 기록에서 역산하지 않는다 — 파트너가 카탈로그에서
        // 직접 고른 품목·수량·고객 단가를 그대로 쓴다(signstage-docs
        // business/unit-product-purchase-self-checkout-review.md 8.5절 결정, 2026-09-11).
        List<CustomerQuoteDto.Request.EquipmentPersonnelLine> requestedLines =
                request.getEquipmentPersonnelLines() == null ? List.of() : request.getEquipmentPersonnelLines();
        if (systemUsageCostAmount.signum() == 0 && requestedLines.isEmpty()) {
            throw new ApplicationException(CeremonyErrorCode.CUSTOMER_QUOTE_EMPTY);
        }
        List<Long> requestedIds = requestedLines.stream()
                .map(CustomerQuoteDto.Request.EquipmentPersonnelLine::getUnitProductId)
                .toList();
        if (requestedIds.size() != requestedIds.stream().distinct().count()) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }

        BigDecimal systemUsageMarginAmount = moneyCalculator.applyMargin(systemUsageCostAmount, margin, currencyPolicy)
                .subtract(systemUsageCostAmount);
        BigDecimal systemUsageCustomerAmount = systemUsageCostAmount.add(systemUsageMarginAmount);

        // 장비/인력 줄별 고객 청구액을 먼저 전부 계산해둔다 — CustomerQuote 헤더를 한 번에
        // 완결된 값으로 만들기 위해서다(BillingQuote와 같은 완전-불변 빌더 원칙).
        record EquipmentPersonnelLineAmount(
                Long unitProductId, String itemName, Integer quantity, BigDecimal customerUnitAmount, BigDecimal customerAmount
        ) {
        }
        List<EquipmentPersonnelLineAmount> equipmentPersonnelLineAmounts = new ArrayList<>();
        BigDecimal equipmentPersonnelTotal = BigDecimal.ZERO;
        for (CustomerQuoteDto.Request.EquipmentPersonnelLine line : requestedLines) {
            if (line.getCustomerUnitAmount().signum() < 0) {
                throw new ApplicationException(CeremonyErrorCode.CUSTOMER_QUOTE_PRICE_INVALID);
            }
            UnitProduct unitProduct = unitProductRepository.findById(line.getUnitProductId())
                    .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_NOT_FOUND));
            if (unitProduct.getCategory().isSystemUsageFee()) {
                throw new ApplicationException(CeremonyErrorCode.CUSTOMER_QUOTE_ITEM_NOT_EQUIPMENT_PERSONNEL);
            }
            BigDecimal customerAmount = moneyCalculator.normalize(
                    line.getCustomerUnitAmount().multiply(BigDecimal.valueOf(line.getQuantity())), currencyPolicy
            );
            equipmentPersonnelTotal = equipmentPersonnelTotal.add(customerAmount);
            equipmentPersonnelLineAmounts.add(new EquipmentPersonnelLineAmount(
                    unitProduct.getId(), unitProduct.getName(), line.getQuantity(), line.getCustomerUnitAmount(), customerAmount
            ));
        }
        BigDecimal totalCustomerAmount = moneyCalculator.normalize(
                systemUsageCustomerAmount.add(equipmentPersonnelTotal), currencyPolicy
        );

        int nextVersion = customerQuoteRepository.findMaxVersion(ceremonyId) + 1;
        CustomerQuote quote = CustomerQuote.builder()
                .ceremony(ceremony)
                .version(nextVersion)
                .currencyCode(ceremony.getCurrencyCode())
                .currencyFractionDigits(ceremony.getCurrencyFractionDigits())
                .currencyRoundingMode(ceremony.getCurrencyRoundingMode())
                .systemUsageCostAmount(systemUsageCostAmount)
                .margin(margin)
                .systemUsageMarginAmount(systemUsageMarginAmount)
                .systemUsageCustomerAmount(systemUsageCustomerAmount)
                .equipmentPersonnelCustomerAmount(equipmentPersonnelTotal)
                .totalCustomerAmount(totalCustomerAmount)
                .pricingCalculatedAt(LocalDateTime.now())
                .build();
        customerQuoteRepository.save(quote);

        customerQuoteLineRepository.save(CustomerQuoteLine.builder()
                .customerQuote(quote)
                .lineType("SYSTEM_USAGE")
                .itemId(null)
                .itemName("시스템 사용료")
                .quantity(1)
                .referenceCostUnitAmount(systemUsageCostAmount)
                .customerUnitAmount(systemUsageCustomerAmount)
                .customerAmount(systemUsageCustomerAmount)
                .build());

        for (EquipmentPersonnelLineAmount lineAmount : equipmentPersonnelLineAmounts) {
            customerQuoteLineRepository.save(CustomerQuoteLine.builder()
                    .customerQuote(quote)
                    .lineType("EQUIPMENT_PERSONNEL")
                    .itemId(lineAmount.unitProductId())
                    .itemName(lineAmount.itemName())
                    .quantity(lineAmount.quantity())
                    // 파트너가 플랫폼에 내는 원가 자체가 없어졌다(8.2절 결정) — 참고 원가는
                    // 항상 비운다.
                    .referenceCostUnitAmount(null)
                    .customerUnitAmount(lineAmount.customerUnitAmount())
                    .customerAmount(lineAmount.customerAmount())
                    .build());
        }

        return findCustomerQuoteDetail(organizationId, ceremonyId, quote.getId(), currentUserId);
    }

    public List<CustomerQuoteDto.Response.QuoteSummary> findCustomerQuotes(Long organizationId, Long ceremonyId, Long currentUserId) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        checkCustomerQuoteAccess(actingMember);

        return customerQuoteRepository.findAllByCeremonyIdOrderByVersionDesc(ceremony.getId()).stream()
                .map(this::toSummary)
                .toList();
    }

    public CustomerQuoteDto.Response.QuoteDetail findCustomerQuoteDetail(
            Long organizationId, Long ceremonyId, Long quoteId, Long currentUserId
    ) {
        ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        checkCustomerQuoteAccess(actingMember);

        CustomerQuote quote = customerQuoteRepository.findByIdAndCeremonyId(quoteId, ceremonyId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CUSTOMER_QUOTE_NOT_FOUND));
        List<CustomerQuoteDto.Response.QuoteLineSummary> lines = customerQuoteLineRepository
                .findAllByCustomerQuoteIdOrderByIdAsc(quote.getId()).stream()
                .map(line -> new CustomerQuoteDto.Response.QuoteLineSummary(
                        line.getLineType(),
                        line.getItemId(),
                        line.getItemName(),
                        line.getQuantity(),
                        line.getReferenceCostUnitAmount(),
                        line.getCustomerUnitAmount(),
                        line.getCustomerAmount()
                ))
                .toList();
        return new CustomerQuoteDto.Response.QuoteDetail(toSummary(quote), lines);
    }

    private void checkCustomerQuoteAccess(Member actingMember) {
        if (!rolePermissionService.isAllowed(actingMember.getRole().name(), ACTION_CUSTOMER_QUOTE_MANAGE)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    private MarginInfo toMarginInfo(String marginType, BigDecimal marginValue) {
        try {
            return new MarginInfo(DiscountType.valueOf(marginType), marginValue);
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(CeremonyErrorCode.MARGIN_VALUE_INVALID);
        }
    }

    private CustomerQuoteDto.Response.QuoteSummary toSummary(CustomerQuote quote) {
        String createdByLoginId = userRepository.findById(quote.getCreatedBy()).map(User::getLoginId).orElse(null);
        return new CustomerQuoteDto.Response.QuoteSummary(
                quote.getId(),
                quote.getVersion(),
                quote.getCurrencyCode(),
                quote.getCurrencyFractionDigits(),
                quote.getSystemUsageCostAmount(),
                quote.getMargin().getMarginType().name(),
                quote.getMargin().getMarginValue(),
                quote.getSystemUsageMarginAmount(),
                quote.getSystemUsageCustomerAmount(),
                quote.getEquipmentPersonnelCustomerAmount(),
                quote.getTotalCustomerAmount(),
                quote.getPricingCalculatedAt(),
                createdByLoginId,
                quote.getCreatedAt()
        );
    }
}
