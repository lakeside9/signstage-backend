package com.eformworks.signstage.backend.feature.support.repository;

import com.eformworks.signstage.backend.feature.support.entity.Announcement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * {@link AnnouncementRepository}가 Spring Data 관례로 표현하기 어려운 동적 검색 조건을
 * 담는다. 구현은 {@link AnnouncementRepositoryImpl}(QueryDSL)이 맡는다 —
 * {@code FaqRepositoryCustom}과 같은 패턴(2026-09-12 사용자 요청).
 */
public interface AnnouncementRepositoryCustom {

    /** keyword/active 둘 다 선택 필터다. keyword는 title/content 중 하나라도 포함하면 매칭된다. */
    Page<Announcement> search(String keyword, Boolean active, Pageable pageable);
}
