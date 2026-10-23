package eu.amadeos.apps.imageviewer.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlbumTest {
    @TempDir
    Path dir;

    private Path file(String name) throws Exception {
        return Files.createFile(dir.resolve(name));
    }

    @Test
    void oneFileOpensItsFolderAtThatFile() throws Exception {
        file("a.jpg");
        Path b = file("b.jpg");
        file("c.png");
        Album album = Album.open(List.of(b));
        assertEquals(3, album.size());
        assertEquals(1, album.index());
        assertEquals(Optional.of(b), album.current());
    }

    @Test
    void aFolderOpensAtItsFirstPicture() throws Exception {
        file("b.jpg");
        file("a.jpg");
        Album album = Album.open(List.of(dir));
        assertEquals(dir.resolve("a.jpg"), album.current().orElseThrow());
    }

    @Test
    void aFileWithAnUnknownExtensionIsStillShown() throws Exception {
        file("a.jpg");
        Path odd = file("scan.dat");
        Album album = Album.open(List.of(odd));
        assertEquals(2, album.size());
        assertEquals(Optional.of(odd), album.current());
    }

    @Test
    void severalFilesAreTheAlbum() throws Exception {
        Path a = file("a.jpg");
        file("b.jpg");
        Path c = file("c.jpg");
        Album album = Album.open(List.of(c, a));
        assertEquals(List.of(c, a), album.files());
    }

    @Test
    void pagingStopsAtTheEnds() {
        Album album = new Album(List.of(Path.of("a"), Path.of("b")), 0);
        assertFalse(album.previous());
        assertTrue(album.next());
        assertFalse(album.next());
        assertTrue(album.first());
        assertTrue(album.last());
        assertEquals(1, album.index());
        assertEquals(Optional.of(Path.of("a")), album.peek(-1));
        assertEquals(Optional.empty(), album.peek(1));
    }

    @Test
    void removingTheLastShowsTheOneBefore() {
        Album album = new Album(List.of(Path.of("a"), Path.of("b"), Path.of("c")), 2);
        album.removeCurrent();
        assertEquals(Optional.of(Path.of("b")), album.current());
        album.first();
        album.removeCurrent();
        assertEquals(Optional.of(Path.of("b")), album.current());
        album.removeCurrent();
        assertTrue(album.isEmpty());
        assertEquals(Optional.empty(), album.current());
    }
}
