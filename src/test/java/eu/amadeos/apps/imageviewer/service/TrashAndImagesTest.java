package eu.amadeos.apps.imageviewer.service;

import eu.amadeos.apps.imageviewer.TestImages;
import eu.amadeos.apps.imageviewer.domain.Orientation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The trash, decoding and turning pictures. */
class TrashAndImagesTest {
    @TempDir
    Path dir;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-23T12:30:00Z"), ZoneOffset.UTC);

    @Test
    void trashKeepsWhereTheFileCameFrom() throws Exception {
        Path folder = Files.createDirectories(dir.resolve("My Pictures"));
        Path file = Files.writeString(folder.resolve("a.jpg"), "x");
        Trash trash = new Trash(dir.resolve("Trash"), clock);
        Path moved = trash.move(file);
        assertFalse(Files.exists(file));
        assertEquals(dir.resolve("Trash/files/a.jpg"), moved);
        String info = Files.readString(dir.resolve("Trash/info/a.jpg.trashinfo"));
        assertTrue(info.startsWith("[Trash Info]\nPath=/"), info);
        assertTrue(info.contains("/My%20Pictures/a.jpg\n"), info);
        assertTrue(info.contains("DeletionDate=2026-10-23T12:30:00\n"), info);
    }

    @Test
    void sameNamesInTheTrashGetANumber() throws Exception {
        Trash trash = new Trash(dir.resolve("Trash"), clock);
        trash.move(Files.writeString(dir.resolve("a.jpg"), "1"));
        Path second = trash.move(Files.writeString(dir.resolve("a.jpg"), "2"));
        assertEquals("a.2.jpg", second.getFileName().toString());
        assertTrue(Files.exists(dir.resolve("Trash/info/a.2.jpg.trashinfo")));
    }

    @Test
    void aFailedMoveLeavesNoInfoFile() throws Exception {
        Trash trash = new Trash(dir.resolve("Trash"), clock);
        assertThrows(java.io.IOException.class, () -> trash.move(dir.resolve("missing.jpg")));
        assertFalse(Files.exists(dir.resolve("Trash/info/missing.jpg.trashinfo")));
    }

    @Test
    void affineTransformMapsCornersIntoTheTurnedRectangle() {
        // 40 × 20 turned right is 20 × 40: the top-left corner goes to the top right.
        var at = ImageTransforms.affine(Orientation.ROTATE_90, 40, 20);
        assertEquals(new Point2D.Double(20, 0), at.transform(new Point2D.Double(0, 0), null));
        assertEquals(new Point2D.Double(0, 40), at.transform(new Point2D.Double(40, 20), null));
        var mirror = ImageTransforms.affine(Orientation.MIRROR, 40, 20);
        assertEquals(new Point2D.Double(40, 0), mirror.transform(new Point2D.Double(0, 0), null));
    }

    @Test
    void applyTurnsThePixels() {
        BufferedImage turned = ImageTransforms.apply(TestImages.halves(40, 20), Orientation.ROTATE_270);
        assertEquals(20, turned.getWidth());
        assertEquals(40, turned.getHeight());
        // Turned left, the left half (red) goes to the bottom.
        assertEquals(0xFF0000, turned.getRGB(10, 35) & 0xFFFFFF);
        assertEquals(0x0000FF, turned.getRGB(10, 5) & 0xFFFFFF);
    }

    @Test
    void loaderReadsAndMakesThumbnails() throws Exception {
        Path file = TestImages.png(dir.resolve("big.png"), TestImages.halves(800, 400));
        ImageLoader loader = new ImageLoader();
        assertEquals(800, loader.load(file).getWidth());
        BufferedImage thumb = loader.thumbnail(file, 88, Orientation.ROTATE_90);
        assertEquals(44, thumb.getWidth());
        assertEquals(88, thumb.getHeight());
        Path text = Files.writeString(dir.resolve("text.png"), "hello");
        assertThrows(ImageLoader.UnsupportedFormatException.class, () -> loader.load(text));
    }

    @Test
    void scaleToFitKeepsSmallPictures() {
        assertEquals(30, ImageTransforms.scaleToFit(TestImages.halves(30, 10), 88).getWidth());
        assertEquals(88, ImageTransforms.scaleToFit(TestImages.halves(300, 100), 88).getWidth());
    }
}
