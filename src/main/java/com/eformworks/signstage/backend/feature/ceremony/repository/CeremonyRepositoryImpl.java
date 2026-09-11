package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.QCeremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.QCeremonyAssignment;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class CeremonyRepositoryImpl implements CeremonyRepositoryCustom {

    private static final QCeremony CEREMONY = QCeremony.ceremony;
    private static final QCeremonyAssignment CEREMONY_ASSIGNMENT = QCeremonyAssignment.ceremonyAssignment;

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Ceremony> search(
            Long organizationId, String title, CeremonyStatus status, Long assignedUserId,
            Boolean hasFinalDiscount, Long billingPlanId, Pageable pageable
    ) {
        // ceremony_assignments에 (ceremony_id, user_id) 유니크 제약이 있어(uq_ca_ceremony_user),
        // assignedUserId로 좁혀도 한 Ceremony당 조인 결과가 많아야 1행이다 — distinct 불필요.
        BooleanExpression[] conditions = {
                organizationIdEq(organizationId),
                titleContains(title),
                statusEq(status),
                hasFinalDiscountEq(hasFinalDiscount),
                billingPlanIdEq(billingPlanId),
        };

        List<Ceremony> content = joinAssignmentIfNeeded(queryFactory.selectFrom(CEREMONY), assignedUserId)
                .where(conditions)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = joinAssignmentIfNeeded(queryFactory.select(CEREMONY.count()).from(CEREMONY), assignedUserId)
                .where(conditions)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private <T> JPAQuery<T> joinAssignmentIfNeeded(JPAQuery<T> query, Long assignedUserId) {
        if (assignedUserId == null) {
            return query;
        }
        return query.join(CEREMONY_ASSIGNMENT).on(CEREMONY_ASSIGNMENT.ceremony.eq(CEREMONY))
                .where(CEREMONY_ASSIGNMENT.user.id.eq(assignedUserId));
    }

    /**
     * null이면 조건 자체를 걸지 않는다 — 플랫폼 관리자의 조직 횡단 목록 화면(signstage-docs
     * business/discount-management-screen-separation-review.md)이 organizationId 없이 이
     * 메서드를 그대로 재사용한다.
     */
    private BooleanExpression organizationIdEq(Long organizationId) {
        return organizationId == null ? null : CEREMONY.organization.id.eq(organizationId);
    }

    private BooleanExpression titleContains(String title) {
        return StringUtils.hasText(title) ? CEREMONY.title.containsIgnoreCase(title) : null;
    }

    private BooleanExpression statusEq(CeremonyStatus status) {
        return status == null ? null : CEREMONY.status.eq(status);
    }

    private BooleanExpression billingPlanIdEq(Long billingPlanId) {
        return billingPlanId == null ? null : CEREMONY.billingPlan.id.eq(billingPlanId);
    }

    private BooleanExpression hasFinalDiscountEq(Boolean hasFinalDiscount) {
        if (hasFinalDiscount == null) {
            return null;
        }
        BooleanExpression discounted = CEREMONY.finalDiscount.discountValue.ne(BigDecimal.ZERO);
        return hasFinalDiscount ? discounted : discounted.not();
    }
}
