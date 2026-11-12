package com.eformworks.signstage.backend.feature.order.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.audit.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.order.dto.PlatformAdminOrderPriceDto;
import com.eformworks.signstage.backend.feature.order.entity.EffectivePeriod;
import com.eformworks.signstage.backend.feature.order.entity.PartnerRateCard;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.repository.PartnerOrderRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerRateCardRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.error.OrganizationErrorCode;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 파트너 발주 단가표 관리 — signstage-docs business/platform-order-partner-subcontract-review.md
 * 6.2절, D05·D28 안 A. 조직을 지정하지 않으면 기본 단가표, 지정하면 그 파트너의 예외 단가표다.
 * 가격 기간 규칙·삭제 규칙과 권한은 {@link OrderPriceService}와 같다(발주 계산에 쓰인 단가표는 삭제할 수 없다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartnerRateCardService {

    private final PartnerRateCardRepository partnerRateCardRepository;
    private final PartnerOrderRepository partnerOrderRepository;
    private final OrganizationRepository organizationRepository;
    private final RolePermissionService rolePermissionService;
    private final PlatformAdminAuditLogRecorder auditLogRecorder;

    public List<PlatformAdminOrderPriceDto.Response.AdminPartnerRateCard> findPartnerRateCards() {
        return partnerRateCardRepository.findAllWithOrganization().stream()
                .map(rateCard -> PlatformAdminOrderPriceDto.Response.AdminPartnerRateCard.from(
                        rateCard, partnerOrderRepository.existsByPartnerRateCardId(rateCard.getId())))
                .toList();
    }

    @Transactional
    public PlatformAdminOrderPriceDto.Response.AdminPartnerRateCard createPartnerRateCard(
            Long adminUserId,
            String actingPlatformRole,
            PlatformAdminOrderPriceDto.Request.CreatePartnerRateCard request
    ) {
        OrderPriceService.checkCanManagePrices(rolePermissionService, actingPlatformRole);
        EffectivePeriod period = new EffectivePeriod(request.getEffectiveFrom(), request.getEffectiveTo());
        Organization organization = request.getOrganizationId() == null
                ? null
                : organizationRepository.findById(request.getOrganizationId())
                        .orElseThrow(() -> new ApplicationException(OrganizationErrorCode.ORGANIZATION_NOT_FOUND));
        if (partnerRateCardRepository.existsOverlap(
                request.getOrganizationId(), period.getEffectiveFrom(), period.effectiveToOrOpenEnd(), null)) {
            throw new ApplicationException(OrderErrorCode.PRICE_PERIOD_OVERLAP);
        }

        PartnerRateCard rateCard = partnerRateCardRepository.save(PartnerRateCard.builder()
                .organization(organization)
                .currencyCode(OrderPriceService.resolveCurrencyCode(request.getCurrencyCode()))
                .baseFee(request.getBaseFee())
                .tabletUnitRate(request.getTabletUnitRate())
                .wifiUnitRate(request.getWifiUnitRate())
                .onsiteStaffDayRate(request.getOnsiteStaffDayRate())
                .period(period)
                .build());
        auditLogRecorder.record(adminUserId, PlatformAdminAction.CREATE_PARTNER_RATE_CARD, null, request.getOrganizationId(),
                "partnerRateCardId=" + rateCard.getId() + ", organizationId=" + request.getOrganizationId());
        return PlatformAdminOrderPriceDto.Response.AdminPartnerRateCard.from(rateCard, false);
    }

    @Transactional
    public PlatformAdminOrderPriceDto.Response.AdminPartnerRateCard closePartnerRateCard(
            Long partnerRateCardId,
            Long adminUserId,
            String actingPlatformRole,
            PlatformAdminOrderPriceDto.Request.ClosePeriod request
    ) {
        OrderPriceService.checkCanManagePrices(rolePermissionService, actingPlatformRole);
        PartnerRateCard rateCard = partnerRateCardRepository.findById(partnerRateCardId)
                .orElseThrow(() -> new ApplicationException(OrderErrorCode.PRICE_NOT_FOUND));
        rateCard.close(request.getEffectiveTo());
        auditLogRecorder.record(adminUserId, PlatformAdminAction.CLOSE_ORDER_PRICE_PERIOD, null, null,
                "partnerRateCardId=" + partnerRateCardId + ", effectiveTo=" + request.getEffectiveTo());
        return PlatformAdminOrderPriceDto.Response.AdminPartnerRateCard.from(
                rateCard, partnerOrderRepository.existsByPartnerRateCardId(partnerRateCardId)
        );
    }

    @Transactional
    public void deletePartnerRateCard(Long partnerRateCardId, Long adminUserId, String actingPlatformRole) {
        OrderPriceService.checkCanManagePrices(rolePermissionService, actingPlatformRole);
        PartnerRateCard rateCard = partnerRateCardRepository.findById(partnerRateCardId)
                .orElseThrow(() -> new ApplicationException(OrderErrorCode.PRICE_NOT_FOUND));
        if (partnerOrderRepository.existsByPartnerRateCardId(partnerRateCardId)) {
            throw new ApplicationException(OrderErrorCode.PRICE_IN_USE);
        }
        partnerRateCardRepository.delete(rateCard);
        auditLogRecorder.record(adminUserId, PlatformAdminAction.DELETE_ORDER_PRICE, null,
                rateCard.getOrganization() != null ? rateCard.getOrganization().getId() : null,
                "partnerRateCardId=" + partnerRateCardId + ", effectiveFrom=" + rateCard.getPeriod().getEffectiveFrom());
    }
}
