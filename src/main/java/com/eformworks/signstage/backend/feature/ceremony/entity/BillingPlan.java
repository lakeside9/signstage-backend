package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 행사(Ceremony) 과금 플랜 카탈로그. Basic/Standard/Premium처럼 사전 정의된 플랜이고,
 * 필수옵션(서명자·템플릿·테스트/본행사 수 한도)은 모든 플랜이 항상 값을 가진다 —
 * signstage-docs business/ceremony-billing-options-review.md 4.9절 결정에 따라
 * "무제한"을 표현하는 별도 sentinel 값이 없다.
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

    @Column(name = "supply_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal supplyPrice;

    @Column(name = "sale_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal salePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "max_signers", nullable = false)
    private Integer maxSigners;

    @Column(name = "max_templates", nullable = false)
    private Integer maxTemplates;

    @Column(name = "max_test_events", nullable = false)
    private Integer maxTestEvents;

    @Column(name = "max_main_events", nullable = false)
    private Integer maxMainEvents;

    /**
     * 사용여부(비활성화해도 행은 지우지 않는다 — 이미 이 플랜을 참조하는 Ceremony가 있을 수
     * 있어 삭제는 여전히 범위 밖이다). 비활성화된 플랜은 새 행사 생성/플랜 변경 대상에서
     * 제외된다({@code CeremonyService}) — signstage-docs
     * business/ceremony-billing-options-review.md 7장 후속 결정.
     */
    @Column(nullable = false)
    private boolean active;

    @Builder
    private BillingPlan(
            String name,
            BigDecimal supplyPrice,
            BigDecimal salePrice,
            DiscountType discountType,
            BigDecimal discountValue,
            Integer maxSigners,
            Integer maxTemplates,
            Integer maxTestEvents,
            Integer maxMainEvents
    ) {
        this.name = name;
        this.supplyPrice = supplyPrice;
        this.salePrice = salePrice;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.maxSigners = maxSigners;
        this.maxTemplates = maxTemplates;
        this.maxTestEvents = maxTestEvents;
        this.maxMainEvents = maxMainEvents;
        this.active = true;
    }

    /**
     * 플랫폼 관리자 카탈로그 관리 화면의 수정. 이 플랜에 묶인 선택옵션 구성은 생성 시점에만
     * 정해지고 여기서 바꾸지 않는다(교체하려면 새 플랜을 만든다 — 카탈로그 관리 화면 결정).
     * 호출할 때마다 {@code BillingPlanHistory}에 이력 한 행을 남기는 것은 서비스
     * ({@code BillingPlanService}) 몫이다.
     */
    public void updateInfo(
            String name,
            BigDecimal supplyPrice,
            BigDecimal salePrice,
            DiscountType discountType,
            BigDecimal discountValue,
            Integer maxSigners,
            Integer maxTemplates,
            Integer maxTestEvents,
            Integer maxMainEvents,
            boolean active
    ) {
        this.name = name;
        this.supplyPrice = supplyPrice;
        this.salePrice = salePrice;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.maxSigners = maxSigners;
        this.maxTemplates = maxTemplates;
        this.maxTestEvents = maxTestEvents;
        this.maxMainEvents = maxMainEvents;
        this.active = active;
    }
}
