package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectDefinition;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectTarget;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectTrigger;
import com.eformworks.signstage.backend.feature.ceremony.entity.QCeremonyEffectDefinition;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class CeremonyEffectDefinitionRepositoryImpl implements CeremonyEffectDefinitionRepositoryCustom {

    private static final QCeremonyEffectDefinition DEFINITION = QCeremonyEffectDefinition.ceremonyEffectDefinition;

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<CeremonyEffectDefinition> search(
            String keyword,
            CeremonyEffectTarget targetType,
            CeremonyEffectTrigger triggerType,
            Boolean enabled,
            Boolean userVisible,
            Pageable pageable
    ) {
        BooleanExpression[] conditions = {
                keywordContains(keyword), targetTypeEq(targetType), triggerTypeEq(triggerType),
                enabledEq(enabled), userVisibleEq(userVisible),
        };

        List<CeremonyEffectDefinition> content = queryFactory.selectFrom(DEFINITION)
                .where(conditions)
                .orderBy(DEFINITION.targetType.asc(), DEFINITION.triggerType.asc(), DEFINITION.displayOrder.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory.select(DEFINITION.count()).from(DEFINITION).where(conditions).fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return DEFINITION.code.containsIgnoreCase(keyword).or(DEFINITION.displayName.containsIgnoreCase(keyword));
    }

    private BooleanExpression targetTypeEq(CeremonyEffectTarget targetType) {
        return targetType == null ? null : DEFINITION.targetType.eq(targetType);
    }

    private BooleanExpression triggerTypeEq(CeremonyEffectTrigger triggerType) {
        return triggerType == null ? null : DEFINITION.triggerType.eq(triggerType);
    }

    private BooleanExpression enabledEq(Boolean enabled) {
        return enabled == null ? null : DEFINITION.enabled.eq(enabled);
    }

    private BooleanExpression userVisibleEq(Boolean userVisible) {
        return userVisible == null ? null : DEFINITION.userVisible.eq(userVisible);
    }
}
