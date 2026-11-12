package com.eformworks.signstage.backend.feature.order.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.core.i18n.InternationalizationDefaults;
import com.eformworks.signstage.backend.core.money.CurrencyPolicy;
import com.eformworks.signstage.backend.feature.audit.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.billing.entity.SalesChannel;
import com.eformworks.signstage.backend.feature.billing.service.BillingCatalogQueryService;
import com.eformworks.signstage.backend.feature.order.dto.PlatformAdminPartnerOrderDto;
import com.eformworks.signstage.backend.feature.order.dto.PlatformAdminSalesOrderDto;
import com.eformworks.signstage.backend.feature.order.dto.SalesQuotePortalDto;
import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoiceStatus;
import com.eformworks.signstage.backend.feature.order.entity.Customer;
import com.eformworks.signstage.backend.feature.order.entity.CustomerStatus;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrder;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrderStatus;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrder;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderAcceptanceChannel;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderLine;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderLineType;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderStatus;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.model.SalesQuoteCalculation;
import com.eformworks.signstage.backend.feature.order.model.SalesQuoteOption;
import com.eformworks.signstage.backend.feature.order.repository.CustomerInvoiceRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerOrderRepository;
import com.eformworks.signstage.backend.feature.order.repository.SalesOrderLineRepository;
import com.eformworks.signstage.backend.feature.order.repository.SalesOrderRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플랫폼 수주(고객 견적·계약) — signstage-docs business/platform-order-partner-subcontract-review.md
 * 3장 1~2단계, D02·D06.
 *
 * <ul>
 *   <li>견적(QUOTED)은 관리자가 만들고 고친다. 저장할 때마다 고객 가격표로 금액을 다시 계산하고,
 *       기본 파트너 단가표로 예상 발주 금액을 계산해 최소 마진을 검사한다.</li>
 *   <li>고객은 견적 링크(토큰)로 로그인 없이 열람·수락한다. 메일·전화로 받은 수락은 관리자가 대신
 *       기록한다. 수락하면(ACCEPTED) 금액과 범위가 고정된다.</li>
 *   <li>취소는 파트너가 수락한 발주가 없을 때만 한다. 응답 대기 발주는 함께 취소한다.</li>
 * </ul>
 *
 * <p>날짜·시각 판단(견적 유효기간, 발주 응답 기한)은 플랫폼 기본 시간대(Asia/Seoul) 기준이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesOrderService {

    static final ZoneId PLATFORM_ZONE = ZoneId.of(InternationalizationDefaults.TIME_ZONE_ID);
    private static final int DEFAULT_QUOTE_VALID_DAYS = 30;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final PartnerOrderRepository partnerOrderRepository;
    private final CustomerInvoiceRepository customerInvoiceRepository;
    private final CustomerService customerService;
    private final BillingCatalogQueryService billingCatalogQueryService;
    private final OrderPricingService orderPricingService;
    private final RolePermissionService rolePermissionService;
    private final PlatformAdminAuditLogRecorder auditLogRecorder;

    public Page<PlatformAdminSalesOrderDto.Response.AdminSalesOrderSummary> findSalesOrders(
            SalesOrderStatus status,
            Long customerId,
            Pageable pageable
    ) {
        return salesOrderRepository.search(status, customerId, pageable)
                .map(PlatformAdminSalesOrderDto.Response.AdminSalesOrderSummary::from);
    }

    public PlatformAdminSalesOrderDto.Response.AdminSalesOrderDetail retrieveSalesOrder(Long salesOrderId) {
        return toDetail(findSalesOrderOrThrow(salesOrderId));
    }

    @Transactional
    public PlatformAdminSalesOrderDto.Response.AdminSalesOrderDetail createSalesOrder(
            Long adminUserId,
            String actingPlatformRole,
            PlatformAdminSalesOrderDto.Request.CreateSalesOrder request
    ) {
        checkCanManage(actingPlatformRole);
        Customer customer = customerService.findCustomerOrThrow(request.getCustomerId());
        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            throw new ApplicationException(OrderErrorCode.CUSTOMER_INACTIVE);
        }
        SalesOrder salesOrder = SalesOrder.builder()
                .customer(customer)
                .currencyCode(InternationalizationDefaults.CURRENCY_CODE)
                .quoteAccessToken(generateAccessToken())
                .build();
        applyQuote(salesOrder, request.getQuote(), true);

        auditLogRecorder.record(adminUserId, PlatformAdminAction.CREATE_SALES_ORDER, null, null,
                "salesOrderId=" + salesOrder.getId() + ", customerId=" + customer.getId()
                        + ", supplyAmount=" + salesOrder.getSupplyAmount());
        return toDetail(salesOrder);
    }

    @Transactional
    public PlatformAdminSalesOrderDto.Response.AdminSalesOrderDetail updateQuote(
            Long salesOrderId,
            Long adminUserId,
            String actingPlatformRole,
            PlatformAdminSalesOrderDto.Request.SaveQuote request
    ) {
        checkCanManage(actingPlatformRole);
        SalesOrder salesOrder = findQuotedOrThrow(salesOrderId);
        applyQuote(salesOrder, request, false);

        auditLogRecorder.record(adminUserId, PlatformAdminAction.UPDATE_SALES_ORDER, null, null,
                "salesOrderId=" + salesOrderId + ", supplyAmount=" + salesOrder.getSupplyAmount());
        return toDetail(salesOrder);
    }

    @Transactional
    public PlatformAdminSalesOrderDto.Response.AdminSalesOrderDetail recordAcceptance(
            Long salesOrderId,
            Long adminUserId,
            String actingPlatformRole,
            String acceptedByName
    ) {
        checkCanManage(actingPlatformRole);
        SalesOrder salesOrder = findQuotedOrThrow(salesOrderId);
        salesOrder.accept(acceptedByName, SalesOrderAcceptanceChannel.PLATFORM_RECORDED);

        auditLogRecorder.record(adminUserId, PlatformAdminAction.ACCEPT_SALES_ORDER, null, null,
                "salesOrderId=" + salesOrderId + ", acceptedByName=" + acceptedByName);
        return toDetail(salesOrder);
    }

    @Transactional
    public PlatformAdminSalesOrderDto.Response.AdminSalesOrderDetail cancelSalesOrder(
            Long salesOrderId,
            Long adminUserId,
            String actingPlatformRole,
            String reason
    ) {
        checkCanManage(actingPlatformRole);
        SalesOrder salesOrder = findSalesOrderOrThrow(salesOrderId);
        if (salesOrder.getStatus() == SalesOrderStatus.CANCELLED) {
            throw new ApplicationException(OrderErrorCode.SALES_ORDER_NOT_QUOTED);
        }
        List<PartnerOrder> partnerOrders = partnerOrderRepository.findAllBySalesOrderIdOrderByIssuedAtDesc(salesOrderId);
        if (partnerOrders.stream().anyMatch(order -> order.getStatus() == PartnerOrderStatus.ACCEPTED)) {
            throw new ApplicationException(OrderErrorCode.SALES_ORDER_HAS_ACCEPTED_PARTNER_ORDER);
        }
        if (customerInvoiceRepository.existsBySalesOrderIdAndStatusNot(salesOrderId, CustomerInvoiceStatus.CANCELLED)) {
            throw new ApplicationException(OrderErrorCode.SALES_ORDER_HAS_ACTIVE_INVOICE);
        }
        partnerOrders.stream()
                .filter(order -> order.getStatus() == PartnerOrderStatus.ISSUED)
                .forEach(order -> order.cancel(reason));
        salesOrder.cancel(reason);

        auditLogRecorder.record(adminUserId, PlatformAdminAction.CANCEL_SALES_ORDER, null, null,
                "salesOrderId=" + salesOrderId + ", reason=" + reason);
        return toDetail(salesOrder);
    }

    public SalesQuotePortalDto.Response.SalesQuote retrieveQuoteByAccessToken(String accessToken) {
        SalesOrder salesOrder = findByAccessTokenOrThrow(accessToken);
        return SalesQuotePortalDto.Response.SalesQuote.of(
                salesOrder, salesOrderLineRepository.findAllBySalesOrderIdOrderByDisplayOrder(salesOrder.getId())
        );
    }

    /** 고객이 견적 링크에서 수락한다(D02). 유효기간이 지났으면 거부한다. */
    @Transactional
    public SalesQuotePortalDto.Response.SalesQuote acceptQuoteByAccessToken(String accessToken, String acceptedByName) {
        SalesOrder salesOrder = findByAccessTokenOrThrow(accessToken);
        if (salesOrder.getStatus() != SalesOrderStatus.QUOTED) {
            throw new ApplicationException(OrderErrorCode.SALES_ORDER_NOT_QUOTED);
        }
        if (salesOrder.isQuoteExpired(LocalDate.now(PLATFORM_ZONE))) {
            throw new ApplicationException(OrderErrorCode.SALES_QUOTE_EXPIRED);
        }
        salesOrder.accept(acceptedByName, SalesOrderAcceptanceChannel.CUSTOMER_LINK);
        return SalesQuotePortalDto.Response.SalesQuote.of(
                salesOrder, salesOrderLineRepository.findAllBySalesOrderIdOrderByDisplayOrder(salesOrder.getId())
        );
    }

    SalesOrder findSalesOrderOrThrow(Long salesOrderId) {
        return salesOrderRepository.findById(salesOrderId)
                .orElseThrow(() -> new ApplicationException(OrderErrorCode.SALES_ORDER_NOT_FOUND));
    }

    /** 수주 범위 중 옵션(단위 상품) 줄 — 발주 수락 때 행사 범위로 옮기고, 파트너 화면에 보여준다. */
    List<SalesOrderLine> findOptionLines(Long salesOrderId) {
        return salesOrderLineRepository.findAllBySalesOrderIdOrderByDisplayOrder(salesOrderId).stream()
                .filter(line -> line.getLineType() == SalesOrderLineType.UNIT_PRODUCT)
                .toList();
    }

    /** 수주 패키지(플랜) 줄의 세금 코드 — 발주 수락 때 행사 플랜 스냅샷에 쓴다. */
    String findPlanTaxCode(Long salesOrderId) {
        return salesOrderLineRepository.findAllBySalesOrderIdOrderByDisplayOrder(salesOrderId).stream()
                .filter(line -> line.getLineType() == SalesOrderLineType.BILLING_PLAN)
                .map(SalesOrderLine::getTaxCode)
                .findFirst()
                .orElseThrow(() -> new ApplicationException(OrderErrorCode.SALES_ORDER_NOT_FOUND));
    }

    private void applyQuote(SalesOrder salesOrder, PlatformAdminSalesOrderDto.Request.SaveQuote request, boolean isNew) {
        if (request.getStartsAt() != null && request.getEndsAt() != null && request.getEndsAt().isBefore(request.getStartsAt())) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
        LocalDate today = LocalDate.now(PLATFORM_ZONE);
        LocalDate quoteValidUntil = request.getQuoteValidUntil() != null
                ? request.getQuoteValidUntil()
                : today.plusDays(DEFAULT_QUOTE_VALID_DAYS);
        if (quoteValidUntil.isBefore(today)) {
            throw new ApplicationException(OrderErrorCode.SALES_QUOTE_EXPIRED);
        }

        BillingPlan plan = billingCatalogQueryService.findBillingPlanOrThrow(request.getBillingPlanId(), SalesChannel.PLATFORM_ORDER);
        CurrencyPolicy currencyPolicy = salesOrder.currencyPolicy();
        List<SalesQuoteOption> options = request.getOptions() == null
                ? List.of()
                : request.getOptions().stream()
                        .map(option -> new SalesQuoteOption(option.getUnitProductId(), option.getQuantity()))
                        .toList();
        SalesQuoteCalculation calculation = orderPricingService.calculateSalesQuote(
                plan, options, request.getRegion(), request.scopeExtras(), request.getDiscountAmount(), currencyPolicy, today
        );
        BigDecimal estimatedPartnerAmount = orderPricingService.estimatePartnerAmountWithinMargin(
                calculation, request.getRegion(), currencyPolicy, today
        );

        salesOrder.updateQuote(
                request.getTitle(), plan, request.getRegion(), request.getLocation(), request.getStartsAt(),
                request.getEndsAt(), quoteValidUntil, request.getMemo()
        );
        salesOrder.applyAmounts(
                calculation.tabletCount(), calculation.wifiCount(), calculation.onsiteStaffPersonDays(), calculation.extras(),
                calculation.supplyAmount(), calculation.vatAmount(), request.getDiscountReason(), estimatedPartnerAmount
        );
        salesOrder.useRegionSurcharge(calculation.regionSurchargeId());
        if (isNew) {
            salesOrderRepository.save(salesOrder);
        } else {
            salesOrderLineRepository.deleteAllBySalesOrderId(salesOrder.getId());
        }
        int displayOrder = 0;
        for (SalesQuoteCalculation.Line line : calculation.lines()) {
            salesOrderLineRepository.save(SalesOrderLine.builder()
                    .salesOrder(salesOrder)
                    .lineType(line.lineType())
                    .unitProduct(line.unitProduct())
                    .name(line.name())
                    .quantity(line.quantity())
                    .unitPrice(line.unitPrice())
                    .amount(line.amount())
                    .taxCode(line.taxCode())
                    .taxRatePercent(line.taxRatePercent())
                    .taxAmount(line.taxAmount())
                    .displayOrder(displayOrder++)
                    .customerPriceId(line.customerPriceId())
                    .build());
        }
    }

    private PlatformAdminSalesOrderDto.Response.AdminSalesOrderDetail toDetail(SalesOrder salesOrder) {
        LocalDateTime now = LocalDateTime.now(PLATFORM_ZONE);
        List<PlatformAdminPartnerOrderDto.Response.AdminPartnerOrderSummary> partnerOrders =
                partnerOrderRepository.findAllBySalesOrderIdOrderByIssuedAtDesc(salesOrder.getId()).stream()
                        .map(order -> PlatformAdminPartnerOrderDto.Response.AdminPartnerOrderSummary.from(order, now))
                        .toList();
        return PlatformAdminSalesOrderDto.Response.AdminSalesOrderDetail.of(
                salesOrder, salesOrderLineRepository.findAllBySalesOrderIdOrderByDisplayOrder(salesOrder.getId()), partnerOrders
        );
    }

    private SalesOrder findQuotedOrThrow(Long salesOrderId) {
        SalesOrder salesOrder = findSalesOrderOrThrow(salesOrderId);
        if (salesOrder.getStatus() != SalesOrderStatus.QUOTED) {
            throw new ApplicationException(OrderErrorCode.SALES_ORDER_NOT_QUOTED);
        }
        return salesOrder;
    }

    private SalesOrder findByAccessTokenOrThrow(String accessToken) {
        return salesOrderRepository.findByQuoteAccessToken(accessToken)
                .filter(salesOrder -> salesOrder.getStatus() != SalesOrderStatus.CANCELLED)
                .orElseThrow(() -> new ApplicationException(OrderErrorCode.SALES_ORDER_NOT_FOUND));
    }

    private void checkCanManage(String actingPlatformRole) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, "ACTION_SALES_ORDER_MANAGE")) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    private static String generateAccessToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
