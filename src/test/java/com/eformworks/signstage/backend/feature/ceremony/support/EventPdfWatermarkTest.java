package com.eformworks.signstage.backend.feature.ceremony.support;

import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class EventPdfWatermarkTest {
    @ParameterizedTest
    @ValueSource(strings = {"TEST", "REHEARSAL"})
    void marksEveryPageIncludingRotatedCroppedAndUnsignedPages(String eventType) throws Exception {
        byte[] source = sourcePdf();
        byte[] original = source.clone();
        try (PDDocument document = Loader.loadPDF(EventPdfWatermark.apply(source, eventType, false))) {
            assertThat(document.getNumberOfPages()).isEqualTo(5);
            PDFTextStripper stripper = new PDFTextStripper();
            for (int index = 0; index < 5; index++) {
                stripper.setStartPage(index + 1);
                stripper.setEndPage(index + 1);
                assertThat(stripper.getText(document).replaceAll("\\s+", "")).contains("SIGNSTAGE", "Agreement");
                assertThat(new PDFRenderer(document).renderImage(index)).isNotNull();
            }
        }
        assertThat(source).isEqualTo(original);
    }

    @Test
    void marksDemoOrganizationMainPdf() throws Exception {
        try (PDDocument document = Loader.loadPDF(EventPdfWatermark.apply(sourcePdf(), "MAIN", true))) {
            assertThat(new PDFTextStripper().getText(document).replaceAll("\\s+", "")).contains("SIGNSTAGE");
        }
    }

    @Test
    void leavesMainPdfByteForByteUnchanged() throws Exception {
        byte[] source = sourcePdf();
        assertThat(EventPdfWatermark.apply(source, "MAIN", false)).isSameAs(source);
    }

    private byte[] sourcePdf() throws Exception {
        try (PDDocument document = new PDDocument()) {
            for (int rotation : new int[]{0, 90, 180, 270, 0}) {
                PDPage page = new PDPage(new PDRectangle(900, 1000));
                page.setCropBox(new PDRectangle(50, 75, 600, 800));
                page.setRotation(rotation);
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 18);
                    stream.newLineAtOffset(100, 500);
                    stream.showText("Agreement");
                    stream.endText();
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }
}
