package com.eformworks.signstage.backend.feature.organization.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PartnerSubcontractProfile} 단위 테스트 — 발주 대상 자격(D24)과 계좌 변경 시 확인 무효화.
 */
class PartnerSubcontractProfileTest {

    @Test
    @DisplayName("사업자 정보·확인된 계좌·약관 동의가 모두 있어야 발주 대상이다")
    void isOrderEligible_requiresAllItems() {
        PartnerSubcontractProfile profile = eligibleProfile();

        assertThat(profile.isOrderEligible()).isTrue();
    }

    @Test
    @DisplayName("사업자 유형이 개인이 아니면 사업자등록번호가 필요하다")
    void isOrderEligible_requiresBusinessNumberForBusinesses() {
        PartnerSubcontractProfile profile = eligibleProfile();
        profile.updateBusinessInfo(PartnerBusinessType.GENERAL, " ", "대표");

        assertThat(profile.isOrderEligible()).isFalse();

        profile.updateBusinessInfo(PartnerBusinessType.INDIVIDUAL, null, "대표");

        assertThat(profile.isOrderEligible()).isTrue();
    }

    @Test
    @DisplayName("계좌를 바꾸면 예금주 확인이 풀려 다시 확인받아야 한다")
    void updateSettlementAccount_resetsVerificationOnChange() {
        PartnerSubcontractProfile profile = eligibleProfile();

        profile.updateSettlementAccount("국민은행", "123-456", "주식회사 파트너");
        assertThat(profile.getAccountVerifiedAt()).isNotNull();

        profile.updateSettlementAccount("국민은행", "999-999", "주식회사 파트너");
        assertThat(profile.getAccountVerifiedAt()).isNull();
        assertThat(profile.getAccountVerifiedBy()).isNull();
        assertThat(profile.isOrderEligible()).isFalse();
    }

    private static PartnerSubcontractProfile eligibleProfile() {
        PartnerSubcontractProfile profile = PartnerSubcontractProfile.builder()
                .organization(Organization.builder().name("파트너").code("PARTNER").build())
                .build();
        profile.updateBusinessInfo(PartnerBusinessType.GENERAL, "123-45-67890", "대표");
        profile.updateSettlementAccount("국민은행", "123-456", "주식회사 파트너");
        profile.verifyAccount(1L);
        profile.agreeSubcontractTerms();
        profile.agreePrivacyConsignment();
        return profile;
    }
}
