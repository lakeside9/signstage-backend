package com.eformworks.signstage.backend.feature.order.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.core.i18n.InternationalizationDefaults;
import com.eformworks.signstage.backend.feature.audit.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.billing.service.BillingCatalogQueryService;
import com.eformworks.signstage.backend.feature.billing.service.TaxPolicyResolver;
import com.eformworks.signstage.backend.feature.order.dto.PlatformAdminOrderPriceDto;
import com.eformworks.signstage.backend.feature.order.entity.CustomerPrice;
import com.eformworks.signstage.backend.feature.order.entity.EffectivePeriod;
import com.eformworks.signstage.backend.feature.order.entity.RegionSurcharge;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.repository.CustomerPriceRepository;
import com.eformworks.signstage.backend.feature.order.repository.RegionSurchargeRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.util.Currency;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고객 가격표·지역 할증 관리 — signstage-docs
 * business/platform-order-partner-subcontract-review.md 6장, D06·D26. 가격은 고치지 않고 기간을
 * 닫은 뒤 새 기간을 만든다. 같은 대상에 기간이 겹치면 거부한다. 등록·종료는
 * {@code ACTION_ORDER_PRICE_MANAGE}가 필요하다. 파트너 단가표는 {@link PartnerRateCardService}가 맡는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderPriceService {

    static final String DEFAULT_TAX_CODE = "KR_VAT_STANDARD";
    private static final String TAX_COUNTRY_CODE = "KR";

    private final CustomerPriceRepository customerPriceRepository;
    private final RegionSurchargeRepository regionSurchargeRepository;
    private final BillingCatalogQueryService billingCatalogQueryService;
    private final TaxPolicyResolver taxPolicyResolver;
    private final RolePermissionService rolePermissionService;
    private final PlatformAdminAuditLogRecorder auditLogRecorder;

    public List<PlatformAdminOrderPriceDto.Response.AdminCustomerPrice> findCustomerPrices() {
        return customerPriceRepository.findAllWithTargets().stream()
                .map(PlatformAdminOrderPriceDto.Response.AdminCustomerPrice::from)
                .toList();
    }

    @Transactional
    public PlatformAdminOrderPriceDto.Response.AdminCustomerPrice createCustomerPrice(
            Long adminUserId,
            String actingPlatformRole,
            PlatformAdminOrderPriceDto.Request.CreateCustomerPrice request
    ) {
        checkCanManagePrices(rolePermissionService, actingPlatformRole);
        if ((request.getBillingPlanId() == null) == (request.getUnitProductId() == null)) {
            throw new ApplicationException(OrderErrorCode.PRICE_TARGET_INVALID);
        }
        EffectivePeriod period = new EffectivePeriod(request.getEffectiveFrom(), request.getEffectiveTo());
        String taxCode = resolveTaxCode(request.getTaxCode(), period);

        BillingPlan plan = null;
        UnitProduct unitProduct = null;
        boolean overlaps;
        if (request.getBillingPlanId() != null) {
            plan = billingCatalogQueryService.findBillingPlanOrThrow(request.getBillingPlanId());
            overlaps = customerPriceRepository.existsOverlapForPlan(
                    plan.getId(), period.getEffectiveFrom(), period.effectiveToOrOpenEnd(), null
            );
        } else {
            unitProduct = billingCatalogQueryService.findUnitProductOrThrow(request.getUnitProductId());
            overlaps = customerPriceRepository.existsOverlapForUnitProduct(
                    unitProduct.getId(), period.getEffectiveFrom(), period.effectiveToOrOpenEnd(), null
            );
        }
        if (overlaps) {
            throw new ApplicationException(OrderErrorCode.PRICE_PERIOD_OVERLAP);
        }

        CustomerPrice price = customerPriceRepository.save(CustomerPrice.builder()
                .billingPlan(plan)
                .unitProduct(unitProduct)
                .currencyCode(resolveCurrencyCode(request.getCurrencyCode()))
                .unitPrice(request.getUnitPrice())
                .taxCode(taxCode)
                .period(period)
                .build());
        auditLogRecorder.record(adminUserId, PlatformAdminAction.CREATE_CUSTOMER_PRICE, null, null,
                "customerPriceId=" + price.getId() + ", billingPlanId=" + request.getBillingPlanId()
                        + ", unitProductId=" + request.getUnitProductId() + ", unitPrice=" + price.getUnitPrice());
        return PlatformAdminOrderPriceDto.Response.AdminCustomerPrice.from(price);
    }

    @Transactional
    public PlatformAdminOrderPriceDto.Response.AdminCustomerPrice closeCustomerPrice(
            Long customerPriceId,
            Long adminUserId,
            String actingPlatformRole,
            PlatformAdminOrderPriceDto.Request.ClosePeriod request
    ) {
        checkCanManagePrices(rolePermissionService, actingPlatformRole);
        CustomerPrice price = customerPriceRepository.findById(customerPriceId)
                .orElseThrow(() -> new ApplicationException(OrderErrorCode.PRICE_NOT_FOUND));
        price.close(request.getEffectiveTo());
        auditLogRecorder.record(adminUserId, PlatformAdminAction.CLOSE_ORDER_PRICE_PERIOD, null, null,
                "customerPriceId=" + customerPriceId + ", effectiveTo=" + request.getEffectiveTo());
        return PlatformAdminOrderPriceDto.Response.AdminCustomerPrice.from(price);
    }

    public List<PlatformAdminOrderPriceDto.Response.AdminRegionSurcharge> findRegionSurcharges() {
        return regionSurchargeRepository.findAllOrdered().stream()
                .map(PlatformAdminOrderPriceDto.Response.AdminRegionSurcharge::from)
                .toList();
    }

    @Transactional
    public PlatformAdminOrderPriceDto.Response.AdminRegionSurcharge createRegionSurcharge(
            Long adminUserId,
            String actingPlatformRole,
            PlatformAdminOrderPriceDto.Request.CreateRegionSurcharge request
    ) {
        checkCanManagePrices(rolePermissionService, actingPlatformRole);
        EffectivePeriod period = new EffectivePeriod(request.getEffectiveFrom(), request.getEffectiveTo());
        String taxCode = resolveTaxCode(request.getTaxCode(), period);
        if (regionSurchargeRepository.existsOverlap(
                request.getRegion(), period.getEffectiveFrom(), period.effectiveToOrOpenEnd(), null)) {
            throw new ApplicationException(OrderErrorCode.PRICE_PERIOD_OVERLAP);
        }

        RegionSurcharge surcharge = regionSurchargeRepository.save(RegionSurcharge.builder()
                .region(request.getRegion())
                .currencyCode(resolveCurrencyCode(request.getCurrencyCode()))
                .customerAmount(request.getCustomerAmount())
                .partnerAmount(request.getPartnerAmount())
                .taxCode(taxCode)
                .period(period)
                .build());
        auditLogRecorder.record(adminUserId, PlatformAdminAction.CREATE_REGION_SURCHARGE, null, null,
                "regionSurchargeId=" + surcharge.getId() + ", region=" + surcharge.getRegion()
                        + ", customerAmount=" + surcharge.getCustomerAmount() + ", partnerAmount=" + surcharge.getPartnerAmount());
        return PlatformAdminOrderPriceDto.Response.AdminRegionSurcharge.from(surcharge);
    }

    @Transactional
    public PlatformAdminOrderPriceDto.Response.AdminRegionSurcharge closeRegionSurcharge(
            Long regionSurchargeId,
            Long adminUserId,
            String actingPlatformRole,
            PlatformAdminOrderPriceDto.Request.ClosePeriod request
    ) {
        checkCanManagePrices(rolePermissionService, actingPlatformRole);
        RegionSurcharge surcharge = regionSurchargeRepository.findById(regionSurchargeId)
                .orElseThrow(() -> new ApplicationException(OrderErrorCode.PRICE_NOT_FOUND));
        surcharge.close(request.getEffectiveTo());
        auditLogRecorder.record(adminUserId, PlatformAdminAction.CLOSE_ORDER_PRICE_PERIOD, null, null,
                "regionSurchargeId=" + regionSurchargeId + ", effectiveTo=" + request.getEffectiveTo());
        return PlatformAdminOrderPriceDto.Response.AdminRegionSurcharge.from(surcharge);
    }

    /** 세금 코드가 시작일에 유효한 세율을 갖는지 확인한다(없으면 {@code BILLING_TAX_POLICY_NOT_FOUND}). */
    private String resolveTaxCode(String requestedTaxCode, EffectivePeriod period) {
        String taxCode = requestedTaxCode == null || requestedTaxCode.isBlank() ? DEFAULT_TAX_CODE : requestedTaxCode.trim();
        taxPolicyResolver.resolve(TAX_COUNTRY_CODE, taxCode, period.getEffectiveFrom());
        return taxCode;
    }

    static void checkCanManagePrices(RolePermissionService rolePermissionService, String actingPlatformRole) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, "ACTION_ORDER_PRICE_MANAGE")) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    static String resolveCurrencyCode(String requestedCurrencyCode) {
        String currencyCode = requestedCurrencyCode == null || requestedCurrencyCode.isBlank()
                ? InternationalizationDefaults.CURRENCY_CODE
                : requestedCurrencyCode.trim().toUpperCase();
        try {
            Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
        return currencyCode;
    }
}
