package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductPurchase;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.QCeremonyUnitProductPurchase;
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

@RequiredArgsConstructor
public class CeremonyUnitProductPurchaseRepositoryImpl implements CeremonyUnitProductPurchaseRepositoryCustom {

    private static final QCeremonyUnitProductPurchase PURCHASE = QCeremonyUnitProductPurchase.ceremonyUnitProductPurchase;
    private static final QUser USER = QUser.user;

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<CeremonyUnitProductPurchase> search(
            PurchaseStatus status, Long organizationId, Long ceremonyId,
            String requesterKeyword, String ceremonyTitle, Pageable pageable
    ) {
        BooleanExpression[] conditions = {
                statusEq(status),
                organizationIdEq(organizationId),
                ceremonyIdEq(ceremonyId),
                requesterKeywordMatches(requesterKeyword),
                ceremonyTitleContains(ceremonyTitle),
        };

        List<CeremonyUnitProductPurchase> content = queryFactory.selectFrom(PURCHASE)
                .where(conditions)
                .orderBy(PURCHASE.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory.select(PURCHASE.count()).from(PURCHASE)
                .where(conditions)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private BooleanExpression statusEq(PurchaseStatus status) {
        return status == null ? null : PURCHASE.status.eq(status);
    }

    private BooleanExpression organizationIdEq(Long organizationId) {
        return organizationId == null ? null : PURCHASE.ceremony.organization.id.eq(organizationId);
    }

    private BooleanExpression ceremonyIdEq(Long ceremonyId) {
        return ceremonyId == null ? null : PURCHASE.ceremony.id.eq(ceremonyId);
    }

    /**
     * 요청자 loginId 또는 name에 포함되는 문자열로 찾는다(2026-09-12 사용자 요청) —
     * {@code createdBy}는 FK가 아니라 순수 사용자 id 참조라 서브쿼리로 매칭한다
     * ({@code UserRepositoryImpl}의 loginId/name 검색과 같은 원칙).
     */
    private BooleanExpression requesterKeywordMatches(String requesterKeyword) {
        if (!StringUtils.hasText(requesterKeyword)) {
            return null;
        }
        return PURCHASE.createdBy.in(
                JPAExpressions.select(USER.id).from(USER)
                        .where(USER.loginId.containsIgnoreCase(requesterKeyword).or(USER.name.containsIgnoreCase(requesterKeyword)))
        );
    }

    private BooleanExpression ceremonyTitleContains(String ceremonyTitle) {
        return StringUtils.hasText(ceremonyTitle) ? PURCHASE.ceremony.title.containsIgnoreCase(ceremonyTitle) : null;
    }
}
