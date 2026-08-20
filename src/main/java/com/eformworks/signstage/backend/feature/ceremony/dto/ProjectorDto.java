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

            /**
             * 이 하위 행사에 실제로 적용된 선택옵션 코드({@code OptionalFeatureCode.name()}) 목록 —
             * 서명 하이라이트/폭죽 같은 프로젝터 전용 연출 효과를 켤지 판단하는 데 쓴다. 관리자 콘솔
             * DTO(`CeremonyEventDto`)에는 이 필드가 없다 — 연출 효과는 프로젝터 화면 전용이라
             * 다른 화면에는 노출할 필요가 없다.
             */
            private final List<String> appliedOptionalFeatureCodes;
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
