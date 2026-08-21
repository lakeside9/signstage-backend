package com.eformworks.signstage.backend.feature.ceremony.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.OrganizationDiscountDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.ceremony.entity.DiscountType;
import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationBillingPlanDiscount;
import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationBillingPlanDiscountHistory;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CapacityAddOnRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OrganizationBillingPlanDiscountHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OrganizationBillingPlanDiscountRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OrganizationCapacityAddOnDiscountHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OrganizationCapacityAddOnDiscountRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OrganizationOptionalFeatureDiscountHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OrganizationOptionalFeatureDiscountRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link OrganizationDiscountService}의 해석 로직(오버라이드 있으면 오버라이드, 없으면 카탈로그
 * 값)과 관리자 CRUD(PLATFORM_OPS 이상만 변경 가능, 조회는 게이트 없음) 단위 테스트. signstage-docs
 * business/organization-event-discount-pricing-review.md 4.1절(2026-08-21 재검토) 참고.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationDiscountServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private BillingPlanRepository billingPlanRepository;
    @Mock
    private OptionalFeatureRepository optionalFeatureRepository;
    @Mock
    private CapacityAddOnRepository capacityAddOnRepository;
    @Mock
    private OrganizationBillingPlanDiscountRepository organizationBillingPlanDiscountRepository;
    @Mock
    private OrganizationOptionalFeatureDiscountRepository organizationOptionalFeatureDiscountRepository;
    @Mock
    private OrganizationCapacityAddOnDiscountRepository organizationCapacityAddOnDiscountRepository;
    @Mock
    private OrganizationBillingPlanDiscountHistoryRepository organizationBillingPlanDiscountHistoryRepository;
    @Mock
    private OrganizationOptionalFeatureDiscountHistoryRepository organizationOptionalFeatureDiscountHistoryRepository;
    @Mock
    private OrganizationCapacityAddOnDiscountHistoryRepository organizationCapacityAddOnDiscountHistoryRepository;
    @Mock
    private PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;

    @InjectMocks
    private OrganizationDiscountService organizationDiscountService;

    private static final Long ORGANIZATION_ID = 1L;
    private static final Long PLAN_ID = 101L;

    private Organization organization() {
        Organization organization = Organization.builder().name("조직").code("ORG1").build();
        ReflectionTestUtils.setField(organization, "id", ORGANIZATION_ID);
        return organization;
    }

    private BillingPlan plan() {
        BillingPlan plan = BillingPlan.builder()
                .name("스탠다드")
                .supplyPrice(new BigDecimal("100000"))
                .salePrice(new BigDecimal("90000"))
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("10000"))
                .maxSigners(10)
                .maxTemplates(10)
                .maxTestEvents(1)
                .maxMainEvents(1)
                .build();
        ReflectionTestUtils.setField(plan, "id", PLAN_ID);
        return plan;
    }

    @Test
    @DisplayName("오버라이드가 없으면 카탈로그(BillingPlan) 자체의 할인값을 그대로 돌려준다")
    void resolveBillingPlanDiscount_withoutOverride_returnsCatalogValue() {
        Organization organization = organization();
        BillingPlan plan = plan();
        given(organizationBillingPlanDiscountRepository.findByOrganizationIdAndBillingPlanId(ORGANIZATION_ID, PLAN_ID))
                .willReturn(Optional.empty());

        OrganizationDiscountService.EffectiveDiscount result =
                organizationDiscountService.resolveBillingPlanDiscount(organization, plan);

        assertThat(result.type()).isEqualTo(DiscountType.FIXED_AMOUNT);
        assertThat(result.value()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("오버라이드가 있으면 카탈로그 값 대신 오버라이드 값을 돌려준다")
    void resolveBillingPlanDiscount_withOverride_returnsOverrideValue() {
        Organization organization = organization();
        BillingPlan plan = plan();
        OrganizationBillingPlanDiscount override = OrganizationBillingPlanDiscount.builder()
                .organization(organization).billingPlan(plan)
                .discountType(DiscountType.PERCENT).discountValue(new BigDecimal("30"))
                .build();
        given(organizationBillingPlanDiscountRepository.findByOrganizationIdAndBillingPlanId(ORGANIZATION_ID, PLAN_ID))
                .willReturn(Optional.of(override));

        OrganizationDiscountService.EffectiveDiscount result =
                organizationDiscountService.resolveBillingPlanDiscount(organization, plan);

        assertThat(result.type()).isEqualTo(DiscountType.PERCENT);
        assertThat(result.value()).isEqualByComparingTo("30");
    }

    @Test
    @DisplayName("PLATFORM_OPS 미만 등급은 오버라이드를 설정할 수 없다")
    void setBillingPlanDiscount_insufficientRole_fail() {
        OrganizationDiscountDto.Request.SetDiscount request =
                new OrganizationDiscountDto.Request.SetDiscount("PERCENT", new BigDecimal("30"));

        assertThatThrownBy(() -> organizationDiscountService.setBillingPlanDiscount(
                ORGANIZATION_ID, PLAN_ID, "PLATFORM_SUPPORT", 1L, request
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);
        verify(organizationBillingPlanDiscountRepository, never()).save(any());
    }

    @Test
    @DisplayName("PLATFORM_OPS는 새 오버라이드를 설정할 수 있고 감사 로그를 남긴다")
    void setBillingPlanDiscount_success_createsOverrideAndRecordsAuditLog() {
        Organization organization = organization();
        BillingPlan plan = plan();
        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(organization));
        given(billingPlanRepository.findById(PLAN_ID)).willReturn(Optional.of(plan));
        given(organizationBillingPlanDiscountRepository.findByOrganizationIdAndBillingPlanId(ORGANIZATION_ID, PLAN_ID))
                .willReturn(Optional.empty());

        OrganizationDiscountDto.Request.SetDiscount request =
                new OrganizationDiscountDto.Request.SetDiscount("PERCENT", new BigDecimal("30"));

        OrganizationDiscountDto.Response.BillingPlanDiscountSummary response = organizationDiscountService.setBillingPlanDiscount(
                ORGANIZATION_ID, PLAN_ID, "PLATFORM_OPS", 1L, request
        );

        assertThat(response.getDiscountType()).isEqualTo("PERCENT");
        assertThat(response.getDiscountValue()).isEqualByComparingTo("30");
        verify(organizationBillingPlanDiscountRepository).save(any(OrganizationBillingPlanDiscount.class));
        verify(platformAdminAuditLogRecorder).record(
                eq(1L), eq(PlatformAdminAction.UPDATE_ORGANIZATION_BILLING_PLAN_DISCOUNT), isNull(), eq(ORGANIZATION_ID), any()
        );

        // 카탈로그(BillingPlanHistory)와 같은 패턴 — 설정 시점마다 그 값을 스냅샷 한 행 남긴다.
        ArgumentCaptor<OrganizationBillingPlanDiscountHistory> historyCaptor =
                ArgumentCaptor.forClass(OrganizationBillingPlanDiscountHistory.class);
        verify(organizationBillingPlanDiscountHistoryRepository).save(historyCaptor.capture());
        OrganizationBillingPlanDiscountHistory history = historyCaptor.getValue();
        assertThat(history.getDiscountType()).isEqualTo(DiscountType.PERCENT);
        assertThat(history.getDiscountValue()).isEqualByComparingTo("30");
        assertThat(history.isRemoved()).isFalse();
    }

    @Test
    @DisplayName("오버라이드 제거는 카탈로그 값으로 되돌리는 것 — 오버라이드 행이 삭제되고, 이력엔 제거 직전 값이 removed=true로 남는다")
    void removeBillingPlanDiscount_existingOverride_deletesRowAndRecordsHistory() {
        Organization organization = organization();
        BillingPlan plan = plan();
        OrganizationBillingPlanDiscount override = OrganizationBillingPlanDiscount.builder()
                .organization(organization).billingPlan(plan)
                .discountType(DiscountType.PERCENT).discountValue(new BigDecimal("30"))
                .build();

        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(organization));
        given(organizationBillingPlanDiscountRepository.findByOrganizationIdAndBillingPlanId(ORGANIZATION_ID, PLAN_ID))
                .willReturn(Optional.of(override));

        organizationDiscountService.removeBillingPlanDiscount(ORGANIZATION_ID, PLAN_ID, "PLATFORM_OPS", 1L);

        verify(organizationBillingPlanDiscountRepository).delete(override);

        ArgumentCaptor<OrganizationBillingPlanDiscountHistory> historyCaptor =
                ArgumentCaptor.forClass(OrganizationBillingPlanDiscountHistory.class);
        verify(organizationBillingPlanDiscountHistoryRepository).save(historyCaptor.capture());
        OrganizationBillingPlanDiscountHistory history = historyCaptor.getValue();
        assertThat(history.getDiscountType()).isEqualTo(DiscountType.PERCENT);
        assertThat(history.getDiscountValue()).isEqualByComparingTo("30");
        assertThat(history.isRemoved()).isTrue();
    }

    @Test
    @DisplayName("이력 조회는 organizationId+billingPlanId로 스코핑해 최신순으로 반환한다")
    void findBillingPlanDiscountHistory_returnsScopedHistory() {
        Organization organization = organization();
        BillingPlan plan = plan();
        OrganizationBillingPlanDiscountHistory setEvent = OrganizationBillingPlanDiscountHistory.builder()
                .organization(organization).billingPlan(plan)
                .discountType(DiscountType.PERCENT).discountValue(new BigDecimal("30"))
                .removed(false)
                .build();
        OrganizationBillingPlanDiscountHistory removeEvent = OrganizationBillingPlanDiscountHistory.builder()
                .organization(organization).billingPlan(plan)
                .discountType(DiscountType.PERCENT).discountValue(new BigDecimal("30"))
                .removed(true)
                .build();

        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(organization));
        given(billingPlanRepository.existsById(PLAN_ID)).willReturn(true);
        given(organizationBillingPlanDiscountHistoryRepository
                .findAllByOrganizationIdAndBillingPlanIdOrderByCreatedAtDesc(ORGANIZATION_ID, PLAN_ID))
                .willReturn(List.of(removeEvent, setEvent));

        var result = organizationDiscountService.findBillingPlanDiscountHistory(ORGANIZATION_ID, PLAN_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).isRemoved()).isTrue();
        assertThat(result.get(1).isRemoved()).isFalse();
    }
}
