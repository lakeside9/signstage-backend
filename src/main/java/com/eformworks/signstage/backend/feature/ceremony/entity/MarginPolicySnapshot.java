package com.eformworks.signstage.backend.feature.ceremony.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 정책을 참조하여 다시 계산하지 않는, 견적 생성 시점의 불변 메타데이터. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MarginPolicySnapshot {
    @Column(name = "margin_source", length = 30)
    private String source;
    @Column(name = "margin_source_id")
    private Long sourceId;
    @Column(name = "margin_effective_from")
    private LocalDate effectiveFrom;
    @Column(name = "margin_effective_to")
    private LocalDate effectiveTo;
    @Column(name = "margin_applied_on")
    private LocalDate appliedOn;
    @Column(name = "margin_time_zone_id", length = 50)
    private String timeZoneId;
}
