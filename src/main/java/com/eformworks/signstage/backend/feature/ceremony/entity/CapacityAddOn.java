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
 * 필수옵션(용량 한도) 추가구매 상품. 예: "서명자 +10명". 행사 마스터(Ceremony) 단위로 구매되며
 * ({@code CeremonyCapacityPurchase}, 2라운드), 유효 한도는 플랜의 기본값에 구매한 만큼 더해서
 * 계산한다 — signstage-docs business/ceremony-billing-options-review.md 4.7/4.9절 참고.
 *
 * <p>{@code secondaryCapacityType}/{@code secondaryUnitAmount}는 한 상품이 두 용량을 동시에
 * 늘리는 "묶음 상품"을 표현한다 — 예: "서명자 +10명 / 태블릿 +10대"를 한 상품으로 구매하는
 * 경우(2026-08-21 추가, signstage-docs business/ceremony-billing-options-review.md 4.7절
 * 후속). 단일 상품(예: "서명자 +10명"만)이면 둘 다 null이다 — 항상 함께 있거나 함께 없다
 * (관리자 카탈로그 등록/수정 시 서비스가 검증한다). 주 용량({@code capacityType}/
 * {@code unitAmount})과 달리 스냅샷 대상이 {@code CeremonyCapacityPurchase.purchasedSecondaryUnitAmount}
 * 하나뿐인 것도 주 용량과 같은 원칙이다 — {@code secondaryCapacityType} 자체는(주
 * {@code capacityType}이 그렇듯) 생성 후 사실상 불변이라 스냅샷하지 않는다.
 */
@Entity
@Table(name = "capacity_addons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CapacityAddOn extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "capacity_type", nullable = false, length = 20)
    private CapacityType capacityType;

    @Column(name = "unit_amount", nullable = false)
    private Integer unitAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "secondary_capacity_type", length = 20)
    private CapacityType secondaryCapacityType;

    @Column(name = "secondary_unit_amount")
    private Integer secondaryUnitAmount;

    @Column(name = "supply_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal supplyPrice;

    @Column(name = "sale_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal salePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    /**
     * 사용여부(비활성화해도 행은 지우지 않는다). 비활성화된 상품은 새 추가구매 대상에서
     * 제외된다({@code CeremonyService}) — signstage-docs
     * business/ceremony-billing-options-review.md 7장 후속 결정.
     */
    @Column(nullable = false)
    private boolean active;

    @Builder
    private CapacityAddOn(
            CapacityType capacityType,
            Integer unitAmount,
            CapacityType secondaryCapacityType,
            Integer secondaryUnitAmount,
            BigDecimal supplyPrice,
            BigDecimal salePrice,
            DiscountType discountType,
            BigDecimal discountValue
    ) {
        this.capacityType = capacityType;
        this.unitAmount = unitAmount;
        this.secondaryCapacityType = secondaryCapacityType;
        this.secondaryUnitAmount = secondaryUnitAmount;
        this.supplyPrice = supplyPrice;
        this.salePrice = salePrice;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.active = true;
    }

    /**
     * 플랫폼 관리자 카탈로그 관리 화면의 수정. {@code capacityType}/{@code secondaryCapacityType}은
     * 상품의 종류를 규정하는 값이라 생성 후 불변이고 여기서 바꾸지 않는다(바꾸려면 새 상품을
     * 만든다). 호출할 때마다 {@code CapacityAddOnHistory}에 이력 한 행을 남기는 것은 서비스 몫이다.
     */
    public void updateInfo(
            Integer unitAmount,
            Integer secondaryUnitAmount,
            BigDecimal supplyPrice,
            BigDecimal salePrice,
            DiscountType discountType,
            BigDecimal discountValue,
            boolean active
    ) {
        this.unitAmount = unitAmount;
        this.secondaryUnitAmount = secondaryUnitAmount;
        this.supplyPrice = supplyPrice;
        this.salePrice = salePrice;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.active = active;
    }
}
