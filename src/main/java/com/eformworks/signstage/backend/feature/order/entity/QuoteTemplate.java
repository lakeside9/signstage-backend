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

    /** 견적서 공급자 칸 — 상호·대표자·사업자등록번호·주소·연락처. 비어 있으면 견적서에 공급자 칸이 없다. */
    @Column(name = "supplier_name", length = 100)
    private String supplierName;

    @Column(name = "supplier_representative", length = 50)
    private String supplierRepresentative;

    @Column(name = "supplier_business_number", length = 20)
    private String supplierBusinessNumber;

    @Column(name = "supplier_address", length = 300)
    private String supplierAddress;

    @Column(name = "supplier_phone", length = 50)
    private String supplierPhone;

    /** 회사 인감 이미지(파일 저장소 키). 관리자 인증 API로만 내려준다. */
    @Column(name = "seal_storage_key", length = 500)
    private String sealStorageKey;

    @Column(name = "seal_content_type", length = 50)
    private String sealContentType;

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

    public boolean hasSupplier() {
        return supplierName != null || supplierRepresentative != null || supplierBusinessNumber != null
                || supplierAddress != null || supplierPhone != null;
    }

    public boolean hasSeal() {
        return sealStorageKey != null;
    }

    /** 공급자 정보를 바꾼다 — 쓰이지 않은 문구이거나, 쓰인 문구라도 아직 비어 있을 때만(서비스가 확인한다). */
    public void updateSupplier(String name, String representative, String businessNumber, String address, String phone) {
        this.supplierName = name;
        this.supplierRepresentative = representative;
        this.supplierBusinessNumber = businessNumber;
        this.supplierAddress = address;
        this.supplierPhone = phone;
    }

    public void useSeal(String storageKey, String contentType) {
        this.sealStorageKey = storageKey;
        this.sealContentType = contentType;
    }

    /** 아직 어떤 견적에도 쓰이지 않은 문구만 고친다(서비스가 확인한다). */
    public void update(String serviceDescription, String footerNotice, EffectivePeriod period) {
        this.serviceDescription = serviceDescription;
        this.footerNotice = footerNotice;
        this.period = period;
    }
}
