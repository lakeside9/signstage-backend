package com.eformworks.signstage.backend.feature.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.audit.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.billing.service.BillingCatalogQueryService;
import com.eformworks.signstage.backend.feature.billing.service.TaxPolicyResolver;
import com.eformworks.signstage.backend.feature.order.dto.PlatformAdminOrderPriceDto;
import com.eformworks.signstage.backend.feature.order.entity.CustomerPrice;
import com.eformworks.signstage.backend.feature.order.entity.EffectivePeriod;
import com.eformworks.signstage.backend.feature.order.entity.PartnerRateCard;
import com.eformworks.signstage.backend.feature.order.entity.RegionSurcharge;
import com.eformworks.signstage.backend.feature.order.entity.ServiceRegion;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.repository.CustomerPriceRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerOrderRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerRateCardRepository;
import com.eformworks.signstage.backend.feature.order.repository.RegionSurchargeRepository;
import com.eformworks.signstage.backend.feature.order.repository.SalesOrderChangeLineRepository;
import com.eformworks.signstage.backend.feature.order.repository.SalesOrderChangeRepository;
import com.eformworks.signstage.backend.feature.order.repository.SalesOrderLineRepository;
import com.eformworks.signstage.backend.feature.order.repository.SalesOrderRepository;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
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
 * 수주 가격표 삭제·수정 — 견적·수주·발주 계산에 쓰이지 않은 가격만 지우거나 고친다({@link OrderPriceService},
 * {@link PartnerRateCardService}).
 */
@ExtendWith(MockitoExtension.class)
class OrderPriceDeletionTest {

    private static final Long ADMIN_ID = 1L;
    private static final String ROLE = "PLATFORM_OPS";

    @Mock
    private CustomerPriceRepository customerPriceRepository;
    @Mock
    private RegionSurchargeRepository regionSurchargeRepository;
    @Mock
    private PartnerRateCardRepository partnerRateCardRepository;
    @Mock
    private SalesOrderLineRepository salesOrderLineRepository;
    @Mock
    private SalesOrderChangeLineRepository salesOrderChangeLineRepository;
    @Mock
    private SalesOrderRepository salesOrderRepository;
    @Mock
    private SalesOrderChangeRepository salesOrderChangeRepository;
    @Mock
    private PartnerOrderRepository partnerOrderRepository;
    @Mock
    private BillingCatalogQueryService billingCatalogQueryService;
    @Mock
    private TaxPolicyResolver taxPolicyResolver;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private RolePermissionService rolePermissionService;
    @Mock
    private PlatformAdminAuditLogRecorder auditLogRecorder;

    private OrderPriceService orderPriceService;
    private PartnerRateCardService partnerRateCardService;

    @BeforeEach
    void setUp() {
        orderPriceService = new OrderPriceService(
                customerPriceRepository, regionSurchargeRepository, salesOrderLineRepository, salesOrderChangeLineRepository,
                salesOrderRepository, salesOrderChangeRepository, partnerOrderRepository, billingCatalogQueryService,
                taxPolicyResolver, rolePermissionService, auditLogRecorder
        );
        partnerRateCardService = new PartnerRateCardService(
                partnerRateCardRepository, partnerOrderRepository, organizationRepository, rolePermissionService, auditLogRecorder
        );
        given(rolePermissionService.isAllowed(ROLE, "ACTION_ORDER_PRICE_MANAGE")).willReturn(true);
    }

    @Test
    @DisplayName("견적 줄에 쓰이지 않은 고객 가격은 삭제하고 감사 로그를 남긴다")
    void deleteCustomerPrice_deletesUnused() {
        CustomerPrice price = customerPrice(10L);
        given(customerPriceRepository.findById(10L)).willReturn(Optional.of(price));

        orderPriceService.deleteCustomerPrice(10L, ADMIN_ID, ROLE);

        verify(customerPriceRepository).delete(price);
        verify(auditLogRecorder).record(eq(ADMIN_ID), eq(PlatformAdminAction.DELETE_ORDER_PRICE), any(), any(), anyString());
    }

