package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductPurchase;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * {@link CeremonyUnitProductPurchaseRepository}가 Spring Data 관례로 표현하기 어려운 동적 검색
 * 조건을 담는다. 구현은 {@link CeremonyUnitProductPurchaseRepositoryImpl}(QueryDSL)이 맡는다 —
 * {@code CeremonyRepositoryCustom}과 같은 패턴.
 */
public interface CeremonyUnitProductPurchaseRepositoryCustom {

    /**
     * 플랫폼 관리자 구매 이력 조회용. 전부 선택 필터다(null/빈 문자열이면 그 조건을 걸지
     * 않는다) — 기존 "승인 큐"(status=PENDING 기본)와 신규 "행사 이력" 화면(ceremonyId로
     * 좁힘)이 같은 조회를 공유한다(signstage-docs
     * business/unit-product-purchase-self-checkout-review.md 8.6절 결정, 2026-09-11).
     *
     * @param requesterKeyword 요청자 loginId 또는 name에 포함되는 문자열(대소문자 무시,
     *                         2026-09-12 사용자 요청 추가)
     * @param ceremonyTitle    행사 제목에 포함되는 문자열(대소문자 무시, 2026-09-12 추가)
     */
    Page<CeremonyUnitProductPurchase> search(
            PurchaseStatus status, Long organizationId, Long ceremonyId,
            String requesterKeyword, String ceremonyTitle, Pageable pageable
    );
}
