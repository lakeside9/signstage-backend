package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyInquiry;
import com.eformworks.signstage.backend.feature.ceremony.entity.InquiryStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.QCeremonyInquiry;
import com.eformworks.signstage.backend.feature.identity.entity.QUser;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

/** {@code CeremonyUnitProductPurchaseRepositoryImpl}과 완전히 같은 모양이다. */
@RequiredArgsConstructor
public class CeremonyInquiryRepositoryImpl implements CeremonyInquiryRepositoryCustom {

    private static final QCeremonyInquiry INQUIRY = QCeremonyInquiry.ceremonyInquiry;
    private static final QUser USER = QUser.user;

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<CeremonyInquiry> search(
            InquiryStatus status, Long organizationId, Long ceremonyId,
            String requesterKeyword, String ceremonyTitle, Pageable pageable
    ) {
        BooleanExpression[] conditions = {
                statusEq(status),
                organizationIdEq(organizationId),
                ceremonyIdEq(ceremonyId),
                requesterKeywordMatches(requesterKeyword),
                ceremonyTitleContains(ceremonyTitle),
        };

        List<CeremonyInquiry> content = queryFactory.selectFrom(INQUIRY)
                .where(conditions)
                .orderBy(INQUIRY.lastMessageAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory.select(INQUIRY.count()).from(INQUIRY)
                .where(conditions)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private BooleanExpression statusEq(InquiryStatus status) {
        return status == null ? null : INQUIRY.status.eq(status);
    }

    private BooleanExpression organizationIdEq(Long organizationId) {
        return organizationId == null ? null : INQUIRY.ceremony.organization.id.eq(organizationId);
    }

    private BooleanExpression ceremonyIdEq(Long ceremonyId) {
        return ceremonyId == null ? null : INQUIRY.ceremony.id.eq(ceremonyId);
    }

    private BooleanExpression requesterKeywordMatches(String requesterKeyword) {
        if (!StringUtils.hasText(requesterKeyword)) {
            return null;
        }
        return INQUIRY.createdBy.in(
                JPAExpressions.select(USER.id).from(USER)
                        .where(USER.loginId.containsIgnoreCase(requesterKeyword).or(USER.name.containsIgnoreCase(requesterKeyword)))
        );
    }

    private BooleanExpression ceremonyTitleContains(String ceremonyTitle) {
        return StringUtils.hasText(ceremonyTitle) ? INQUIRY.ceremony.title.containsIgnoreCase(ceremonyTitle) : null;
    }
}
