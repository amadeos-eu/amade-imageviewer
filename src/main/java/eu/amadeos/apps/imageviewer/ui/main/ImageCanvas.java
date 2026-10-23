package eu.amadeos.apps.imageviewer.ui.main;

import eu.amadeos.apps.imageviewer.domain.Orientation;
import eu.amadeos.apps.imageviewer.domain.ZoomSteps;
import eu.amadeos.apps.imageviewer.service.ImageTransforms;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * Shows one picture on a dark background: fitted into the view, or at a zoom factor, then movable with the mouse.
 * Shrunk pictures are scaled once in good quality and kept, since smooth scaling of a 24-megapixel photo on every
 * repaint would be slow; while the window is being resized a quick scaling stands in.
 */
public final class ImageCanvas extends JComponent {
    /** What the canvas reports to its window. */
    public interface Listener {
        /** The wheel turned without Ctrl while the picture fits: -1 for back, 1 for forward. */
        void browse(int direction);

        void doubleClicked();

        /** A single click, at that fraction of the width (0 left edge, 1 right edge). */
        void clicked(double x);

        /** The shown scale changed (fit or zoom), for the status bar. */
        void scaleChanged(double scale);
    }

    private static final Color BACKGROUND = new Color(0x2b2b2b);

    /** The picture as given, to recognise it again. */
    private BufferedImage source;
    /** The same in the screen's pixel format. */
    private BufferedImage image;
    private Orientation orientation = Orientation.NORMAL;
    private boolean fit = true;
    private double factor = 1;
    /** The point of the turned picture, in its pixels, that is in the middle of the view. */
    private double centreX, centreY;
    private String message;
    private Listener listener;
    private BufferedImage scaled;
    private double scaledFor = -1;
    private Orientation scaledOrientation;
    private final Timer rescale = new Timer(150, e -> repaint());
    private double reportedScale = -1;

