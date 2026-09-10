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
 * {@code CeremonyEffectDefinition}(효과 하나) ↔ {@link UnitProduct}(단위 상품, 특히
 * {@code type=EVENT_EFFECT_BUNDLE} 종류) N:N 매핑. 효과 하나가 여러 묶음(예: "3종"과 "5종")에
 * 동시에 포함될 수 있고, 묶음 구성은 단위 상품 쪽(카탈로그 관리 화면)에서 자유롭게 편집한다
 * (2026-09-08 결정 — signstage-docs business/ceremony-event-effect-implementation-tasks.md
 * 참고). FK가 가리키는 대상만 옛 {@code OptionalFeature}에서 {@link UnitProduct}로 바뀌었을
 * 뿐 구조·동작은 그대로다(signstage-docs
 * business/billing-catalog-unit-product-model-redesign-review.md 결정, 2026-09-10, 3.6절).
 *
 * <p>조직의 entitlement 판정은 "이 효과가 속한 매핑 중 하나라도 그 조직이 적용해둔 단위 상품과
 * 일치하는가"(합집합)로 한다 — {@code CeremonyEventEffectSettingService} 참고.
 */
@Entity
@Table(
        name = "ceremony_effect_definition_options",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_cedo_definition_product",
                columnNames = {"effect_definition_id", "unit_product_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CeremonyEffectDefinitionOption extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "effect_definition_id", nullable = false)
    private CeremonyEffectDefinition effectDefinition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_product_id", nullable = false)
    private UnitProduct unitProduct;

    @Builder
    private CeremonyEffectDefinitionOption(CeremonyEffectDefinition effectDefinition, UnitProduct unitProduct) {
        this.effectDefinition = effectDefinition;
        this.unitProduct = unitProduct;
    }
}
