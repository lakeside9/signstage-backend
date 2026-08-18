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
 * 선택옵션 카탈로그 상품(서명확대/폭죽/화상참석 등). 행사 마스터(Ceremony) 단위로 구매되고
 * ({@code CeremonyOptionalFeaturePurchase}, 2라운드), 실제 적용 여부는 CeremonyEvent 단위로
 * 선택한다({@code CeremonyEventOptionalFeature}, 2라운드) — signstage-docs
 * business/ceremony-billing-options-review.md 4.6/4.11절 참고.
 */
@Entity
@Table(name = "optional_features")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OptionalFeature extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OptionalFeatureCode code;

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

    @Builder
    private OptionalFeature(
            OptionalFeatureCode code,
            String name,
            BigDecimal supplyPrice,
            BigDecimal salePrice,
            DiscountType discountType,
            BigDecimal discountValue
    ) {
        this.code = code;
        this.name = name;
        this.supplyPrice = supplyPrice;
        this.salePrice = salePrice;
        this.discountType = discountType;
        this.discountValue = discountValue;
    }

    /**
     * 플랫폼 관리자 카탈로그 관리 화면의 수정. {@code code}는 옵션의 종류를 규정하는 값이라
     * 생성 후 불변이고 여기서 바꾸지 않는다(바꾸려면 새 옵션을 만든다).
     */
    public void updateInfo(
            String name,
            BigDecimal supplyPrice,
            BigDecimal salePrice,
            DiscountType discountType,
            BigDecimal discountValue
    ) {
        this.name = name;
        this.supplyPrice = supplyPrice;
        this.salePrice = salePrice;
        this.discountType = discountType;
        this.discountValue = discountValue;
    }
}
