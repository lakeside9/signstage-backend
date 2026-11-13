package com.eformworks.signstage.backend.feature.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.core.money.DiscountType;
import com.eformworks.signstage.backend.core.money.CurrencyPolicy;
import com.eformworks.signstage.backend.core.money.MoneyCalculator;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlanUnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.SalesChannel;
import com.eformworks.signstage.backend.feature.billing.entity.TaxPolicy;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.billing.service.BillingCatalogQueryService;
import com.eformworks.signstage.backend.feature.billing.service.TaxPolicyResolver;
import com.eformworks.signstage.backend.feature.order.entity.CustomerPrice;
import com.eformworks.signstage.backend.feature.order.entity.EffectivePeriod;
import com.eformworks.signstage.backend.feature.order.entity.RegionSurcharge;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderLineType;
import com.eformworks.signstage.backend.feature.order.entity.ServiceRegion;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.model.PartnerOrderAmount;
import com.eformworks.signstage.backend.feature.order.model.SalesQuoteCalculation;
import com.eformworks.signstage.backend.feature.order.model.SalesQuoteAdjustment;
import com.eformworks.signstage.backend.feature.order.model.SalesQuoteCustomItem;
import com.eformworks.signstage.backend.feature.order.model.SalesQuoteOption;
import com.eformworks.signstage.backend.feature.order.model.ScopeExtras;
import com.eformworks.signstage.backend.feature.order.repository.CustomerPriceRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerRateCardRepository;
import com.eformworks.signstage.backend.feature.order.repository.RegionSurchargeRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link OrderPricingService} 단위 테스트 — signstage-docs
 * business/platform-order-partner-subcontract-review.md 6장의 고객 견적·발주 금액 계산.
 */
@ExtendWith(MockitoExtension.class)
class OrderPricingServiceTest {

    private static final CurrencyPolicy KRW = CurrencyPolicy.krw();

    @Mock
    private CustomerPriceRepository customerPriceRepository;
    @Mock
    private RegionSurchargeRepository regionSurchargeRepository;
    @Mock
    private PartnerRateCardRepository partnerRateCardRepository;
    @Mock
    private BillingCatalogQueryService billingCatalogQueryService;
    @Mock
    private TaxPolicyResolver taxPolicyResolver;

    private OrderPricingService orderPricingService;

    @BeforeEach
    void setUp() {
        orderPricingService = new OrderPricingService(
                customerPriceRepository, regionSurchargeRepository, partnerRateCardRepository,
                billingCatalogQueryService, taxPolicyResolver, new MoneyCalculator(),
                new PlatformOrderMarginPolicy(new BigDecimal("0.2"))
        );
    }

    @Test
    @DisplayName("견적서 줄 설명 — 패키지 줄은 견적서 표시를 켠 포함 상품만, 옵션 줄은 상품 견적 설명, 나머지는 없다")
    void describeLine_showsOnlyFlaggedPlanInclusionsAndOptionDescriptions() {
        BillingPlan plan = OrderFixtures.plan(1L);
        UnitProduct templates = OrderFixtures.unitProduct(11L, UnitProductType.TEMPLATES, "템플릿 문서");
        ReflectionTestUtils.setField(templates, "saleUnitQuantity", 10);
        ReflectionTestUtils.setField(templates, "quoteDescription", "행사용 서명 문서 템플릿 제작");
        UnitProduct signers = OrderFixtures.unitProduct(12L, UnitProductType.SIGNERS, "서명자");
        UnitProduct tablet = OrderFixtures.unitProduct(13L, UnitProductType.TABLETS, "태블릿");
        BillingPlanUnitProduct templateLine = OrderFixtures.planUnitProduct(plan, templates, 2);
        BillingPlanUnitProduct signerLine = OrderFixtures.planUnitProduct(plan, signers, 1_500);
        BillingPlanUnitProduct tabletLine = OrderFixtures.planUnitProduct(plan, tablet, 0);
        ReflectionTestUtils.setField(signerLine, "showOnQuote", true);
        ReflectionTestUtils.setField(tabletLine, "showOnQuote", true);
        given(billingCatalogQueryService.findPlanUnitProducts(1L)).willReturn(List.of(templateLine, signerLine, tabletLine));
        ReflectionTestUtils.setField(tablet, "quoteDescription", "현장용 태블릿 대여");

        String planDescription = orderPricingService.describeLine(line(SalesOrderLineType.BILLING_PLAN, null), plan);
        String optionDescription = orderPricingService.describeLine(line(SalesOrderLineType.UNIT_PRODUCT, tablet), plan);
        String discountDescription = orderPricingService.describeLine(line(SalesOrderLineType.DISCOUNT, null), plan);

        // 견적서 표시를 끈 템플릿은 빠지고, 켰더라도 기본 포함이 0인 태블릿(추가구매만 가능)은 빠진다.
        assertThat(planDescription).isEqualTo("서명자 1,500명");
        assertThat(optionDescription).isEqualTo("현장용 태블릿 대여");
        assertThat(discountDescription).isNull();
    }

