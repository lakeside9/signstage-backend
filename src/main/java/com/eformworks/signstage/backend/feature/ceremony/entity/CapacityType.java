package com.eformworks.signstage.backend.feature.ceremony.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * 필수옵션(용량 한도) 추가구매 상품이 어느 한도를 늘리는지 구분한다.
 * signstage-docs business/ceremony-billing-options-review.md 4.7절(CapacityAddOn) 참고.
 */
public enum CapacityType {
    SIGNERS,
    TEMPLATES,
    TEST_EVENTS,
    /** 하위 행사 REHEARSAL 구분의 한도(2026-08-27 legacy 포팅) — TEST와는 별도 버킷이다. */
    REHEARSAL_EVENTS,
    MAIN_EVENTS,
    /**
     * 태블릿 대여 대수. 다른 값과 달리 플랜 기본 포함 개념이 없다 — 항상 0에서 시작해
     * {@link CapacityAddOn} 추가구매로만 늘어난다(signstage-docs
     * business/ceremony-billing-options-review.md 4.7절 후속, 2026-08-21). 그래서
     * {@link #isPlanIncludable()}가 {@code false}다.
     */
    TABLETS,
    /**
     * 현장지원 실제 지원 건수 — {@code TABLETS}와 같은 이유로 플랜 기본 포함 개념이 없다
     * (2026-09-08 결정, signstage-docs business/ceremony-support-services-billing-review.md
     * 참고). 수도권/지방 출장비 차등은 이 타입을 공유하는 {@link CapacityAddOn} 카탈로그 행을
     * 두 개로 나눠 등록하는 방식으로 표현한다.
     */
    ONSITE_SUPPORT,
    /** 온라인지원 실제 지원 건수 — {@code ONSITE_SUPPORT}와 같은 이유로 플랜 기본 포함 개념이 없다. */
    ONLINE_SUPPORT;

    /**
     * 이 값이 {@link BillingPlan}에 기본 포함 수량으로 등록될 수 있는지 — signstage-docs
     * business/billing-catalog-zero-base-schema-redesign-review.md 결정(2026-09-08, 항목 B).
     * 예전엔 서명자/템플릿/테스트행사/리허설행사/본행사 5종만 {@code BillingPlan}에 고정 컬럼으로
     * 있었고 {@code TABLETS}는 대응 컬럼이 없었다 — 이제 고정 컬럼 대신 {@link BillingPlanCapacity}
     * 조인 테이블로 표현하지만, "어떤 종류가 플랜에 기본 포함될 수 있는가"라는 구분 자체는
     * 그대로 유지한다(`TABLETS`는 여전히 제외). 새로 추가되는 용량 종류가 플랜 기본 포함
     * 대상이면 이 집합에 넣는 것으로 대응한다(스키마 변경 불필요, enum 값 추가 배포만 필요).
     */
    public boolean isPlanIncludable() {
        return PLAN_INCLUDABLE.contains(this);
    }

    private static final Set<CapacityType> PLAN_INCLUDABLE =
            EnumSet.of(SIGNERS, TEMPLATES, TEST_EVENTS, REHEARSAL_EVENTS, MAIN_EVENTS);

    public static Set<CapacityType> planIncludableTypes() {
        return EnumSet.copyOf(PLAN_INCLUDABLE);
    }
}
