package com.eformworks.signstage.backend.feature.ceremony.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;

/**
 * Adds a print-visible "SIGNSTAGE" watermark to newly generated test/rehearsal results and to every result of a demo
 * organization, before digital signing. The projector and signer portal screens carry no watermark (2026-11-13) —
 * only the generated PDF does.
 */
public final class EventPdfWatermark {
    private static final String TEXT = "SIGNSTAGE";

    private EventPdfWatermark() {
    }

    public static byte[] apply(byte[] pdfBytes, String eventType, boolean demo) throws IOException {
        if (!demo && !"TEST".equals(eventType) && !"REHEARSAL".equals(eventType)) {
            return pdfBytes;
        }
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            for (PDPage page : document.getPages()) {
                draw(document, page, font);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    private static void draw(PDDocument document, PDPage page, PDType1Font font) throws IOException {
        PDRectangle box = page.getCropBox();
        int rotation = Math.floorMod(page.getRotation(), 360);
        boolean sideways = rotation == 90 || rotation == 270;
        float width = sideways ? box.getHeight() : box.getWidth();
        float height = sideways ? box.getWidth() : box.getHeight();
        float cellWidth = width / 2;
        float cellHeight = height / 3;
        float fontSize = Math.min(cellWidth * 0.85f / (font.getStringWidth(TEXT) / 1000), cellHeight * 0.35f);
        float textWidth = font.getStringWidth(TEXT) / 1000 * fontSize;
        double angle = Math.toRadians(30);
        Matrix displayToPage = switch (rotation) {
            case 90 -> new Matrix(0, 1, -1, 0, box.getUpperRightX(), box.getLowerLeftY());
            case 180 -> new Matrix(-1, 0, 0, -1, box.getUpperRightX(), box.getUpperRightY());
            case 270 -> new Matrix(0, -1, 1, 0, box.getLowerLeftX(), box.getUpperRightY());
            default -> Matrix.getTranslateInstance(box.getLowerLeftX(), box.getLowerLeftY());
        };
        try (PDPageContentStream stream = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            stream.saveGraphicsState();
            stream.transform(displayToPage);
            PDExtendedGraphicsState state = new PDExtendedGraphicsState();
            state.setNonStrokingAlphaConstant(0.16f);
            stream.setGraphicsStateParameters(state);
            stream.setNonStrokingColor(0.3f, 0.3f, 0.3f);
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 2; column++) {
                    float centerX = (column + 0.5f) * cellWidth;
                    float centerY = (row + 0.5f) * cellHeight;
                    float x = centerX - (float) Math.cos(angle) * textWidth / 2;
                    float y = centerY - (float) Math.sin(angle) * textWidth / 2;
                    stream.beginText();
                    stream.setFont(font, fontSize);
                    stream.setTextMatrix(Matrix.getRotateInstance(angle, x, y));
                    stream.showText(TEXT);
                    stream.endText();
                }
            }
            stream.restoreGraphicsState();
        }
    }
}
