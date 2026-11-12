package com.eformworks.signstage.backend.feature.order.service;

import com.eformworks.signstage.backend.feature.order.dto.PartnerEvaluationDto;
import com.eformworks.signstage.backend.feature.order.model.PartnerInspectionStats;
import com.eformworks.signstage.backend.feature.order.model.PartnerOrderStats;
import com.eformworks.signstage.backend.feature.order.repository.OrderInspectionRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerOrderRepository;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import com.eformworks.signstage.backend.feature.organization.service.PartnerSubcontractProfileService;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 파트너 평가 — signstage-docs business/platform-order-partner-subcontract-review.md 11장 8단계.
 * 발주 응답(수락률·거절·만료)과 승인된 검수(하자·지체·감액·승인 간주·수행 평점)를 파트너별로
 * 집계한다. 플랫폼 내부용이며 파트너에게 보이지 않는다. 대상은 지금 발주 가능한 파트너와 발주
 * 이력이 있는 파트너다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartnerEvaluationService {

    private final PartnerOrderRepository partnerOrderRepository;
    private final OrderInspectionRepository orderInspectionRepository;
    private final OrganizationRepository organizationRepository;
    private final PartnerSubcontractProfileService partnerSubcontractProfileService;

    /** 발주 가능한 파트너를 먼저, 이름순으로 돌려준다. */
    public List<PartnerEvaluationDto.Response.PartnerEvaluation> findEvaluations() {
        LocalDateTime now = LocalDateTime.now(SalesOrderService.PLATFORM_ZONE);
        Map<Long, PartnerOrderStats> orderStats = partnerOrderRepository.aggregateByOrganization(now).stream()
                .collect(Collectors.toMap(PartnerOrderStats::organizationId, Function.identity()));
        Map<Long, PartnerInspectionStats> inspectionStats = orderInspectionRepository.aggregateApprovedByOrganization().stream()
                .collect(Collectors.toMap(PartnerInspectionStats::organizationId, Function.identity()));
        Set<Long> eligibleIds = new HashSet<>(partnerSubcontractProfileService.findOrderEligibleOrganizationIds());

        Set<Long> organizationIds = new HashSet<>(eligibleIds);
        organizationIds.addAll(orderStats.keySet());
        return organizationRepository.findAllById(organizationIds).stream()
                .map(organization -> PartnerEvaluationDto.Response.PartnerEvaluation.of(
                        organization.getId(),
                        organization.getName(),
                        eligibleIds.contains(organization.getId()),
                        orderStats.get(organization.getId()),
                        inspectionStats.get(organization.getId())
                ))
                .sorted(Comparator
                        .comparing(PartnerEvaluationDto.Response.PartnerEvaluation::isOrderEligible).reversed()
                        .thenComparing(PartnerEvaluationDto.Response.PartnerEvaluation::getOrganizationName))
                .toList();
    }
}
