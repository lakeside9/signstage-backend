package com.eformworks.signstage.backend.feature.support.repository;

import com.eformworks.signstage.backend.feature.support.entity.ContactInquiry;
import com.eformworks.signstage.backend.feature.support.entity.ContactInquiryStatus;
import com.eformworks.signstage.backend.feature.support.entity.QContactInquiry;
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
public class ContactInquiryRepositoryImpl implements ContactInquiryRepositoryCustom {

    private static final QContactInquiry INQUIRY = QContactInquiry.contactInquiry;

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<ContactInquiry> search(ContactInquiryStatus status, String keyword, Pageable pageable) {
        BooleanExpression[] conditions = { statusEq(status), keywordContains(keyword) };

        List<ContactInquiry> content = queryFactory.selectFrom(INQUIRY)
                .where(conditions)
                .orderBy(INQUIRY.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory.select(INQUIRY.count()).from(INQUIRY).where(conditions).fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private BooleanExpression statusEq(ContactInquiryStatus status) {
        return status == null ? null : INQUIRY.status.eq(status);
    }

    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return INQUIRY.companyName.containsIgnoreCase(keyword)
                .or(INQUIRY.contactName.containsIgnoreCase(keyword))
                .or(INQUIRY.email.containsIgnoreCase(keyword))
                .or(INQUIRY.message.containsIgnoreCase(keyword));
    }
}
