package com.eformworks.signstage.backend.feature.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;

import com.eformworks.signstage.backend.feature.order.dto.PartnerEvaluationDto;
import com.eformworks.signstage.backend.feature.order.model.PartnerInspectionStats;
import com.eformworks.signstage.backend.feature.order.model.PartnerOrderStats;
import com.eformworks.signstage.backend.feature.order.repository.OrderInspectionRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerOrderRepository;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import com.eformworks.signstage.backend.feature.organization.service.PartnerSubcontractProfileService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link PartnerEvaluationService} 단위 테스트 — 파트너 평가 집계(11장 8단계). */
@ExtendWith(MockitoExtension.class)
class PartnerEvaluationServiceTest {

    @Mock
    private PartnerOrderRepository partnerOrderRepository;
    @Mock
    private OrderInspectionRepository orderInspectionRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private PartnerSubcontractProfileService partnerSubcontractProfileService;

    @InjectMocks
    private PartnerEvaluationService partnerEvaluationService;

    @Test
    @DisplayName("발주 응답과 검수 결과를 파트너별로 계산하고 발주 가능한 파트너를 먼저 둔다")
    void findEvaluations_combinesOrderAndInspectionStats() {
        LocalDateTime lastIssuedAt = LocalDateTime.of(2026, 11, 1, 10, 0);
        given(partnerOrderRepository.aggregateByOrganization(any())).willReturn(List.of(
                new PartnerOrderStats(7L, 8, 5, 1, 1, lastIssuedAt)
        ));
        given(orderInspectionRepository.aggregateApprovedByOrganization()).willReturn(List.of(
                new PartnerInspectionStats(7L, 4, 1, 1, 0, 1, new BigDecimal("50000.0000"), 4.333, 3)
        ));
        given(partnerSubcontractProfileService.findOrderEligibleOrganizationIds()).willReturn(List.of(8L));
        given(organizationRepository.findAllById(anyCollection())).willReturn(List.of(
                OrderFixtures.organization(7L), OrderFixtures.organization(8L)
        ));

        List<PartnerEvaluationDto.Response.PartnerEvaluation> evaluations = partnerEvaluationService.findEvaluations();

        assertThat(evaluations).extracting(PartnerEvaluationDto.Response.PartnerEvaluation::getOrganizationId)
                .containsExactly(8L, 7L);
        PartnerEvaluationDto.Response.PartnerEvaluation newcomer = evaluations.get(0);
        assertThat(newcomer.isOrderEligible()).isTrue();
        assertThat(newcomer.getIssuedCount()).isZero();
        assertThat(newcomer.getAcceptanceRatePercent()).isNull();
        assertThat(newcomer.getAverageRating()).isNull();

        PartnerEvaluationDto.Response.PartnerEvaluation veteran = evaluations.get(1);
        assertThat(veteran.isOrderEligible()).isFalse();
        // 수락 5 / (수락 5 + 거절 1 + 만료 1)
        assertThat(veteran.getAcceptanceRatePercent()).isEqualByComparingTo("71.4");
        assertThat(veteran.getAverageRating()).isEqualByComparingTo("4.3");
        assertThat(veteran.getDeductionTotal()).isEqualByComparingTo("50000");
        assertThat(veteran.getInspectedCount()).isEqualTo(4);
        assertThat(veteran.getLastIssuedAt()).isEqualTo(lastIssuedAt);
    }
}
