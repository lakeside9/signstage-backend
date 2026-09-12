package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyOnsiteSupportRequest;
import com.eformworks.signstage.backend.feature.ceremony.entity.OnsiteSupportRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * {@link CeremonyOnsiteSupportRequestRepository}가 Spring Data 관례로 표현하기 어려운 동적
 * 검색 조건을 담는다. 구현은 {@link CeremonyOnsiteSupportRequestRepositoryImpl}(QueryDSL)이
 * 맡는다 — {@code CeremonyInquiryRepositoryCustom}과 같은 패턴(플랫폼 관리자 조직 횡단 조회).
 */
public interface CeremonyOnsiteSupportRequestRepositoryCustom {

    /**
     * 플랫폼 관리자 조직 횡단 조회용. {@code organizationId}/{@code ceremonyId}/{@code status}는
     * 전부 선택 필터다(null이면 그 조건을 걸지 않는다).
     *
     * @param requesterKeyword 요청을 등록한 파트너 담당자의 loginId 또는 name에 포함되는
     *                         문자열(대소문자 무시)
     */
    Page<CeremonyOnsiteSupportRequest> search(
            OnsiteSupportRequestStatus status, Long organizationId, Long ceremonyId,
            String requesterKeyword, Pageable pageable
    );
}
