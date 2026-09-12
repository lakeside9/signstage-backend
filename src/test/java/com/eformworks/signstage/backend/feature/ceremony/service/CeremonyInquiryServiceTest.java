package com.eformworks.signstage.backend.feature.ceremony.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyInquiryDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyInquiry;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyInquiryMessage;
import com.eformworks.signstage.backend.feature.ceremony.entity.InquirySenderType;
import com.eformworks.signstage.backend.feature.ceremony.entity.InquiryStatus;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyInquiryMessageRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyInquiryRepository;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminCeremonyInquiryDto;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link CeremonyInquiryService} 단위 테스트 — signstage-docs
 * business/partner-support-center-review.md 5장. 이 코드베이스에 새로 들어온 "헤더+메시지
 * 대화 스레드" 패턴의 상태 전이(5.2절)를 중심으로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class CeremonyInquiryServiceTest {

    @Mock
    private CeremonyInquiryRepository ceremonyInquiryRepository;
    @Mock
    private CeremonyInquiryMessageRepository ceremonyInquiryMessageRepository;
    @Mock
    private CeremonyService ceremonyService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RolePermissionService rolePermissionService;
    @Mock
    private PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;

    private CeremonyInquiryService ceremonyInquiryService;

    private Ceremony ceremony;
    private Member actingMember;

    @BeforeEach
    void setUp() {
        ceremonyInquiryService = new CeremonyInquiryService(
                ceremonyInquiryRepository, ceremonyInquiryMessageRepository, ceremonyService,
                userRepository, rolePermissionService, platformAdminAuditLogRecorder
        );

        Organization organization = Organization.builder().name("파트너사").code("PARTNER").build();
        ReflectionTestUtils.setField(organization, "id", 10L);

        ceremony = Ceremony.builder().organization(organization).title("협약식").build();
        ReflectionTestUtils.setField(ceremony, "id", 100L);

        actingMember = Member.builder().role(MemberRole.OWNER).build();

        lenient().when(ceremonyService.findCeremonyInOrganizationOrThrow(10L, 100L)).thenReturn(ceremony);
        lenient().when(ceremonyService.findActiveMemberOrThrow(10L, 1L)).thenReturn(actingMember);
        lenient().doNothing().when(ceremonyService).checkCeremonyManageAccess(ceremony, actingMember, 1L);
        lenient().doNothing().when(ceremonyService).checkCeremonyReadAccess(ceremony, actingMember, 1L);
    }

    private CeremonyInquiry openInquiry() {
        CeremonyInquiry inquiry = CeremonyInquiry.builder().ceremony(ceremony).title("문의").lastMessageAt(LocalDateTime.now()).build();
        ReflectionTestUtils.setField(inquiry, "id", 500L);
        ReflectionTestUtils.setField(inquiry, "createdBy", 1L);
        return inquiry;
    }

    @Test
    @DisplayName("문의 등록 — 헤더와 첫 메시지(PARTNER)를 함께 저장하고 OPEN 상태로 시작한다")
    void createInquiry_savesHeaderAndFirstMessage() {
        given(ceremonyInquiryRepository.save(any())).willAnswer(invocation -> {
            CeremonyInquiry saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 500L);
            return saved;
        });
        given(ceremonyInquiryMessageRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        CeremonyInquiryDto.Response.InquiryDetail response = ceremonyInquiryService.createInquiry(
                10L, 100L, 1L, new CeremonyInquiryDto.Request.CreateInquiry("문의 제목", "첫 문의 내용")
        );

        assertThat(response.getStatus()).isEqualTo(InquiryStatus.OPEN.name());
        assertThat(response.getMessages()).hasSize(1);
        assertThat(response.getMessages().get(0).getSenderType()).isEqualTo(InquirySenderType.PARTNER.name());
        verify(ceremonyService).checkCeremonyManageAccess(ceremony, actingMember, 1L);
    }

    @Test
    @DisplayName("파트너 메시지 추가 — ANSWERED 상태였어도 OPEN으로 되돌린다")
    void addMessage_partnerMessage_setsStatusOpen() {
        CeremonyInquiry inquiry = openInquiry();
        inquiry.applyMessage(InquirySenderType.PLATFORM_ADMIN, LocalDateTime.now());
        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.ANSWERED);

        given(ceremonyInquiryRepository.findById(500L)).willReturn(Optional.of(inquiry));
        given(ceremonyInquiryMessageRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(ceremonyInquiryMessageRepository.findAllByInquiryIdOrderByCreatedAtAsc(500L)).willReturn(List.of());

        ceremonyInquiryService.addMessage(10L, 100L, 500L, 1L, new CeremonyInquiryDto.Request.AddMessage("추가 질문"));

        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.OPEN);
    }

    @Test
    @DisplayName("종료(CLOSED)된 문의에는 메시지를 추가할 수 없다")
    void addMessage_onClosedInquiry_fail() {
        CeremonyInquiry inquiry = openInquiry();
        inquiry.close();
        given(ceremonyInquiryRepository.findById(500L)).willReturn(Optional.of(inquiry));

        assertThatThrownBy(() -> ceremonyInquiryService.addMessage(
                10L, 100L, 500L, 1L, new CeremonyInquiryDto.Request.AddMessage("더 물어볼게요")
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.CEREMONY_INQUIRY_ALREADY_CLOSED);
    }

    @Test
    @DisplayName("이미 종료된 문의를 다시 종료하려 하면 거부된다")
    void closeInquiry_alreadyClosed_fail() {
        CeremonyInquiry inquiry = openInquiry();
        inquiry.close();
        given(ceremonyInquiryRepository.findById(500L)).willReturn(Optional.of(inquiry));

        assertThatThrownBy(() -> ceremonyInquiryService.closeInquiry(10L, 100L, 500L, 1L))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.CEREMONY_INQUIRY_ALREADY_CLOSED);
    }

    @Test
    @DisplayName("관리자 답변 — ANSWERED로 전이하고 감사 로그를 남긴다")
    void replyAsAdmin_setsStatusAnsweredAndRecordsAuditLog() {
        CeremonyInquiry inquiry = openInquiry();
        given(rolePermissionService.isAllowed("PLATFORM_OPS", "ACTION_CEREMONY_INQUIRY_MANAGE")).willReturn(true);
        given(ceremonyInquiryRepository.findById(500L)).willReturn(Optional.of(inquiry));
        given(ceremonyInquiryMessageRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(ceremonyInquiryMessageRepository.findAllByInquiryIdOrderByCreatedAtAsc(500L)).willReturn(List.of());
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.empty());

        ceremonyInquiryService.replyAsAdmin(
                500L, "PLATFORM_OPS", 999L, new PlatformAdminCeremonyInquiryDto.Request.Reply("답변입니다")
        );

        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.ANSWERED);
        verify(platformAdminAuditLogRecorder).record(eq(999L), any(), isNull(), eq(10L), any());
    }

    @Test
    @DisplayName("PLATFORM_OPS 미만 등급은 답변할 수 없다")
    void replyAsAdmin_insufficientRole_fail() {
        given(rolePermissionService.isAllowed("PLATFORM_SUPPORT", "ACTION_CEREMONY_INQUIRY_MANAGE")).willReturn(false);

        assertThatThrownBy(() -> ceremonyInquiryService.replyAsAdmin(
                500L, "PLATFORM_SUPPORT", 999L, new PlatformAdminCeremonyInquiryDto.Request.Reply("답변")
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }
}
