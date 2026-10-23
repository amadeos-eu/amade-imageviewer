package eu.amadeos.apps.imageviewer.ui.main;

import eu.amadeos.commons.print.PaperSize;
import eu.amadeos.commons.print.PrintJobs;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.print.Book;
import java.awt.print.PageFormat;
import java.awt.print.Printable;

/**
 * One picture on one page: turned to landscape if it is wider than high, as large as fits within the margins
 * and in the middle. Small pictures are not enlarged beyond 150 dpi, so they do not print blurred.
 */
public final class PicturePrinter implements Printable {
    /** 1.5 cm on every side. */
    static final double MARGIN = 42.5;

    private final BufferedImage image;

    public PicturePrinter(BufferedImage image) {
        this.image = image;
    }

    // --8<-- [start:book]
    public Book book(PaperSize paper) {
        Book book = new Book();
        book.append(this, PrintJobs.pageFormat(paper, image.getWidth() > image.getHeight(), MARGIN));
        return book;
    }
    // --8<-- [end:book]

    /** The size of the printed picture on the page, in points, and its place. */
    static double[] placement(int imageWidth, int imageHeight, PageFormat page) {
        double areaW = page.getImageableWidth(), areaH = page.getImageableHeight();
        // 150 dpi: one pixel is 72 / 150 points.
        double scale = Math.min(Math.min(areaW / imageWidth, areaH / imageHeight), 72.0 / 150);
        double w = imageWidth * scale, h = imageHeight * scale;
        return new double[]{page.getImageableX() + (areaW - w) / 2, page.getImageableY() + (areaH - h) / 2, w, h};
    }

    @Override
    public int print(Graphics graphics, PageFormat page, int index) {
        if (index > 0) return NO_SUCH_PAGE;
        Graphics2D g = (Graphics2D) graphics;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        double[] p = placement(image.getWidth(), image.getHeight(), page);
        g.drawImage(image, (int) Math.round(p[0]), (int) Math.round(p[1]), (int) Math.round(p[2]),
                (int) Math.round(p[3]), null);
        return PAGE_EXISTS;
    }
}
