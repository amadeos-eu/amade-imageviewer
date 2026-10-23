package eu.amadeos.apps.imageviewer.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Moves files into the trash of the freedesktop.org trash specification ({@code ~/.local/share/Trash}), where
 * the file manager can restore them: the file goes to {@code files/}, a {@code .trashinfo} with its old path and
 * the time goes to {@code info/}.
 */
public class Trash {
    private final Path directory;
    private final Clock clock;

    /** @param directory the trash, normally {@code $XDG_DATA_HOME/Trash} */
    public Trash(Path directory, Clock clock) {
        this.directory = directory;
        this.clock = clock;
    }

    // --8<-- [start:move]
    /** Moves the file to the trash; returns where it went. */
    public Path move(Path file) throws IOException {
        Path files = directory.resolve("files");
        Path info = directory.resolve("info");
        Files.createDirectories(files);
        Files.createDirectories(info);
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int n = 1; ; n++) {
            String candidate = n == 1 ? name : stem + "." + n + ext;
            Path infoFile = info.resolve(candidate + ".trashinfo");
            try {
                // Creating the info file first reserves the name, as the specification asks.
                Files.writeString(infoFile, trashInfo(file), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
            } catch (FileAlreadyExistsException e) {
                continue;
            }
            Path target = files.resolve(candidate);
            try {
                Files.move(file, target);
                return target;
            } catch (IOException e) {
                Files.deleteIfExists(infoFile);
                throw e;
            }
        }
    }
    // --8<-- [end:move]

    String trashInfo(Path file) {
        // The path is URI-escaped, without the scheme: /home/anna/My%20Pictures/a.jpg
        String path = file.toAbsolutePath().toUri().getRawPath();
        String date = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return "[Trash Info]\nPath=" + path + "\nDeletionDate=" + date + "\n";
    }
}
