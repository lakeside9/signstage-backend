package com.eformworks.signstage.backend.feature.ceremony.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * 한 서명란(TemplateField)에 그려야 할 스트로크 목록. JPA와 무관한 순수 값 객체라
 * model 패키지에 둔다. {@code SignatureOverlayRenderer}의 입력 형태다.
 *
 * <p>{@code strokes}는 획 단위 목록이다 — 바깥 리스트 원소 하나가 pen-down부터 pen-up까지
 * 한 획이고, 안쪽 리스트는 그 획을 이루는 점 {@code [x, y]}(필드 바운딩 박스 기준 0~1 비율,
 * 좌상단 원점) 배열이다.
 */
public record FieldStrokes(
        int pageIndex,
        BigDecimal xRatio,
        BigDecimal yRatio,
        BigDecimal widthRatio,
        BigDecimal heightRatio,
        List<List<double[]>> strokes
) {
}
