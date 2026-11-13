package com.eformworks.signstage.backend.feature.order.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 견적 공통 문구 — 견적서 "제공 서비스" 본문과 하단 안내문구(2026-11-13 결정, 플랜의 서비스 설명을 옮겼다).
 * 수주 가격표처럼 기간별로 두고 기간이 겹치지 않는다. 견적은 저장일에 유효한 문구를 복사해 두므로 문구를 바꿔도
 * 이미 만든 견적은 그대로다.
 */
@Entity
@Table(name = "quote_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuoteTemplate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_description", columnDefinition = "TEXT")
    private String serviceDescription;

    @Column(name = "footer_notice", columnDefinition = "TEXT")
    private String footerNotice;

    @Embedded
    private EffectivePeriod period;

    @Builder
    private QuoteTemplate(String serviceDescription, String footerNotice, EffectivePeriod period) {
        this.serviceDescription = serviceDescription;
        this.footerNotice = footerNotice;
        this.period = period;
    }

    public void close(LocalDate effectiveTo) {
        this.period = period.close(effectiveTo);
    }

    /** 아직 어떤 견적에도 쓰이지 않은 문구만 고친다(서비스가 확인한다). */
    public void update(String serviceDescription, String footerNotice, EffectivePeriod period) {
        this.serviceDescription = serviceDescription;
        this.footerNotice = footerNotice;
        this.period = period;
    }
}
