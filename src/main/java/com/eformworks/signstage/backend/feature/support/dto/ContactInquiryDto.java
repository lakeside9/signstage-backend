package com.eformworks.signstage.backend.feature.support.dto;

import com.eformworks.signstage.backend.feature.support.entity.ContactInquiry;
import com.eformworks.signstage.backend.feature.support.entity.ContactInquiryStatus;
import com.eformworks.signstage.backend.feature.support.entity.ContactInquiryType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 고객 문의하기(공개 접수·관리자 처리, 2026-11-13). */
public final class ContactInquiryDto {

    private ContactInquiryDto() {
    }

    public static final class Request {

        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Submit {

            @NotNull
            private ContactInquiryType inquiryType;

            @NotBlank
            @Size(max = 100)
            private String companyName;

            @NotBlank
            @Size(max = 50)
            private String contactName;

            @NotBlank
            @Email
            @Size(max = 200)
            private String email;

            @Size(max = 50)
            private String phone;

            private LocalDate eventDate;

            @Min(1)
            @Max(100000)
            private Integer expectedSigners;

            @NotBlank
            @Size(max = 2000)
            private String message;

            /** 개인정보 수집·이용 동의 — 동의하지 않으면 서비스가 안내 문구와 함께 거절한다. */
            private boolean privacyConsented;

            /** 사람에게는 보이지 않는 칸(스팸 봇 걸러내기). 값이 있으면 저장하지 않고 성공처럼 응답한다. */
            private String website;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Handle {

            @NotNull
            private ContactInquiryStatus status;

            @Size(max = 1000)
            private String adminMemo;
        }
    }

    public static final class Response {

        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class ContactInquirySummary {

            private final Long id;
            private final ContactInquiryType inquiryType;
            private final String companyName;
            private final String contactName;
            private final String email;
            private final String phone;
            private final LocalDate eventDate;
            private final Integer expectedSigners;
            private final String message;
            private final ContactInquiryStatus status;
            private final String adminMemo;
            private final LocalDateTime handledAt;
            private final LocalDateTime createdAt;

            public static ContactInquirySummary from(ContactInquiry inquiry) {
                return new ContactInquirySummary(
                        inquiry.getId(),
                        inquiry.getInquiryType(),
                        inquiry.getCompanyName(),
                        inquiry.getContactName(),
                        inquiry.getEmail(),
                        inquiry.getPhone(),
                        inquiry.getEventDate(),
                        inquiry.getExpectedSigners(),
                        inquiry.getMessage(),
                        inquiry.getStatus(),
                        inquiry.getAdminMemo(),
                        inquiry.getHandledAt(),
                        inquiry.getCreatedAt()
                );
            }
        }
    }
}
