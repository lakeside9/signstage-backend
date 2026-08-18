package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.QCeremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.QCeremonyAssignment;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
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
    public Page<Ceremony> search(Long organizationId, String title, CeremonyStatus status, Long assignedUserId, Pageable pageable) {
        // ceremony_assignments에 (ceremony_id, user_id) 유니크 제약이 있어(uq_ca_ceremony_user),
        // assignedUserId로 좁혀도 한 Ceremony당 조인 결과가 많아야 1행이다 — distinct 불필요.
        BooleanExpression[] conditions = {
                CEREMONY.organization.id.eq(organizationId),
                titleContains(title),
                statusEq(status),
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

    private BooleanExpression titleContains(String title) {
        return StringUtils.hasText(title) ? CEREMONY.title.containsIgnoreCase(title) : null;
    }

    private BooleanExpression statusEq(CeremonyStatus status) {
        return status == null ? null : CEREMONY.status.eq(status);
    }
}
