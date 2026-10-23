package eu.amadeos.apps.imageviewer.ui.main;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static eu.amadeos.apps.imageviewer.ui.main.Fixture.edt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F5, Space, Esc and the arrow keys in a slideshow. */
class SlideshowPresenterTest {
    @TempDir
    Path dir;

    private Fixture fixture;
    private FakeView view;
    private MainPresenter presenter;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new Fixture(dir).withPictures();
        view = new FakeView();
        presenter = fixture.presenter(view);
        edt(() -> presenter.start(List.of(fixture.file("a.png"))));
    }

    @Test
    void startsInFullScreenAndTicks() throws Exception {
        edt(presenter::slideshow);
        assertEquals(Boolean.TRUE, view.fullScreen);
        assertTrue(view.slideshowRunning);
        assertEquals(5, fixture.ticker.seconds);
        edt(fixture.ticker::tick);
        assertEquals("b.jpg", view.fileName());
    }

    // --8<-- [start:space-test]
    @Test
    void spacePausesAndGoesOn() throws Exception {
        edt(presenter::slideshow);
        edt(presenter::space);
        assertTrue(view.slideshowPaused);
        assertFalse(fixture.ticker.running());
        assertEquals("a.png", view.fileName());
        edt(presenter::space);
        assertFalse(view.slideshowPaused);
        assertTrue(fixture.ticker.running());
    }
    // --8<-- [end:space-test]

    @Test
    void f5InARunningSlideshowPausesToo() throws Exception {
        edt(presenter::slideshow);
        edt(presenter::slideshow);
        assertTrue(view.slideshowPaused);
    }

    @Test
    void arrowsJumpAndTheClockStartsAgain() throws Exception {
        edt(presenter::slideshow);
        fixture.ticker.seconds = null;
        edt(presenter::last);
        assertEquals("c.png", view.fileName());
        assertEquals(5, fixture.ticker.seconds);
        // With the default loop it starts again at the first picture.
        edt(fixture.ticker::tick);
        assertEquals("a.png", view.fileName());
    }

    @Test
    void withoutLoopItEndsAndLeavesFullScreen() throws Exception {
        edt(() -> presenter.setLoop(false));
        edt(presenter::slideshow);
        edt(fixture.ticker::tick);
        edt(fixture.ticker::tick);
        assertEquals("c.png", view.fileName());
        edt(fixture.ticker::tick);
        assertFalse(view.slideshowRunning);
        assertEquals(Boolean.FALSE, view.fullScreen);
    }

    @Test
    void escapeEndsSlideshowAndFullScreen() throws Exception {
        edt(presenter::slideshow);
        edt(presenter::escape);
        assertFalse(view.slideshowRunning);
        assertFalse(fixture.ticker.running());
        assertEquals(Boolean.FALSE, view.fullScreen);
        // Space pages again.
        edt(presenter::space);
        assertEquals("b.jpg", view.fileName());
    }

    @Test
    void aNewIntervalAppliesAtOnce() throws Exception {
        edt(presenter::slideshow);
        edt(() -> presenter.setSlideSeconds(3));
        assertEquals(3, fixture.ticker.seconds);
    }

    @Test
    void oneSlideIsNoShow() throws Exception {
        Path single = java.nio.file.Files.createDirectories(dir.resolve("one"));
        eu.amadeos.apps.imageviewer.TestImages.png(single.resolve("x.png"),
                eu.amadeos.apps.imageviewer.TestImages.halves(10, 10));
        edt(() -> presenter.open(List.of(single.resolve("x.png"))));
        edt(presenter::slideshow);
        assertEquals(1, view.beeps);
        assertFalse(view.slideshowRunning);
    }
}
