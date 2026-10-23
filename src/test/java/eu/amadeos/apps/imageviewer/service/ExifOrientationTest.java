package eu.amadeos.apps.imageviewer.service;

import eu.amadeos.apps.imageviewer.TestImages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExifOrientationTest {
    @TempDir
    Path dir;

    @Test
    void readsAndWritesBigEndian() throws Exception {
        byte[] jpeg = Files.readAllBytes(TestImages.jpeg(dir.resolve("a.jpg"), TestImages.halves(16, 16),
                new TestImages.Exif().camera("Canon", "EOS").orientation(6).build()));
        assertEquals(Optional.of(6), ExifOrientation.read(jpeg));
        assertTrue(ExifOrientation.write(jpeg, 1));
        assertEquals(Optional.of(1), ExifOrientation.read(jpeg));
    }

    @Test
    void readsAndWritesLittleEndian() throws Exception {
        byte[] jpeg = Files.readAllBytes(TestImages.jpeg(dir.resolve("b.jpg"), TestImages.halves(16, 16),
                new TestImages.Exif().littleEndian().orientation(8).build()));
        assertEquals(Optional.of(8), ExifOrientation.read(jpeg));
        assertTrue(ExifOrientation.write(jpeg, 3));
        assertEquals(Optional.of(3), ExifOrientation.read(jpeg));
    }

    @Test
    void picturesWithoutTheEntryStayUntouched() throws Exception {
        byte[] plain = TestImages.jpegBytes(TestImages.halves(16, 16));
        byte[] copy = plain.clone();
        assertEquals(Optional.empty(), ExifOrientation.read(plain));
        assertFalse(ExifOrientation.write(plain, 1));
        assertEquals(java.util.Arrays.toString(copy), java.util.Arrays.toString(plain));
        assertEquals(Optional.empty(), ExifOrientation.read(new byte[]{1, 2, 3}));
    }
}
