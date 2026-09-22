package com.eformworks.signstage.backend.feature.ceremony.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.money.MoneyCalculator;
import com.eformworks.signstage.backend.feature.ceremony.entity.*;
import com.eformworks.signstage.backend.feature.ceremony.repository.*;
import com.eformworks.signstage.backend.feature.organization.entity.*;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BillingSimulatorServiceTest {
    private final MemberRepository members = mock(MemberRepository.class);
    private final RolePermissionService permissions = mock(RolePermissionService.class);
    private final BillingPlanRepository plans = mock(BillingPlanRepository.class);
    private final BillingPlanUnitProductRepository lines = mock(BillingPlanUnitProductRepository.class);
    private final BillingPlanDiscountPeriodRepository periods = mock(BillingPlanDiscountPeriodRepository.class);
    private final UnitProductPricePeriodRepository prices = mock(UnitProductPricePeriodRepository.class);
    private final OrganizationBillingPlanDiscountRepository discounts = mock(OrganizationBillingPlanDiscountRepository.class);
    private final UnitProductRepository products = mock(UnitProductRepository.class);
    private final BillingSimulatorService service = new BillingSimulatorService(
            members, permissions, plans, lines, periods, prices, discounts, new MoneyCalculator(), products);

    @Test
    void deniesOtherOrganizationWithoutReadingCatalog() {
        assertThatThrownBy(() -> service.catalog(1L, 9L)).isInstanceOf(ApplicationException.class);
        verifyNoInteractions(plans, lines, prices, discounts);
    }

    @Test
    void deniesMemberWithoutMenuPermission() {
        when(members.findByOrganizationIdAndUserIdAndStatus(1L, 9L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(Member.builder().role(MemberRole.VIEWER).build()));
        assertThatThrownBy(() -> service.catalog(1L, 9L)).isInstanceOf(ApplicationException.class);
        verifyNoInteractions(plans, lines, prices, discounts);
    }

    @Test
    void returnsOnlyPlatformFeesWithPartnerDiscountAndSaleUnits() {
        prepare();
        var platform = product(10L, true, 2, 1000);
        var equipment = product(20L, false, 1, 2000);
        when(lines.findAllByBillingPlanId(2L)).thenReturn(List.of(platform, equipment));
        var override = mock(OrganizationBillingPlanDiscount.class);
        when(override.getDiscount()).thenReturn(new DiscountInfo(DiscountType.FIXED_AMOUNT, new BigDecimal("1000")));
        when(discounts.findEffective(eq(1L), eq(2L), any())).thenReturn(Optional.of(override));

        var plan = service.catalog(1L, 9L).plans().getFirst();
        assertThat(plan.products()).hasSize(1);
        assertThat(plan.products().getFirst().id()).isEqualTo(10L);
        assertThat(plan.products().getFirst().saleUnitQuantity()).isEqualTo(5);
        assertThat(plan.products().getFirst().maxPurchaseQuantity()).isEqualTo(20);
        assertThat(plan.subtotal()).isEqualByComparingTo("2000");
        assertThat(plan.appliedPrice()).isEqualByComparingTo("1500");
        assertThat(plan.partnerDiscount()).isTrue();
    }

    @Test
    void missingIncludedPriceDoesNotBecomeFreePlan() {
        prepare();
        var line = product(10L, true, 2, 1000);
        when(prices.findEffective(eq(10L), any())).thenReturn(Optional.empty());
        when(lines.findAllByBillingPlanId(2L)).thenReturn(List.of(line));
        assertThat(service.catalog(1L, 9L).plans()).isEmpty();
    }

    @Test
    void additionalProductsIncludePlatformItemsOutsidePlanAndExcludeNonPlatformItems() {
        prepare();
        var included = product(10L, true, 2, 1000);
        var outside = product(30L, true, 0, 2000);
        var equipment = product(40L, false, 0, 3000);
        when(lines.findAllByBillingPlanId(2L)).thenReturn(List.of(included));
        var allProducts = List.of(included.getUnitProduct(), outside.getUnitProduct(), equipment.getUnitProduct());
        when(products.findAllByOrderByDisplayOrderAscIdAsc()).thenReturn(allProducts);

        var catalog = service.catalog(1L, 9L);
        assertThat(catalog.plans().getFirst().products()).extracting(BillingSimulatorService.Product::id)
                .containsExactly(10L);
        assertThat(catalog.additionalProducts()).extracting(BillingSimulatorService.Product::id)
                .containsExactly(10L, 30L);
        assertThat(catalog.additionalProducts().get(1).salePrice()).isEqualByComparingTo("2000");
    }

    private void prepare() {
        var org = mock(Organization.class);
        when(org.getName()).thenReturn("판매사");
        when(org.getBillingCurrencyCode()).thenReturn("KRW");
        when(org.getDefaultTimeZoneId()).thenReturn("Asia/Seoul");
        var member = mock(Member.class);
        when(member.getOrganization()).thenReturn(org);
        when(member.getRole()).thenReturn(MemberRole.OWNER);
        when(members.findByOrganizationIdAndUserIdAndStatus(1L, 9L, MemberStatus.ACTIVE)).thenReturn(Optional.of(member));
        when(permissions.isAllowed("OWNER", "MENU_ORG_BILLING_SIMULATOR")).thenReturn(true);
        var plan = mock(BillingPlan.class);
        when(plan.getId()).thenReturn(2L);
        when(plans.findAll()).thenReturn(List.of(plan));
        var period = mock(BillingPlanDiscountPeriod.class);
        when(period.isActive()).thenReturn(true);
        when(period.getDiscount()).thenReturn(new DiscountInfo(DiscountType.PERCENT, BigDecimal.ZERO));
        when(periods.findEffective(eq(2L), any())).thenReturn(Optional.of(period));
    }

    private BillingPlanUnitProduct product(Long id, boolean platformFee, int quantity, int price) {
        var product = mock(UnitProduct.class);
        when(product.getId()).thenReturn(id);
        when(product.getType()).thenReturn(UnitProductType.TEMPLATES);
        when(product.isPlatformUsageFee()).thenReturn(platformFee);
        when(product.getSaleUnitQuantity()).thenReturn(5);
        when(product.getMaxPurchaseQuantity()).thenReturn(20);
        var line = mock(BillingPlanUnitProduct.class);
        when(line.getUnitProduct()).thenReturn(product);
        when(line.getIncludedQuantity()).thenReturn(quantity);
        var period = mock(UnitProductPricePeriod.class);
        when(period.isActive()).thenReturn(true);
        when(period.getPriceInfo()).thenReturn(ProductPriceInfo.of("KRW", null, BigDecimal.valueOf(price), "STANDARD"));
        when(prices.findEffective(eq(id), any())).thenReturn(Optional.of(period));
        return line;
    }
}
