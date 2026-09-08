package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import com.eformworks.signstage.backend.core.i18n.InternationalizationDefaults;
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
 * 선택옵션 카탈로그 상품(서명 하이라이트/폭죽/화상참석 등). 행사 마스터(Ceremony) 단위로 구매되고
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

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "supply_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal supplyPrice;

    @Column(name = "sale_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal salePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountValue;

    @Column(name = "tax_code", nullable = false, length = 50)
    private String taxCode;

    /**
     * 사용여부(비활성화해도 행은 지우지 않는다). 비활성화된 선택옵션은 새 추가구매 대상에서
     * 제외된다({@code CeremonyService}) — signstage-docs
     * business/ceremony-billing-options-review.md 7장 후속 결정.
     */
    @Column(nullable = false)
    private boolean active;

    /**
     * 이 옵션이 프로젝터(전시용) 화면에 실제로 효과를 내는지 — signstage-docs
     * business/ceremony-billing-options-review.md 8.7절 계열. 프로젝터 효과가 아닌 옵션(예:
     * 화상참석)이 늘어날 걸 대비해 코드 변경 없이 카탈로그 등록만으로 구분할 수 있게 한다.
     * 실제 효과 로직 자체는 여전히 프런트 {@code projectorEffects.ts}에 코드별로 구현해야
     * 한다 — 이 필드는 "그런 종류의 옵션이다"라는 분류 정보일 뿐, 켠다고 효과가 저절로
     * 생기지 않는다.
     */
    @Column(name = "projector_effect", nullable = false)
    private boolean projectorEffect;

    /**
     * 배타 그룹 — 같은 값을 가진 선택옵션들은 한 CeremonyEvent에 동시에 적용할 수 없다
     * ({@code CeremonyEventService#applyOptionalFeatures}가 강제한다). {@code Signer.roleCode}/
     * {@code TemplateField.roleCode}처럼 enum이 아니라 관리자가 카탈로그 등록 시 자유롭게
     * 붙이는 문자열 라벨이다 — 예: "서명 하이라이트 파란색"/"빨간색" 두 상품에 같은 그룹값을
     * 매기면 관리자 코드 변경 없이 배타 관계를 구성할 수 있다. null이면(기본값) 다른 옵션과
     * 배타 관계가 없다 — 지금 있는 두 옵션(서명 하이라이트/폭죽)은 항상 null로 시작한다.
     */
    @Column(name = "exclusivity_group", length = 50)
    private String exclusivityGroup;

    @Builder
    private OptionalFeature(
            OptionalFeatureCode code,
            String name,
            String currencyCode,
            BigDecimal supplyPrice,
            BigDecimal salePrice,
            DiscountType discountType,
            BigDecimal discountValue,
            String taxCode,
            Boolean projectorEffect,
            String exclusivityGroup
    ) {
        this.code = code;
        this.name = name;
        this.currencyCode = InternationalizationDefaults.currencyCodeOrDefault(currencyCode);
        this.supplyPrice = supplyPrice;
        this.salePrice = salePrice;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.taxCode = taxCode == null || taxCode.isBlank() ? "KR_VAT_STANDARD" : taxCode;
        this.active = true;
        this.projectorEffect = projectorEffect != null ? projectorEffect : true;
        this.exclusivityGroup = exclusivityGroup;
    }

    /**
     * 플랫폼 관리자 카탈로그 관리 화면의 수정. {@code code}는 옵션의 종류를 규정하는 값이라
     * 생성 후 불변이고 여기서 바꾸지 않는다(바꾸려면 새 옵션을 만든다). 호출할 때마다
     * {@code OptionalFeatureHistory}에 이력 한 행을 남기는 것은 서비스 몫이다.
     */
    public void updateInfo(
            String name,
            String currencyCode,
            BigDecimal supplyPrice,
            BigDecimal salePrice,
            DiscountType discountType,
            BigDecimal discountValue,
            String taxCode,
            boolean active,
            boolean projectorEffect,
            String exclusivityGroup
    ) {
        this.name = name;
        this.currencyCode = InternationalizationDefaults.currencyCodeOrDefault(currencyCode);
        this.supplyPrice = supplyPrice;
        this.salePrice = salePrice;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.taxCode = taxCode == null || taxCode.isBlank() ? this.taxCode : taxCode;
        this.active = active;
        this.projectorEffect = projectorEffect;
        this.exclusivityGroup = exclusivityGroup;
    }
}