    @Test
    @DisplayName("고객 견적 — 패키지 + 옵션 + 지역 할증 − 할인, 줄별 부가세, 태블릿·인력 수량은 플랜 포함분과 옵션을 더한다")
    void calculateSalesQuote_sumsLinesAndScopeQuantities() {
        BillingPlan plan = OrderFixtures.plan(1L);
        UnitProduct tablet = OrderFixtures.unitProduct(11L, UnitProductType.TABLETS, "태블릿");
        UnitProduct staff = OrderFixtures.unitProduct(12L, UnitProductType.ONSITE_SUPPORT, "현장 인력");
        given(billingCatalogQueryService.findPlanUnitProducts(1L)).willReturn(List.of(
                OrderFixtures.planUnitProduct(plan, tablet, 4),
                OrderFixtures.planUnitProduct(plan, staff, 1)
        ));
        CustomerPrice planPrice = customerPrice(plan, null, 3_000_000);
        ReflectionTestUtils.setField(planPrice, "id", 201L);
        CustomerPrice tabletPrice = customerPrice(null, tablet, 50_000);
        ReflectionTestUtils.setField(tabletPrice, "id", 202L);
        RegionSurcharge surcharge = OrderFixtures.regionSurcharge(ServiceRegion.YEONGNAM_HONAM, 200_000, 150_000);
        ReflectionTestUtils.setField(surcharge, "id", 301L);
        given(customerPriceRepository.findEffectiveForPlan(1L, OrderFixtures.TODAY)).willReturn(Optional.of(planPrice));
        given(billingCatalogQueryService.findUnitProductOrThrow(11L, SalesChannel.PLATFORM_ORDER)).willReturn(tablet);
        given(customerPriceRepository.findEffectiveForUnitProduct(11L, OrderFixtures.TODAY)).willReturn(Optional.of(tabletPrice));
        given(regionSurchargeRepository.findEffective(ServiceRegion.YEONGNAM_HONAM, OrderFixtures.TODAY))
                .willReturn(Optional.of(surcharge));
        stubVat();

        SalesQuoteCalculation calculation = orderPricingService.calculateSalesQuote(
                plan, List.of(new SalesQuoteOption(11L, 2)), List.of(), ServiceRegion.YEONGNAM_HONAM, ScopeExtras.NONE,
                SalesQuoteAdjustment.fixed(BigDecimal.valueOf(100_000)), KRW, OrderFixtures.TODAY
        );

        assertThat(calculation.lines()).extracting(SalesQuoteCalculation.Line::lineType).containsExactly(
                SalesOrderLineType.BILLING_PLAN, SalesOrderLineType.UNIT_PRODUCT,
                SalesOrderLineType.REGION_SURCHARGE, SalesOrderLineType.DISCOUNT
        );
        // 3,000,000 + 50,000×2 + 200,000 − 100,000
        assertThat(calculation.supplyAmount()).isEqualByComparingTo("3200000");
        assertThat(calculation.vatAmount()).isEqualByComparingTo("320000");
        assertThat(calculation.tabletCount()).isEqualTo(6);
        assertThat(calculation.onsiteStaffPersonDays()).isEqualTo(1);
        // 가격표 사용 여부 판단용 — 패키지·옵션 줄은 고객 가격 행, 견적은 권역 할증 행을 남긴다.
        assertThat(calculation.lines()).extracting(SalesQuoteCalculation.Line::customerPriceId)
                .containsExactly(201L, 202L, null, null);
        assertThat(calculation.regionSurchargeId()).isEqualTo(301L);
    }

