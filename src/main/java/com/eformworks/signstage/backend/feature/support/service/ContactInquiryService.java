package com.eformworks.signstage.backend.feature.support.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.core.web.RateLimiter;
import com.eformworks.signstage.backend.feature.audit.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.notification.entity.NotificationType;
import com.eformworks.signstage.backend.feature.notification.service.NotificationService;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import com.eformworks.signstage.backend.feature.support.dto.ContactInquiryDto;
import com.eformworks.signstage.backend.feature.support.entity.ContactInquiry;
import com.eformworks.signstage.backend.feature.support.entity.ContactInquiryStatus;
import com.eformworks.signstage.backend.feature.support.error.SupportErrorCode;
import com.eformworks.signstage.backend.feature.support.repository.ContactInquiryRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 고객 문의하기(2026-11-13). 공개 화면에서 로그인 없이 접수하고, {@code ACTION_CONTACT_INQUIRY_MANAGE} 권한자에게
 * 앱 알림을 보낸다. 담당자는 시스템 밖에서 연락한 뒤 처리 상태와 내부 메모를 기록한다.
 *
 * <p>로그인 없이 열린 API라 같은 IP의 접수를 10분에 5건으로 제한하고, 숨은 칸(website)에 값이 있으면 봇으로 보고
 * 저장하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContactInquiryService {

    static final String ACTION_CONTACT_INQUIRY_MANAGE = "ACTION_CONTACT_INQUIRY_MANAGE";
    private static final ZoneId PLATFORM_ZONE = ZoneId.of("Asia/Seoul");
    private static final int SUBMIT_LIMIT = 5;
    private static final long SUBMIT_WINDOW_SECONDS = 600L;

    private final ContactInquiryRepository contactInquiryRepository;
    private final NotificationService notificationService;
    private final PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;
    private final RolePermissionService rolePermissionService;
    /** 포털용 리미터(분당 60건)와 따로 둔다 — 문의 접수는 훨씬 드물다. */
    private final RateLimiter submitRateLimiter = new RateLimiter(SUBMIT_LIMIT, SUBMIT_WINDOW_SECONDS);

    @Transactional
    public void submit(ContactInquiryDto.Request.Submit request, String requesterIp) {
        if (!submitRateLimiter.tryAcquire(requesterIp == null ? "unknown" : requesterIp)) {
            throw new ApplicationException(CommonErrorCode.TOO_MANY_REQUESTS);
        }
        if (StringUtils.hasText(request.getWebsite())) {
            return;
        }
        if (!request.isPrivacyConsented()) {
            throw new ApplicationException(SupportErrorCode.CONTACT_INQUIRY_CONSENT_REQUIRED);
        }

        ContactInquiry inquiry = ContactInquiry.builder()
                .inquiryType(request.getInquiryType())
                .companyName(request.getCompanyName().trim())
                .contactName(request.getContactName().trim())
                .email(request.getEmail().trim())
                .phone(blankToNull(request.getPhone()))
                .eventDate(request.getEventDate())
                .expectedSigners(request.getExpectedSigners())
                .message(request.getMessage().trim())
                .privacyConsentedAt(LocalDateTime.now(PLATFORM_ZONE))
                .requesterIp(requesterIp)
                .build();
        contactInquiryRepository.save(inquiry);

        notificationService.notifyPlatform(ACTION_CONTACT_INQUIRY_MANAGE, NotificationType.CONTACT_INQUIRY_RECEIVED,
                "고객 문의 · " + inquiry.getCompanyName(),
                inquiry.getContactName() + " · " + abbreviate(inquiry.getMessage()),
                "/admin/contact-inquiries?inquiryId=" + inquiry.getId());
    }

    public Page<ContactInquiryDto.Response.ContactInquirySummary> findInquiries(
            ContactInquiryStatus status, String keyword, Pageable pageable
    ) {
        return contactInquiryRepository.search(status, keyword, pageable)
                .map(ContactInquiryDto.Response.ContactInquirySummary::from);
    }

    public ContactInquiryDto.Response.ContactInquirySummary findInquiry(Long inquiryId) {
        return ContactInquiryDto.Response.ContactInquirySummary.from(findOrThrow(inquiryId));
    }

    @Transactional
    public ContactInquiryDto.Response.ContactInquirySummary handle(
            Long inquiryId, String actingPlatformRole, Long adminUserId, ContactInquiryDto.Request.Handle request
    ) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, ACTION_CONTACT_INQUIRY_MANAGE)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
        ContactInquiry inquiry = findOrThrow(inquiryId);
        inquiry.handle(request.getStatus(), blankToNull(request.getAdminMemo()), adminUserId, LocalDateTime.now(PLATFORM_ZONE));

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.HANDLE_CONTACT_INQUIRY, null, null,
                "contactInquiryId=" + inquiryId + ", status=" + request.getStatus()
        );
        return ContactInquiryDto.Response.ContactInquirySummary.from(inquiry);
    }

    private ContactInquiry findOrThrow(Long inquiryId) {
        return contactInquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new ApplicationException(SupportErrorCode.CONTACT_INQUIRY_NOT_FOUND));
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String abbreviate(String message) {
        String singleLine = message.replaceAll("\\s+", " ");
        return singleLine.length() <= 80 ? singleLine : singleLine.substring(0, 80) + "…";
    }
}
