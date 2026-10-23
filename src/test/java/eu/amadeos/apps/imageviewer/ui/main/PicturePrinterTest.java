package eu.amadeos.apps.imageviewer.ui.main;

import eu.amadeos.apps.imageviewer.TestImages;
import eu.amadeos.commons.print.PaperSize;
import eu.amadeos.commons.print.PrintJobs;
import org.junit.jupiter.api.Test;

import java.awt.print.PageFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PicturePrinterTest {
    @Test
    void widePicturesPrintInLandscape() {
        var book = new PicturePrinter(TestImages.halves(400, 200)).book(PaperSize.A4);
        assertEquals(PageFormat.LANDSCAPE, book.getPageFormat(0).getOrientation());
        var tall = new PicturePrinter(TestImages.halves(200, 400)).book(PaperSize.A4);
        assertEquals(PageFormat.PORTRAIT, tall.getPageFormat(0).getOrientation());
    }

    @Test
    void largePicturesFillThePageAndSmallOnesStayAt150Dpi() {
        PageFormat page = PrintJobs.pageFormat(PaperSize.A4, false, PicturePrinter.MARGIN);
        double[] big = PicturePrinter.placement(4000, 3000, page);
        assertEquals(page.getImageableWidth(), big[2], 0.01);
        assertTrue(big[3] < page.getImageableHeight());
        double[] small = PicturePrinter.placement(300, 150, page);
        assertEquals(300 * 72.0 / 150, small[2], 0.01);
        // In the middle.
        assertEquals(page.getImageableX() + (page.getImageableWidth() - small[2]) / 2, small[0], 0.01);
    }
}