    @Test
    @DisplayName("고객 견적 — 퍼센트 할인은 할인 전 공급가 소계에 율을 곱해 할인 줄을 만들고, 정액 할인 값은 무시한다")
    void calculateSalesQuote_appliesPercentDiscount() {
        BillingPlan plan = OrderFixtures.plan(1L);
        given(billingCatalogQueryService.findPlanUnitProducts(1L)).willReturn(List.of());
        given(customerPriceRepository.findEffectiveForPlan(1L, OrderFixtures.TODAY))
                .willReturn(Optional.of(customerPrice(plan, null, 3_000_000)));
        given(regionSurchargeRepository.findEffective(ServiceRegion.YEONGNAM_HONAM, OrderFixtures.TODAY))
                .willReturn(Optional.of(OrderFixtures.regionSurcharge(ServiceRegion.YEONGNAM_HONAM, 200_000, 150_000)));
        stubVat();

        SalesQuoteCalculation calculation = orderPricingService.calculateSalesQuote(
                plan, List.of(), List.of(), ServiceRegion.YEONGNAM_HONAM, ScopeExtras.NONE,
                SalesQuoteAdjustment.of(DiscountType.PERCENT, BigDecimal.valueOf(999_999), new BigDecimal("12.5"), null), KRW, OrderFixtures.TODAY
        );

        // (3,000,000 + 200,000) × 12.5% = 400,000
        SalesQuoteCalculation.Line discountLine = calculation.lines().get(calculation.lines().size() - 1);
        assertThat(discountLine.lineType()).isEqualTo(SalesOrderLineType.DISCOUNT);
        assertThat(discountLine.name()).isEqualTo("영업 할인(12.5%)");
        assertThat(discountLine.amount()).isEqualByComparingTo("-400000");
        assertThat(calculation.supplyAmount()).isEqualByComparingTo("2800000");
        assertThat(calculation.vatAmount()).isEqualByComparingTo("280000");
    }

    @Test
    @DisplayName("고객 견적 — 할인 뒤 공급가를 절사 단위 미만에서 버리고, 부가세는 절사한 공급가로 계산한다")
    void calculateSalesQuote_truncatesTotal() {
        BillingPlan plan = OrderFixtures.plan(1L);
        given(billingCatalogQueryService.findPlanUnitProducts(1L)).willReturn(List.of());
        given(customerPriceRepository.findEffectiveForPlan(1L, OrderFixtures.TODAY))
                .willReturn(Optional.of(customerPrice(plan, null, 1_234_567)));
        given(regionSurchargeRepository.findEffective(ServiceRegion.CAPITAL, OrderFixtures.TODAY))
                .willReturn(Optional.of(OrderFixtures.regionSurcharge(ServiceRegion.CAPITAL, 0, 0)));
        stubVat();

        SalesQuoteCalculation calculation = orderPricingService.calculateSalesQuote(
                plan, List.of(), List.of(), ServiceRegion.CAPITAL, ScopeExtras.NONE,
                new SalesQuoteAdjustment(null, BigDecimal.TEN, 10_000), KRW, OrderFixtures.TODAY
        );

        // 공급가 1,234,567 − 할인 10%(123,457) = 1,111,110 → 10,000 단위로 1,110을 버려 1,110,000, 부가세 111,000
        SalesQuoteCalculation.Line truncationLine = calculation.lines().get(calculation.lines().size() - 1);
        assertThat(truncationLine.lineType()).isEqualTo(SalesOrderLineType.TRUNCATION);
        assertThat(truncationLine.name()).isEqualTo("단수 절사(10,000 단위)");
        assertThat(truncationLine.amount()).isEqualByComparingTo("-1110");
        assertThat(truncationLine.taxAmount()).isEqualByComparingTo("-111");
        assertThat(calculation.supplyAmount()).isEqualByComparingTo("1110000");
        assertThat(calculation.vatAmount()).isEqualByComparingTo("111000");
    }

