package com.eformworks.signstage.backend.feature.ceremony.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 공개 프로젝터 화면(`/api/projector/events/{eventAccessKey}`, permitAll)이 쓰는 DTO —
 * 서명자 포털과 같은 인가 모델(accessKey 소지만으로 접근, JWT 없음)이라 관리자 콘솔용
 * DTO와 분리해 둔다.
 */
public final class ProjectorDto {

    private ProjectorDto() {
    }

    public static final class Response {

        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class ProjectorContext {

            private final Long eventId;
            private final String eventName;
            private final String eventStatus;
            private final String eventAccessKey;

            /** EXHIBITION 매핑이 없으면 전부 null/빈 리스트. */
            private final ExhibitionDocument exhibition;
        }

        @Getter
        @AllArgsConstructor
        public static class ExhibitionDocument {

            private final Long templateId;
            private final String title;
            private final Integer pageCount;
            private final Float width;
            private final Float height;
            private final List<TemplateFieldDto.Response.TemplateFieldSummary> fields;
            /** fields[].signerId가 가리키는 서명자만 모은 이름 조회용 목록. */
            private final List<SignerInfo> signers;
        }

        @Getter
        @AllArgsConstructor
        public static class SignerInfo {

            private final Long id;
            private final String name;
        }
    }
}
