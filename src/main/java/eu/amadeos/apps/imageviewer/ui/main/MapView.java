package eu.amadeos.apps.imageviewer.ui.main;

import eu.amadeos.apps.imageviewer.domain.GeoPosition;
import eu.amadeos.apps.imageviewer.domain.MapTiles;
import eu.amadeos.apps.imageviewer.repository.TileStore;
import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.JButton;
import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * A small OpenStreetMap map with a marker at the photo's position: tiles from the {@link TileStore}, loaded in
 * the background as they come into view; drag to move, wheel or + and − to zoom. The attribution in the corner
 * links to the licence page.
 *
 * <p>The sidebar only creates this view once the user allowed maps, so nothing is fetched before.
 */
public final class MapView extends JComponent {
    public static final int ZOOM = 15;
    private static final String CREDIT = "© OpenStreetMap contributors";

    private final TileStore tiles;
    private final Executor background;
    private final Executor ui;
    private final Map<String, BufferedImage> loaded = new HashMap<>();
    private final Set<String> requested = new HashSet<>();
    private final JButton plus = zoomButton("+", 1);
    private final JButton minus = zoomButton("−", -1);
    private Runnable onCredit = () -> { };
    private GeoPosition position;
    private int zoom = ZOOM;
    /** The map point in the middle of the view, in tiles at the current zoom. */
    private double centreX, centreY;

    public MapView(TileStore tiles, Executor background, Executor ui) {
        this.tiles = tiles;
        this.background = background;
        this.ui = ui;
        setLayout(null);
        setPreferredSize(new Dimension(254, 170));
        setOpaque(true);
        add(plus);
        add(minus);
        MouseAdapter mouse = new MouseAdapter() {
            private Point from;

            @Override
            public void mousePressed(MouseEvent e) {
                if (creditBounds().contains(e.getPoint())) {
                    onCredit.run();
                    return;
                }
                from = e.getPoint();
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (from == null) return;
                centreX -= (e.getX() - from.x) / (double) MapTiles.SIZE;
                centreY -= (e.getY() - from.y) / (double) MapTiles.SIZE;
                from = e.getPoint();
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                from = null;
                setCursor(Cursor.getDefaultCursor());
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                setCursor(creditBounds().contains(e.getPoint()) ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (e.getWheelRotation() != 0) setZoom(zoom - Integer.signum(e.getWheelRotation()));
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    /** The link under the attribution. */
    public void onCredit(Runnable action) {
        onCredit = action;
    }

    public void show(GeoPosition newPosition) {
        if (newPosition.equals(position)) return;
        position = newPosition;
        zoom = ZOOM;
        centre();
        repaint();
    }

    public int zoom() {
        return zoom;
    }

    public void setZoom(int newZoom) {
        int z = Math.clamp(newZoom, MapTiles.MIN_ZOOM, MapTiles.MAX_ZOOM);
        if (z == zoom) return;
        // Keep the same place in the middle.
        double lon = MapTiles.longitude(centreX, zoom), lat = MapTiles.latitude(centreY, zoom);
        zoom = z;
        centreX = MapTiles.x(lon, zoom);
        centreY = MapTiles.y(lat, zoom);
        repaint();
    }

    private void centre() {
        centreX = MapTiles.x(position.longitude(), zoom);
        centreY = MapTiles.y(position.latitude(), zoom);
    }

    private JButton zoomButton(String text, int step) {
        JButton b = new JButton(text);
        b.setName(step > 0 ? "map.zoomIn" : "map.zoomOut");
        b.setFocusable(false);
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        b.addActionListener(e -> setZoom(zoom + step));
        return b;
    }

    @Override
    public void doLayout() {
        int size = 20;
        plus.setBounds(getWidth() - size - 4, 4, size, size);
        minus.setBounds(getWidth() - size - 4, 4 + size + 2, size, size);
    }

    // --8<-- [start:paint]
    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setColor(new Color(0xf2efe9));
        g.fillRect(0, 0, getWidth(), getHeight());
        if (position == null) {
            g.dispose();
            return;
        }
        double left = centreX - getWidth() / 2.0 / MapTiles.SIZE;
        double top = centreY - getHeight() / 2.0 / MapTiles.SIZE;
        int firstX = (int) Math.floor(left), firstY = (int) Math.floor(top);
        int lastX = (int) Math.floor(left + (double) getWidth() / MapTiles.SIZE);
        int lastY = (int) Math.floor(top + (double) getHeight() / MapTiles.SIZE);
        for (int ty = firstY; ty <= lastY; ty++) {
            for (int tx = firstX; tx <= lastX; tx++) {
                int px = (int) Math.round((tx - left) * MapTiles.SIZE);
                int py = (int) Math.round((ty - top) * MapTiles.SIZE);
                tile(zoom, tx, ty).ifPresent(image -> g.drawImage(image, px, py, null));
            }
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double mx = (MapTiles.x(position.longitude(), zoom) - left) * MapTiles.SIZE;
        double my = (MapTiles.y(position.latitude(), zoom) - top) * MapTiles.SIZE;
        paintMarker(g, mx, my);
        paintCredit(g);
        g.dispose();
    }
    // --8<-- [end:paint]

    /** The tile if it is loaded; else it is asked for once and the map repaints when it comes. */
    private Optional<BufferedImage> tile(int z, int x, int y) {
        if (!MapTiles.exists(z, x, y)) return Optional.empty();
        String key = z + "/" + x + "/" + y;
        BufferedImage image = loaded.get(key);
        if (image != null) return Optional.of(image);
        if (requested.add(key)) {
            background.execute(() -> {
                Optional<BufferedImage> fetched;
                try {
                    fetched = tiles.tile(z, x, y);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    fetched = Optional.empty();
                }
                Optional<BufferedImage> result = fetched;
                ui.execute(() -> {
                    // A tile that failed is not asked for again while the map shows, so a missing network
                    // causes no stream of requests.
                    result.ifPresent(i -> loaded.put(key, i));
                    repaint();
                });
            });
        }
        return Optional.empty();
    }

    private static void paintMarker(Graphics2D g, double x, double y) {
        Path2D pin = new Path2D.Double();
        pin.moveTo(x, y);
        pin.curveTo(x - 4, y - 8, x - 10, y - 12, x - 10, y - 19);
        pin.curveTo(x - 10, y - 26, x - 5, y - 30, x, y - 30);
        pin.curveTo(x + 5, y - 30, x + 10, y - 26, x + 10, y - 19);
        pin.curveTo(x + 10, y - 12, x + 4, y - 8, x, y);
        g.setColor(new Color(0xd23c3c));
        g.fill(pin);
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(new Color(0x7a1d1d));
        g.draw(pin);
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(x - 4, y - 23, 8, 8));
    }

    private Rectangle creditBounds() {
        FontMetrics fm = getFontMetrics(creditFont());
        int w = fm.stringWidth(CREDIT) + 6, h = fm.getHeight();
        return new Rectangle(getWidth() - w, getHeight() - h, w, h);
    }

    private Font creditFont() {
        Font base = getFont() == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 12) : getFont();
        return base.deriveFont(Math.max(9f, base.getSize2D() - 3));
    }

    private void paintCredit(Graphics2D g) {
        Rectangle r = creditBounds();
        g.setColor(new Color(255, 255, 255, 200));
        g.fill(r);
        g.setFont(creditFont());
        g.setColor(AmadeUI.theme().selectionBackground().darker());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.drawString(CREDIT, r.x + 3, r.y + g.getFontMetrics().getAscent());
    }
}
