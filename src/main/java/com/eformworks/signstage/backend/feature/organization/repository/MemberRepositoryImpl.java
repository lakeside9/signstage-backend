package com.eformworks.signstage.backend.feature.organization.repository;

import com.eformworks.signstage.backend.feature.identity.entity.QUser;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.entity.UserStatus;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.entity.QMember;
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
public class MemberRepositoryImpl implements MemberRepositoryCustom {

    private static final QUser USER = QUser.user;
    private static final QMember MEMBER = QMember.member;

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<User> searchUsersWithoutOrganization(String loginId, String name, String email, Pageable pageable) {
        BooleanExpression[] conditions = {
                USER.status.eq(UserStatus.ACTIVE),
                USER.id.notIn(
                        JPAExpressions.select(MEMBER.user.id)
                                .from(MEMBER)
                                .where(MEMBER.status.eq(MemberStatus.ACTIVE))
                ),
                // 플랫폼 관리자는 조직에 소속될 수 없다(2026-08-24 결정) — 애초에 후보 목록에도
                // 띄우지 않는다. 실제 추가 시점 검사는 MemberService/PlatformAdminMemberService가 한다.
                USER.platformRole.isNull(),
                loginIdContains(loginId),
                nameContains(name),
                emailContains(email),
        };

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
}
