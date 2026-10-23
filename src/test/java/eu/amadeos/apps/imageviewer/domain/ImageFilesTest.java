package eu.amadeos.apps.imageviewer.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageFilesTest {
    @TempDir
    Path dir;

    @Test
    void knowsPictureExtensionsInAnyCase() {
        assertTrue(ImageFiles.isImage(Path.of("a.JPG")));
        assertTrue(ImageFiles.isImage(Path.of("b.webp")));
        assertTrue(ImageFiles.isImage(Path.of("c.TIFF")));
        assertFalse(ImageFiles.isImage(Path.of("notes.txt")));
        assertFalse(ImageFiles.isImage(Path.of("jpg")));
    }

    @Test
    void turnsAreSavedIntoJpegPngAndBmpOnly() {
        assertTrue(ImageFiles.canSaveRotation(Path.of("a.jpeg")));
        assertTrue(ImageFiles.canSaveRotation(Path.of("a.png")));
        assertTrue(ImageFiles.canSaveRotation(Path.of("a.bmp")));
        assertFalse(ImageFiles.canSaveRotation(Path.of("a.gif")));
        assertFalse(ImageFiles.canSaveRotation(Path.of("a.webp")));
    }

    @Test
    void naturalOrderSortsNumbersByValue() {
        List<Path> files = new ArrayList<>(List.of(Path.of("IMG_10.jpg"), Path.of("img_9.jpg"), Path.of("IMG_100.jpg"),
                Path.of("b.jpg"), Path.of("IMG_009a.jpg"), Path.of("A.jpg")));
        files.sort(ImageFiles.NATURAL);
        assertEquals(List.of("A.jpg", "b.jpg", "img_9.jpg", "IMG_009a.jpg", "IMG_10.jpg", "IMG_100.jpg"),
                files.stream().map(Path::toString).toList());
    }

    @Test
    void folderListsPicturesWithoutHiddenFilesAndSubfolders() throws Exception {
        Files.createFile(dir.resolve("2.png"));
        Files.createFile(dir.resolve("10.jpg"));
        Files.createFile(dir.resolve(".hidden.jpg"));
        Files.createFile(dir.resolve("readme.txt"));
        Files.createDirectory(dir.resolve("sub.jpg"));
        assertEquals(List.of(dir.resolve("2.png"), dir.resolve("10.jpg")), ImageFiles.inFolder(dir));
    }
}
