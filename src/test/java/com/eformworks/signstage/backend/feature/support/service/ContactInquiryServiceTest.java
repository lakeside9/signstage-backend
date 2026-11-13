package com.eformworks.signstage.backend.feature.support.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.audit.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.notification.entity.NotificationType;
import com.eformworks.signstage.backend.feature.notification.service.NotificationService;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import com.eformworks.signstage.backend.feature.support.dto.ContactInquiryDto;
import com.eformworks.signstage.backend.feature.support.entity.ContactInquiry;
import com.eformworks.signstage.backend.feature.support.entity.ContactInquiryStatus;
import com.eformworks.signstage.backend.feature.support.entity.ContactInquiryType;
import com.eformworks.signstage.backend.feature.support.error.SupportErrorCode;
import com.eformworks.signstage.backend.feature.support.repository.ContactInquiryRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 고객 문의하기 — 공개 접수(동의·봇 차단·횟수 제한)와 관리자 처리 기록({@link ContactInquiryService}). */
@ExtendWith(MockitoExtension.class)
class ContactInquiryServiceTest {

    private static final String IP = "203.0.113.10";

    @Mock
    private ContactInquiryRepository contactInquiryRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private PlatformAdminAuditLogRecorder auditLogRecorder;
    @Mock
    private RolePermissionService rolePermissionService;

    @InjectMocks
    private ContactInquiryService contactInquiryService;

    private static ContactInquiryDto.Request.Submit request(boolean consented, String website) {
        return new ContactInquiryDto.Request.Submit(ContactInquiryType.INTRODUCTION, " 이폼웍스 ", "홍길동", "a@example.com", " ",
                null, 20, "도입 상담을 원합니다.", consented, website);
    }

    @Test
    @DisplayName("문의를 저장하고 고객 문의 권한자에게 알린다")
    void submit_savesAndNotifies() {
        contactInquiryService.submit(request(true, null), IP);

        ArgumentCaptor<ContactInquiry> captor = ArgumentCaptor.forClass(ContactInquiry.class);
        verify(contactInquiryRepository).save(captor.capture());
        ContactInquiry saved = captor.getValue();
        assertThat(saved.getCompanyName()).isEqualTo("이폼웍스");
        assertThat(saved.getPhone()).isNull();
        assertThat(saved.getStatus()).isEqualTo(ContactInquiryStatus.RECEIVED);
        assertThat(saved.getRequesterIp()).isEqualTo(IP);
        assertThat(saved.getPrivacyConsentedAt()).isNotNull();
        verify(notificationService).notifyPlatform(eq("ACTION_CONTACT_INQUIRY_MANAGE"),
                eq(NotificationType.CONTACT_INQUIRY_RECEIVED), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("개인정보 수집·이용에 동의하지 않으면 접수하지 않는다")
    void submit_requiresConsent() {
        assertThatThrownBy(() -> contactInquiryService.submit(request(false, null), IP))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCode").isEqualTo(SupportErrorCode.CONTACT_INQUIRY_CONSENT_REQUIRED);
        verify(contactInquiryRepository, never()).save(any());
    }

    @Test
    @DisplayName("숨은 칸에 값이 있으면 봇으로 보고 저장하지 않는다")
    void submit_ignoresHoneypot() {
        contactInquiryService.submit(request(true, "http://spam.example"), IP);

        verify(contactInquiryRepository, never()).save(any());
        verify(notificationService, never()).notifyPlatform(anyString(), any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("같은 주소에서 10분에 5건을 넘기면 거절한다")
    void submit_limitsPerAddress() {
        for (int i = 0; i < 5; i++) {
            contactInquiryService.submit(request(true, null), IP);
        }

        assertThatThrownBy(() -> contactInquiryService.submit(request(true, null), IP))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.TOO_MANY_REQUESTS);
        contactInquiryService.submit(request(true, null), "198.51.100.7");
    }

    @Test
    @DisplayName("처리 상태와 메모를 기록하고 감사 로그를 남긴다")
    void handle_recordsStatusAndMemo() {
        ContactInquiry inquiry = ContactInquiry.builder()
                .inquiryType(ContactInquiryType.PRICING).companyName("이폼웍스").contactName("홍길동")
                .email("a@example.com").message("요금 문의").build();
        given(rolePermissionService.isAllowed("PLATFORM_SUPPORT", "ACTION_CONTACT_INQUIRY_MANAGE")).willReturn(true);
        given(contactInquiryRepository.findById(7L)).willReturn(Optional.of(inquiry));

        ContactInquiryDto.Response.ContactInquirySummary response = contactInquiryService.handle(7L, "PLATFORM_SUPPORT", 1L,
                new ContactInquiryDto.Request.Handle(ContactInquiryStatus.IN_PROGRESS, " 11/14 통화 예정 "));

        assertThat(response.getStatus()).isEqualTo(ContactInquiryStatus.IN_PROGRESS);
        assertThat(response.getAdminMemo()).isEqualTo("11/14 통화 예정");
        assertThat(inquiry.getHandledBy()).isEqualTo(1L);
        verify(auditLogRecorder).record(eq(1L), eq(PlatformAdminAction.HANDLE_CONTACT_INQUIRY), any(), any(), anyString());
    }

    @Test
    @DisplayName("권한이 없으면 처리 기록을 막는다")
    void handle_requiresPermission() {
        given(rolePermissionService.isAllowed("PLATFORM_SUPPORT", "ACTION_CONTACT_INQUIRY_MANAGE")).willReturn(false);

        assertThatThrownBy(() -> contactInquiryService.handle(7L, "PLATFORM_SUPPORT", 1L,
                new ContactInquiryDto.Request.Handle(ContactInquiryStatus.DONE, null)))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }
}
