package com.eformworks.signstage.backend.feature.ceremony.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 서명자/문서 양식/하위 행사의 표시 순서(displayOrder) 일괄 변경 요청 — 목록 화면의 위/아래
 * 이동 버튼이 매번 전체 목록을 원하는 순서로 다시 인덱싱해 통째로 보낸다. 세 컨트롤러
 * (SignerController/TemplateController/CeremonyEventController)가 모양이 완전히 같아 공용
 * DTO로 뺐다 — signstage-docs business/ceremony-feature-migration-review.md 참고
 * (legacy(~/Works/eform/source/signstage) 2026-08-27 포팅).
 */
public final class DisplayOrderRequest {

    private DisplayOrderRequest() {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {

        @NotNull
        private Long id;

        @NotNull
        private Integer displayOrder;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateDisplayOrders {

        @NotEmpty
        @Valid
        private List<Item> items;
    }
}
