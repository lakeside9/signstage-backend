package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.core.money.CurrencyPolicy;
import com.eformworks.signstage.backend.core.money.MoneyCalculator;
import com.eformworks.signstage.backend.feature.ceremony.entity.*;
import com.eformworks.signstage.backend.feature.ceremony.repository.*;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 판매사 전용 읽기 전용 카탈로그. 공급가와 타 조직 정보는 반환하지 않는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillingSimulatorService {
    private final MemberRepository memberRepository;
    private final RolePermissionService rolePermissionService;
    private final BillingPlanRepository billingPlanRepository;
    private final BillingPlanUnitProductRepository billingPlanUnitProductRepository;
    private final BillingPlanDiscountPeriodRepository billingPlanDiscountPeriodRepository;
    private final UnitProductPricePeriodRepository unitProductPricePeriodRepository;
    private final OrganizationBillingPlanDiscountRepository organizationBillingPlanDiscountRepository;
    private final MoneyCalculator moneyCalculator;
    private final UnitProductRepository unitProductRepository;

    public record Product(Long id, String name, String description, String type, int includedQuantity,
                          BigDecimal salePrice, int saleUnitQuantity, Integer maxPurchaseQuantity) {}
    public record Plan(Long id, String name, boolean subscription, BigDecimal subtotal,
                       BigDecimal appliedPrice, boolean partnerDiscount, List<Product> products) {}
    public record Catalog(String organizationName, String currencyCode, LocalDate asOfDate,
                          List<Plan> plans, List<Product> additionalProducts) {}
    private record Amount(BigDecimal value, boolean platformUsageFee) {}

    public Catalog catalog(Long organizationId, Long userId) {
        var member = memberRepository.findByOrganizationIdAndUserIdAndStatus(organizationId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApplicationException(CommonErrorCode.ACCESS_DENIED));
        if (!rolePermissionService.isAllowed(member.getRole().name(), "MENU_ORG_BILLING_SIMULATOR")) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
        var organization = member.getOrganization();
        String currency = organization.getBillingCurrencyCode();
        var policy = new CurrencyPolicy(currency, Currency.getInstance(currency).getDefaultFractionDigits(), RoundingMode.HALF_UP);
        LocalDate today = LocalDate.now(ZoneId.of(organization.getDefaultTimeZoneId()));
        List<Plan> plans = new ArrayList<>();
        for (var plan : billingPlanRepository.findAll()) {
            var period = billingPlanDiscountPeriodRepository.findEffective(plan.getId(), today);
            if (period.isEmpty() || !period.get().isActive()) continue;
            List<Product> products = new ArrayList<>();
            List<Amount> amounts = new ArrayList<>();
            boolean available = true;
            for (var line : billingPlanUnitProductRepository.findAllByBillingPlanId(plan.getId())) {
                var product = line.getUnitProduct();
                if (product.getType() == UnitProductType.ONSITE_SUPPORT_REQUEST) continue;
                var price = unitProductPricePeriodRepository.findEffective(product.getId(), today);
                if (price.isEmpty() || !price.get().isActive()
                        || !currency.equals(price.get().getPriceInfo().getCurrencyCode())) {
                    if (line.getIncludedQuantity() > 0) available = false;
                    continue;
                }
                if (line.getIncludedQuantity() > 0) {
                    amounts.add(new Amount(price.get().getPriceInfo().getSalePrice()
                            .multiply(BigDecimal.valueOf(line.getIncludedQuantity())), product.isPlatformUsageFee()));
                }
                if (!product.isPlatformUsageFee()) continue;
                Integer max = product.getType().isToggle()
                        ? Math.max(0, 1 - line.getIncludedQuantity()) : product.getMaxPurchaseQuantity();
                products.add(new Product(product.getId(), product.getName(), product.getDescription(), product.getType().name(),
                        line.getIncludedQuantity(), price.get().getPriceInfo().getSalePrice(),
                        product.getSaleUnitQuantity(), max));
            }
            if (!available || products.isEmpty()) continue;
            var override = organizationBillingPlanDiscountRepository.findEffective(organizationId, plan.getId(), today);
            var discount = override.map(OrganizationBillingPlanDiscount::getDiscount).orElse(period.get().getDiscount());
            BigDecimal subtotal = products.stream()
                    .map(p -> p.salePrice().multiply(BigDecimal.valueOf(p.includedQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            plans.add(new Plan(plan.getId(), plan.getName(), plan.isSubscription(), subtotal,
                    platformAppliedPrice(amounts, discount, policy), override.isPresent(), products));
        }
        List<Product> additionalProducts = new ArrayList<>();
        for (var product : unitProductRepository.findAllByOrderByDisplayOrderAscIdAsc()) {
            if (!product.isPlatformUsageFee()) continue;
            var price = unitProductPricePeriodRepository.findEffective(product.getId(), today);
            if (price.isEmpty() || !price.get().isActive()
                    || !currency.equals(price.get().getPriceInfo().getCurrencyCode())) continue;
            additionalProducts.add(new Product(product.getId(), product.getName(), product.getDescription(), product.getType().name(),
                    0, price.get().getPriceInfo().getSalePrice(), product.getSaleUnitQuantity(),
                    product.getType().isToggle() ? 1 : product.getMaxPurchaseQuantity()));
        }
        return new Catalog(organization.getName(), currency, today, plans, additionalProducts);
    }

    /** 실제 견적과 같이 플랜 전체 할인을 품목별로 배분한 후 플랫폼 이용료만 합산한다. */
    private BigDecimal platformAppliedPrice(List<Amount> amounts, DiscountInfo discount, CurrencyPolicy policy) {
        BigDecimal subtotal = amounts.stream().map(Amount::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (subtotal.signum() == 0) return BigDecimal.ZERO;
        BigDecimal applied = moneyCalculator.applyDiscount(subtotal, discount, policy);
        BigDecimal allocated = BigDecimal.ZERO;
        BigDecimal platform = BigDecimal.ZERO;
        for (int i = 0; i < amounts.size(); i++) {
            var amount = amounts.get(i);
            BigDecimal share = i == amounts.size() - 1 ? applied.subtract(allocated)
                    : moneyCalculator.normalize(amount.value().multiply(applied)
                            .divide(subtotal, 12, policy.roundingMode()), policy);
            allocated = allocated.add(share);
            if (amount.platformUsageFee()) platform = platform.add(share);
        }
        return platform;
    }
}
