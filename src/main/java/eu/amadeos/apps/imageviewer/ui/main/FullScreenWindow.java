package eu.amadeos.apps.imageviewer.ui.main;

import eu.amadeos.apps.imageviewer.domain.Settings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.swing.StandardGlyph;
import eu.amadeos.commons.swing.ToolButtons;
import eu.amadeos.commons.swing.ToolIcon;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * The picture on the whole screen, without frame and menus. A mouse move brings up a small control bar at the
 * bottom (previous, slideshow, next, interval, leave); after two seconds without a move it disappears again,
 * together with the mouse pointer.
 */
public final class FullScreenWindow extends JFrame {
    /** What the control bar does. */
    public interface Controls {
        void previous();

        void next();

        void slideshow();

        void leave();

        void setSlideSeconds(int seconds);
    }

    private static final int HIDE_AFTER = 2000;

    private final ImageCanvas canvas = new ImageCanvas();
    private final JPanel bar = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 4));
    private final JLabel pause;
    private final JButton play;
    private final JComboBox<Integer> seconds = new JComboBox<>(Settings.SLIDE_SECONDS.toArray(Integer[]::new));
    private final Timer hide = new Timer(HIDE_AFTER, e -> hideControls());
    private final Timer pauseSign = new Timer(1500, e -> setPauseSign(false));
    private final Cursor blank = blankCursor();
    private final Messages msg;
    private GraphicsDevice device;
    private boolean updating;

    public FullScreenWindow(Messages msg, Controls controls) {
        super(msg.get("window.title"));
        this.msg = msg;
        setName("fullScreen");
        setUndecorated(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        hide.setRepeats(false);
        pauseSign.setRepeats(false);
        JLayeredPane layers = getLayeredPane();
        getContentPane().setBackground(Color.BLACK);
        canvas.setBackground(Color.BLACK);
        layers.add(canvas, JLayeredPane.DEFAULT_LAYER);

        bar.setName("fullScreen.controls");
        bar.setBorder(BorderFactory.createCompoundBorder(AmadeUI.raised(), BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        bar.add(ToolButtons.button(action("go.previous", ViewerGlyph.PREVIOUS, controls::previous)));
        play = ToolButtons.button(action("view.slideshow", ViewerGlyph.PLAY, controls::slideshow));
        bar.add(play);
        bar.add(ToolButtons.button(action("go.next", ViewerGlyph.NEXT, controls::next)));
        seconds.setName("fullScreen.seconds");
        seconds.setFocusable(false);
        seconds.setToolTipText(msg.get("menu.options.slideshow"));
        seconds.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                return super.getListCellRendererComponent(list, msg.format("slideshow.seconds", value), index,
                        selected, focus);
            }
        });
        seconds.addActionListener(e -> {
            if (!updating && seconds.getSelectedItem() instanceof Integer s) controls.setSlideSeconds(s);
        });
        bar.add(seconds);
        bar.add(ToolButtons.button(action("view.full-screen.leave", StandardGlyph.LEAVE_FULL_SCREEN, controls::leave)));
        bar.setVisible(false);
        layers.add(bar, JLayeredPane.PALETTE_LAYER);

        pause = new JLabel(msg.get("slideshow.paused"), SwingConstants.CENTER);
        pause.setName("fullScreen.paused");
        pause.setOpaque(true);
        pause.setBackground(new Color(0, 0, 0, 170));
        pause.setForeground(Color.WHITE);
        pause.setFont(pause.getFont().deriveFont(Font.BOLD, AmadeUI.scaled(22)));
        pause.setBorder(BorderFactory.createEmptyBorder(10, 24, 10, 24));
        pause.setVisible(false);
        layers.add(pause, JLayeredPane.POPUP_LAYER);

        layers.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                place();
            }
        });
        MouseAdapter moves = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                showControls();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                showControls();
            }
        };
        canvas.addMouseMotionListener(moves);
        bar.addMouseMotionListener(moves);
    }

    private Action action(String key, ToolIcon.Glyph glyph, Runnable run) {
        AbstractAction action = new AbstractAction(msg.get("tool." + key)) {
            @Override
            public void actionPerformed(ActionEvent e) {
                run.run();
                showControls();
            }
        };
        action.putValue(Action.ACTION_COMMAND_KEY, "fullScreen." + key);
        action.putValue(Action.SHORT_DESCRIPTION, msg.get("tool." + key));
        action.putValue(Action.LARGE_ICON_KEY, new ToolIcon(glyph));
        return action;
    }

    public ImageCanvas canvas() {
        return canvas;
    }

    // --8<-- [start:open]
    /** Covers the screen the main window is on. */
    public void open(GraphicsConfiguration screen) {
        device = screen.getDevice();
        Rectangle bounds = screen.getBounds();
        setBounds(bounds);
        if (device.isFullScreenSupported()) {
            // Only the window takes the screen; the display mode stays as it is.
            device.setFullScreenWindow(this);
        } else {
            setAlwaysOnTop(true);
            setVisible(true);
        }
        place();
        canvas.requestFocusInWindow();
        showControls();
    }

    public void close() {
        hide.stop();
        if (device != null && device.getFullScreenWindow() == this) device.setFullScreenWindow(null);
        setVisible(false);
        dispose();
    }
    // --8<-- [end:open]

    public void showSlideshow(boolean running, boolean paused, int interval) {
        updating = true;
        try {
            seconds.setSelectedItem(interval);
        } finally {
            updating = false;
        }
        boolean playing = running && !paused;
        play.setIcon(new ToolIcon(playing ? ViewerGlyph.PAUSE : ViewerGlyph.PLAY));
        String tip = msg.get(playing ? "tool.slideshow.pause" : "tool.view.slideshow");
        play.setToolTipText(tip);
        play.getAccessibleContext().setAccessibleName(tip);
        setPauseSign(running && paused);
        if (running && paused) pauseSign.restart();
    }

    private void setPauseSign(boolean visible) {
        pause.setVisible(visible);
        place();
    }

    public boolean controlsShown() {
        return bar.isVisible();
    }

    public boolean pauseShown() {
        return pause.isVisible();
    }

    void showControls() {
        bar.setVisible(true);
        canvas.setCursor(Cursor.getDefaultCursor());
        place();
        hide.restart();
    }

    private void hideControls() {
        Point mouse = bar.getMousePosition();
        if (mouse != null) {
            // The pointer rests on the bar: keep it.
            hide.restart();
            return;
        }
        bar.setVisible(false);
        canvas.setCursor(blank);
    }

    private void place() {
        Dimension size = getLayeredPane().getSize();
        canvas.setBounds(0, 0, size.width, size.height);
        Dimension b = bar.getPreferredSize();
        bar.setBounds((size.width - b.width) / 2, size.height - b.height - 24, b.width, b.height);
        Dimension p = pause.getPreferredSize();
        pause.setBounds((size.width - p.width) / 2, (size.height - p.height) / 2, p.width, p.height);
        getLayeredPane().revalidate();
        getLayeredPane().repaint();
    }

    private static Cursor blankCursor() {
        if (java.awt.GraphicsEnvironment.isHeadless()) return Cursor.getDefaultCursor();
        BufferedImage empty = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        return Toolkit.getDefaultToolkit().createCustomCursor(empty, new Point(0, 0), "blank");
    }

    /** For tests: the control bar's slideshow seconds. */
    JComboBox<Integer> secondsBox() {
        return seconds;
    }
}
