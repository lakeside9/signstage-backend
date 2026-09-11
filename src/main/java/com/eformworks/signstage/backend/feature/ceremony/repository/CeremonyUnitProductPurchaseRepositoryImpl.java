package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductPurchase;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.QCeremonyUnitProductPurchase;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@RequiredArgsConstructor
public class CeremonyUnitProductPurchaseRepositoryImpl implements CeremonyUnitProductPurchaseRepositoryCustom {

    private static final QCeremonyUnitProductPurchase PURCHASE = QCeremonyUnitProductPurchase.ceremonyUnitProductPurchase;

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<CeremonyUnitProductPurchase> search(PurchaseStatus status, Long organizationId, Long ceremonyId, Pageable pageable) {
        BooleanExpression[] conditions = {
                statusEq(status),
                organizationIdEq(organizationId),
                ceremonyIdEq(ceremonyId),
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
}
