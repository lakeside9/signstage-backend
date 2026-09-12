package com.eformworks.signstage.backend.feature.ceremony.service;

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
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminCeremonyInquiryDto;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 행사별 1:1 문의(파트너 ↔ 플랫폼 관리자) — signstage-docs
 * business/partner-support-center-review.md 5장. 이 코드베이스에 아직 없던 "헤더+메시지
 * 대화 스레드" 패턴을 새로 도입한다(2.4절에서 확인한 갭). {@code CeremonyService}처럼 파트너
 * 쪽과 관리자 쪽 메서드를 한 서비스에 같이 둔다({@code approveUnitProductPurchase}류와 같은
 * 관례) — 조직/행사 접근 검사는 {@code CeremonyService}의 package-private 헬퍼를 그대로
 * 재사용한다(SignerService/TemplateService와 같은 원칙).
 *
 * <p>쓰기 권한은 새 권한키를 만들지 않고 기존 {@code ACTION_CEREMONY_MANAGE}(파트너 쪽,
 * {@code checkCeremonyManageAccess}가 이미 검사)와 신규 {@code ACTION_CEREMONY_INQUIRY_MANAGE}
 * (관리자 쪽, 답변·종료 공용 — {@code ACTION_PURCHASE_APPROVAL}이 승인/반려/취소를 한 액션으로
 * 묶는 것과 같은 이유)만 쓴다 — 9장 결정 #6(파트너 쪽은 행사 관리와 동일 등급, VIEWER 제외).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CeremonyInquiryService {

    private static final String ACTION_CEREMONY_INQUIRY_MANAGE = "ACTION_CEREMONY_INQUIRY_MANAGE";

    private final CeremonyInquiryRepository ceremonyInquiryRepository;
    private final CeremonyInquiryMessageRepository ceremonyInquiryMessageRepository;
    private final CeremonyService ceremonyService;
    private final UserRepository userRepository;
    private final RolePermissionService rolePermissionService;
    private final PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;

    // ──────────────────────────── 파트너 쪽 ────────────────────────────

    @Transactional
    public CeremonyInquiryDto.Response.InquiryDetail createInquiry(
            Long organizationId, Long ceremonyId, Long currentUserId, CeremonyInquiryDto.Request.CreateInquiry request
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);

        LocalDateTime now = LocalDateTime.now();
        CeremonyInquiry inquiry = CeremonyInquiry.builder()
                .ceremony(ceremony)
                .title(request.getTitle())
                .lastMessageAt(now)
                .build();
        ceremonyInquiryRepository.save(inquiry);

        CeremonyInquiryMessage message = CeremonyInquiryMessage.builder()
                .inquiry(inquiry)
                .senderType(InquirySenderType.PARTNER)
                .content(request.getContent())
                .build();
        ceremonyInquiryMessageRepository.save(message);

        return toDetail(inquiry, List.of(message));
    }

    public List<CeremonyInquiryDto.Response.InquirySummary> findInquiries(
            Long organizationId, Long ceremonyId, Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        return ceremonyInquiryRepository.findAllByCeremonyIdOrderByLastMessageAtDesc(ceremonyId).stream()
                .map(this::toSummary)
                .toList();
    }

    public CeremonyInquiryDto.Response.InquiryDetail findInquiry(
            Long organizationId, Long ceremonyId, Long inquiryId, Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        CeremonyInquiry inquiry = findInquiryInCeremonyOrThrow(ceremonyId, inquiryId);
        return toDetail(inquiry, ceremonyInquiryMessageRepository.findAllByInquiryIdOrderByCreatedAtAsc(inquiryId));
    }

    @Transactional
    public CeremonyInquiryDto.Response.InquiryDetail addMessage(
            Long organizationId, Long ceremonyId, Long inquiryId, Long currentUserId,
            CeremonyInquiryDto.Request.AddMessage request
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);

        CeremonyInquiry inquiry = findInquiryInCeremonyOrThrow(ceremonyId, inquiryId);
        checkNotClosed(inquiry);

        LocalDateTime now = LocalDateTime.now();
        CeremonyInquiryMessage message = CeremonyInquiryMessage.builder()
                .inquiry(inquiry)
                .senderType(InquirySenderType.PARTNER)
                .content(request.getContent())
                .build();
        ceremonyInquiryMessageRepository.save(message);
        inquiry.applyMessage(InquirySenderType.PARTNER, now);

        return toDetail(inquiry, ceremonyInquiryMessageRepository.findAllByInquiryIdOrderByCreatedAtAsc(inquiryId));
    }

    @Transactional
    public CeremonyInquiryDto.Response.InquiryDetail closeInquiry(
            Long organizationId, Long ceremonyId, Long inquiryId, Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);

        CeremonyInquiry inquiry = findInquiryInCeremonyOrThrow(ceremonyId, inquiryId);
        checkNotClosed(inquiry);
        inquiry.close();

        return toDetail(inquiry, ceremonyInquiryMessageRepository.findAllByInquiryIdOrderByCreatedAtAsc(inquiryId));
    }

    // ──────────────────────────── 플랫폼 관리자 쪽 ────────────────────────────

    /**
     * 조직 횡단 목록 — organizationId/ceremonyId/status/requesterKeyword/ceremonyTitle 전부
     * 선택 필터다({@code CeremonyService#findUnitProductPurchaseRequests}와 같은 패턴). 조회는
     * PLATFORM_SUPPORT 이상이면 누구나(SecurityConfig가 이미 게이트, 별도 checkAllowed 없음).
     */
    public Page<PlatformAdminCeremonyInquiryDto.Response.InquirySummary> findInquiriesAcrossOrganizations(
            InquiryStatus status, Long organizationId, Long ceremonyId, String requesterKeyword, String ceremonyTitle,
            Pageable pageable
    ) {
        return ceremonyInquiryRepository.search(status, organizationId, ceremonyId, requesterKeyword, ceremonyTitle, pageable)
                .map(this::toAdminSummary);
    }

    public PlatformAdminCeremonyInquiryDto.Response.InquiryDetail findInquiryByPlatformAdmin(Long inquiryId) {
        CeremonyInquiry inquiry = findInquiryOrThrow(inquiryId);
        return toAdminDetail(inquiry, ceremonyInquiryMessageRepository.findAllByInquiryIdOrderByCreatedAtAsc(inquiryId));
    }

    @Transactional
    public PlatformAdminCeremonyInquiryDto.Response.InquiryDetail replyAsAdmin(
            Long inquiryId, String actingPlatformRole, Long adminUserId, PlatformAdminCeremonyInquiryDto.Request.Reply request
    ) {
        checkAllowed(actingPlatformRole);

        CeremonyInquiry inquiry = findInquiryOrThrow(inquiryId);
        checkNotClosed(inquiry);

        LocalDateTime now = LocalDateTime.now();
        CeremonyInquiryMessage message = CeremonyInquiryMessage.builder()
                .inquiry(inquiry)
                .senderType(InquirySenderType.PLATFORM_ADMIN)
                .content(request.getContent())
                .build();
        ceremonyInquiryMessageRepository.save(message);
        inquiry.applyMessage(InquirySenderType.PLATFORM_ADMIN, now);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.REPLY_CEREMONY_INQUIRY, null,
                inquiry.getCeremony().getOrganization().getId(), "inquiryId=" + inquiryId
        );

        return toAdminDetail(inquiry, ceremonyInquiryMessageRepository.findAllByInquiryIdOrderByCreatedAtAsc(inquiryId));
    }

    @Transactional
    public PlatformAdminCeremonyInquiryDto.Response.InquiryDetail closeInquiryAsAdmin(
            Long inquiryId, String actingPlatformRole, Long adminUserId
    ) {
        checkAllowed(actingPlatformRole);

        CeremonyInquiry inquiry = findInquiryOrThrow(inquiryId);
        checkNotClosed(inquiry);
        inquiry.close();

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.CLOSE_CEREMONY_INQUIRY, null,
                inquiry.getCeremony().getOrganization().getId(), "inquiryId=" + inquiryId
        );

        return toAdminDetail(inquiry, ceremonyInquiryMessageRepository.findAllByInquiryIdOrderByCreatedAtAsc(inquiryId));
    }

    // ──────────────────────────── 공용 ────────────────────────────

    private CeremonyInquiry findInquiryInCeremonyOrThrow(Long ceremonyId, Long inquiryId) {
        CeremonyInquiry inquiry = findInquiryOrThrow(inquiryId);
        if (!inquiry.getCeremony().getId().equals(ceremonyId)) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_INQUIRY_NOT_FOUND);
        }
        return inquiry;
    }

    private CeremonyInquiry findInquiryOrThrow(Long inquiryId) {
        return ceremonyInquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CEREMONY_INQUIRY_NOT_FOUND));
    }

    private void checkNotClosed(CeremonyInquiry inquiry) {
        if (inquiry.getStatus() == InquiryStatus.CLOSED) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_INQUIRY_ALREADY_CLOSED);
        }
    }

    private void checkAllowed(String actingPlatformRole) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, ACTION_CEREMONY_INQUIRY_MANAGE)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    private CeremonyInquiryDto.Response.InquirySummary toSummary(CeremonyInquiry inquiry) {
        return new CeremonyInquiryDto.Response.InquirySummary(
                inquiry.getId(), inquiry.getCeremony().getId(), inquiry.getTitle(),
                inquiry.getStatus().name(), inquiry.getLastMessageAt(), inquiry.getCreatedAt()
        );
    }

    private CeremonyInquiryDto.Response.InquiryDetail toDetail(CeremonyInquiry inquiry, List<CeremonyInquiryMessage> messages) {
        return new CeremonyInquiryDto.Response.InquiryDetail(
                inquiry.getId(), inquiry.getCeremony().getId(), inquiry.getTitle(),
                inquiry.getStatus().name(), inquiry.getLastMessageAt(), inquiry.getCreatedAt(),
                messages.stream().map(this::toMessageSummary).toList()
        );
    }

    private CeremonyInquiryDto.Response.MessageSummary toMessageSummary(CeremonyInquiryMessage message) {
        return new CeremonyInquiryDto.Response.MessageSummary(
                message.getId(), message.getSenderType().name(), message.getContent(),
                message.getCreatedBy(), message.getCreatedAt()
        );
    }

    private PlatformAdminCeremonyInquiryDto.Response.InquirySummary toAdminSummary(CeremonyInquiry inquiry) {
        User requester = resolveRequester(inquiry);
        return new PlatformAdminCeremonyInquiryDto.Response.InquirySummary(
                inquiry.getId(),
                inquiry.getCeremony().getOrganization().getId(),
                inquiry.getCeremony().getOrganization().getName(),
                inquiry.getCeremony().getId(),
                inquiry.getCeremony().getTitle(),
                requester == null ? null : requester.getLoginId(),
                requester == null ? null : requester.getName(),
                inquiry.getTitle(),
                inquiry.getStatus().name(),
                inquiry.getLastMessageAt(),
                inquiry.getCreatedAt()
        );
    }

    private PlatformAdminCeremonyInquiryDto.Response.InquiryDetail toAdminDetail(
            CeremonyInquiry inquiry, List<CeremonyInquiryMessage> messages
    ) {
        User requester = resolveRequester(inquiry);
        return new PlatformAdminCeremonyInquiryDto.Response.InquiryDetail(
                inquiry.getId(),
                inquiry.getCeremony().getOrganization().getId(),
                inquiry.getCeremony().getOrganization().getName(),
                inquiry.getCeremony().getId(),
                inquiry.getCeremony().getTitle(),
                requester == null ? null : requester.getLoginId(),
                requester == null ? null : requester.getName(),
                inquiry.getTitle(),
                inquiry.getStatus().name(),
                inquiry.getLastMessageAt(),
                inquiry.getCreatedAt(),
                messages.stream().map(this::toMessageSummary).toList()
        );
    }

    /** 문의를 등록한 사람 — 항상 첫 메시지의 작성자(createdBy)와 같다(헤더 자체엔 요청자 컬럼이 없다). */
    private User resolveRequester(CeremonyInquiry inquiry) {
        return inquiry.getCreatedBy() == null ? null : userRepository.findById(inquiry.getCreatedBy()).orElse(null);
    }
}
