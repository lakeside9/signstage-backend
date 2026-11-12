package com.eformworks.signstage.backend.feature.organization.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 파트너 조직이 플랫폼 발주를 받기 위한 외주 수행사 정보 — signstage-docs
 * business/platform-order-partner-subcontract-review.md D22. 조직당 하나다.
 *
 * <p>발주를 받으려면 {@link #isOrderEligible()}이 참이어야 한다: 사업자 유형(개인이 아니면
 * 사업자번호), 정산 계좌와 플랫폼의 예금주 확인, 외주 기본계약·개인정보 처리위탁 동의. 계좌를
 * 바꾸면 예금주 확인을 다시 받는다. 동의는 한 번 하면 철회 API를 두지 않는다(1차).
 *
 * <p>{@code accountVerifiedBy}는 감사 로그의 관리자 id처럼 FK 없는 행위자 참조다.
 */
@Entity
@Table(name = "partner_subcontract_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartnerSubcontractProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false, unique = true)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", length = 20)
    private PartnerBusinessType businessType;

    @Column(name = "business_registration_number", length = 20)
    private String businessRegistrationNumber;

    @Column(name = "representative_name", length = 100)
    private String representativeName;

    @Column(name = "bank_name", length = 50)
    private String bankName;

    @Column(name = "bank_account_number", length = 50)
    private String bankAccountNumber;

    @Column(name = "account_holder_name", length = 100)
    private String accountHolderName;

    @Column(name = "account_verified_by")
    private Long accountVerifiedBy;

    @Column(name = "account_verified_at")
    private LocalDateTime accountVerifiedAt;

    @Column(name = "subcontract_terms_agreed_at")
    private LocalDateTime subcontractTermsAgreedAt;

    @Column(name = "privacy_consignment_agreed_at")
    private LocalDateTime privacyConsignmentAgreedAt;

    @Builder
    private PartnerSubcontractProfile(Organization organization) {
        this.organization = organization;
    }

    public void updateBusinessInfo(
            PartnerBusinessType businessType,
            String businessRegistrationNumber,
            String representativeName
    ) {
        this.businessType = businessType;
        this.businessRegistrationNumber = blankToNull(businessRegistrationNumber);
        this.representativeName = blankToNull(representativeName);
    }

    /** 계좌가 바뀌면 이전 예금주 확인은 무효다. */
    public void updateSettlementAccount(String bankName, String bankAccountNumber, String accountHolderName) {
        String newBankName = blankToNull(bankName);
        String newAccountNumber = blankToNull(bankAccountNumber);
        String newHolderName = blankToNull(accountHolderName);
        boolean changed = !Objects.equals(this.bankName, newBankName)
                || !Objects.equals(this.bankAccountNumber, newAccountNumber)
                || !Objects.equals(this.accountHolderName, newHolderName);
        this.bankName = newBankName;
        this.bankAccountNumber = newAccountNumber;
        this.accountHolderName = newHolderName;
        if (changed) {
            this.accountVerifiedBy = null;
            this.accountVerifiedAt = null;
        }
    }

    public void agreeSubcontractTerms() {
        if (subcontractTermsAgreedAt == null) {
            subcontractTermsAgreedAt = LocalDateTime.now();
        }
    }

    public void agreePrivacyConsignment() {
        if (privacyConsignmentAgreedAt == null) {
            privacyConsignmentAgreedAt = LocalDateTime.now();
        }
    }

    public boolean hasSettlementAccount() {
        return bankName != null && bankAccountNumber != null && accountHolderName != null;
    }

    public void verifyAccount(Long verifiedBy) {
        this.accountVerifiedBy = verifiedBy;
        this.accountVerifiedAt = LocalDateTime.now();
    }

    public boolean isOrderEligible() {
        boolean businessInfoComplete = businessType != null
                && (businessType == PartnerBusinessType.INDIVIDUAL || businessRegistrationNumber != null);
        return businessInfoComplete
                && hasSettlementAccount()
                && accountVerifiedAt != null
                && subcontractTermsAgreedAt != null
                && privacyConsignmentAgreedAt != null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
