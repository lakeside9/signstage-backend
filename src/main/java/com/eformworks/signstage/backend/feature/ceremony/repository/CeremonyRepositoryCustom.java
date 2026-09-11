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
     * {@code organizationId}가 null이면 조직 조건 자체를 걸지 않는다 — 플랫폼 관리자의 조직 횡단
     * 목록(signstage-docs business/discount-management-screen-separation-review.md)이 쓴다.
     * {@code hasFinalDiscount}가 null이 아니면 행사 건별 재량 할인(finalDiscount)이 설정돼(값이
     * 0이 아닌) 있는지 여부로 좁힌다 — 같은 문서 6장 결정 #1(목록 기본 필터).
     * {@code billingPlanId}가 null이 아니면 이 과금 플랜을 쓰는 행사로만 좁힌다 — 조직 횡단
     * 조회다(signstage-docs business/ceremony-plan-price-snapshot-consistency-review.md
     * 3.5절, 2026-09-11) — 카탈로그 관리자가 "이 플랜을 쓰는 행사" 목록을 보는 데 쓴다.
     */
    Page<Ceremony> search(
            Long organizationId, String title, CeremonyStatus status, Long assignedUserId,
            Boolean hasFinalDiscount, Long billingPlanId, Pageable pageable
    );
}