    @Test
    @DisplayName("수주 견적 줄이나 범위 변경 견적 줄에 쓰인 고객 가격은 삭제하지 않는다")
    void deleteCustomerPrice_rejectsUsed() {
        CustomerPrice price = customerPrice(10L);
        given(customerPriceRepository.findById(10L)).willReturn(Optional.of(price));
        given(salesOrderChangeLineRepository.existsByCustomerPriceId(10L)).willReturn(true);

        assertThatThrownBy(() -> orderPriceService.deleteCustomerPrice(10L, ADMIN_ID, ROLE))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.PRICE_IN_USE);
        verify(customerPriceRepository, never()).delete(any());
    }

    @Test
    @DisplayName("발주에 쓰인 권역 할증은 삭제하지 않고, 목록에는 사용 여부를 보여준다")
    void deleteRegionSurcharge_rejectsUsedByPartnerOrder() {
        RegionSurcharge surcharge = OrderFixtures.regionSurcharge(ServiceRegion.CAPITAL, 0, 0);
        ReflectionTestUtils.setField(surcharge, "id", 20L);
        given(regionSurchargeRepository.findById(20L)).willReturn(Optional.of(surcharge));
        given(regionSurchargeRepository.findAllOrdered()).willReturn(List.of(surcharge));
        given(partnerOrderRepository.existsByRegionSurchargeId(20L)).willReturn(true);

        assertThatThrownBy(() -> orderPriceService.deleteRegionSurcharge(20L, ADMIN_ID, ROLE))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.PRICE_IN_USE);
        assertThat(orderPriceService.findRegionSurcharges()).singleElement()
                .extracting(PlatformAdminOrderPriceDto.Response.AdminRegionSurcharge::isUsed).isEqualTo(true);
    }

    @Test
    @DisplayName("발주에 쓰이지 않은 파트너 단가표는 삭제하고, 쓰인 단가표는 거부한다")
    void deletePartnerRateCard_checksPartnerOrders() {
        PartnerRateCard unused = OrderFixtures.rateCard(null, 500_000, 100_000, 400_000);
        PartnerRateCard used = OrderFixtures.rateCard(null, 500_000, 100_000, 400_000);
        given(partnerRateCardRepository.findById(30L)).willReturn(Optional.of(unused));
        given(partnerRateCardRepository.findById(31L)).willReturn(Optional.of(used));
        given(partnerOrderRepository.existsByPartnerRateCardId(30L)).willReturn(false);
        given(partnerOrderRepository.existsByPartnerRateCardId(31L)).willReturn(true);

        partnerRateCardService.deletePartnerRateCard(30L, ADMIN_ID, ROLE);

        verify(partnerRateCardRepository).delete(unused);
        assertThatThrownBy(() -> partnerRateCardService.deletePartnerRateCard(31L, ADMIN_ID, ROLE))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.PRICE_IN_USE);
        verify(partnerRateCardRepository, never()).delete(used);
    }

    @Test
    @DisplayName("쓰이지 않은 고객 가격은 단가·기간을 고치고, 겹침 확인에서 자기 자신은 뺀다")
    void updateCustomerPrice_updatesUnused() {
        CustomerPrice price = customerPrice(10L);
        given(customerPriceRepository.findById(10L)).willReturn(Optional.of(price));
        PlatformAdminOrderPriceDto.Request.CreateCustomerPrice request = new PlatformAdminOrderPriceDto.Request.CreateCustomerPrice(
                null, 999L, "KRW", BigDecimal.valueOf(120_000), null, OrderFixtures.TODAY.plusDays(1), null
        );

        orderPriceService.updateCustomerPrice(10L, ADMIN_ID, ROLE, request);

        // 대상은 바꾸지 않는다 — 요청의 unitProductId(999)가 아니라 원래 상품(11)으로 겹침을 본다.
        verify(customerPriceRepository).existsOverlapForUnitProduct(11L, OrderFixtures.TODAY.plusDays(1), EffectivePeriod.OPEN_END, 10L);
        assertThat(price.getUnitPrice()).isEqualByComparingTo("120000");
        assertThat(price.getUnitProduct().getId()).isEqualTo(11L);
        assertThat(price.getPeriod().getEffectiveFrom()).isEqualTo(OrderFixtures.TODAY.plusDays(1));
        verify(auditLogRecorder).record(eq(ADMIN_ID), eq(PlatformAdminAction.UPDATE_ORDER_PRICE), any(), any(), anyString());
    }

    @Test
    @DisplayName("견적에 쓰인 고객 가격은 고치지 않는다")
    void updateCustomerPrice_rejectsUsed() {
        CustomerPrice price = customerPrice(10L);
        given(customerPriceRepository.findById(10L)).willReturn(Optional.of(price));
        given(salesOrderLineRepository.existsByCustomerPriceId(10L)).willReturn(true);
        PlatformAdminOrderPriceDto.Request.CreateCustomerPrice request = new PlatformAdminOrderPriceDto.Request.CreateCustomerPrice(
                null, 11L, "KRW", BigDecimal.valueOf(120_000), null, OrderFixtures.TODAY, null
        );

        assertThatThrownBy(() -> orderPriceService.updateCustomerPrice(10L, ADMIN_ID, ROLE, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.PRICE_IN_USE);
        assertThat(price.getUnitPrice()).isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("발주에 쓰인 파트너 단가표는 고치지 않는다")
    void updatePartnerRateCard_rejectsUsed() {
        PartnerRateCard used = OrderFixtures.rateCard(null, 500_000, 100_000, 400_000);
        given(partnerRateCardRepository.findById(31L)).willReturn(Optional.of(used));
        given(partnerOrderRepository.existsByPartnerRateCardId(31L)).willReturn(true);
        PlatformAdminOrderPriceDto.Request.CreatePartnerRateCard request = new PlatformAdminOrderPriceDto.Request.CreatePartnerRateCard(
                null, "KRW", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, OrderFixtures.TODAY, null
        );

        assertThatThrownBy(() -> partnerRateCardService.updatePartnerRateCard(31L, ADMIN_ID, ROLE, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.PRICE_IN_USE);
    }

    private static CustomerPrice customerPrice(Long id) {
        CustomerPrice price = CustomerPrice.builder()
                .unitProduct(OrderFixtures.unitProduct(11L, UnitProductType.TABLETS, "태블릿"))
                .currencyCode("KRW")
                .unitPrice(BigDecimal.valueOf(100_000))
                .taxCode("KR_VAT_STANDARD")
                .period(new EffectivePeriod(OrderFixtures.TODAY, null))
                .build();
        ReflectionTestUtils.setField(price, "id", id);
        return price;
    }
}
