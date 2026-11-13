package com.eformworks.signstage.backend.feature.ceremony.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.feature.billing.model.EffectiveDiscount;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyDto;
import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlanUnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistory;
import com.eformworks.signstage.backend.core.money.DiscountType;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyStatus;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link CeremonyService} 단위 테스트 — 행사 생성(조직×플랜 할인 오버라이드 스냅샷, 플랜 없는 선등록), 플랫폼 관리자 행사 목록.
 * 2026-11-11 {@code CeremonyServiceTest}에서 분리했다. 공용 mock과 서비스 조립은
 * {@link CeremonyServiceTestSupport}에 있다.
 */
class CeremonyServiceTest extends CeremonyServiceTestSupport {

    /**
     * 2026-09-12 사용자 요청 — "행사를 만들때 현재는 행사제목만 우선 입력을 받는데 나머지
     * 필드도 입력할 수 있도록 화면을 변경해주세요." 등록 화면에서 제목과 함께 받을 수 있게
     * 확장한 나머지 정보(설명/주관 기관·부서/담당자 정보)가 실제로 저장되는지 확인한다 —
     * "선등록 후플랜" 원칙(플랜은 여기서 다루지 않음)은 그대로다.
     */
    @Test
    @DisplayName("Ceremony 생성 — 제목 외 나머지 정보(설명/주관 기관·부서/담당자)도 함께 저장한다")
    void createCeremony_withOptionalFields_savesThemAll() {
        Organization organization = organization();
        Member member = Member.builder().role(MemberRole.OWNER).build();
        User creator = User.builder().loginId("user1").name("사용자1").build();

        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(organization));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(userRepository.findById(CURRENT_USER_ID)).willReturn(Optional.of(creator));

        CeremonyDto.Request.CreateCeremony request = new CeremonyDto.Request.CreateCeremony(
                null, "행사1", "설명입니다", "주관기관", "주관부서",
                "담당자", "과장", "010-1234-5678", "contact@example.com",
                "서울 행사장", java.time.LocalDateTime.of(2026, 10, 1, 10, 0),
                java.time.LocalDateTime.of(2026, 10, 1, 12, 0)
        );

        CeremonyDto.Response.CeremonySummary result = ceremonyService.createCeremony(ORGANIZATION_ID, CURRENT_USER_ID, request);

