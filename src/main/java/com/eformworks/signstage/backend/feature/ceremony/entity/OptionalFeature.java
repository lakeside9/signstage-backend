package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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

    /**
     * 가격정보(통화/공급가/판매가/할인/세금코드) — signstage-docs
     * business/billing-catalog-zero-base-schema-redesign-review.md 결정 #1(2026-09-08, 항목 A).
     */
    @Embedded
    private CatalogPriceInfo priceInfo;

    /**
     * 사용여부(비활성화해도 행은 지우지 않는다). 비활성화된 선택옵션은 새 추가구매 대상에서
     * 제외된다({@code CeremonyService}) — signstage-docs
     * business/ceremony-billing-options-review.md 7장 후속 결정.
     */
    @Column(nullable = false)
    private boolean active;

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

    /**
     * 상위 분류(장비/인력/애플리케이션) — signstage-docs
     * business/ceremony-support-services-billing-review.md 결정(2026-09-08, 4.3절 안 B).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OptionalFeatureCategory category;

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
            String exclusivityGroup,
            OptionalFeatureCategory category
    ) {
        this.code = code;
        this.name = name;
        this.priceInfo = CatalogPriceInfo.of(
                currencyCode, supplyPrice, salePrice, discountType, discountValue,
                taxCode == null || taxCode.isBlank() ? "KR_VAT_STANDARD" : taxCode
        );
        this.active = true;
        this.exclusivityGroup = exclusivityGroup;
        this.category = category;
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
            String exclusivityGroup,
            OptionalFeatureCategory category
    ) {
        this.name = name;
        this.priceInfo = CatalogPriceInfo.of(
                currencyCode, supplyPrice, salePrice, discountType, discountValue,
                taxCode == null || taxCode.isBlank() ? this.priceInfo.getTaxCode() : taxCode
        );
        this.active = active;
        this.exclusivityGroup = exclusivityGroup;
        this.category = category;
    }
}
