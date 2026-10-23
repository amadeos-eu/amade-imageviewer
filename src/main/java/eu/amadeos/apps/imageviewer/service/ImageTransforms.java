package eu.amadeos.apps.imageviewer.service;

import eu.amadeos.apps.imageviewer.domain.Orientation;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/** Turns pictures in Java2D. */
public final class ImageTransforms {
    private ImageTransforms() {
    }

    // --8<-- [start:affine]
    /**
     * Maps a picture of {@code width} × {@code height} pixels, turned by {@code orientation}, onto the rectangle
     * from (0, 0) that it covers afterwards (width and height swapped for quarter turns).
     */
    public static AffineTransform affine(Orientation orientation, int width, int height) {
        int[] m = orientation.matrix();
        double targetWidth = orientation.swapsSides() ? height : width;
        double targetHeight = orientation.swapsSides() ? width : height;
        double cx = width / 2.0, cy = height / 2.0;
        return new AffineTransform(m[0], m[2], m[1], m[3],
                targetWidth / 2 - (m[0] * cx + m[1] * cy),
                targetHeight / 2 - (m[2] * cx + m[3] * cy));
    }
    // --8<-- [end:affine]

    /** A turned copy; the picture itself if nothing turns. */
    public static BufferedImage apply(BufferedImage image, Orientation orientation) {
        if (orientation == Orientation.NORMAL) return image;
        int w = image.getWidth(), h = image.getHeight();
        int tw = orientation.swapsSides() ? h : w, th = orientation.swapsSides() ? w : h;
        int type = image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage turned = new BufferedImage(tw, th, type);
        Graphics2D g = turned.createGraphics();
        g.drawImage(image, affine(orientation, w, h), null);
        g.dispose();
        return turned;
    }

    /** A copy that fits into {@code box} × {@code box}, smoothly scaled; small pictures stay as they are. */
    public static BufferedImage scaleToFit(BufferedImage image, int box) {
        double factor = Math.min(1, Math.min((double) box / image.getWidth(), (double) box / image.getHeight()));
        int w = Math.max(1, (int) Math.round(image.getWidth() * factor));
        int h = Math.max(1, (int) Math.round(image.getHeight() * factor));
        BufferedImage scaled = new BufferedImage(w, h, image.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(image, 0, 0, w, h, null);
        g.dispose();
        return scaled;
    }
}
