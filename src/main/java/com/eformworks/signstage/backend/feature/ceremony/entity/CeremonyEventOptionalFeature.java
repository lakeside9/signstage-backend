package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이 CeremonyEvent에 실제로 적용된 단위 상품(주로 {@code type=EVENT_EFFECT_BUNDLE}). 그
 * Ceremony가 구매한(플랜 기본 포함 또는 추가구매) 단위 상품의 부분집합만 선택할 수 있다 —
 * signstage-docs business/ceremony-billing-options-review.md 4.11절 결정. FK가 가리키는
 * 대상만 옛 {@code OptionalFeature}에서 {@link UnitProduct}로 바뀌었을 뿐 구조·동작은
 * 그대로다(signstage-docs business/billing-catalog-unit-product-model-redesign-review.md
 * 결정, 2026-09-10, 3.6절).
 */
@Entity
@Table(
        name = "ceremony_event_optional_features",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_ceof_event_product",
                columnNames = {"ceremony_event_id", "unit_product_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CeremonyEventOptionalFeature extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceremony_event_id", nullable = false)
    private CeremonyEvent ceremonyEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_product_id", nullable = false)
    private UnitProduct unitProduct;

    @Builder
    private CeremonyEventOptionalFeature(CeremonyEvent ceremonyEvent, UnitProduct unitProduct) {
        this.ceremonyEvent = ceremonyEvent;
        this.unitProduct = unitProduct;
    }
}
