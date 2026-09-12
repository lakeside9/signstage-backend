package com.eformworks.signstage.backend.feature.support.repository;

import com.eformworks.signstage.backend.feature.support.entity.Faq;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * {@link FaqRepository}가 Spring Data 관례로 표현하기 어려운 동적 검색 조건을 담는다 —
 * {@code CeremonyEffectDefinitionRepositoryCustom}과 같은 패턴. 구현은
 * {@link FaqRepositoryImpl}(QueryDSL)이 맡는다.
 */
public interface FaqRepositoryCustom {

    /**
     * 관리자 목록 조회용(2026-09-12 사용자 요청 — "공지사항/FAQ에도 검색 기능을 적용해주세요").
     * keyword/active 둘 다 선택 필터다(null/빈 문자열이면 그 조건을 걸지 않는다). keyword는
     * category/question/answer 중 하나라도 포함하면 매칭된다.
     */
    Page<Faq> search(String keyword, Boolean active, Pageable pageable);
}
