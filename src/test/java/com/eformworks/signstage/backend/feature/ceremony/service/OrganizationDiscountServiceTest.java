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
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OrganizationBillingPlanDiscountHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OrganizationBillingPlanDiscountRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * {@link OrganizationDiscountService}의 해석 로직(asOfDate에 유효한 오버라이드 기간이 있으면 그
 * 값, 없으면 카탈로그 값)과 관리자 CRUD(동적 RBAC, 기간 겹침 방지) 단위 테스트. signstage-docs
 * business/organization-discount-override-security-and-validity-period-review.md 결정
 * #4(2026-09-08, 안 B 채택) 참고.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationDiscountServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private BillingPlanRepository billingPlanRepository;
    @Mock
    private OrganizationBillingPlanDiscountRepository organizationBillingPlanDiscountRepository;
    @Mock
    private OrganizationBillingPlanDiscountHistoryRepository organizationBillingPlanDiscountHistoryRepository;
    @Mock
    private PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;
    @Mock
    private RolePermissionService rolePermissionService;

    @InjectMocks
    private OrganizationDiscountService organizationDiscountService;

    private static final Long ORGANIZATION_ID = 1L;
    private static final Long PLAN_ID = 101L;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 8);

    private Organization organization() {
        Organization organization = Organization.builder().name("조직").code("ORG1").build();
        ReflectionTestUtils.setField(organization, "id", ORGANIZATION_ID);
        return organization;
    }

    private BillingPlan plan() {
        BillingPlan plan = BillingPlan.builder().name("스탠다드").build();
        ReflectionTestUtils.setField(plan, "id", PLAN_ID);
        return plan;
    }

    @Test
    @DisplayName("asOfDate에 유효한 오버라이드 기간이 없으면 카탈로그(BillingPlan) 자체의 할인값을 그대로 돌려준다")
    void resolveBillingPlanDiscount_withoutEffectivePeriod_returnsCatalogValue() {
        Organization organization = organization();
        given(organizationBillingPlanDiscountRepository.findEffective(ORGANIZATION_ID, PLAN_ID, TODAY))
                .willReturn(Optional.empty());

        OrganizationDiscountService.EffectiveDiscount result = organizationDiscountService.resolveBillingPlanDiscount(
                organization, PLAN_ID, DiscountType.FIXED_AMOUNT, new BigDecimal("10000"), TODAY
        );

        assertThat(result.type()).isEqualTo(DiscountType.FIXED_AMOUNT);
        assertThat(result.value()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("asOfDate에 유효한 오버라이드 기간이 있으면 카탈로그 값 대신 그 기간의 값을 돌려준다")
    void resolveBillingPlanDiscount_withEffectivePeriod_returnsPeriodValue() {
        Organization organization = organization();
        BillingPlan plan = plan();
        OrganizationBillingPlanDiscount period = OrganizationBillingPlanDiscount.builder()
                .organization(organization).billingPlan(plan)
                .discountType(DiscountType.PERCENT).discountValue(new BigDecimal("30"))
                .effectiveFrom(TODAY.minusDays(1)).effectiveTo(null)
                .build();
        given(organizationBillingPlanDiscountRepository.findEffective(ORGANIZATION_ID, PLAN_ID, TODAY))
                .willReturn(Optional.of(period));

        OrganizationDiscountService.EffectiveDiscount result = organizationDiscountService.resolveBillingPlanDiscount(
                organization, PLAN_ID, DiscountType.FIXED_AMOUNT, new BigDecimal("10000"), TODAY
        );

        assertThat(result.type()).isEqualTo(DiscountType.PERCENT);
        assertThat(result.value()).isEqualByComparingTo("30");
    }

    @Test
    @DisplayName("권한이 없는 등급은 기간을 생성할 수 없다")
    void createBillingPlanDiscountPeriod_insufficientRole_fail() {
        given(rolePermissionService.isAllowed("PLATFORM_SUPPORT", "ACTION_ORGANIZATION_DISCOUNT_MANAGE")).willReturn(false);
        OrganizationDiscountDto.Request.SetDiscount request =
                new OrganizationDiscountDto.Request.SetDiscount("PERCENT", new BigDecimal("30"), TODAY, null);

        assertThatThrownBy(() -> organizationDiscountService.createBillingPlanDiscountPeriod(
                ORGANIZATION_ID, PLAN_ID, "PLATFORM_SUPPORT", 1L, request
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);
        verify(organizationBillingPlanDiscountRepository, never()).save(any());
    }

    @Test
    @DisplayName("권한이 있으면 새 기간을 생성할 수 있고 감사 로그를 남긴다")
    void createBillingPlanDiscountPeriod_success_createsPeriodAndRecordsAuditLog() {
        Organization organization = organization();
        BillingPlan plan = plan();
        given(rolePermissionService.isAllowed("PLATFORM_OPS", "ACTION_ORGANIZATION_DISCOUNT_MANAGE")).willReturn(true);
        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(organization));
        given(billingPlanRepository.findById(PLAN_ID)).willReturn(Optional.of(plan));
        given(organizationBillingPlanDiscountRepository.findAllByOrganizationIdAndBillingPlanIdOrderByEffectiveFromAsc(ORGANIZATION_ID, PLAN_ID))
                .willReturn(List.of());

        OrganizationDiscountDto.Request.SetDiscount request =
                new OrganizationDiscountDto.Request.SetDiscount("PERCENT", new BigDecimal("30"), TODAY, null);

        OrganizationDiscountDto.Response.BillingPlanDiscountSummary response = organizationDiscountService.createBillingPlanDiscountPeriod(
                ORGANIZATION_ID, PLAN_ID, "PLATFORM_OPS", 1L, request
        );

        assertThat(response.getDiscountType()).isEqualTo("PERCENT");
        assertThat(response.getDiscountValue()).isEqualByComparingTo("30");
        assertThat(response.getEffectiveFrom()).isEqualTo(TODAY);
        verify(organizationBillingPlanDiscountRepository).save(any(OrganizationBillingPlanDiscount.class));
        verify(platformAdminAuditLogRecorder).record(
                eq(1L), eq(PlatformAdminAction.UPDATE_ORGANIZATION_BILLING_PLAN_DISCOUNT), isNull(), eq(ORGANIZATION_ID), any()
        );

        ArgumentCaptor<OrganizationBillingPlanDiscountHistory> historyCaptor =
                ArgumentCaptor.forClass(OrganizationBillingPlanDiscountHistory.class);
        verify(organizationBillingPlanDiscountHistoryRepository).save(historyCaptor.capture());
        OrganizationBillingPlanDiscountHistory history = historyCaptor.getValue();
        assertThat(history.getDiscount().getDiscountType()).isEqualTo(DiscountType.PERCENT);
        assertThat(history.getDiscount().getDiscountValue()).isEqualByComparingTo("30");
        assertThat(history.isRemoved()).isFalse();
    }

    @Test
    @DisplayName("같은 조직×플랜에 겹치는 기간이 이미 있으면 새 기간 생성을 거부한다")
    void createBillingPlanDiscountPeriod_overlappingPeriod_rejected() {
        Organization organization = organization();
        BillingPlan plan = plan();
        given(rolePermissionService.isAllowed("PLATFORM_OPS", "ACTION_ORGANIZATION_DISCOUNT_MANAGE")).willReturn(true);
        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(organization));
        given(billingPlanRepository.findById(PLAN_ID)).willReturn(Optional.of(plan));
        OrganizationBillingPlanDiscount existing = OrganizationBillingPlanDiscount.builder()
                .organization(organization).billingPlan(plan)
                .discountType(DiscountType.PERCENT).discountValue(new BigDecimal("10"))
                .effectiveFrom(LocalDate.of(2026, 9, 1)).effectiveTo(LocalDate.of(2026, 9, 30))
                .build();
        ReflectionTestUtils.setField(existing, "id", 501L);
        given(organizationBillingPlanDiscountRepository.findAllByOrganizationIdAndBillingPlanIdOrderByEffectiveFromAsc(ORGANIZATION_ID, PLAN_ID))
                .willReturn(List.of(existing));

        OrganizationDiscountDto.Request.SetDiscount request = new OrganizationDiscountDto.Request.SetDiscount(
                "PERCENT", new BigDecimal("20"), LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 15)
        );

        assertThatThrownBy(() -> organizationDiscountService.createBillingPlanDiscountPeriod(
                ORGANIZATION_ID, PLAN_ID, "PLATFORM_OPS", 1L, request
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.DISCOUNT_PERIOD_OVERLAPPING);
        verify(organizationBillingPlanDiscountRepository, never()).save(any());
    }

    @Test
    @DisplayName("종료일이 시작일보다 빠르면 기간 생성을 거부한다")
    void createBillingPlanDiscountPeriod_invalidPeriod_rejected() {
        given(rolePermissionService.isAllowed("PLATFORM_OPS", "ACTION_ORGANIZATION_DISCOUNT_MANAGE")).willReturn(true);
        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(organization()));
        given(billingPlanRepository.findById(PLAN_ID)).willReturn(Optional.of(plan()));

        OrganizationDiscountDto.Request.SetDiscount request = new OrganizationDiscountDto.Request.SetDiscount(
                "PERCENT", new BigDecimal("20"), LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)
        );

        assertThatThrownBy(() -> organizationDiscountService.createBillingPlanDiscountPeriod(
                ORGANIZATION_ID, PLAN_ID, "PLATFORM_OPS", 1L, request
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.DISCOUNT_PERIOD_INVALID);
    }

    @Test
    @DisplayName("음수 할인값은 DiscountInfo 생성 시점에 거부된다 — 할증 방지")
    void createBillingPlanDiscountPeriod_negativeDiscountValue_rejected() {
        given(rolePermissionService.isAllowed("PLATFORM_OPS", "ACTION_ORGANIZATION_DISCOUNT_MANAGE")).willReturn(true);
        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(organization()));
        given(billingPlanRepository.findById(PLAN_ID)).willReturn(Optional.of(plan()));
        given(organizationBillingPlanDiscountRepository.findAllByOrganizationIdAndBillingPlanIdOrderByEffectiveFromAsc(ORGANIZATION_ID, PLAN_ID))
                .willReturn(List.of());

        OrganizationDiscountDto.Request.SetDiscount request =
                new OrganizationDiscountDto.Request.SetDiscount("FIXED_AMOUNT", new BigDecimal("-50000"), TODAY, null);

        assertThatThrownBy(() -> organizationDiscountService.createBillingPlanDiscountPeriod(
                ORGANIZATION_ID, PLAN_ID, "PLATFORM_OPS", 1L, request
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.DISCOUNT_VALUE_INVALID);
    }

    @Test
    @DisplayName("기간 제거는 카탈로그 값으로 되돌리는 것 — 오버라이드 행이 삭제되고, 이력엔 제거 직전 값이 removed=true로 남는다")
    void removeBillingPlanDiscountPeriod_existingPeriod_deletesRowAndRecordsHistory() {
        Organization organization = organization();
        BillingPlan plan = plan();
        OrganizationBillingPlanDiscount period = OrganizationBillingPlanDiscount.builder()
                .organization(organization).billingPlan(plan)
                .discountType(DiscountType.PERCENT).discountValue(new BigDecimal("30"))
                .effectiveFrom(TODAY).effectiveTo(null)
                .build();
        ReflectionTestUtils.setField(period, "id", 501L);

        given(rolePermissionService.isAllowed("PLATFORM_OPS", "ACTION_ORGANIZATION_DISCOUNT_MANAGE")).willReturn(true);
        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(organization));
        given(organizationBillingPlanDiscountRepository.findById(501L)).willReturn(Optional.of(period));

        organizationDiscountService.removeBillingPlanDiscountPeriod(ORGANIZATION_ID, PLAN_ID, 501L, "PLATFORM_OPS", 1L);

        verify(organizationBillingPlanDiscountRepository).delete(period);

        ArgumentCaptor<OrganizationBillingPlanDiscountHistory> historyCaptor =
                ArgumentCaptor.forClass(OrganizationBillingPlanDiscountHistory.class);
        verify(organizationBillingPlanDiscountHistoryRepository).save(historyCaptor.capture());
        OrganizationBillingPlanDiscountHistory history = historyCaptor.getValue();
        assertThat(history.getDiscount().getDiscountType()).isEqualTo(DiscountType.PERCENT);
        assertThat(history.getDiscount().getDiscountValue()).isEqualByComparingTo("30");
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
                .effectiveFrom(TODAY).effectiveTo(null)
                .removed(false)
                .build();
        OrganizationBillingPlanDiscountHistory removeEvent = OrganizationBillingPlanDiscountHistory.builder()
                .organization(organization).billingPlan(plan)
                .discountType(DiscountType.PERCENT).discountValue(new BigDecimal("30"))
                .effectiveFrom(TODAY).effectiveTo(null)
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
