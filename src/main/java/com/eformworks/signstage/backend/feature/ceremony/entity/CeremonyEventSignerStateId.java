package com.eformworks.signstage.backend.feature.ceremony.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** {@link CeremonyEventSignerState}의 복합 PK — V202609071000의 PRIMARY KEY(event_id, signer_id). */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CeremonyEventSignerStateId implements Serializable {

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "signer_id")
    private Long signerId;

    public CeremonyEventSignerStateId(Long eventId, Long signerId) {
        this.eventId = eventId;
        this.signerId = signerId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CeremonyEventSignerStateId that)) {
            return false;
        }
        return Objects.equals(eventId, that.eventId) && Objects.equals(signerId, that.signerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, signerId);
    }
}
