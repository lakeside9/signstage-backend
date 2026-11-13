package com.eformworks.signstage.backend.feature.support.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.support.dto.AnnouncementDto;
import com.eformworks.signstage.backend.feature.support.entity.Announcement;
import com.eformworks.signstage.backend.feature.support.entity.SupportAudience;
import com.eformworks.signstage.backend.feature.support.error.SupportErrorCode;
import com.eformworks.signstage.backend.feature.support.repository.AnnouncementRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/** {@link AnnouncementService} 단위 테스트 — signstage-docs business/partner-support-center-review.md 3장. */
@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {

    @Mock
    private AnnouncementRepository announcementRepository;
    @Mock
    private PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;
    @Mock
    private RolePermissionService rolePermissionService;

    private AnnouncementService announcementService;

    @BeforeEach
    void setUp() {
        announcementService = new AnnouncementService(announcementRepository, platformAdminAuditLogRecorder, rolePermissionService);
    }

    @Test
    @DisplayName("PLATFORM_OPS 미만 등급은 공지사항을 등록할 수 없다")
    void createAnnouncement_insufficientRole_fail() {
        given(rolePermissionService.isAllowed("PLATFORM_SUPPORT", "ACTION_ANNOUNCEMENT_MANAGE")).willReturn(false);

        assertThatThrownBy(() -> announcementService.createAnnouncement(
                "PLATFORM_SUPPORT", 1L, new AnnouncementDto.Request.CreateAnnouncement(SupportAudience.PARTNER, "제목", "내용", null)
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);
        verify(announcementRepository, never()).save(any());
    }

    @Test
    @DisplayName("등록 — pinned를 생략하면 false로 저장한다")
    void createAnnouncement_pinnedOmitted_defaultsFalse() {
        given(rolePermissionService.isAllowed("PLATFORM_OPS", "ACTION_ANNOUNCEMENT_MANAGE")).willReturn(true);
        given(announcementRepository.save(any())).willAnswer(invocation -> {
            Announcement saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });

        AnnouncementDto.Response.AnnouncementSummary response = announcementService.createAnnouncement(
                "PLATFORM_OPS", 1L, new AnnouncementDto.Request.CreateAnnouncement(SupportAudience.PARTNER, "제목", "내용", null)
        );

        assertThat(response.isPinned()).isFalse();
    }

    @Test
    @DisplayName("비활성 공지는 상세 조회에서 찾을 수 없다")
    void findPublicAnnouncement_inactive_notFound() {
        Announcement announcement = Announcement.builder().audience(SupportAudience.PARTNER).title("제목").content("내용").pinned(false).build();
        ReflectionTestUtils.setField(announcement, "id", 1L);
        announcement.updateInfo(SupportAudience.PARTNER, "제목", "내용", false, false);
        given(announcementRepository.findById(1L)).willReturn(Optional.of(announcement));

        assertThatThrownBy(() -> announcementService.findPublicAnnouncement(SupportAudience.PARTNER, 1L))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(SupportErrorCode.ANNOUNCEMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 대상의 공지는 상세 조회에서 찾을 수 없다 — 파트너용 공지를 공개 API로 열 수 없다")
    void findPublicAnnouncement_otherAudience_notFound() {
        Announcement announcement = Announcement.builder().audience(SupportAudience.PARTNER).title("제목").content("내용").pinned(false).build();
        given(announcementRepository.findById(1L)).willReturn(Optional.of(announcement));

        assertThatThrownBy(() -> announcementService.findPublicAnnouncement(SupportAudience.CUSTOMER, 1L))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(SupportErrorCode.ANNOUNCEMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("관리자 상세 조회(findAnnouncement)는 비활성 공지도 볼 수 있다 — 수정 화면용")
    void findAnnouncement_inactive_returnsAnyway() {
        Announcement announcement = Announcement.builder().audience(SupportAudience.PARTNER).title("제목").content("내용").pinned(false).build();
        ReflectionTestUtils.setField(announcement, "id", 1L);
        announcement.updateInfo(SupportAudience.PARTNER, "제목", "내용", false, false);
        given(announcementRepository.findById(1L)).willReturn(Optional.of(announcement));

        AnnouncementDto.Response.AnnouncementSummary response = announcementService.findAnnouncement(1L);

        assertThat(response.isActive()).isFalse();
    }

    @Test
    @DisplayName("관리자 목록 조회 — keyword/active를 그대로 리포지토리 검색에 넘긴다")
    void findAnnouncements_delegatesKeywordAndActiveToRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        Announcement announcement = Announcement.builder().audience(SupportAudience.PARTNER).title("점검 안내").content("내용").pinned(false).build();
        given(announcementRepository.search(SupportAudience.PARTNER, "점검", true, pageable))
                .willReturn(new PageImpl<>(List.of(announcement), pageable, 1));

        Page<AnnouncementDto.Response.AnnouncementSummary> response =
                announcementService.findAnnouncements(SupportAudience.PARTNER, "점검", true, pageable);

        assertThat(response.getTotalElements()).isEqualTo(1);
        verify(announcementRepository).search(SupportAudience.PARTNER, "점검", true, pageable);
    }

    @Test
    @DisplayName("공개 목록 — 활성 공지만 고정 우선 + 최신순으로 반환한다")
    void findPublicAnnouncements_returnsActiveOnly() {
        Announcement pinned = Announcement.builder().audience(SupportAudience.PARTNER).title("고정 공지").content("내용").pinned(true).build();
        given(announcementRepository.findAllByAudienceAndActiveTrueOrderByPinnedDescCreatedAtDesc(SupportAudience.CUSTOMER)).willReturn(List.of(pinned));

        List<AnnouncementDto.Response.AnnouncementSummary> response = announcementService.findPublicAnnouncements(SupportAudience.CUSTOMER);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).isPinned()).isTrue();
    }
}
