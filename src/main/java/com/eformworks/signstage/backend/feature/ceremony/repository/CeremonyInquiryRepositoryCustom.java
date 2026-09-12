package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyInquiry;
import com.eformworks.signstage.backend.feature.ceremony.entity.InquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * {@link CeremonyInquiryRepository}가 Spring Data 관례로 표현하기 어려운 동적 검색 조건을
 * 담는다. 구현은 {@link CeremonyInquiryRepositoryImpl}(QueryDSL)이 맡는다 —
 * {@code CeremonyUnitProductPurchaseRepositoryCustom}과 같은 패턴(플랫폼 관리자 조직 횡단 조회).
 */
public interface CeremonyInquiryRepositoryCustom {

    /**
     * 플랫폼 관리자 조직 횡단 조회용. {@code organizationId}/{@code ceremonyId}/{@code status}는
     * 전부 선택 필터다(null이면 그 조건을 걸지 않는다).
     *
     * @param requesterKeyword 문의를 등록한 파트너 담당자의 loginId 또는 name에 포함되는
     *                         문자열(대소문자 무시) — {@code CeremonyUnitProductPurchase}와
     *                         같은 원칙으로 {@code createdBy}(순수 사용자 id)를 서브쿼리로 매칭한다.
     * @param ceremonyTitle    행사 제목에 포함되는 문자열(대소문자 무시)
     */
    Page<CeremonyInquiry> search(
            InquiryStatus status, Long organizationId, Long ceremonyId,
            String requesterKeyword, String ceremonyTitle, Pageable pageable
    );
}
