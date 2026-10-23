package eu.amadeos.apps.imageviewer.bootstrap;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationTest {
    @Test
    void commandLinePathsAndUris() {
        assertEquals(List.of(Path.of("/home/anna/a b.jpg"), Path.of("pics")),
                AmadeImageViewerApplication.paths(new String[]{"file:///home/anna/a%20b.jpg", "--foo", "pics", " "}));
        assertEquals("dev", AmadeImageViewerApplication.version());
    }
}
