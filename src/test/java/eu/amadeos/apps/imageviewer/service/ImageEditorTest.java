package eu.amadeos.apps.imageviewer.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ImageEditorTest {
    @TempDir
    Path dir;

    @Test
    void findsTheExecutableOnThePath() throws IOException {
        assumeTrue(File.pathSeparatorChar == ':', "POSIX permissions");
        Path empty = Files.createDirectories(dir.resolve("empty"));
        Path notExecutable = Files.createDirectories(dir.resolve("plain"));
        Files.writeString(notExecutable.resolve(ImageEditor.PROGRAM), "#!/bin/sh\n");
        Path bin = Files.createDirectories(dir.resolve("bin"));
        Path program = Files.writeString(bin.resolve(ImageEditor.PROGRAM), "#!/bin/sh\n");
        Files.setPosixFilePermissions(program, PosixFilePermissions.fromString("rwxr-xr-x"));

        assertEquals(Optional.of(program), ImageEditor.find(empty + ":" + notExecutable + "::" + bin));
        assertEquals(Optional.empty(), ImageEditor.find(empty + ":" + notExecutable));
        assertEquals(Optional.empty(), ImageEditor.find(null));
    }

    @Test
    void startsTheEditorWithTheAbsolutePath() throws IOException {
        List<List<String>> started = new ArrayList<>();
        ImageEditor editor = new ImageEditor(Optional.of(Path.of("/usr/bin/amade-imageeditor")), started::add);
        assertTrue(editor.available());
        editor.open(Path.of("lake.jpg"));
        assertEquals(List.of(List.of("/usr/bin/amade-imageeditor", Path.of("lake.jpg").toAbsolutePath().toString())),
                started);
    }

    @Test
    void aMissingEditorCannotOpen() {
        ImageEditor editor = new ImageEditor(Optional.empty(), command -> {
            throw new AssertionError("started");
        });
        assertFalse(editor.available());
        assertThrows(IOException.class, () -> editor.open(Path.of("lake.jpg")));
    }
}
