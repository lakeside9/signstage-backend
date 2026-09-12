package com.eformworks.signstage.backend.feature.support.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.support.dto.AnnouncementDto;
import com.eformworks.signstage.backend.feature.support.entity.Announcement;
import com.eformworks.signstage.backend.feature.support.error.SupportErrorCode;
import com.eformworks.signstage.backend.feature.support.repository.AnnouncementRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공지사항 관리 — signstage-docs business/partner-support-center-review.md 3장.
 * {@code CeremonyEffectDefinitionService}와 같은 패턴: 등록·수정·삭제는 플랫폼 관리자
 * 전용({@code ACTION_ANNOUNCEMENT_MANAGE}), 활성 목록 조회는 인증된 사용자 누구나 가능하다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnnouncementService {

    private static final String ACTION_ANNOUNCEMENT_MANAGE = "ACTION_ANNOUNCEMENT_MANAGE";

    private final AnnouncementRepository announcementRepository;
    private final PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;
    private final RolePermissionService rolePermissionService;

    /** {@code /api/announcements} — 인증된 사용자 누구나, 활성 공지만 고정 우선 + 최신순. */
    public List<AnnouncementDto.Response.AnnouncementSummary> findPublicAnnouncements() {
        return announcementRepository.findAllByActiveTrueOrderByPinnedDescCreatedAtDesc().stream()
                .map(this::toSummary)
                .toList();
    }

    public AnnouncementDto.Response.AnnouncementSummary findPublicAnnouncement(Long announcementId) {
        Announcement announcement = announcementRepository.findById(announcementId)
                .filter(Announcement::isActive)
                .orElseThrow(() -> new ApplicationException(SupportErrorCode.ANNOUNCEMENT_NOT_FOUND));
        return toSummary(announcement);
    }

    /**
     * 관리자 상세/수정 화면용 — {@link #findPublicAnnouncement}와 달리 활성 여부로 걸러내지
     * 않는다(관리자는 비활성 공지도 수정할 수 있어야 한다, 2026-09-12 페이지 전환 시 발견).
     */
    public AnnouncementDto.Response.AnnouncementSummary findAnnouncement(Long announcementId) {
        return toSummary(announcementRepository.findById(announcementId)
                .orElseThrow(() -> new ApplicationException(SupportErrorCode.ANNOUNCEMENT_NOT_FOUND)));
    }

    /** {@code keyword}는 title/content 중 하나라도 포함하면 매칭된다(2026-09-12 사용자 요청). */
    public Page<AnnouncementDto.Response.AnnouncementSummary> findAnnouncements(String keyword, Boolean active, Pageable pageable) {
        return announcementRepository.search(keyword, active, pageable).map(this::toSummary);
    }

    @Transactional
    public AnnouncementDto.Response.AnnouncementSummary createAnnouncement(
            String actingPlatformRole, Long adminUserId, AnnouncementDto.Request.CreateAnnouncement request
    ) {
        checkAllowed(actingPlatformRole);

        Announcement announcement = Announcement.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .pinned(request.getPinned() != null && request.getPinned())
                .build();
        announcementRepository.save(announcement);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.CREATE_ANNOUNCEMENT, null, null, "announcementId=" + announcement.getId()
        );
        return toSummary(announcement);
    }

    @Transactional
    public AnnouncementDto.Response.AnnouncementSummary updateAnnouncement(
            Long announcementId, String actingPlatformRole, Long adminUserId,
            AnnouncementDto.Request.UpdateAnnouncement request
    ) {
        checkAllowed(actingPlatformRole);

        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new ApplicationException(SupportErrorCode.ANNOUNCEMENT_NOT_FOUND));
        announcement.updateInfo(request.getTitle(), request.getContent(), request.getPinned(), request.getActive());

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_ANNOUNCEMENT, null, null, "announcementId=" + announcementId
        );
        return toSummary(announcement);
    }

    @Transactional
    public void deleteAnnouncement(Long announcementId, String actingPlatformRole, Long adminUserId) {
        checkAllowed(actingPlatformRole);

        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new ApplicationException(SupportErrorCode.ANNOUNCEMENT_NOT_FOUND));
        announcementRepository.delete(announcement);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.DELETE_ANNOUNCEMENT, null, null, "announcementId=" + announcementId
        );
    }

    private AnnouncementDto.Response.AnnouncementSummary toSummary(Announcement announcement) {
        return new AnnouncementDto.Response.AnnouncementSummary(
                announcement.getId(), announcement.getTitle(), announcement.getContent(),
                announcement.isPinned(), announcement.isActive(), announcement.getCreatedAt()
        );
    }

    private void checkAllowed(String actingPlatformRole) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, ACTION_ANNOUNCEMENT_MANAGE)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }
}
