package com.eformworks.signstage.backend.feature.ceremony.support;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

/** Converts stored pen widths to page-relative PDF widths, independently for each page. */
public final class SignaturePdfWidth {
    private static final double CONTRACT_REFERENCE_WIDTH = 595.276; // A4 portrait, PDF points
    private static final double EXHIBITION_REFERENCE_WIDTH = 1280.0;
    private static final double EXHIBITION_REFERENCE_PEN_WIDTH = 2.5;

    private SignaturePdfWidth() {}

    public static float resolve(PDPage page, String documentRole, double storedWidth) {
        PDRectangle visibleBox = page.getCropBox();
        int rotation = Math.floorMod(page.getRotation(), 360);
        double visibleWidth = rotation == 90 || rotation == 270
                ? visibleBox.getHeight() : visibleBox.getWidth();
        double penWidth = Double.isFinite(storedWidth) && storedWidth > 0
                ? storedWidth : 2.0;
        double scale = "EXHIBITION".equals(documentRole)
                ? (EXHIBITION_REFERENCE_PEN_WIDTH / 2.0)
                    * visibleWidth / EXHIBITION_REFERENCE_WIDTH
                : visibleWidth / CONTRACT_REFERENCE_WIDTH;
        return (float) (penWidth * scale);
    }
}
