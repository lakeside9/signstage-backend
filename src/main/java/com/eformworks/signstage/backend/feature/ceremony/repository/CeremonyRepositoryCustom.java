package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * {@link CeremonyRepository}가 Spring Data 관례로 표현하기 어려운 동적 검색 조건을 담는다.
 * 구현은 {@link CeremonyRepositoryImpl}(QueryDSL)이 맡는다.
 */
public interface CeremonyRepositoryCustom {

    /**
     * 행사 목록 검색용. title은 부분 일치(대소문자 무시), status는 정확히 일치, 둘 다 값이 없으면(null)
     * 무시된다. {@code assignedUserId}가 null이 아니면 그 사용자가 배정된(CeremonyAssignment) 행사로만
     * 좁힌다 — OPERATOR 스코핑용, OWNER/ADMIN/VIEWER는 null로 넘겨 조직 전체를 본다.
     */
    Page<Ceremony> search(Long organizationId, String title, CeremonyStatus status, Long assignedUserId, Pageable pageable);
}
