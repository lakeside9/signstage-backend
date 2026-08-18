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

    @Column(name = "supply_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal supplyPrice;

    @Column(name = "sale_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal salePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Builder
    private CapacityAddOn(
            CapacityType capacityType,
            Integer unitAmount,
            BigDecimal supplyPrice,
            BigDecimal salePrice,
            DiscountType discountType,
            BigDecimal discountValue
    ) {
        this.capacityType = capacityType;
        this.unitAmount = unitAmount;
        this.supplyPrice = supplyPrice;
        this.salePrice = salePrice;
        this.discountType = discountType;
        this.discountValue = discountValue;
    }

    /**
     * 플랫폼 관리자 카탈로그 관리 화면의 수정. {@code capacityType}은 상품의 종류를 규정하는 값이라
     * 생성 후 불변이고 여기서 바꾸지 않는다(바꾸려면 새 상품을 만든다).
     */
    public void updateInfo(
            Integer unitAmount,
            BigDecimal supplyPrice,
            BigDecimal salePrice,
            DiscountType discountType,
            BigDecimal discountValue
    ) {
        this.unitAmount = unitAmount;
        this.supplyPrice = supplyPrice;
        this.salePrice = salePrice;
        this.discountType = discountType;
        this.discountValue = discountValue;
    }
}
