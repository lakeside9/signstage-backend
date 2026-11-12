package com.eformworks.signstage.backend.feature.organization.dto;

import com.eformworks.signstage.backend.feature.organization.entity.PartnerBusinessType;
import com.eformworks.signstage.backend.feature.organization.entity.PartnerSubcontractProfile;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class PartnerSubcontractProfileDto {

    private PartnerSubcontractProfileDto() {
    }

    public static final class Request {
        private Request() {
        }

        /**
         * 동의 항목은 true일 때만 반영한다(이미 동의했으면 시각을 바꾸지 않는다). false로 철회하지
         * 않는다.
         */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UpdateProfile {

            @NotNull
            private PartnerBusinessType businessType;

            @Size(max = 20)
            private String businessRegistrationNumber;

            @Size(max = 100)
            private String representativeName;

            @Size(max = 50)
            private String bankName;

            @Size(max = 50)
            private String bankAccountNumber;

            @Size(max = 100)
            private String accountHolderName;

            private boolean agreeSubcontractTerms;

            private boolean agreePrivacyConsignment;
        }
    }

    public static final class Response {
        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class Profile {

            private final Long organizationId;
            private final String businessType;
            private final String businessRegistrationNumber;
            private final String representativeName;
            private final String bankName;
            private final String bankAccountNumber;
            private final String accountHolderName;
            private final LocalDateTime accountVerifiedAt;
            private final LocalDateTime subcontractTermsAgreedAt;
            private final LocalDateTime privacyConsignmentAgreedAt;
            private final boolean orderEligible;

            public static Profile from(PartnerSubcontractProfile profile) {
                return new Profile(
                        profile.getOrganization().getId(),
                        profile.getBusinessType() != null ? profile.getBusinessType().name() : null,
                        profile.getBusinessRegistrationNumber(),
                        profile.getRepresentativeName(),
                        profile.getBankName(),
                        profile.getBankAccountNumber(),
                        profile.getAccountHolderName(),
                        profile.getAccountVerifiedAt(),
                        profile.getSubcontractTermsAgreedAt(),
                        profile.getPrivacyConsignmentAgreedAt(),
                        profile.isOrderEligible()
                );
            }

            /** 아직 한 번도 입력하지 않은 조직 — 행을 만들지 않고 빈 응답을 돌려준다. */
            public static Profile empty(Long organizationId) {
                return new Profile(organizationId, null, null, null, null, null, null, null, null, null, false);
            }
        }
    }
}
