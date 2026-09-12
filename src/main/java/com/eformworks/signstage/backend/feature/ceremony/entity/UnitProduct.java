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
    @Column(nullable = false, length = 30)
    private UnitProductType type;

    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 설명 — 이 상품이 무엇인지(특히 이벤트 효과 묶음처럼 이름만으로는 무엇이 포함되는지
     * 알기 어려운 종류) 관리자가 적어두는 자유 텍스트. nullable(2026-09-11 사용자 요청) —
     * 모든 상품이 설명을 필요로 하지는 않는다.
     */
    @Column(length = 500)
    private String description;

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

    /**
     * 이 상품을 한 행사에서 추가구매로 누적 살 수 있는 최대 수량 — nullable(무제한, 기본값).
     * 관리자가 카탈로그에서 설정한다(2026-09-12 사용자 요청). 토글형({@link
     * UnitProductType#isToggle()}, 지금은 {@code EVENT_EFFECT_BUNDLE})은 이 값과 무관하게
     * 타입 자체의 규칙으로 항상 최대 1이라({@code CeremonyService#checkPurchaseQuantity})
     * 이 필드는 항상 {@code null}로 정규화한다(생성자/{@link #updateInfo} 양쪽) — 관리자가
     * 실수로 값을 보내도 저장되지 않는다.
     */
    @Column(name = "max_purchase_quantity")
    private Integer maxPurchaseQuantity;

    /**
     * 카탈로그 목록 화면의 표시 순서(2026-09-10, 사용자 요청 — {@code Signer}/{@code Template}/
     * {@code CeremonyEvent}와 같은 displayOrder 일괄 재정렬 패턴). 위/아래 이동 버튼이 전체
     * 목록을 다시 인덱싱해 저장한다({@code UnitProductService#updateDisplayOrders}). 동률이면
     * id 오름차순으로 정렬한다({@code UnitProductRepository.findAllByOrderByDisplayOrderAscIdAsc})
     * — 새로 등록되는 상품은 기본값 0을 받아, 순서를 한 번도 안 바꾼 다른 0짜리 상품들 중
     * id가 가장 크므로 자연스럽게 목록 맨 끝에 온다.
     */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    /**
     * 이 상품이 "플랫폼 이용료"(파트너가 플랫폼에 내는 돈) 대상인지 — 기존
     * {@code UnitProductCategory#isSystemUsageFee()}의 카테고리 파생 판정을 저장된 값으로
     * 승격한 필드다(2026-09-12 사용자 요청 — 분류체계를 명시적으로 분리, signstage-docs
     * business/onsite-support-negotiation-and-billing-classification-review.md 3.1절
     * 결정). 등록 시 카테고리로부터 자동 계산해 기본값을 채우지만, 관리자가 등록/수정
     * 화면에서 자유롭게 override할 수 있다(같은 문서 결정 #2) — 현장지원 요청(관리자 견적)
     * 전용 앵커 상품처럼 카테고리(PERSONNEL)와 과금 축(플랫폼 이용료)이 어긋나는 예외가
     * 실제로 있다.
     */
    @Column(name = "is_platform_usage_fee", nullable = false)
    private boolean platformUsageFee;

    @Builder
    private UnitProduct(
            UnitProductType type, String name, String description, UnitProductCategory category,
            String exclusivityGroup, Integer maxPurchaseQuantity, Boolean platformUsageFee
    ) {
        this.type = type;
        this.name = name;
        this.description = description;
        this.category = category;
        this.exclusivityGroup = exclusivityGroup;
        // type이 null인 빌더 호출(테스트 픽스처가 id만 필요해 다른 필드를 생략하는 경우 등)이
        // 있어 null-safe하게 판정한다 — type이 실제로 정해지는 실서비스 경로(createUnitProduct)
        // 에서는 항상 non-null이다.
        this.maxPurchaseQuantity = (type != null && type.isToggle()) ? null : maxPurchaseQuantity;
        this.platformUsageFee = platformUsageFee != null ? platformUsageFee : (category != null && category.isSystemUsageFee());
    }

    /**
     * 플랫폼 관리자 카탈로그 관리 화면의 수정. {@code type}은 상품의 종류를 규정하는 값이라
     * 생성 후 불변이고 여기서 바꾸지 않는다(바꾸려면 새 상품을 만든다). 가격/사용여부는 여기서
     * 다루지 않는다 — {@link UnitProductPricePeriod} 기간 단위 CRUD로 관리한다.
     */
    public void updateInfo(
            String name, String description, UnitProductCategory category, String exclusivityGroup,
            Integer maxPurchaseQuantity, boolean platformUsageFee
    ) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.exclusivityGroup = exclusivityGroup;
        this.maxPurchaseQuantity = this.type.isToggle() ? null : maxPurchaseQuantity;
        this.platformUsageFee = platformUsageFee;
    }

    /** 단위 상품 목록의 위/아래 이동 버튼이 호출한다 — {@code null}이면 바꾸지 않는다. */
    public void updateDisplayOrder(Integer displayOrder) {
        if (displayOrder != null) {
            this.displayOrder = displayOrder;
        }
    }
}
