package eu.amadeos.apps.imageviewer.metadata;

import eu.amadeos.apps.imageviewer.TestImages;
import eu.amadeos.apps.imageviewer.domain.Orientation;
import eu.amadeos.apps.imageviewer.domain.PhotoInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataReaderTest {
    @TempDir
    Path dir;

    private final MetadataReader reader = new MetadataReader();

    private static TestImages.Exif camera() {
        return new TestImages.Exif()
                .camera("Canon", "Canon EOS R6")
                .orientation(6)
                .description("OLYMPUS DIGITAL CAMERA")
                .artist("Anna")
                .copyright("CC BY 4.0")
                .taken("2026:08:14 20:47:05")
                .exposure(1, 250, 80, 100)
                .focalLength(35, 35)
                .flash(16)
                .lens("RF24-105mm F4 L IS USM")
                .gps(47.6603, 9.1754, 398);
    }

    @Test
    void readsCameraExposureAndPosition() throws Exception {
        Path file = TestImages.jpeg(dir.resolve("a.jpg"), TestImages.halves(40, 20), camera().build());
        PhotoInfo info = reader.read(file);
        assertEquals(Optional.of("Canon EOS R6"), info.camera());
        assertEquals(Optional.of(LocalDateTime.of(2026, 8, 14, 20, 47, 5)), info.taken());
        assertEquals(Optional.of("1/250"), info.exposureTime());
        assertEquals(Optional.of(8.0), info.fNumber());
        assertEquals(Optional.of(100), info.iso());
        assertEquals(Optional.of(35.0), info.focalLength());
        assertEquals(Optional.of(false), info.flash());
        assertEquals(Optional.of("RF24-105mm F4 L IS USM"), info.lens());
        assertEquals(Orientation.ROTATE_90, info.orientation());
        assertEquals(Optional.of("Anna"), info.author());
        assertEquals(Optional.of("CC BY 4.0"), info.copyright());
        assertEquals(47.6603, info.position().orElseThrow().latitude(), 1e-4);
        assertEquals(9.1754, info.position().orElseThrow().longitude(), 1e-4);
        assertEquals(Optional.of(398.0), info.position().orElseThrow().altitude());
        assertTrue(info.hasCamera());
        assertFalse(info.tags().isEmpty());
    }

    @Test
    void theCameraPlaceholderIsNoCaption() throws Exception {
        Path file = TestImages.jpeg(dir.resolve("a.jpg"), TestImages.halves(40, 20), camera().build());
        PhotoInfo info = reader.read(file);
        assertEquals(Optional.empty(), info.caption());
        assertEquals(Optional.empty(), info.title());
    }

    @Test
    void littleEndianExifAndSouthWest() throws Exception {
        byte[] exif = new TestImages.Exif().littleEndian().camera("NIKON CORPORATION", "D750").orientation(3)
                .exposure(2, 1, 56, 3200).gps(-33.8568, -70.6483, -12).build();
        PhotoInfo info = reader.read(TestImages.jpeg(dir.resolve("b.jpg"), TestImages.halves(20, 20), exif));
        assertEquals(Optional.of("NIKON CORPORATION D750"), info.camera());
        assertEquals(Orientation.ROTATE_180, info.orientation());
        assertEquals(Optional.of("2"), info.exposureTime());
        assertEquals(Optional.of(5.6), info.fNumber());
        assertEquals(-33.8568, info.position().orElseThrow().latitude(), 1e-4);
        assertEquals(-70.6483, info.position().orElseThrow().longitude(), 1e-4);
        assertEquals(Optional.of(-12.0), info.position().orElseThrow().altitude());
    }

    @Test
    void xmpComesBeforeExif() throws Exception {
        byte[] exif = new TestImages.Exif().description("A ferry").artist("Somebody").build();
        byte[] xmp = TestImages.xmp("Evening at the Lake", "Last ferry, seen from the harbour wall",
                List.of("lake", "sunset", "harbour"), "Ralf", "CC BY 4.0");
        PhotoInfo info = reader.read(TestImages.jpeg(dir.resolve("c.jpg"), TestImages.halves(20, 20), exif, xmp));
        assertEquals(Optional.of("Evening at the Lake"), info.title());
        assertEquals(Optional.of("Last ferry, seen from the harbour wall"), info.caption());
        assertEquals(List.of("lake", "sunset", "harbour"), info.keywords());
        assertEquals(Optional.of("Ralf"), info.author());
        assertEquals(Optional.of("CC BY 4.0"), info.copyright());
        assertTrue(info.hasDescription());
    }

    @Test
    void zeroPositionIsNoPosition() throws Exception {
        byte[] exif = new TestImages.Exif().gps(0, 0, null).build();
        PhotoInfo info = reader.read(TestImages.jpeg(dir.resolve("d.jpg"), TestImages.halves(20, 20), exif));
        assertEquals(Optional.empty(), info.position());
    }

    @Test
    void picturesWithoutMetadataAndBrokenFilesGiveNothing() throws Exception {
        PhotoInfo plain = reader.read(TestImages.png(dir.resolve("e.png"), TestImages.halves(20, 20)));
        assertFalse(plain.hasCamera());
        assertEquals(Orientation.NORMAL, plain.orientation());
        Path broken = Files.writeString(dir.resolve("f.jpg"), "not a picture");
        assertEquals(PhotoInfo.NONE, reader.read(broken));
        assertEquals(Orientation.NORMAL, reader.orientation(broken));
    }

    @Test
    void orientationAlone() throws Exception {
        Path file = TestImages.jpeg(dir.resolve("g.jpg"), TestImages.halves(20, 20),
                new TestImages.Exif().orientation(8).build());
        assertEquals(Orientation.ROTATE_270, reader.orientation(file));
    }
}
