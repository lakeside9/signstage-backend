package com.eformworks.signstage.backend.core.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * QueryDSL로 동적 검색 조건을 만들 때 쓰는 {@link JPAQueryFactory}를 빈으로 등록한다.
 * 각 feature의 {@code XxxRepositoryImpl}은 이 빈을 생성자로 주입받아 사용한다.
 */
@Configuration
public class QuerydslConfig {

    @PersistenceContext
    private EntityManager entityManager;

    @Bean
    public JPAQueryFactory jpaQueryFactory() {
        return new JPAQueryFactory(entityManager);
    }
}