    public ImageCanvas() {
        setOpaque(true);
        setBackground(BACKGROUND);
        setFocusable(true);
        setPreferredSize(new Dimension(640, 440));
        rescale.setRepeats(false);
        MouseAdapter mouse = new MouseAdapter() {
            private Point dragFrom;

            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (SwingUtilities.isLeftMouseButton(e) && scrollable()) {
                    dragFrom = e.getPoint();
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragFrom == null) return;
                double scale = scale();
                centreX -= (e.getX() - dragFrom.x) / scale;
                centreY -= (e.getY() - dragFrom.y) / scale;
                dragFrom = e.getPoint();
                clampCentre();
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragFrom = null;
                setCursor(Cursor.getDefaultCursor());
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (listener == null || !SwingUtilities.isLeftMouseButton(e)) return;
                if (e.getClickCount() == 2) listener.doubleClicked();
                else if (e.getClickCount() == 1 && getWidth() > 0) listener.clicked((double) e.getX() / getWidth());
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (image == null) return;
                int direction = e.getWheelRotation() > 0 ? 1 : e.getWheelRotation() < 0 ? -1 : 0;
                if (direction == 0) return;
                if (e.isControlDown()) {
                    zoomAt(direction < 0 ? ZoomSteps.in(scale()) : ZoomSteps.out(scale()), e.getPoint());
                } else if (scrollable() && displayHeight() * scale() > getHeight()) {
                    centreY += direction * 60 / scale();
                    clampCentre();
                    repaint();
                } else if (listener != null) {
                    listener.browse(direction);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                clampCentre();
                rescale.restart();
            }
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Shows a picture fitted into the view. The same picture again (turned, or after a setting changed) keeps
     * its zoom.
     */
    public void show(BufferedImage picture, Orientation turned) {
        if (picture != null && picture == source && message == null) {
            if (turned != orientation) turn(turned);
            return;
        }
        source = picture;
        image = picture == null ? null : compatible(picture);
        orientation = turned;
        message = null;
        scaled = null;
        fit = true;
        centreX = displayWidth() / 2.0;
        centreY = displayHeight() / 2.0;
        repaint();
        reportScale();
    }

    private void turn(Orientation turned) {
        orientation = turned;
        scaled = null;
        centreX = displayWidth() / 2.0;
        centreY = displayHeight() / 2.0;
        clampCentre();
        repaint();
        reportScale();
    }

    public void showMessage(String text) {
        source = null;
        image = null;
        scaled = null;
        message = text;
        repaint();
    }

    public BufferedImage image() {
        return image;
    }

    public boolean fitted() {
        return fit;
    }

    // --8<-- [start:zoom]
    public void zoom(ZoomCommand command) {
        if (image == null) return;
        Point middle = new Point(getWidth() / 2, getHeight() / 2);
        switch (command) {
            case IN -> zoomAt(ZoomSteps.in(scale()), middle);
            case OUT -> zoomAt(ZoomSteps.out(scale()), middle);
            case ACTUAL -> zoomAt(1, middle);
            case FIT -> {
                fit = true;
                centreX = displayWidth() / 2.0;
                centreY = displayHeight() / 2.0;
                repaint();
                reportScale();
            }
        }
    }

    /** Zooms so that the picture point under {@code at} stays under it. */
    private void zoomAt(double newFactor, Point at) {
        double old = scale();
        double px = centreX + (at.x - getWidth() / 2.0) / old;
        double py = centreY + (at.y - getHeight() / 2.0) / old;
        fit = false;
        factor = Math.clamp(newFactor, ZoomSteps.MIN, ZoomSteps.MAX);
        centreX = px - (at.x - getWidth() / 2.0) / factor;
        centreY = py - (at.y - getHeight() / 2.0) / factor;
        clampCentre();
        repaint();
        reportScale();
    }
    // --8<-- [end:zoom]

    /** The scale the picture is shown at. */
    public double scale() {
        if (image == null) return 1;
        return fit ? ZoomSteps.fit(displayWidth(), displayHeight(), getWidth(), getHeight(), false) : factor;
    }

    private boolean scrollable() {
        return image != null && (displayWidth() * scale() > getWidth() + 0.5 || displayHeight() * scale() > getHeight() + 0.5);
    }

    private int displayWidth() {
        return image == null ? 0 : orientation.swapsSides() ? image.getHeight() : image.getWidth();
    }

    private int displayHeight() {
        return image == null ? 0 : orientation.swapsSides() ? image.getWidth() : image.getHeight();
    }

    /** Keeps the picture from being dragged out of the view. */
    private void clampCentre() {
        if (image == null) return;
        double scale = scale();
        double halfW = getWidth() / 2.0 / scale, halfH = getHeight() / 2.0 / scale;
        centreX = displayWidth() <= 2 * halfW ? displayWidth() / 2.0 : Math.clamp(centreX, halfW, displayWidth() - halfW);
        centreY = displayHeight() <= 2 * halfH ? displayHeight() / 2.0 : Math.clamp(centreY, halfH, displayHeight() - halfH);
    }

    private void reportScale() {
        double scale = scale();
        if (listener != null && Math.abs(scale - reportedScale) > 1e-9) {
            reportedScale = scale;
            listener.scaleChanged(scale);
        }
    }

    // --8<-- [start:paint]
    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        if (image == null) {
            paintMessage(g);
            g.dispose();
            return;
        }
        if (fit) clampCentre();
        double scale = scale();
        double x = getWidth() / 2.0 - centreX * scale;
        double y = getHeight() / 2.0 - centreY * scale;
        if (scale < 1) {
            BufferedImage small = scaledImage(scale);
            if (small != null) {
                g.drawImage(small, (int) Math.round(x), (int) Math.round(y), null);
            } else {
                // Resizing: a quick picture now, the good one when the size settles.
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                drawTurned(g, x, y, scale);
                rescale.restart();
            }
        } else {
            // Enlarged: hard pixels from 200 % on, as image editors show them.
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, scale >= 2
                    ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            drawTurned(g, x, y, scale);
        }
        g.dispose();
        reportScale();
    }
    // --8<-- [end:paint]

    private void drawTurned(Graphics2D g, double x, double y, double scale) {
        AffineTransform at = new AffineTransform();
        at.translate(x, y);
        at.scale(scale, scale);
        at.concatenate(ImageTransforms.affine(orientation, image.getWidth(), image.getHeight()));
        g.drawImage(image, at, null);
    }

    /** The picture scaled down in good quality, or null while that would hold up a resize. */
    private BufferedImage scaledImage(double scale) {
        if (scaled != null && Math.abs(scaledFor - scale) < 1e-6 && scaledOrientation == orientation) return scaled;
        if (rescale.isRunning()) return null;
        scaled = smoothScale(ImageTransforms.apply(image, orientation), scale);
        scaledFor = scale;
        scaledOrientation = orientation;
        return scaled;
    }

    // --8<-- [start:smooth]
    /** Halves the picture step by step, then scales the rest bilinearly: close to area averaging, but fast. */
    static BufferedImage smoothScale(BufferedImage source, double scale) {
        int targetW = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int targetH = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage current = source;
        int w = source.getWidth(), h = source.getHeight();
        do {
            w = Math.max(targetW, w / 2);
            h = Math.max(targetH, h / 2);
            BufferedImage step = new BufferedImage(w, h, current.getColorModel().hasAlpha()
                    ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            Graphics2D g = step.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(current, 0, 0, w, h, null);
            g.dispose();
            current = step;
        } while (w != targetW || h != targetH);
        return current;
    }
    // --8<-- [end:smooth]

    /** A copy in the screen's pixel format, which Java2D draws without converting every time. */
    private BufferedImage compatible(BufferedImage picture) {
        GraphicsConfiguration config = getGraphicsConfiguration();
        if (config == null) {
            config = java.awt.GraphicsEnvironment.isHeadless() ? null
                    : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                    .getDefaultConfiguration();
        }
        if (config == null || picture.getColorModel().equals(config.getColorModel())) return picture;
        int transparency = picture.getColorModel().hasAlpha() ? Transparency.TRANSLUCENT : Transparency.OPAQUE;
        BufferedImage copy = config.createCompatibleImage(picture.getWidth(), picture.getHeight(), transparency);
        Graphics2D g = copy.createGraphics();
        g.drawImage(picture, 0, 0, null);
        g.dispose();
        return copy;
    }

    private void paintMessage(Graphics2D g) {
        if (message == null || message.isEmpty()) return;
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0xc8c8c8));
        g.setFont(getFont() == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 14) : getFont().deriveFont(14f));
        FontMetrics fm = g.getFontMetrics();
        String[] lines = message.split("\n");
        int y = (getHeight() - lines.length * fm.getHeight()) / 2 + fm.getAscent();
        for (String line : lines) {
            g.drawString(line, (getWidth() - fm.stringWidth(line)) / 2, y);
            y += fm.getHeight();
        }
    }
}
