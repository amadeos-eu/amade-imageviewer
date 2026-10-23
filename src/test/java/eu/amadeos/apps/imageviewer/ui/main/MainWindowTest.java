package eu.amadeos.apps.imageviewer.ui.main;

import eu.amadeos.apps.imageviewer.OnEdt;
import eu.amadeos.apps.imageviewer.repository.TileStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.KeyStroke;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The real window with test pictures: sidebar, map question, film strip, status, full screen. Needs a display. */
class MainWindowTest {
    @TempDir
    Path dir;

    private Fixture fixture;
    private MainWindow window;
    private MainPresenter presenter;
    private final List<String> tilesAsked = new ArrayList<>();

    private void open(String file) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        fixture = new Fixture(dir).withPictures();
        TileStore tiles = new TileStore(dir.resolve("tiles"), uri -> {
            tilesAsked.add(uri.toString());
            throw new java.io.IOException("offline in tests");
        }, Clock.systemUTC());
        OnEdt.run(() -> {
            window = new MainWindow(Fixture.MSG, null,
                    new Thumbnails(new eu.amadeos.apps.imageviewer.service.ImageLoader(),
                            new eu.amadeos.apps.imageviewer.metadata.MetadataReader(), Runnable::run, Runnable::run),
                    () -> new MapView(tiles, Runnable::run, Runnable::run));
            presenter = fixture.presenter(window);
            window.bind(presenter);
            window.setVisible(true);
            presenter.start(List.of(fixture.file(file)));
        });
    }

    @AfterEach
    void close() throws Exception {
        if (window != null) OnEdt.run(() -> window.close());
    }

    private void key(int code, int modifiers) throws Exception {
        OnEdt.run(() -> {
            var root = window.fullScreenWindow().map(f -> f.getRootPane()).orElse(window.getRootPane());
            Object name = root.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .get(KeyStroke.getKeyStroke(code, modifiers));
            if (name == null) {
                // A menu accelerator.
                for (int i = 0; i < window.menu().getMenuCount(); i++) {
                    for (var c : window.menu().getMenu(i).getMenuComponents()) {
                        if (c instanceof javax.swing.JMenuItem item && KeyStroke.getKeyStroke(code, modifiers)
                                .equals(item.getAccelerator())) {
                            item.doClick(0);
                            return;
                        }
                    }
                }
                throw new AssertionError("No key " + KeyEvent.getKeyText(code));
            }
            root.getActionMap().get(name).actionPerformed(new ActionEvent(root, 0, ""));
        });
    }

    @Test
    void showsThePictureTitleAndStatus() throws Exception {
        open("b.jpg");
        OnEdt.run(() -> {
            assertEquals("b.jpg - Image Viewer", window.getTitle());
            assertTrue(window.statusText().startsWith("b.jpg   32 × 64   "), window.statusText());
            assertTrue(window.statusText().endsWith("2 of 3"), window.statusText());
            assertEquals(1, window.filmStrip().thumbnailList().getSelectedIndex());
            assertTrue(window.menu().item("menu.go.previous").isEnabled());
            assertFalse(window.menu().item("menu.image.save").isEnabled());
        });
    }

    @Test
    void sidebarAsksBeforeTheMapLoads() throws Exception {
        open("b.jpg");
        OnEdt.run(() -> presenter.setSidebar(true));
        OnEdt.run(() -> {
            assertTrue(window.sidebar().isVisible());
            List<String> texts = window.sidebar().texts();
            assertTrue(texts.contains("Evening at the Lake"), texts.toString());
            assertTrue(texts.contains("Canon EOS R6"), texts.toString());
            assertTrue(texts.contains("info.map.show"), texts.toString());
            assertFalse(texts.contains("map"));
        });
        assertEquals(List.of(), tilesAsked);
        OnEdt.run(() -> presenter.allowMap());
        OnEdt.run(() -> {
            assertTrue(window.sidebar().texts().contains("map"));
            window.sidebar().map().orElseThrow().paintImmediately(0, 0, 254, 170);
        });
        assertFalse(tilesAsked.isEmpty());
        assertTrue(tilesAsked.getFirst().startsWith("https://tile.openstreetmap.org/15/"), tilesAsked.getFirst());
    }

    @Test
    void picturesWithoutPositionHaveNoLocation() throws Exception {
        open("a.png");
        OnEdt.run(() -> presenter.setSidebar(true));
        OnEdt.run(() -> {
            List<String> texts = window.sidebar().texts();
            assertFalse(texts.contains("Location"), texts.toString());
            assertTrue(texts.contains("info.all"));
        });
    }

    @Test
    void keysPageAndTurn() throws Exception {
        open("a.png");
        key(KeyEvent.VK_RIGHT, 0);
        OnEdt.run(() -> assertTrue(window.getTitle().startsWith("b.jpg")));
        key(KeyEvent.VK_SPACE, 0);
        OnEdt.run(() -> assertTrue(window.getTitle().startsWith("c.png")));
        key(KeyEvent.VK_BACK_SPACE, 0);
        key(KeyEvent.VK_HOME, 0);
        key(KeyEvent.VK_R, java.awt.event.InputEvent.CTRL_DOWN_MASK);
        OnEdt.run(() -> {
            assertTrue(window.statusText().startsWith("a.png   20 × 40"), window.statusText());
            assertTrue(window.menu().item("menu.image.save").isEnabled());
        });
    }

    @Test
    void zoomChangesTheScaleInTheStatusBar() throws Exception {
        open("a.png");
        OnEdt.run(() -> presenter.zoom(ZoomCommand.ACTUAL));
        OnEdt.run(() -> presenter.zoom(ZoomCommand.IN));
        OnEdt.run(() -> {
            assertEquals(1.5, window.canvas().scale(), 1e-9);
            assertTrue(window.statusText().contains("150 %"), window.statusText());
            assertFalse(window.canvas().fitted());
        });
        OnEdt.run(() -> presenter.zoom(ZoomCommand.FIT));
        OnEdt.run(() -> assertTrue(window.canvas().fitted()));
    }

    // --8<-- [start:full-screen-test]
    @Test
    void slideshowInFullScreenWithPause() throws Exception {
        open("a.png");
        key(KeyEvent.VK_F5, 0);
        OnEdt.run(() -> {
            FullScreenWindow full = window.fullScreenWindow().orElseThrow();
            assertTrue(full.isVisible());
            assertTrue(full.controlsShown());
            assertEquals(5, full.secondsBox().getSelectedItem());
        });
        key(KeyEvent.VK_SPACE, 0);
        OnEdt.run(() -> assertTrue(window.fullScreenWindow().orElseThrow().pauseShown()));
        key(KeyEvent.VK_RIGHT, 0);
        OnEdt.run(() -> assertTrue(window.getTitle().startsWith("b.jpg")));
        key(KeyEvent.VK_ESCAPE, 0);
        OnEdt.run(() -> {
            assertTrue(window.fullScreenWindow().isEmpty());
            assertFalse(presenter.slideshowRunning());
        });
    }
    // --8<-- [end:full-screen-test]

    @Test
    void intervalFromTheControlBar() throws Exception {
        open("a.png");
        key(KeyEvent.VK_F11, 0);
        OnEdt.run(() -> window.fullScreenWindow().orElseThrow().secondsBox().setSelectedItem(10));
        assertEquals(10, fixture.preferences.settings().slideSeconds());
        key(KeyEvent.VK_F11, 0);
        OnEdt.run(() -> assertTrue(window.fullScreenWindow().isEmpty()));
    }

    @Test
    void filmStripClickShowsThePicture() throws Exception {
        open("a.png");
        OnEdt.run(() -> window.filmStrip().thumbnailList().setSelectedIndex(2));
        OnEdt.run(() -> assertTrue(window.getTitle().startsWith("c.png")));
    }

    @Test
    void sidebarAndFilmStripFollowTheSettings() throws Exception {
        open("a.png");
        key(KeyEvent.VK_F9, 0);
        OnEdt.run(() -> presenter.setFilmStrip(false));
        OnEdt.run(() -> {
            assertTrue(window.sidebar().isVisible());
            assertFalse(window.filmStrip().isVisible());
            assertTrue(window.toolAction("view.sidebar") != null);
        });
    }
}
