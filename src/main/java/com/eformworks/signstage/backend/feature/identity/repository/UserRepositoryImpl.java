package com.eformworks.signstage.backend.feature.identity.repository;

import com.eformworks.signstage.backend.feature.identity.entity.PlatformRole;
import com.eformworks.signstage.backend.feature.identity.entity.QUser;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.entity.UserStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepositoryCustom {

    private static final QUser USER = QUser.user;

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<User> search(String loginId, String name, String email, UserStatus status, Pageable pageable) {
        return fetchPage(
                pageable,
                // 플랫폼 관리자는 조직 소속과 배타적이므로(2026-08-24 결정) 회원 관리 목록에는
                // 뜨지 않는다 — 관리자 계정은 별도 화면(관리자 계정, searchAccounts)에서 다룬다.
                USER.platformRole.isNull(),
                loginIdContains(loginId),
                nameContains(name),
                emailContains(email),
                statusEq(status)
        );
    }

    @Override
    public Page<User> searchAccounts(String loginId, String name, String email, PlatformRole platformRole, Pageable pageable) {
        return fetchPage(
                pageable,
                USER.platformRole.isNotNull(),
                loginIdContains(loginId),
                nameContains(name),
                emailContains(email),
                platformRoleEq(platformRole)
        );
    }

    private Page<User> fetchPage(Pageable pageable, BooleanExpression... conditions) {
        List<User> content = queryFactory.selectFrom(USER)
                .where(conditions)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory.select(USER.count())
                .from(USER)
                .where(conditions)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private BooleanExpression loginIdContains(String loginId) {
        return StringUtils.hasText(loginId) ? USER.loginId.containsIgnoreCase(loginId) : null;
    }

    private BooleanExpression nameContains(String name) {
        return StringUtils.hasText(name) ? USER.name.containsIgnoreCase(name) : null;
    }

    private BooleanExpression emailContains(String email) {
        return StringUtils.hasText(email) ? USER.email.containsIgnoreCase(email) : null;
    }

    private BooleanExpression statusEq(UserStatus status) {
        return status == null ? null : USER.status.eq(status);
    }

    private BooleanExpression platformRoleEq(PlatformRole platformRole) {
        return platformRole == null ? null : USER.platformRole.eq(platformRole);
    }
}
