package com.eformworks.signstage.backend.feature.ceremony.support;

import com.eformworks.signstage.backend.feature.ceremony.model.FieldStrokes;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

/**
 * 원본 템플릿 PDF에 서명 획을 좌표에 맞춰 직접 그린다(signstage-docs
 * business/ceremony-feature-migration-review.md §5.1 결정 — PDFBox 직접 그리기).
 * 상태 없는 정적 유틸리티다.
 *
 * <p>좌표 변환은 두 단계다 — 필드 바운딩 박스(페이지 기준 0~1 비율, 좌상단 원점)를 먼저
 * PDF 좌표(좌하단 원점)로 바꾸고, 그 안에서 스트로크 점(필드 기준 0~1 비율, 좌상단 원점)을
 * 다시 바꾼다. PDF가 좌하단 원점이라 y축을 두 번 뒤집어야 한다.
 */
public final class SignatureOverlayRenderer {

    private static final float LINE_WIDTH = 1.5f;

    private SignatureOverlayRenderer() {
    }

    public static byte[] render(byte[] originalPdfBytes, List<FieldStrokes> fieldStrokesList) throws IOException {
        try (PDDocument document = Loader.loadPDF(originalPdfBytes)) {
            for (FieldStrokes fieldStrokes : fieldStrokesList) {
                if (fieldStrokes.strokes().isEmpty()) {
                    continue;
                }
                if (fieldStrokes.pageIndex() < 0 || fieldStrokes.pageIndex() >= document.getNumberOfPages()) {
                    continue;
                }
                drawFieldStrokes(document, document.getPage(fieldStrokes.pageIndex()), fieldStrokes);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static void drawFieldStrokes(PDDocument document, PDPage page, FieldStrokes fieldStrokes) throws IOException {
        PDRectangle box = page.getMediaBox();
        float pageWidth = box.getWidth();
        float pageHeight = box.getHeight();

        // 1단계: 필드 바운딩 박스(0~1, 좌상단 원점) → PDF 좌표(좌하단 원점).
        float fieldLeft = box.getLowerLeftX() + fieldStrokes.xRatio().floatValue() * pageWidth;
        float fieldTopFromBottom = box.getUpperRightY() - fieldStrokes.yRatio().floatValue() * pageHeight;
        float fieldWidth = fieldStrokes.widthRatio().floatValue() * pageWidth;
        float fieldHeight = fieldStrokes.heightRatio().floatValue() * pageHeight;
        float fieldBottom = fieldTopFromBottom - fieldHeight;

        try (PDPageContentStream contentStream = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            contentStream.setStrokingColor(0, 0, 0);
            contentStream.setLineWidth(LINE_WIDTH);

            for (List<double[]> stroke : fieldStrokes.strokes()) {
                if (stroke.size() < 2) {
                    continue;
                }
                boolean first = true;
                for (double[] point : stroke) {
                    // 2단계: 스트로크 점(0~1, 필드 기준 좌상단 원점) → 필드 내부 PDF 좌표.
                    float px = fieldLeft + (float) point[0] * fieldWidth;
                    float py = fieldBottom + fieldHeight - (float) point[1] * fieldHeight;
                    if (first) {
                        contentStream.moveTo(px, py);
                        first = false;
                    } else {
                        contentStream.lineTo(px, py);
                    }
                }
                contentStream.stroke();
            }
        }
    }
}
