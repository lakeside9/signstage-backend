package com.eformworks.signstage.backend.feature.ceremony.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {@link CeremonyEventEffectSetting}의 복합 PK — 한 행사 이벤트는 (target, trigger) 분류마다
 * 최대 한 개의 프리셋만 선택할 수 있다(V202609071000의 PRIMARY KEY(event_id, target_type,
 * trigger_type)).
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CeremonyEventEffectSettingId implements Serializable {

    @Column(name = "event_id")
    private Long eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 30)
    private CeremonyEffectTarget targetType;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", length = 40)
    private CeremonyEffectTrigger triggerType;

    public CeremonyEventEffectSettingId(Long eventId, CeremonyEffectTarget targetType, CeremonyEffectTrigger triggerType) {
        this.eventId = eventId;
        this.targetType = targetType;
        this.triggerType = triggerType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CeremonyEventEffectSettingId that)) {
            return false;
        }
        return Objects.equals(eventId, that.eventId) && targetType == that.targetType && triggerType == that.triggerType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, targetType, triggerType);
    }
}
