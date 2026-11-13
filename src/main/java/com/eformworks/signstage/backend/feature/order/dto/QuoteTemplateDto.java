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

            public static AdminQuoteTemplate from(QuoteTemplate template, boolean used) {
                return new AdminQuoteTemplate(
                        template.getId(),
                        template.getServiceDescription(),
                        template.getFooterNotice(),
                        template.getPeriod().getEffectiveFrom(),
                        template.getPeriod().getEffectiveTo(),
                        used
                );
            }
        }
    }
}
