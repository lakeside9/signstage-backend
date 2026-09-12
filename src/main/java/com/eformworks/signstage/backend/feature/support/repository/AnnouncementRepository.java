package com.eformworks.signstage.backend.feature.support.repository;

import com.eformworks.signstage.backend.feature.support.entity.Announcement;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    /** {@code /api/announcements} — 인증된 사용자 누구나, 활성 공지만 고정 우선 + 최신순. */
    List<Announcement> findAllByActiveTrueOrderByPinnedDescCreatedAtDesc();

    Page<Announcement> findAllByActiveOrderByPinnedDescCreatedAtDesc(boolean active, Pageable pageable);

    Page<Announcement> findAllByOrderByPinnedDescCreatedAtDesc(Pageable pageable);
}
