package com.eformworks.signstage.backend.feature.organization.repository;

import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.entity.OrganizationStatus;
import com.eformworks.signstage.backend.feature.organization.entity.QOrganization;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class OrganizationRepositoryImpl implements OrganizationRepositoryCustom {

    private static final QOrganization ORGANIZATION = QOrganization.organization;

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Organization> search(String name, String code, OrganizationStatus status, Pageable pageable) {
        BooleanExpression[] conditions = {
                nameContains(name),
                codeContains(code),
                statusEq(status),
        };

        List<Organization> content = queryFactory.selectFrom(ORGANIZATION)
                .where(conditions)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory.select(ORGANIZATION.count())
                .from(ORGANIZATION)
                .where(conditions)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private BooleanExpression nameContains(String name) {
        return StringUtils.hasText(name) ? ORGANIZATION.name.containsIgnoreCase(name) : null;
    }

    private BooleanExpression codeContains(String code) {
        return StringUtils.hasText(code) ? ORGANIZATION.code.containsIgnoreCase(code) : null;
    }

    private BooleanExpression statusEq(OrganizationStatus status) {
        return status == null ? null : ORGANIZATION.status.eq(status);
    }
}
