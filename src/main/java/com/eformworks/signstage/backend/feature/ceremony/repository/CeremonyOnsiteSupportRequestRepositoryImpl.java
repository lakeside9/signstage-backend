package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyOnsiteSupportRequest;
import com.eformworks.signstage.backend.feature.ceremony.entity.OnsiteSupportRequestStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.QCeremonyOnsiteSupportRequest;
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

/** {@code CeremonyInquiryRepositoryImpl}과 완전히 같은 모양이다. */
@RequiredArgsConstructor
public class CeremonyOnsiteSupportRequestRepositoryImpl implements CeremonyOnsiteSupportRequestRepositoryCustom {

    private static final QCeremonyOnsiteSupportRequest REQUEST = QCeremonyOnsiteSupportRequest.ceremonyOnsiteSupportRequest;
    private static final QUser USER = QUser.user;

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<CeremonyOnsiteSupportRequest> search(
            OnsiteSupportRequestStatus status, Long organizationId, Long ceremonyId, String requesterKeyword, Pageable pageable
    ) {
        BooleanExpression[] conditions = {
                statusEq(status), organizationIdEq(organizationId), ceremonyIdEq(ceremonyId), requesterKeywordMatches(requesterKeyword),
        };

        List<CeremonyOnsiteSupportRequest> content = queryFactory.selectFrom(REQUEST)
                .where(conditions)
                .orderBy(REQUEST.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory.select(REQUEST.count()).from(REQUEST).where(conditions).fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private BooleanExpression statusEq(OnsiteSupportRequestStatus status) {
        return status == null ? null : REQUEST.status.eq(status);
    }

    private BooleanExpression organizationIdEq(Long organizationId) {
        return organizationId == null ? null : REQUEST.ceremony.organization.id.eq(organizationId);
    }

    private BooleanExpression ceremonyIdEq(Long ceremonyId) {
        return ceremonyId == null ? null : REQUEST.ceremony.id.eq(ceremonyId);
    }

    private BooleanExpression requesterKeywordMatches(String requesterKeyword) {
        if (!StringUtils.hasText(requesterKeyword)) {
            return null;
        }
        return REQUEST.createdBy.in(
                JPAExpressions.select(USER.id).from(USER)
                        .where(USER.loginId.containsIgnoreCase(requesterKeyword).or(USER.name.containsIgnoreCase(requesterKeyword)))
        );
    }
}
