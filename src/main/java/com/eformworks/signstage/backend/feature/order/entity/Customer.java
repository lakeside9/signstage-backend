package com.eformworks.signstage.backend.feature.order.entity;

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
 * 플랫폼이 수주하는 실고객 — signstage-docs business/platform-order-partner-subcontract-review.md
 * D02. 플랫폼 소유 정보이며 파트너에게는 행사 운영에 필요한 담당자 정보만 넘긴다(D14).
 */
@Entity
@Table(name = "customers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "business_registration_number", length = 20)
    private String businessRegistrationNumber;

    @Column(name = "representative_name", length = 100)
    private String representativeName;

    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "tax_invoice_email", length = 255)
    private String taxInvoiceEmail;

    @Column(length = 500)
    private String address;

    @Column(length = 1000)
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerStatus status;

    @Builder
    private Customer(
            String name, String businessRegistrationNumber, String representativeName, String contactName,
            String contactPhone, String contactEmail, String taxInvoiceEmail, String address, String memo
    ) {
        this.status = CustomerStatus.ACTIVE;
        updateInfo(name, businessRegistrationNumber, representativeName, contactName, contactPhone, contactEmail,
                taxInvoiceEmail, address, memo);
    }

    public void updateInfo(
            String name, String businessRegistrationNumber, String representativeName, String contactName,
            String contactPhone, String contactEmail, String taxInvoiceEmail, String address, String memo
    ) {
        this.name = name.trim();
        this.businessRegistrationNumber = blankToNull(businessRegistrationNumber);
        this.representativeName = blankToNull(representativeName);
        this.contactName = blankToNull(contactName);
        this.contactPhone = blankToNull(contactPhone);
        this.contactEmail = blankToNull(contactEmail);
        this.taxInvoiceEmail = blankToNull(taxInvoiceEmail);
        this.address = blankToNull(address);
        this.memo = blankToNull(memo);
    }

    public void changeStatus(CustomerStatus status) {
        this.status = status;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
