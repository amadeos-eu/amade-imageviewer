package eu.amadeos.apps.imageviewer.ui.main;

import eu.amadeos.apps.imageviewer.OnEdt;
import eu.amadeos.apps.imageviewer.metadata.MetadataReader;
import eu.amadeos.apps.imageviewer.service.ImageLoader;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The film strip's layout; works without a display. */
class FilmStripTest {
    private static FilmStrip stripWith(int pictures, int width) {
        FilmStrip strip = new FilmStrip(new Thumbnails(new ImageLoader(), new MetadataReader(),
                r -> { }, Runnable::run));
        List<Path> files = new ArrayList<>();
        for (int i = 0; i < pictures; i++) files.add(Path.of("/pictures/p" + i + ".png"));
        strip.show(files, 0);
        strip.setSize(width, strip.getPreferredSize().height);
        strip.doLayout();
        strip.scrollPane().doLayout();
        return strip;
    }

    @Test
    void theScrollbarSitsBelowTheThumbnails() throws Exception {
        OnEdt.run(() -> {
            FilmStrip strip = stripWith(47, 600);
            var scroll = strip.scrollPane();
            assertTrue(scroll.getHorizontalScrollBar().isVisible(), "47 pictures need a scrollbar");
            Rectangle viewport = scroll.getViewport().getBounds();
            Rectangle cell = strip.thumbnailList().getCellBounds(0, 0);
            assertTrue(viewport.height >= cell.height,
                    "viewport " + viewport.height + " px is lower than a cell of " + cell.height + " px");
            assertTrue(scroll.getHorizontalScrollBar().getY() >= viewport.y + viewport.height,
                    "the scrollbar overlaps the thumbnails");
        });
    }

    @Test
    void cellsAreHighEnoughForSquareThumbnails() throws Exception {
        OnEdt.run(() -> {
            Rectangle cell = stripWith(3, 600).thumbnailList().getCellBounds(0, 0);
            assertTrue(cell.height >= Thumbnails.SIZE, "cell " + cell.height + " px");
        });
    }
}
