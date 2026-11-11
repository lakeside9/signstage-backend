package com.eformworks.signstage.backend.feature.billing.port;

/**
 * 과금 카탈로그(플랜·단위 상품)와 구독을 행사가 얼마나 쓰고 있는지 조회하는 계약. 구현은
 * {@code feature.ceremony}가 제공한다 — billing이 ceremony의 Entity/Repository를 직접 참조하지
 * 않도록 의존 방향을 뒤집는다(backend-coding-convention.md 3.4절).
 */
public interface CeremonyBillingUsagePort {

    /** 행사가 지금 이 플랜을 쓰고 있거나, 플랜 확정 스냅샷으로 남긴 적이 있는지. */
    boolean isBillingPlanUsed(Long billingPlanId);

    /** 지금 이 플랜을 쓰는 행사 수. */
    long countCeremoniesByBillingPlan(Long billingPlanId);

    /** 행사 플랜 스냅샷·단위 상품 구매·이벤트 옵션 기능·효과 묶음 중 하나라도 이 단위 상품을 참조하는지. */
    boolean isUnitProductUsed(Long unitProductId);

    /** 승인된 단위 상품 구매 건수. */
    long countApprovedPurchases(Long unitProductId);

    /** 이 구독을 소진한 행사 수. */
    long countCeremoniesBySubscription(Long subscriptionId);
}
