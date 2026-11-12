package com.eformworks.signstage.backend.feature.order.service;

import com.eformworks.signstage.backend.feature.billing.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlanUnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductCategory;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.order.entity.Customer;
import com.eformworks.signstage.backend.feature.order.entity.EffectivePeriod;
import com.eformworks.signstage.backend.feature.order.entity.PartnerRateCard;
import com.eformworks.signstage.backend.feature.order.entity.RegionSurcharge;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrder;
import com.eformworks.signstage.backend.feature.order.entity.ServiceRegion;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

/** order feature 테스트 공용 픽스처. */
final class OrderFixtures {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 12);

    private OrderFixtures() {
    }

    static BillingPlan plan(Long id) {
        BillingPlan plan = BillingPlan.builder().name("소형 패키지").build();
        ReflectionTestUtils.setField(plan, "id", id);
        return plan;
    }

    static UnitProduct unitProduct(Long id, UnitProductType type, String name) {
        UnitProduct unitProduct = UnitProduct.builder()
                .type(type)
                .name(name)
                .category(UnitProductCategory.EQUIPMENT)
                .build();
        ReflectionTestUtils.setField(unitProduct, "id", id);
        return unitProduct;
    }

    static BillingPlanUnitProduct planUnitProduct(BillingPlan plan, UnitProduct unitProduct, int includedQuantity) {
        return BillingPlanUnitProduct.builder()
                .billingPlan(plan)
                .unitProduct(unitProduct)
                .includedQuantity(includedQuantity)
                .build();
    }

    static RegionSurcharge regionSurcharge(ServiceRegion region, long customerAmount, long partnerAmount) {
        return RegionSurcharge.builder()
                .region(region)
                .currencyCode("KRW")
                .customerAmount(BigDecimal.valueOf(customerAmount))
                .partnerAmount(BigDecimal.valueOf(partnerAmount))
                .taxCode("KR_VAT_STANDARD")
                .period(new EffectivePeriod(TODAY.minusDays(30), null))
                .build();
    }

    static PartnerRateCard rateCard(Organization organization, long baseFee, long tabletRate, long staffDayRate) {
        return PartnerRateCard.builder()
                .organization(organization)
                .currencyCode("KRW")
                .baseFee(BigDecimal.valueOf(baseFee))
                .tabletUnitRate(BigDecimal.valueOf(tabletRate))
                .onsiteStaffDayRate(BigDecimal.valueOf(staffDayRate))
                .period(new EffectivePeriod(TODAY.minusDays(30), null))
                .build();
    }

    static Organization organization(Long id) {
        Organization organization = Organization.builder().name("파트너" + id).code("P" + id).build();
        ReflectionTestUtils.setField(organization, "id", id);
        return organization;
    }

    static Customer customer(Long id) {
        Customer customer = Customer.builder()
                .name("고객사")
                .contactName("홍길동")
                .contactPhone("010-0000-0000")
                .contactEmail("customer@example.com")
                .build();
        ReflectionTestUtils.setField(customer, "id", id);
        return customer;
    }

    /** 고객 공급가 3,000,000원, 태블릿 4대, 현장 인력 1인·일인 수주. */
    static SalesOrder salesOrder(Long id, Customer customer, BillingPlan plan) {
        SalesOrder salesOrder = SalesOrder.builder().customer(customer).currencyCode("KRW").quoteAccessToken("token-" + id).build();
        salesOrder.updateQuote("협약식", plan, ServiceRegion.CAPITAL, "서울", LocalDateTime.of(2026, 12, 1, 10, 0),
                LocalDateTime.of(2026, 12, 1, 12, 0), TODAY.plusDays(30), null);
        salesOrder.applyAmounts(4, 1, BigDecimal.valueOf(3_000_000), BigDecimal.valueOf(300_000), null,
                BigDecimal.valueOf(1_300_000));
        ReflectionTestUtils.setField(salesOrder, "id", id);
        return salesOrder;
    }
}
