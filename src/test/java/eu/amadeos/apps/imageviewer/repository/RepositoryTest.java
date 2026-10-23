package eu.amadeos.apps.imageviewer.repository;

import eu.amadeos.apps.imageviewer.TestImages;
import eu.amadeos.apps.imageviewer.domain.Settings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Settings file and map tile cache. */
class RepositoryTest {
    @TempDir
    Path dir;

    @Test
    void settingsSurviveARestart() {
        Path file = dir.resolve("imageviewer.properties");
        Settings changed = Settings.DEFAULT.withSidebar(true).withFilmStrip(false).withTitleFromMetadata(true)
                .withAlwaysShowMaps(true).withSlideSeconds(10).withShuffle(true).withLoop(false);
        new ViewerPreferences(file).setSettings(changed);
        assertEquals(changed, new ViewerPreferences(file).settings());
    }

    @Test
    void brokenSettingsFallBackToTheDefaults() throws Exception {
        Path file = Files.writeString(dir.resolve("imageviewer.properties"), "slideshow.seconds=abc\nsidebar=false\n");
        assertEquals(Settings.DEFAULT, new ViewerPreferences(file).settings());
        assertEquals(Settings.DEFAULT, new ViewerPreferences(dir.resolve("missing.properties")).settings());
    }

    private static byte[] png() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(TestImages.halves(256, 256), "png", out);
        return out.toByteArray();
    }

    // --8<-- [start:tile-test]
    @Test
    void tilesAreDownloadedOnceAWeek() throws Exception {
        List<URI> asked = new ArrayList<>();
        byte[] png = png();
        Instant now = Instant.parse("2026-10-23T12:00:00Z");
        TileStore store = new TileStore(dir, uri -> {
            asked.add(uri);
            return png;
        }, Clock.fixed(now, ZoneOffset.UTC));
        assertTrue(store.tile(15, 17219, 11436).isPresent());
        assertTrue(store.tile(15, 17219, 11436).isPresent());
        assertEquals(List.of(URI.create("https://tile.openstreetmap.org/15/17219/11436.png")), asked);
        assertTrue(Files.exists(dir.resolve("15/17219/11436.png")));

        // Eight days later it is asked for again.
        Files.setLastModifiedTime(dir.resolve("15/17219/11436.png"), FileTime.from(now.minus(Duration.ofDays(8))));
        store.tile(15, 17219, 11436);
        assertEquals(2, asked.size());
    }
    // --8<-- [end:tile-test]

    @Test
    void anOldTileIsBetterThanNoneWhenOffline() throws Exception {
        Instant now = Instant.parse("2026-10-23T12:00:00Z");
        Path tile = dir.resolve("10/1/2.png");
        Files.createDirectories(tile.getParent());
        Files.write(tile, png());
        Files.setLastModifiedTime(tile, FileTime.from(now.minus(Duration.ofDays(30))));
        TileStore offline = new TileStore(dir, uri -> {
            throw new IOException("no network");
        }, Clock.fixed(now, ZoneOffset.UTC));
        assertTrue(offline.tile(10, 1, 2).isPresent());
        assertTrue(offline.tile(10, 3, 3).isEmpty());
        assertTrue(offline.tile(30, 0, 0).isEmpty());
        assertTrue(offline.cached(10, 1, 2).isPresent());
    }
}
