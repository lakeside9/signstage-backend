package com.eformworks.signstage.backend.feature.ceremony.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyOnsiteSupportRequestDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyOnsiteSupportRequest;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductPurchase;
import com.eformworks.signstage.backend.feature.ceremony.entity.OnsiteSupportRequestStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductCategory;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductPricePeriod;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyOnsiteSupportRequestRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyUnitProductPurchaseLineRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyUnitProductPurchaseRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.UnitProductRepository;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminOnsiteSupportRequestDto;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link CeremonyOnsiteSupportRequestService} 단위 테스트 — signstage-docs
 * business/onsite-support-negotiation-and-billing-classification-review.md 3.2절. "요청 →
 * 관리자가 값을 매김 → 요청자가 수락/거부" 협상 상태 전이와, 수락 시 구매가 실제로 생기는지를
 * 중심으로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class CeremonyOnsiteSupportRequestServiceTest {

    @Mock
    private CeremonyOnsiteSupportRequestRepository ceremonyOnsiteSupportRequestRepository;
    @Mock
    private CeremonyUnitProductPurchaseRepository ceremonyUnitProductPurchaseRepository;
    @Mock
    private CeremonyUnitProductPurchaseLineRepository ceremonyUnitProductPurchaseLineRepository;
    @Mock
    private UnitProductRepository unitProductRepository;
    @Mock
    private CeremonyService ceremonyService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RolePermissionService rolePermissionService;
    @Mock
    private PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;

    private CeremonyOnsiteSupportRequestService service;

    private Ceremony ceremony;
    private Member actingMember;
    private UnitProduct anchor;

    @BeforeEach
    void setUp() {
        service = new CeremonyOnsiteSupportRequestService(
                ceremonyOnsiteSupportRequestRepository, ceremonyUnitProductPurchaseRepository,
                ceremonyUnitProductPurchaseLineRepository, unitProductRepository, ceremonyService,
                userRepository, rolePermissionService, platformAdminAuditLogRecorder
        );

        Organization organization = Organization.builder().name("파트너사").code("PARTNER").build();
        ReflectionTestUtils.setField(organization, "id", 10L);

        ceremony = Ceremony.builder().organization(organization).title("협약식").build();
        ReflectionTestUtils.setField(ceremony, "id", 100L);

        actingMember = Member.builder().role(MemberRole.OWNER).build();
        anchor = UnitProduct.builder().type(UnitProductType.ONSITE_SUPPORT_REQUEST).name("현장지원(요청형)")
                .category(UnitProductCategory.PERSONNEL).platformUsageFee(true).build();
        ReflectionTestUtils.setField(anchor, "id", 900L);

        lenient().when(ceremonyService.findCeremonyInOrganizationOrThrow(10L, 100L)).thenReturn(ceremony);
        lenient().when(ceremonyService.findActiveMemberOrThrow(10L, 1L)).thenReturn(actingMember);
        lenient().doNothing().when(ceremonyService).checkCeremonyManageAccess(ceremony, actingMember, 1L);
        lenient().doNothing().when(ceremonyService).checkCeremonyReadAccess(ceremony, actingMember, 1L);
    }

    private CeremonyOnsiteSupportRequest quotedRequest() {
        CeremonyOnsiteSupportRequest request = CeremonyOnsiteSupportRequest.builder()
                .ceremony(ceremony).requestedAt(LocalDateTime.now()).location("서울시청").build();
        ReflectionTestUtils.setField(request, "id", 500L);
        request.quote(BigDecimal.valueOf(260000), "지방 근거리", 999L);
        return request;
    }

    @Test
    @DisplayName("요청 등록 — REQUESTED 상태로 시작한다")
    void createRequest_startsAsRequested() {
        given(ceremonyOnsiteSupportRequestRepository.save(any())).willAnswer(invocation -> {
            CeremonyOnsiteSupportRequest saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 500L);
            return saved;
        });

        CeremonyOnsiteSupportRequestDto.Response.RequestSummary response = service.createRequest(
                10L, 100L, 1L,
                new CeremonyOnsiteSupportRequestDto.Request.CreateRequest(LocalDateTime.now(), "서울시청", "정문 앞")
        );

        assertThat(response.getStatus()).isEqualTo(OnsiteSupportRequestStatus.REQUESTED.name());
        verify(ceremonyService).checkCeremonyManageAccess(ceremony, actingMember, 1L);
    }

    @Test
    @DisplayName("관리자 견적 — REQUESTED 상태에서만 매길 수 있다")
    void quoteRequest_onlyFromRequestedStatus() {
        CeremonyOnsiteSupportRequest alreadyQuoted = quotedRequest();
        given(rolePermissionService.isAllowed("PLATFORM_OPS", "ACTION_ONSITE_SUPPORT_REQUEST_MANAGE")).willReturn(true);
        given(ceremonyOnsiteSupportRequestRepository.findById(500L)).willReturn(Optional.of(alreadyQuoted));

        assertThatThrownBy(() -> service.quoteRequest(
                500L, "PLATFORM_OPS", 999L,
                new PlatformAdminOnsiteSupportRequestDto.Request.Quote(BigDecimal.valueOf(300000), null)
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.ONSITE_SUPPORT_REQUEST_NOT_REQUESTED);
    }

    @Test
    @DisplayName("PLATFORM_OPS 미만 등급은 견적을 매길 수 없다")
    void quoteRequest_insufficientRole_fail() {
        given(rolePermissionService.isAllowed("PLATFORM_SUPPORT", "ACTION_ONSITE_SUPPORT_REQUEST_MANAGE")).willReturn(false);

        assertThatThrownBy(() -> service.quoteRequest(
                500L, "PLATFORM_SUPPORT", 999L,
                new PlatformAdminOnsiteSupportRequestDto.Request.Quote(BigDecimal.valueOf(300000), null)
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("수락 — 견적 금액 그대로 구매 1건을 만들고 approve()를 호출한다")
    void acceptRequest_createsPurchaseWithQuotedAmountAndApproves() {
        CeremonyOnsiteSupportRequest request = quotedRequest();
        given(ceremonyOnsiteSupportRequestRepository.findById(500L)).willReturn(Optional.of(request));
        given(unitProductRepository.findFirstByTypeOrderByIdAsc(UnitProductType.ONSITE_SUPPORT_REQUEST))
                .willReturn(Optional.of(anchor));
        UnitProductPricePeriod period = UnitProductPricePeriod.builder()
                .unitProduct(anchor).currencyCode("KRW").salePrice(BigDecimal.ZERO).taxCode("KR_VAT_STANDARD")
                .effectiveFrom(LocalDate.now()).build();
        given(ceremonyService.resolveSellableUnitProductPeriod(anchor, LocalDate.now(java.time.ZoneId.of(ceremony.getTimeZoneId()))))
                .willReturn(period);
        given(ceremonyUnitProductPurchaseRepository.save(any())).willAnswer(invocation -> {
            CeremonyUnitProductPurchase saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 700L);
            return saved;
        });

        CeremonyOnsiteSupportRequestDto.Response.RequestSummary response = service.acceptRequest(10L, 100L, 500L, 1L);

        assertThat(response.getStatus()).isEqualTo(OnsiteSupportRequestStatus.ACCEPTED.name());
        var lineCaptor = org.mockito.ArgumentCaptor.forClass(com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductPurchaseLine.class);
        verify(ceremonyUnitProductPurchaseLineRepository).save(lineCaptor.capture());
        assertThat(lineCaptor.getValue().getPurchasedSalePrice()).isEqualByComparingTo(BigDecimal.valueOf(260000));
        assertThat(lineCaptor.getValue().getUnitProduct()).isEqualTo(anchor);
    }

    @Test
    @DisplayName("수락/거부 — QUOTED 상태가 아니면 거부된다")
    void acceptRequest_notQuoted_fail() {
        CeremonyOnsiteSupportRequest request = CeremonyOnsiteSupportRequest.builder()
                .ceremony(ceremony).requestedAt(LocalDateTime.now()).location("서울시청").build();
        ReflectionTestUtils.setField(request, "id", 500L);
        given(ceremonyOnsiteSupportRequestRepository.findById(500L)).willReturn(Optional.of(request));

        assertThatThrownBy(() -> service.acceptRequest(10L, 100L, 500L, 1L))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.ONSITE_SUPPORT_REQUEST_NOT_QUOTED);
    }

    @Test
    @DisplayName("거부 — 종결(DECLINED)되고 구매를 만들지 않는다")
    void declineRequest_declinesWithoutCreatingPurchase() {
        CeremonyOnsiteSupportRequest request = quotedRequest();
        given(ceremonyOnsiteSupportRequestRepository.findById(500L)).willReturn(Optional.of(request));

        CeremonyOnsiteSupportRequestDto.Response.RequestSummary response = service.declineRequest(10L, 100L, 500L, 1L);

        assertThat(response.getStatus()).isEqualTo(OnsiteSupportRequestStatus.DECLINED.name());
        verify(ceremonyUnitProductPurchaseRepository, org.mockito.Mockito.never()).save(any());
    }
}
