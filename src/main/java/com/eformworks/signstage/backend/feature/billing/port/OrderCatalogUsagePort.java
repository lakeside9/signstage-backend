package com.eformworks.signstage.backend.feature.billing.port;

/**
 * 플랫폼 수주(고객 가격표·수주 견적·범위 변경 견적)가 과금 카탈로그를 참조하는지 조회하는 계약.
 * 구현은 {@code feature.order}가 제공한다 — {@link CeremonyBillingUsagePort}와 같은 이유로
 * 의존 방향을 뒤집는다(backend-coding-convention.md 3.4절).
 */
public interface OrderCatalogUsagePort {

    /** 고객 가격표나 수주 견적이 이 플랜을 참조하는지. */
    boolean isBillingPlanUsed(Long billingPlanId);

    /** 고객 가격표나 수주·범위 변경 견적 줄이 이 단위 상품을 참조하는지. */
    boolean isUnitProductUsed(Long unitProductId);
}
