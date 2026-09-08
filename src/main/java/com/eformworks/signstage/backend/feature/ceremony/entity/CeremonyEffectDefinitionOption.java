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
 * {@code CeremonyEffectDefinition}(효과 하나) ↔ {@code OptionalFeature}(선택옵션, 특히
 * "이벤트 효과 묶음" 종류) N:N 매핑. 효과 하나가 여러 묶음(예: "3종"과 "5종")에 동시에
 * 포함될 수 있고, 묶음 구성은 선택옵션 쪽(카탈로그 관리 화면)에서 자유롭게 편집한다
 * (2026-09-08 결정 — signstage-docs business/ceremony-event-effect-implementation-tasks.md
 * 참고). 예전에는 {@code CeremonyEffectDefinition.requiredOptionalFeatureId} 단일 FK였는데,
 * 묶음 상품이 계속 늘어나야 해서(코드 배포 없이) 이 매핑 테이블로 옮겼다.
 *
 * <p>조직의 entitlement 판정은 "이 효과가 속한 매핑 중 하나라도 그 조직이 적용해둔 선택옵션과
 * 일치하는가"(합집합)로 한다 — {@code CeremonyEventEffectSettingService} 참고.
 */
@Entity
@Table(
        name = "ceremony_effect_definition_options",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_cedo_definition_feature",
                columnNames = {"effect_definition_id", "optional_feature_id"}
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
    @JoinColumn(name = "optional_feature_id", nullable = false)
    private OptionalFeature optionalFeature;

    @Builder
    private CeremonyEffectDefinitionOption(CeremonyEffectDefinition effectDefinition, OptionalFeature optionalFeature) {
        this.effectDefinition = effectDefinition;
        this.optionalFeature = optionalFeature;
    }
}
