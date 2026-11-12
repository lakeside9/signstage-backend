package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.SalesChannel;
import com.eformworks.signstage.backend.feature.billing.service.BillingCatalogQueryService;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyAssignment;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyOrderRoute;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductPurchase;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductPurchaseLine;
import com.eformworks.signstage.backend.feature.ceremony.model.PlatformOrderCeremonyCommand;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyAssignmentRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyUnitProductPurchaseLineRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyUnitProductPurchaseRepository;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.error.OrganizationErrorCode;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플랫폼 수주 행사를 만든다 — 파트너가 발주를 수락하면 {@code feature.order}가 호출한다
 * (signstage-docs business/platform-order-partner-subcontract-review.md D03 안 A, D07).
 *
 * <ul>
 *   <li>행사는 수행 파트너 조직이 소유한다. 지금 접근 검사·운영 화면을 그대로 쓴다.</li>
 *   <li>플랜은 제공 범위로만 쓴다. 파트너 직접 수주와 같은 스냅샷을 남기고 곧바로 확정(IN_PROGRESS)
 *       상태로 시작한다. 구독은 소진하지 않는다.</li>
 *   <li>수주에서 추가한 단위 상품은 0원 자동 승인 구매로 남겨 한도·효과 묶음에 반영한다 — 파트너는
 *       플랫폼 이용료를 내지 않는다.</li>
 *   <li>발주를 수락한 사용자를 담당자로 배정한다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlatformOrderCeremonyService {

    private final CeremonyRepository ceremonyRepository;
    private final CeremonyAssignmentRepository ceremonyAssignmentRepository;
    private final CeremonyUnitProductPurchaseRepository ceremonyUnitProductPurchaseRepository;
    private final CeremonyUnitProductPurchaseLineRepository ceremonyUnitProductPurchaseLineRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final BillingCatalogQueryService billingCatalogQueryService;
    private final CeremonyCatalogService ceremonyCatalogService;
    private final CeremonyPlanService ceremonyPlanService;

    @Transactional
    public Ceremony createPlatformOrderCeremony(PlatformOrderCeremonyCommand command) {
        Organization organization = organizationRepository.findById(command.organizationId())
                .orElseThrow(() -> new ApplicationException(OrganizationErrorCode.ORGANIZATION_NOT_FOUND));
        BillingPlan plan = billingCatalogQueryService.findBillingPlanOrThrow(
                command.billingPlanId(), SalesChannel.PLATFORM_ORDER
        );
        // 플랫폼 수주 플랜에는 할인·판매 가격 기간이 없다 — 통화는 수주 통화로 확인한다(U05).
        ceremonyCatalogService.checkCurrencyMatches(organization.getBillingCurrencyCode(), command.currencyCode());

        Ceremony ceremony = Ceremony.builder()
                .organization(organization)
                .orderRoute(CeremonyOrderRoute.PLATFORM_ORDER)
                .billingPlan(plan)
                .title(command.title())
                .organizingInstitution(command.organizingInstitution())
                .contactName(command.contactName())
                .contactPhone(command.contactPhone())
                .contactEmail(command.contactEmail())
                .build();
        ceremony.updateSchedule(command.location(), command.startsAt(), command.endsAt());
        ceremonyRepository.save(ceremony);
        ceremonyPlanService.recordPlatformOrderPlanHistory(ceremony, plan, command.planTaxCode());
        ceremony.confirmPlan();

        saveScopeOptions(ceremony, command.options());

        User acceptingUser = userRepository.findById(command.acceptingUserId())
                .orElseThrow(() -> new ApplicationException(CommonErrorCode.ACCESS_DENIED));
        ceremonyAssignmentRepository.save(
                CeremonyAssignment.builder().ceremony(ceremony).user(acceptingUser).build()
        );
        return ceremony;
    }

    /**
     * 수락 후 교체된 발주의 행사를 새 파트너에게 넘긴다(D03). 기존 담당자 배정을 지우고 새로 수락한
     * 사용자를 담당자로 배정한다. 서명자·문서·하위 행사는 행사에 그대로 남는다.
     */
    @Transactional
    public Ceremony transferPlatformOrderCeremony(Ceremony ceremony, Long organizationId, Long acceptingUserId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApplicationException(OrganizationErrorCode.ORGANIZATION_NOT_FOUND));
        ceremonyCatalogService.checkCurrencyMatches(organization.getBillingCurrencyCode(), ceremony.getCurrencyCode());
        ceremony.transferTo(organization);
        ceremonyAssignmentRepository.deleteAllByCeremonyId(ceremony.getId());
        User acceptingUser = userRepository.findById(acceptingUserId)
                .orElseThrow(() -> new ApplicationException(CommonErrorCode.ACCESS_DENIED));
        ceremonyAssignmentRepository.save(
                CeremonyAssignment.builder().ceremony(ceremony).user(acceptingUser).build()
        );
        return ceremony;
    }

    /**
     * 발주가 수락 후 취소되면 이전 파트너의 담당자 배정을 지우고 행사 접근을 막는다. 새 파트너가 수락해
     * 행사를 옮기면({@link #transferPlatformOrderCeremony}) 다시 열린다.
     */
    @Transactional
    public void suspendPartnerAccess(Ceremony ceremony) {
        ceremonyAssignmentRepository.deleteAllByCeremonyId(ceremony.getId());
        ceremony.suspendPartnerAccess();
    }

    /**
     * 수주 범위 변경(D08)을 행사에 반영한다 — 장소·일정을 바꾸고, 늘어난 단위 상품 수량만 0원 자동 승인
     * 구매로 더한다. 줄어든 수량은 이미 확정된 구매를 되돌리지 않는다(한도가 남을 뿐 비용은 없다).
     */
    @Transactional
    public void applyScopeChange(
            Ceremony ceremony,
            String location,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            List<PlatformOrderCeremonyCommand.ScopeOption> additions
    ) {
        ceremony.updateSchedule(location, startsAt, endsAt);
        saveScopeOptions(ceremony, additions);
    }

    private void saveScopeOptions(Ceremony ceremony, List<PlatformOrderCeremonyCommand.ScopeOption> options) {
        if (options == null || options.isEmpty()) {
            return;
        }
        CeremonyUnitProductPurchase purchase = CeremonyUnitProductPurchase.builder().ceremony(ceremony).build();
        purchase.autoApprove();
        ceremonyUnitProductPurchaseRepository.save(purchase);
        for (PlatformOrderCeremonyCommand.ScopeOption option : options) {
            UnitProduct unitProduct = billingCatalogQueryService.findUnitProductOrThrow(
                    option.unitProductId(), SalesChannel.PLATFORM_ORDER
            );
            ceremonyUnitProductPurchaseLineRepository.save(
                    CeremonyUnitProductPurchaseLine.builder()
                            .purchase(purchase)
                            .unitProduct(unitProduct)
                            .quantity(option.quantity())
                            .currencyCode(ceremony.getCurrencyCode())
                            .purchasedName(unitProduct.getName())
                            .purchasedSalePrice(BigDecimal.ZERO)
                            .purchasedTaxCode(option.taxCode())
                            .build()
            );
        }
    }
}
