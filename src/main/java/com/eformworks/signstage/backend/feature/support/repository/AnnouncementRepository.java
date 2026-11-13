package com.eformworks.signstage.backend.feature.support.repository;

import com.eformworks.signstage.backend.feature.support.entity.Announcement;
import com.eformworks.signstage.backend.feature.support.entity.SupportAudience;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long>, AnnouncementRepositoryCustom {

    /** 대상별 활성 공지 — 파트너용은 {@code /api/announcements}, 고객용은 공개 {@code /api/public/announcements}. 고정 우선 + 최신순. */
    List<Announcement> findAllByAudienceAndActiveTrueOrderByPinnedDescCreatedAtDesc(SupportAudience audience);
}
