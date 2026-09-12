package com.eformworks.signstage.backend.feature.support.repository;

import com.eformworks.signstage.backend.feature.support.entity.Faq;
import com.eformworks.signstage.backend.feature.support.entity.QFaq;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

/** {@code CeremonyEffectDefinitionRepositoryImpl}과 같은 모양이다. */
@RequiredArgsConstructor
public class FaqRepositoryImpl implements FaqRepositoryCustom {

    private static final QFaq FAQ = QFaq.faq;

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Faq> search(String keyword, Boolean active, Pageable pageable) {
        BooleanExpression[] conditions = { keywordContains(keyword), activeEq(active) };

        List<Faq> content = queryFactory.selectFrom(FAQ)
                .where(conditions)
                .orderBy(FAQ.displayOrder.asc(), FAQ.id.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory.select(FAQ.count()).from(FAQ).where(conditions).fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return FAQ.category.containsIgnoreCase(keyword)
                .or(FAQ.question.containsIgnoreCase(keyword))
                .or(FAQ.answer.containsIgnoreCase(keyword));
    }

    private BooleanExpression activeEq(Boolean active) {
        return active == null ? null : FAQ.active.eq(active);
    }
}
