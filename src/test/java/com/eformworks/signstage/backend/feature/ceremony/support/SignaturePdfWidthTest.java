package com.eformworks.signstage.backend.feature.ceremony.support;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class SignaturePdfWidthTest {
    @Test
    void normalizesBothRolesAndPreservesCustomPenWidths() {
        PDPage exhibition = new PDPage(new PDRectangle(3840, 1440));
        assertThat(SignaturePdfWidth.resolve(exhibition, "EXHIBITION", 2)).isEqualTo(7.5f);
        assertThat(SignaturePdfWidth.resolve(exhibition, "EXHIBITION", 4)).isEqualTo(15);
        PDPage a4 = new PDPage(new PDRectangle(595.276f, 841.89f));
        assertThat(SignaturePdfWidth.resolve(a4, "CONTRACT", 2)).isCloseTo(2f, offset(0.0001f));
        PDPage doubled = new PDPage(new PDRectangle(1190.552f, 1683.78f));
        assertThat(SignaturePdfWidth.resolve(doubled, "CONTRACT", 2)).isCloseTo(4f, offset(0.0001f));
    }

    @Test
    void usesVisibleCropWidthAndDisplayRotation() {
        PDPage page = new PDPage(new PDRectangle(4000, 2000));
        page.setCropBox(new PDRectangle(100, 100, 1280, 640));
        assertThat(SignaturePdfWidth.resolve(page, "EXHIBITION", 2)).isEqualTo(2.5f);
        for (int rotation : new int[]{90, 270, -90}) {
            page.setRotation(rotation);
            assertThat(SignaturePdfWidth.resolve(page, "EXHIBITION", 2)).isEqualTo(1.25f);
        }
        page.setRotation(180);
        assertThat(SignaturePdfWidth.resolve(page, "EXHIBITION", Double.NaN)).isEqualTo(2.5f);
    }
}
