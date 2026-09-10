package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 행사(Ceremony) 과금 플랜 카탈로그. Basic/Standard/Premium처럼 사전 정의된 플랜이고,
 * 필수옵션(서명자·템플릿·테스트/본행사 수 한도)은 모든 플랜이 항상 값을 가진다 —
 * signstage-docs business/ceremony-billing-options-review.md 4.9절 결정에 따라
 * "무제한"을 표현하는 별도 sentinel 값이 없다.
 *
 * <p>포함하는 단위 상품 구성(한도/무료 포함 옵션/추가구매 큐레이션)은 예전엔 이 엔티티가 직접
 * 갖거나 3개 조인 테이블로 나뉘어 있었는데, {@link BillingPlanUnitProduct} 조인 테이블 하나로
 * 통합됐다(signstage-docs business/billing-catalog-unit-product-model-redesign-review.md 결정,
 * 2026-09-10) — {@code BillingPlanService}가 별도 리포지토리로 관리하고, 이 엔티티는 그 구성을
 * 직접 갖지 않는다.
 *
 * <p>이 플랜은 더 이상 자기 가격을 갖지 않는다 — "오늘 가격"은
 * {@code Σ(BillingPlanUnitProduct.unitProduct.effectivePrice × includedQuantity)}로 조회
 * 시점에 계산되고, 그 합계에 적용할 할인만 {@link BillingPlanDiscountPeriod}로 기간별 관리한다
 * (같은 문서 결정). 이 엔티티는 이제 이름만 갖는 정체성일 뿐이다. 플랜 id는 {@code Ceremony}
 * 등에서 FK로 널리 참조되므로 정체성은 그대로 유지한다.
 */
@Entity
@Table(name = "billing_plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingPlan extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Builder
    private BillingPlan(String name) {
        this.name = name;
    }

    /**
     * 플랫폼 관리자 카탈로그 관리 화면의 수정. 이 플랜에 묶인 선택옵션 구성은 생성 시점에만
     * 정해지고 여기서 바꾸지 않는다(교체하려면 새 플랜을 만든다 — 카탈로그 관리 화면 결정).
     * 가격/사용여부는 여기서 다루지 않는다 — {@link BillingPlanPricePeriod} 기간 단위 CRUD로
     * 관리한다.
     */
    public void updateInfo(String name) {
        this.name = name;
    }
}