        assertThat(result.getTitle()).isEqualTo("행사1");
        assertThat(result.getDescription()).isEqualTo("설명입니다");
        assertThat(result.getOrganizingInstitution()).isEqualTo("주관기관");
        assertThat(result.getOrganizingDepartment()).isEqualTo("주관부서");
        assertThat(result.getContactName()).isEqualTo("담당자");
        assertThat(result.getContactTitle()).isEqualTo("과장");
        assertThat(result.getContactPhone()).isEqualTo("010-1234-5678");
        assertThat(result.getContactEmail()).isEqualTo("contact@example.com");
        assertThat(result.getLocation()).isEqualTo("서울 행사장");
        assertThat(result.getStartsAt()).isEqualTo(request.getStartsAt());
        assertThat(result.getEndsAt()).isEqualTo(request.getEndsAt());
    }

    @Test
    @DisplayName("Ceremony 생성 시 조직×플랜 할인 오버라이드가 있으면 카탈로그 값 대신 그 값을 CeremonyPlanHistory에 스냅샷한다")
    void createCeremony_withPlanDiscountOverride_snapshotsOverride() {
        // given
        Organization organization = organization();
        BillingPlan plan = BillingPlan.builder().name("스탠다드").build();
        ReflectionTestUtils.setField(plan, "id", 101L);
        Member member = Member.builder().role(MemberRole.OWNER).build();
        User creator = User.builder().loginId("user1").name("사용자1").build();

        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(organization));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(billingPlanRepository.findById(101L)).willReturn(Optional.of(plan));
        given(userRepository.findById(CURRENT_USER_ID)).willReturn(Optional.of(creator));
        // 이 플랜은 포함 단위 상품이 없다고 가정한다 — 통화 검증(resolvePlanCurrency)이
        // 자연히 건너뛰어지고, recordPlanHistory의 스냅샷 루프도 빈 채로 끝난다.
        given(billingPlanUnitProductRepository.findAllByBillingPlanId(101L)).willReturn(List.of());
        given(billingPlanDiscountPeriodRepository.findEffective(eq(101L), any(LocalDate.class)))
                .willReturn(Optional.of(planDiscountPeriod(plan, DiscountType.FIXED_AMOUNT, new BigDecimal("10000"))));

        EffectiveDiscount overrideDiscount =
                new EffectiveDiscount(DiscountType.PERCENT, new BigDecimal("30"));
        given(organizationDiscountService.resolveBillingPlanDiscount(eq(organization), eq(101L), any(), any(), any(LocalDate.class)))
                .willReturn(overrideDiscount);

        CeremonyDto.Request.CreateCeremony request = new CeremonyDto.Request.CreateCeremony(101L, "행사1", null, null, null, null, null, null, null, null, null, null);

        // when
        ceremonyService.createCeremony(ORGANIZATION_ID, CURRENT_USER_ID, request);

        // then — 카탈로그 자체 할인(FIXED_AMOUNT 10000)이 아니라 오버라이드(PERCENT 30)가 스냅샷됐는지 확인
        ArgumentCaptor<CeremonyPlanHistory> captor = ArgumentCaptor.forClass(CeremonyPlanHistory.class);
        verify(ceremonyPlanHistoryRepository).save(captor.capture());
        CeremonyPlanHistory history = captor.getValue();
        assertThat(history.getPlanDiscountType()).isEqualTo(DiscountType.PERCENT);
        assertThat(history.getPlanDiscountValue()).isEqualByComparingTo("30");
    }

    /**
     * 2026-09-11 사용자 요청 — signstage-docs
     * business/ceremony-plan-price-snapshot-consistency-review.md 3.1절(방어). 플랜에
     * 포함된 단위 상품 중 그 날짜에 유효한 가격 기간이 없으면(카탈로그 관리자가 가격 기간
     * 사이에 공백을 남긴 경우) 예전엔 조용히 0원으로 스냅샷됐지만, 이제 추가구매 경로와
     * 같은 기준으로 예외를 던져 플랜 선택 자체를 막는다.
     */
    @Test
    @DisplayName("플랜에 포함된 단위 상품 중 가격 공백(그 날짜에 유효한 기간 없음)이 있으면 플랜 선택 자체를 거부한다")
    void createCeremony_withUnitProductPriceGap_rejected() {
        Organization organization = organization();
        BillingPlan plan = BillingPlan.builder().name("스탠다드").build();
        ReflectionTestUtils.setField(plan, "id", 101L);
        Member member = Member.builder().role(MemberRole.OWNER).build();

        UnitProduct signers = unitProduct(201L, UnitProductType.SIGNERS);
        BillingPlanUnitProduct line = BillingPlanUnitProduct.builder()
                .billingPlan(plan).unitProduct(signers).includedQuantity(1).build();

        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(organization));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(billingPlanRepository.findById(101L)).willReturn(Optional.of(plan));
        given(billingPlanUnitProductRepository.findAllByBillingPlanId(101L)).willReturn(List.of(line));
        given(billingPlanDiscountPeriodRepository.findEffective(eq(101L), any(LocalDate.class)))
                .willReturn(Optional.of(planDiscountPeriod(plan, DiscountType.FIXED_AMOUNT, BigDecimal.ZERO)));
        // 공백 — 이 단위 상품엔 오늘 유효한 가격 기간이 없다.
        given(unitProductPricePeriodRepository.findEffective(eq(201L), any(LocalDate.class))).willReturn(Optional.empty());
        given(organizationDiscountService.resolveBillingPlanDiscount(eq(organization), eq(101L), any(), any(), any(LocalDate.class)))
                .willReturn(new EffectiveDiscount(DiscountType.FIXED_AMOUNT, BigDecimal.ZERO));

        CeremonyDto.Request.CreateCeremony request = new CeremonyDto.Request.CreateCeremony(101L, "행사1", null, null, null, null, null, null, null, null, null, null);

        // 실제로는 @Transactional이라 ceremonyRepository.save 자체는 먼저 불리고 예외로 롤백된다 —
        // 여기서는 순수하게 "예외가 나는가"만 확인한다.
        assertThatThrownBy(() -> ceremonyService.createCeremony(ORGANIZATION_ID, CURRENT_USER_ID, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.UNIT_PRODUCT_INACTIVE);
    }

    @Test
    @DisplayName("플랫폼 관리자용 행사 목록 조회는 조직 멤버십 없이도(memberRepository 조회 없이) 그 조직의 모든 행사를 본다")
    void findCeremoniesByPlatformAdmin_returnsCeremoniesWithoutMembershipCheck() {
        // given
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, 10L);
        Pageable pageable = PageRequest.of(0, 20);
        Page<Ceremony> page = new PageImpl<>(List.of(ceremony), pageable, 1);

        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(organization));
        given(ceremonyRepository.search(ORGANIZATION_ID, null, null, null, null, null, false, pageable)).willReturn(page);

        // when
        Page<CeremonyDto.Response.CeremonySummary> result =
                ceremonyService.findCeremoniesByPlatformAdmin(ORGANIZATION_ID, null, null, pageable);

        // then — memberRepository는 이 흐름에서 전혀 조회되지 않는다(플랫폼 관리자는 조직 멤버가 아니어도 된다).
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(10L);
        verify(memberRepository, never()).findByOrganizationIdAndUserIdAndStatus(any(), any(), any());
    }

    /**
     * 행사 등록 시 플랜 선택을 나중으로 미루는 흐름(2026-09-10, 사용자 요청) 단위 테스트 —
     * signstage-docs business/ceremony-registration-flow-and-billing-tab-separation-review.md.
     */
    @Test
    @DisplayName("생성 — billingPlanId를 생략하면 플랜 없이 DRAFT로 만들고 플랜 이력을 남기지 않는다")
    void createCeremony_withoutBillingPlanId_createsDraftWithoutPlanHistory() {
        Organization organization = organization();
        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(organization));
        Member member = Member.builder().role(MemberRole.OWNER).build();
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        User creator = User.builder().loginId("u1").name("사용자").build();
        given(userRepository.findById(CURRENT_USER_ID)).willReturn(Optional.of(creator));

        CeremonyDto.Request.CreateCeremony request = new CeremonyDto.Request.CreateCeremony(null, "행사", null, null, null, null, null, null, null, null, null, null);

        CeremonyDto.Response.CeremonySummary result = ceremonyService.createCeremony(ORGANIZATION_ID, CURRENT_USER_ID, request);

        assertThat(result.getBillingPlanId()).isNull();
        assertThat(result.getStatus()).isEqualTo("DRAFT");
        verify(ceremonyPlanHistoryRepository, never()).save(any());
        verify(billingPlanRepository, never()).findById(any());
    }

    @Test
    @DisplayName("관리자 상태 강제 변경 — 완료된 행사는 진행 중으로 되돌릴 수 없다(2026-11-13 사용자 결정)")
    void updateStatusByPlatformAdmin_rejectsRevertingCompletedCeremony() {
        Ceremony ceremony = ceremony(organization(), 10L);
        ceremony.changeStatus(CeremonyStatus.COMPLETED);
        given(rolePermissionService.isAllowed("PLATFORM_OPS", "ACTION_CEREMONY_STATUS_CONTROL")).willReturn(true);
        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));

        assertThatThrownBy(() -> ceremonyService.updateStatusByPlatformAdmin(
                ORGANIZATION_ID, 10L, CURRENT_USER_ID, "PLATFORM_OPS", new CeremonyDto.Request.UpdateStatus("IN_PROGRESS")))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.CEREMONY_ALREADY_COMPLETED);
        assertThat(ceremony.getStatus()).isEqualTo(CeremonyStatus.COMPLETED);
    }

    @Test
    @DisplayName("관리자 상태 강제 변경 — 진행 중인 행사는 완료로 바꿀 수 있다")
    void updateStatusByPlatformAdmin_completesInProgressCeremony() {
        Ceremony ceremony = ceremony(organization(), 10L);
        ceremony.changeStatus(CeremonyStatus.IN_PROGRESS);
        given(rolePermissionService.isAllowed("PLATFORM_OPS", "ACTION_CEREMONY_STATUS_CONTROL")).willReturn(true);
        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));

        CeremonyDto.Response.CeremonySummary result = ceremonyService.updateStatusByPlatformAdmin(
                ORGANIZATION_ID, 10L, CURRENT_USER_ID, "PLATFORM_OPS", new CeremonyDto.Request.UpdateStatus("COMPLETED"));

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
    }
}