    @Test
    @DisplayName("고객 견적 — 허용하지 않는 절사 단위는 거부한다")
    void calculateSalesQuote_rejectsUnknownTruncationUnit() {
        BillingPlan plan = OrderFixtures.plan(1L);
        given(billingCatalogQueryService.findPlanUnitProducts(1L)).willReturn(List.of());
        given(customerPriceRepository.findEffectiveForPlan(1L, OrderFixtures.TODAY))
                .willReturn(Optional.of(customerPrice(plan, null, 1_234_567)));
        given(regionSurchargeRepository.findEffective(ServiceRegion.CAPITAL, OrderFixtures.TODAY))
                .willReturn(Optional.of(OrderFixtures.regionSurcharge(ServiceRegion.CAPITAL, 0, 0)));
        stubVat();

        assertThatThrownBy(() -> orderPricingService.calculateSalesQuote(
                plan, List.of(), List.of(), ServiceRegion.CAPITAL, ScopeExtras.NONE, new SalesQuoteAdjustment(null, null, 500), KRW, OrderFixtures.TODAY
        )).isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("고객 견적 — 퍼센트 할인율이 100을 넘으면 거부한다")
    void calculateSalesQuote_rejectsPercentOverHundred() {
        BillingPlan plan = OrderFixtures.plan(1L);
        given(billingCatalogQueryService.findPlanUnitProducts(1L)).willReturn(List.of());
        given(customerPriceRepository.findEffectiveForPlan(1L, OrderFixtures.TODAY))
                .willReturn(Optional.of(customerPrice(plan, null, 3_000_000)));
        given(regionSurchargeRepository.findEffective(ServiceRegion.CAPITAL, OrderFixtures.TODAY))
                .willReturn(Optional.of(OrderFixtures.regionSurcharge(ServiceRegion.CAPITAL, 0, 0)));
        stubVat();

        assertThatThrownBy(() -> orderPricingService.calculateSalesQuote(
                plan, List.of(), List.of(), ServiceRegion.CAPITAL, ScopeExtras.NONE, new SalesQuoteAdjustment(null, new BigDecimal("100.01"), null), KRW, OrderFixtures.TODAY
        )).isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("고객 견적 — 기준일에 유효한 패키지 가격이 없으면 거부한다")
    void calculateSalesQuote_rejectsMissingPlanPrice() {
        BillingPlan plan = OrderFixtures.plan(1L);
        given(billingCatalogQueryService.findPlanUnitProducts(1L)).willReturn(List.of());
        given(customerPriceRepository.findEffectiveForPlan(1L, OrderFixtures.TODAY)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderPricingService.calculateSalesQuote(
                plan, List.of(), List.of(), ServiceRegion.CAPITAL, ScopeExtras.NONE, SalesQuoteAdjustment.NONE, KRW, OrderFixtures.TODAY
        )).isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.CUSTOMER_PRICE_NOT_FOUND);
    }

    @Test
    @DisplayName("발주 금액 — 파트너 예외 단가표가 없으면 기본 단가표로 계산한다")
    void calculatePartnerOrderAmount_fallsBackToDefaultRateCard() {
        given(partnerRateCardRepository.findEffectiveForPartner(7L, OrderFixtures.TODAY)).willReturn(Optional.empty());
        given(partnerRateCardRepository.findEffectiveDefault(OrderFixtures.TODAY))
                .willReturn(Optional.of(OrderFixtures.rateCard(null, 500_000, 100_000, 400_000)));
        given(regionSurchargeRepository.findEffective(ServiceRegion.CAPITAL, OrderFixtures.TODAY))
                .willReturn(Optional.of(OrderFixtures.regionSurcharge(ServiceRegion.CAPITAL, 0, 0)));

        PartnerOrderAmount amount = orderPricingService.calculatePartnerOrderAmount(
                7L, 4, 0, 1, ScopeExtras.NONE, null, ServiceRegion.CAPITAL, BigDecimal.valueOf(-50_000), KRW, OrderFixtures.TODAY
        );

        // 500,000 + 4×100,000 + 1×400,000 + 0 − 50,000
        assertThat(amount.totalAmount()).isEqualByComparingTo("1250000");
        assertThat(amount.adjustmentAmount()).isEqualByComparingTo("-50000");
    }

    @Test
    @DisplayName("고객 견적 — 리허설 별도일 인력은 현장 인력 가격, 숙박은 박수 × 인원 × 1박 금액으로 더하고 예비 태블릿은 넣지 않는다")
    void calculateSalesQuote_addsRehearsalAndLodgingButNotSpareTablets() {
        BillingPlan plan = OrderFixtures.plan(1L);
        UnitProduct staff = OrderFixtures.unitProduct(12L, UnitProductType.ONSITE_SUPPORT, "현장 인력");
        given(billingCatalogQueryService.findPlanUnitProducts(1L)).willReturn(List.of());
        given(customerPriceRepository.findEffectiveForPlan(1L, OrderFixtures.TODAY))
                .willReturn(Optional.of(customerPrice(plan, null, 3_000_000)));
        given(billingCatalogQueryService.findFirstUnitProductByType(UnitProductType.ONSITE_SUPPORT, SalesChannel.PLATFORM_ORDER)).willReturn(Optional.of(staff));
        given(customerPriceRepository.findEffectiveForUnitProduct(12L, OrderFixtures.TODAY))
                .willReturn(Optional.of(customerPrice(null, staff, 250_000)));
        given(regionSurchargeRepository.findEffective(ServiceRegion.JEJU_ISLAND, OrderFixtures.TODAY))
                .willReturn(Optional.of(OrderFixtures.regionSurcharge(ServiceRegion.JEJU_ISLAND, 0, 0, 100_000, 80_000)));
        stubVat();

        SalesQuoteCalculation calculation = orderPricingService.calculateSalesQuote(
                plan, List.of(), List.of(), ServiceRegion.JEJU_ISLAND, new ScopeExtras(3, 2, 1, 2), SalesQuoteAdjustment.NONE, KRW, OrderFixtures.TODAY
        );

        assertThat(calculation.lines()).extracting(SalesQuoteCalculation.Line::lineType).containsExactly(
                SalesOrderLineType.BILLING_PLAN, SalesOrderLineType.REHEARSAL_STAFF, SalesOrderLineType.LODGING
        );
        // 3,000,000 + 250,000×2 + 100,000×(1박×2명)
        assertThat(calculation.supplyAmount()).isEqualByComparingTo("3700000");
        assertThat(calculation.extras().spareTabletCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("발주 금액 — 예비 태블릿·리허설 별도일 인력·숙박을 단가표와 1박 파트너 금액으로 더한다")
    void calculatePartnerOrderAmount_includesExtras() {
        given(partnerRateCardRepository.findEffectiveDefault(OrderFixtures.TODAY))
                .willReturn(Optional.of(OrderFixtures.rateCard(null, 500_000, 100_000, 400_000)));
        given(regionSurchargeRepository.findEffective(ServiceRegion.JEJU_ISLAND, OrderFixtures.TODAY))
                .willReturn(Optional.of(OrderFixtures.regionSurcharge(ServiceRegion.JEJU_ISLAND, 0, 0, 100_000, 80_000)));

        PartnerOrderAmount amount = orderPricingService.calculatePartnerOrderAmount(
                null, 4, 0, 1, new ScopeExtras(2, 1, 1, 2), null, ServiceRegion.JEJU_ISLAND, null, KRW, OrderFixtures.TODAY
        );

        // 500,000 + (4+2)×100,000 + (1+1)×400,000 + 80,000×2
        assertThat(amount.totalAmount()).isEqualByComparingTo("2060000");
        assertThat(amount.lodgingPersonNights()).isEqualTo(2);
    }

    @Test
    @DisplayName("고객 견적 — 와이파이 장비 대수는 플랜 포함분과 옵션 수량을 더한다")
    void calculateSalesQuote_sumsWifiEquipment() {
        BillingPlan plan = OrderFixtures.plan(1L);
        UnitProduct wifi = OrderFixtures.unitProduct(13L, UnitProductType.WIFI_EQUIPMENT, "와이파이 장비 대여");
        given(billingCatalogQueryService.findPlanUnitProducts(1L)).willReturn(List.of(
                OrderFixtures.planUnitProduct(plan, wifi, 2)
        ));
        given(customerPriceRepository.findEffectiveForPlan(1L, OrderFixtures.TODAY))
                .willReturn(Optional.of(customerPrice(plan, null, 3_000_000)));
        given(billingCatalogQueryService.findUnitProductOrThrow(13L, SalesChannel.PLATFORM_ORDER)).willReturn(wifi);
        given(customerPriceRepository.findEffectiveForUnitProduct(13L, OrderFixtures.TODAY))
                .willReturn(Optional.of(customerPrice(null, wifi, 30_000)));
        given(regionSurchargeRepository.findEffective(ServiceRegion.CAPITAL, OrderFixtures.TODAY))
                .willReturn(Optional.of(OrderFixtures.regionSurcharge(ServiceRegion.CAPITAL, 0, 0)));
        stubVat();

        SalesQuoteCalculation calculation = orderPricingService.calculateSalesQuote(
                plan, List.of(new SalesQuoteOption(13L, 1)), List.of(), ServiceRegion.CAPITAL, ScopeExtras.NONE, SalesQuoteAdjustment.NONE, KRW, OrderFixtures.TODAY
        );

        // 3,000,000 + 30,000×1
        assertThat(calculation.supplyAmount()).isEqualByComparingTo("3030000");
        assertThat(calculation.wifiCount()).isEqualTo(3);
        assertThat(calculation.tabletCount()).isZero();
    }

    @Test
    @DisplayName("발주 금액 — 와이파이 장비 대수 × 단가표 대당 단가를 더하고 발주에 대수·단가를 남긴다")
    void calculatePartnerOrderAmount_includesWifiEquipment() {
        given(partnerRateCardRepository.findEffectiveDefault(OrderFixtures.TODAY))
                .willReturn(Optional.of(OrderFixtures.rateCard(null, 500_000, 100_000, 20_000, 400_000)));
        given(regionSurchargeRepository.findEffective(ServiceRegion.CAPITAL, OrderFixtures.TODAY))
                .willReturn(Optional.of(OrderFixtures.regionSurcharge(ServiceRegion.CAPITAL, 0, 0)));

        PartnerOrderAmount amount = orderPricingService.calculatePartnerOrderAmount(
                null, 4, 3, 1, ScopeExtras.NONE, null, ServiceRegion.CAPITAL, null, KRW, OrderFixtures.TODAY
        );

        // 500,000 + 4×100,000 + 3×20,000 + 1×400,000
        assertThat(amount.totalAmount()).isEqualByComparingTo("1360000");
        assertThat(amount.wifiCount()).isEqualTo(3);
        assertThat(amount.wifiUnitRate()).isEqualByComparingTo("20000");
    }

    @Test
    @DisplayName("고객 견적 — 직접 입력 항목은 패키지 세금 코드로 줄을 만들고, 수량 × 발주 단가를 발주 금액 합계로 낸다")
    void calculateSalesQuote_addsCustomItems() {
        BillingPlan plan = OrderFixtures.plan(1L);
        given(billingCatalogQueryService.findPlanUnitProducts(1L)).willReturn(List.of());
        given(customerPriceRepository.findEffectiveForPlan(1L, OrderFixtures.TODAY))
                .willReturn(Optional.of(customerPrice(plan, null, 3_000_000)));
        given(regionSurchargeRepository.findEffective(ServiceRegion.CAPITAL, OrderFixtures.TODAY))
                .willReturn(Optional.of(OrderFixtures.regionSurcharge(ServiceRegion.CAPITAL, 0, 0)));
        stubVat();

        SalesQuoteCalculation calculation = orderPricingService.calculateSalesQuote(
                plan, List.of(),
                List.of(
                        new SalesQuoteCustomItem(" 태블릿 받침대 ", 4, BigDecimal.valueOf(20_000), BigDecimal.valueOf(12_000)),
                        new SalesQuoteCustomItem("현수막 디자인", 1, BigDecimal.valueOf(150_000), null)
                ),
                ServiceRegion.CAPITAL, ScopeExtras.NONE, SalesQuoteAdjustment.NONE, KRW, OrderFixtures.TODAY
        );

        assertThat(calculation.lines()).extracting(SalesQuoteCalculation.Line::lineType).containsExactly(
                SalesOrderLineType.BILLING_PLAN, SalesOrderLineType.CUSTOM_ITEM, SalesOrderLineType.CUSTOM_ITEM
        );
        SalesQuoteCalculation.Line stand = calculation.lines().get(1);
        assertThat(stand.name()).isEqualTo("태블릿 받침대");
        assertThat(stand.amount()).isEqualByComparingTo("80000");
        assertThat(stand.taxCode()).isEqualTo("KR_VAT_STANDARD");
        assertThat(stand.partnerUnitPrice()).isEqualByComparingTo("12000");
        assertThat(stand.customerPriceId()).isNull();
        assertThat(calculation.lines().get(2).partnerUnitPrice()).isEqualByComparingTo("0");
        // 3,000,000 + 20,000×4 + 150,000
        assertThat(calculation.supplyAmount()).isEqualByComparingTo("3230000");
        // 12,000×4 + 0
        assertThat(calculation.customItemPartnerAmount()).isEqualByComparingTo("48000");
    }

    @Test
    @DisplayName("고객 견적 — 묶음 판매 상품은 개당 단가 × 개수로 계산하고, 판매 단위의 배수가 아니면 거부한다")
    void calculateSalesQuote_checksSaleUnitQuantity() {
        BillingPlan plan = OrderFixtures.plan(1L);
        UnitProduct templates = OrderFixtures.unitProduct(17L, UnitProductType.TEMPLATES, "템플릿 문서");
        ReflectionTestUtils.setField(templates, "saleUnitQuantity", 10);
        given(billingCatalogQueryService.findPlanUnitProducts(1L)).willReturn(List.of());
        given(customerPriceRepository.findEffectiveForPlan(1L, OrderFixtures.TODAY))
                .willReturn(Optional.of(customerPrice(plan, null, 3_000_000)));
        given(billingCatalogQueryService.findUnitProductOrThrow(17L, SalesChannel.PLATFORM_ORDER)).willReturn(templates);
        given(customerPriceRepository.findEffectiveForUnitProduct(17L, OrderFixtures.TODAY))
                .willReturn(Optional.of(customerPrice(null, templates, 1_000)));
        given(regionSurchargeRepository.findEffective(ServiceRegion.CAPITAL, OrderFixtures.TODAY))
                .willReturn(Optional.of(OrderFixtures.regionSurcharge(ServiceRegion.CAPITAL, 0, 0)));
        stubVat();

        SalesQuoteCalculation calculation = orderPricingService.calculateSalesQuote(
                plan, List.of(new SalesQuoteOption(17L, 20)), List.of(), ServiceRegion.CAPITAL, ScopeExtras.NONE, SalesQuoteAdjustment.NONE, KRW,
                OrderFixtures.TODAY
        );

        // 3,000,000 + 1,000 × 20개(10개 묶음 2개)
        assertThat(calculation.supplyAmount()).isEqualByComparingTo("3020000");
        assertThatThrownBy(() -> orderPricingService.calculateSalesQuote(
                plan, List.of(new SalesQuoteOption(17L, 15)), List.of(), ServiceRegion.CAPITAL, ScopeExtras.NONE, SalesQuoteAdjustment.NONE, KRW,
                OrderFixtures.TODAY
        )).isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.OPTION_SALE_UNIT_MISMATCH);
    }

    @Test
    @DisplayName("직접 입력 항목은 이름이 있고 금액이 음수가 아니어야 한다")
    void calculateSalesQuote_rejectsInvalidCustomItem() {
        BillingPlan plan = OrderFixtures.plan(1L);
        given(billingCatalogQueryService.findPlanUnitProducts(1L)).willReturn(List.of());
        given(customerPriceRepository.findEffectiveForPlan(1L, OrderFixtures.TODAY))
                .willReturn(Optional.of(customerPrice(plan, null, 3_000_000)));
        stubVat();

        assertThatThrownBy(() -> orderPricingService.calculateSalesQuote(
                plan, List.of(), List.of(new SalesQuoteCustomItem("받침대", 1, BigDecimal.TEN, BigDecimal.valueOf(-1))),
                ServiceRegion.CAPITAL, ScopeExtras.NONE, SalesQuoteAdjustment.NONE, KRW, OrderFixtures.TODAY
        )).isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("발주 금액 — 직접 입력 항목의 발주 금액 합계를 더한다")
    void calculatePartnerOrderAmount_includesCustomItems() {
        given(partnerRateCardRepository.findEffectiveDefault(OrderFixtures.TODAY))
                .willReturn(Optional.of(OrderFixtures.rateCard(null, 500_000, 100_000, 400_000)));
        given(regionSurchargeRepository.findEffective(ServiceRegion.CAPITAL, OrderFixtures.TODAY))
                .willReturn(Optional.of(OrderFixtures.regionSurcharge(ServiceRegion.CAPITAL, 0, 0)));

        PartnerOrderAmount amount = orderPricingService.calculatePartnerOrderAmount(
                null, 4, 0, 1, ScopeExtras.NONE, BigDecimal.valueOf(48_000), ServiceRegion.CAPITAL, null, KRW, OrderFixtures.TODAY
        );

        // 500,000 + 4×100,000 + 1×400,000 + 48,000
        assertThat(amount.totalAmount()).isEqualByComparingTo("1348000");
        assertThat(amount.customItemAmount()).isEqualByComparingTo("48000");
    }

    @Test
    @DisplayName("숙박은 박수와 인원을 함께 넣어야 한다")
    void calculatePartnerOrderAmount_rejectsHalfLodging() {
        assertThatThrownBy(() -> orderPricingService.calculatePartnerOrderAmount(
                null, 4, 0, 1, new ScopeExtras(0, 0, 1, 0), null, ServiceRegion.CAPITAL, null, KRW, OrderFixtures.TODAY
        )).isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("예상 발주 금액이 최소 마진(20%)을 깎으면 견적을 거부한다")
    void estimatePartnerAmountWithinMargin_rejectsBelowMinimumMargin() {
        given(partnerRateCardRepository.findEffectiveDefault(OrderFixtures.TODAY))
                .willReturn(Optional.of(OrderFixtures.rateCard(null, 500_000, 100_000, 400_000)));
        given(regionSurchargeRepository.findEffective(ServiceRegion.CAPITAL, OrderFixtures.TODAY))
                .willReturn(Optional.of(OrderFixtures.regionSurcharge(ServiceRegion.CAPITAL, 0, 0)));
        // 발주 1,300,000 / 공급가 1,500,000 → 마진 200,000 < 300,000(20%)
        SalesQuoteCalculation calculation = new SalesQuoteCalculation(
                List.of(), BigDecimal.valueOf(1_500_000), BigDecimal.valueOf(150_000), 4, 1, ScopeExtras.NONE, null, 0, BigDecimal.ZERO
        );

        assertThatThrownBy(() -> orderPricingService.estimatePartnerAmountWithinMargin(
                calculation, ServiceRegion.CAPITAL, KRW, OrderFixtures.TODAY
        )).isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.MARGIN_BELOW_MINIMUM);
    }

    private static SalesQuoteCalculation.Line line(SalesOrderLineType lineType, UnitProduct unitProduct) {
        return new SalesQuoteCalculation.Line(
                lineType, unitProduct, "줄", 1, BigDecimal.ONE, BigDecimal.ONE, "VAT", BigDecimal.TEN, BigDecimal.ZERO, null
        );
    }

    private void stubVat() {
        TaxPolicy taxPolicy = mock(TaxPolicy.class);
        given(taxPolicy.getRatePercent()).willReturn(BigDecimal.TEN);
        given(taxPolicyResolver.resolve(eq("KR"), anyString(), any())).willReturn(taxPolicy);
    }

    private static CustomerPrice customerPrice(BillingPlan plan, UnitProduct unitProduct, long unitPrice) {
        return CustomerPrice.builder()
                .billingPlan(plan)
                .unitProduct(unitProduct)
                .currencyCode("KRW")
                .unitPrice(BigDecimal.valueOf(unitPrice))
                .taxCode("KR_VAT_STANDARD")
                .period(new EffectivePeriod(OrderFixtures.TODAY.minusDays(30), null))
                .build();
    }
}
