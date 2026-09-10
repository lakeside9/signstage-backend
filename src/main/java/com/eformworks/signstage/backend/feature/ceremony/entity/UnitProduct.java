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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카탈로그 단위 상품 — 기존 {@code OptionalFeature}(선택옵션)와 {@code CapacityAddOn}(용량
 * 추가구매)을 하나로 합쳤다(signstage-docs
 * business/billing-catalog-unit-product-model-redesign-review.md 결정, 2026-09-10). 필수 5종
 * (서명자/템플릿/테스트행사/리허설행사/본행사)과 선택 4종(태블릿/현장지원/온라인지원/이벤트
 * 효과묶음) 전부 이 엔티티 하나로 등록·관리한다 — "쇼핑몰 상품관리"처럼 모든 품목이 같은
 * 방식으로 카탈로그화된다.
 *
 * <p>{@code type}은 정체성이라 생성 후 불변이다. 같은 {@code type}을 여러 행이 공유할 수
 * 있다(코드 레벨 유니크 제약 없음) — 예전엔 {@code OptionalFeatureCode}(EVENT_EFFECT_BUNDLE
 * 제외)만 유니크였고 {@code CapacityType}은 애초에 자유로웠는데, 이제 현장지원 4단계(수도권/
 * 근·중·원거리)처럼 대부분의 타입이 여러 카탈로그 행을 필요로 해 유니크 제약 자체를 없앴다.
 *
 * <p>가격정보({@link ProductPriceInfo}, 할인 없음)와 사용여부는 이 엔티티가 아니라
 * {@link UnitProductPricePeriod}로 분리돼 있다 — "지금 판매 가능한지·얼마인지"는 항상
 * {@code UnitProductPricePeriodRepository.findEffective}로 그때그때 조회한다.
 */
@Entity
@Table(name = "unit_products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UnitProduct extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UnitProductType type;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UnitProductCategory category;

    /**
     * 배타 그룹 — 같은 값을 가진 단위 상품들은 한 CeremonyEvent에 동시에 적용할 수 없다. 관리자가
     * 카탈로그 등록 시 자유롭게 붙이는 문자열 라벨이다. null이면(기본값) 다른 상품과 배타
     * 관계가 없다 — 기존 {@code OptionalFeature.exclusivityGroup}과 같은 필드.
     */
    @Column(name = "exclusivity_group", length = 50)
    private String exclusivityGroup;

    @Builder
    private UnitProduct(UnitProductType type, String name, UnitProductCategory category, String exclusivityGroup) {
        this.type = type;
        this.name = name;
        this.category = category;
        this.exclusivityGroup = exclusivityGroup;
    }

    /**
     * 플랫폼 관리자 카탈로그 관리 화면의 수정. {@code type}은 상품의 종류를 규정하는 값이라
     * 생성 후 불변이고 여기서 바꾸지 않는다(바꾸려면 새 상품을 만든다). 가격/사용여부는 여기서
     * 다루지 않는다 — {@link UnitProductPricePeriod} 기간 단위 CRUD로 관리한다.
     */
    public void updateInfo(String name, UnitProductCategory category, String exclusivityGroup) {
        this.name = name;
        this.category = category;
        this.exclusivityGroup = exclusivityGroup;
    }
}
