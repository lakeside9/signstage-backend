package com.eformworks.signstage.backend.feature.support.repository;

import com.eformworks.signstage.backend.feature.support.entity.Announcement;
import com.eformworks.signstage.backend.feature.support.entity.QAnnouncement;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

/** {@code FaqRepositoryImpl}과 같은 모양이다. */
@RequiredArgsConstructor
public class AnnouncementRepositoryImpl implements AnnouncementRepositoryCustom {

    private static final QAnnouncement ANNOUNCEMENT = QAnnouncement.announcement;

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Announcement> search(String keyword, Boolean active, Pageable pageable) {
        BooleanExpression[] conditions = { keywordContains(keyword), activeEq(active) };

        List<Announcement> content = queryFactory.selectFrom(ANNOUNCEMENT)
                .where(conditions)
                .orderBy(ANNOUNCEMENT.pinned.desc(), ANNOUNCEMENT.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory.select(ANNOUNCEMENT.count()).from(ANNOUNCEMENT).where(conditions).fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return ANNOUNCEMENT.title.containsIgnoreCase(keyword).or(ANNOUNCEMENT.content.containsIgnoreCase(keyword));
    }

    private BooleanExpression activeEq(Boolean active) {
        return active == null ? null : ANNOUNCEMENT.active.eq(active);
    }
}
