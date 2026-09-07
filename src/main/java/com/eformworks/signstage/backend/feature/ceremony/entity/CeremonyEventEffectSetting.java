package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 행사 이벤트 하나가 (target, trigger) 분류별로 선택한 효과 프리셋 — V202609071000의
 * {@code ceremony_event_effect_settings}. 실제 선택·해제 흐름(BE-SETTING)과 runtime ON/OFF
 * 전환(BE-RUNTIME)은 이후 단계에서 구현하며, 이 라운드(BE-CATALOG-01)는 조회에 필요한 entity와
 * fetch join repository만 먼저 둔다.
 *
 * <p>SQL은 {@code (effect_id, target_type, trigger_type)}이 {@code ceremony_effect_definitions}의
 * {@code (id, target_type, trigger_type)} 복합 unique를 참조하게 해 분류 불일치를 DB 제약으로도
 * 막지만, JPA 연관관계는 {@code effect_id → id} 단일 컬럼으로만 매핑한다 — 두 컬럼(targetType/
 * triggerType)이 이미 이 entity의 PK 일부라 굳이 복합 FK를 그대로 옮기지 않아도 무결성이
 * 지켜진다(효과 선택 시 정의의 분류와 일치하는지는 서비스가 검증한다).
 */
@Entity
@Table(name = "ceremony_event_effect_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CeremonyEventEffectSetting extends BaseEntity {

    @EmbeddedId
    private CeremonyEventEffectSettingId id;

    @MapsId("eventId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private CeremonyEvent event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "effect_id", nullable = false)
    private CeremonyEffectDefinition definition;

    @Column(name = "runtime_enabled", nullable = false)
    private boolean runtimeEnabled;

    @Builder
    private CeremonyEventEffectSetting(CeremonyEvent event, CeremonyEffectDefinition definition) {
        this.event = event;
        this.definition = definition;
        this.id = new CeremonyEventEffectSettingId(
                event.getId(), definition.getTargetType(), definition.getTriggerType()
        );
        this.runtimeEnabled = true;
    }

    /** BE-SETTING(효과 교체)에서 사용 — 새 정의로 바꾸면 runtime은 항상 다시 ON으로 초기화한다. */
    public void replaceDefinition(CeremonyEffectDefinition definition) {
        this.definition = definition;
        this.runtimeEnabled = true;
    }

    /** BE-RUNTIME(runtime ON/OFF)에서 사용. */
    public void updateRuntimeEnabled(boolean runtimeEnabled) {
        this.runtimeEnabled = runtimeEnabled;
    }
}
