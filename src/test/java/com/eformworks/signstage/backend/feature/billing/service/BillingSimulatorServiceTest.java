package com.eformworks.signstage.backend.feature.billing.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.money.DiscountType;
import com.eformworks.signstage.backend.core.money.MoneyCalculator;
import com.eformworks.signstage.backend.feature.billing.dto.BillingSimulatorDto;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlanDiscountPeriod;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlanUnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.DiscountInfo;
import com.eformworks.signstage.backend.feature.billing.entity.OrganizationBillingPlanDiscount;
import com.eformworks.signstage.backend.feature.billing.entity.ProductPriceInfo;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductPricePeriod;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.billing.repository.BillingPlanDiscountPeriodRepository;
import com.eformworks.signstage.backend.feature.billing.repository.BillingPlanRepository;
import com.eformworks.signstage.backend.feature.billing.repository.BillingPlanUnitProductRepository;
import com.eformworks.signstage.backend.feature.billing.repository.OrganizationBillingPlanDiscountRepository;
import com.eformworks.signstage.backend.feature.billing.repository.UnitProductPricePeriodRepository;
import com.eformworks.signstage.backend.feature.billing.repository.UnitProductRepository;
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

        var plan = service.catalog(1L, 9L).getPlans().getFirst();
        assertThat(plan.getProducts()).hasSize(1);
        assertThat(plan.getProducts().getFirst().getId()).isEqualTo(10L);
        assertThat(plan.getProducts().getFirst().getSaleUnitQuantity()).isEqualTo(5);
        assertThat(plan.getProducts().getFirst().getMaxPurchaseQuantity()).isEqualTo(20);
        // 플랫폼 이용료 2묶음 × 5개 × 1,000 = 10,000(장비 1묶음 × 5개 × 2,000 = 10,000과 할인 1,000을 반씩 나눈다)
        assertThat(plan.getProducts().getFirst().getIncludedQuantity()).isEqualTo(10);
        assertThat(plan.getSubtotal()).isEqualByComparingTo("10000");
        assertThat(plan.getAppliedPrice()).isEqualByComparingTo("9500");
        assertThat(plan.isPartnerDiscount()).isTrue();
    }

    @Test
    void missingIncludedPriceDoesNotBecomeFreePlan() {
        prepare();
        var line = product(10L, true, 2, 1000);
        when(prices.findEffective(eq(10L), any())).thenReturn(Optional.empty());
        when(lines.findAllByBillingPlanId(2L)).thenReturn(List.of(line));
        assertThat(service.catalog(1L, 9L).getPlans()).isEmpty();
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
        assertThat(catalog.getPlans().getFirst().getProducts()).extracting(BillingSimulatorDto.Response.Product::getId)
                .containsExactly(10L);
        assertThat(catalog.getAdditionalProducts()).extracting(BillingSimulatorDto.Response.Product::getId)
                .containsExactly(10L, 30L);
        assertThat(catalog.getAdditionalProducts().get(1).getSalePrice()).isEqualByComparingTo("2000");
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
        // 포함 수량은 묶음 수 — 실제 개수는 판매 단위(5)를 곱한다.
        when(line.totalQuantity()).thenReturn(quantity * 5);
        var period = mock(UnitProductPricePeriod.class);
        when(period.isActive()).thenReturn(true);
        when(period.getPriceInfo()).thenReturn(ProductPriceInfo.of("KRW", null, BigDecimal.valueOf(price), "STANDARD"));
        when(prices.findEffective(eq(id), any())).thenReturn(Optional.of(period));
        return line;
    }
}
