package com.eformworks.signstage.backend.feature.order.dto;

import com.eformworks.signstage.backend.feature.order.entity.QuoteTemplate;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 견적 공통 문구 API DTO — 견적서 "제공 서비스" 본문과 하단 안내문구를 기간별로 둔다. */
public final class QuoteTemplateDto {

    private QuoteTemplateDto() {
    }

    public static final class Request {
        private Request() {
        }

        /** 등록·수정 공용. 문구는 일반 텍스트(줄바꿈 유지)이고, 비우면 견적서에 그 칸이 없다. */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class SaveQuoteTemplate {

            @Size(max = 5000)
            private String serviceDescription;

            @Size(max = 2000)
            private String footerNotice;

            @NotNull
            private LocalDate effectiveFrom;

            private LocalDate effectiveTo;
        }

        /** 견적서 공급자 칸. 비운 항목은 견적서에 나오지 않는다. */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class SaveSupplier {

            @Size(max = 100)
            private String name;

            @Size(max = 50)
            private String representative;

            @Size(max = 20)
            private String businessNumber;

            @Size(max = 300)
            private String address;

            @Size(max = 50)
            private String phone;
        }
    }

    public static final class Response {
        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class AdminQuoteTemplate {

            private final Long id;
            private final String serviceDescription;
            private final String footerNotice;
            private final LocalDate effectiveFrom;
            private final LocalDate effectiveTo;
            /** 견적에 복사됐는지 — 쓰이지 않은 문구만 고치거나 지울 수 있다. */
            private final boolean used;
            private final QuoteSupplier supplier;

            public static AdminQuoteTemplate from(QuoteTemplate template, boolean used) {
                return new AdminQuoteTemplate(
                        template.getId(),
                        template.getServiceDescription(),
                        template.getFooterNotice(),
                        template.getPeriod().getEffectiveFrom(),
                        template.getPeriod().getEffectiveTo(),
                        used,
                        QuoteSupplier.from(template)
                );
            }
        }

        /**
         * 견적서 공급자 칸과 인감 유무. 인감 이미지는 {@code GET /api/platform-admin/quote-templates/{id}/seal}로 받는다.
         * {@code quoteTemplateId}가 null이면 공통 문구 없이 저장된 견적이다.
         */
        @Getter
        @AllArgsConstructor
        public static class QuoteSupplier {

            private final Long quoteTemplateId;
            private final String name;
            private final String representative;
            private final String businessNumber;
            private final String address;
            private final String phone;
            private final boolean sealAvailable;

            public static QuoteSupplier from(QuoteTemplate template) {
                if (template == null) {
                    return new QuoteSupplier(null, null, null, null, null, null, false);
                }
                return new QuoteSupplier(
                        template.getId(),
                        template.getSupplierName(),
                        template.getSupplierRepresentative(),
                        template.getSupplierBusinessNumber(),
                        template.getSupplierAddress(),
                        template.getSupplierPhone(),
                        template.hasSeal()
                );
            }
        }
    }
}
