package eu.amadeos.apps.imageviewer.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Opens a picture in amade-imageeditor, the amadeOS image editor, if it is installed. */
public class ImageEditor {
    public static final String PROGRAM = "amade-imageeditor";

    /** Starts a program without waiting for it. */
    public interface Starter {
        void start(List<String> command) throws IOException;
    }

    private final Optional<Path> program;
    private final Starter starter;

    public ImageEditor(Optional<Path> program, Starter starter) {
        this.program = program;
        this.starter = starter;
    }

    /** The editor found on the PATH, started as a process of its own whose output is not read. */
    public static ImageEditor system() {
        return new ImageEditor(find(System.getenv("PATH")), command -> new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start());
    }

    /** The executable amade-imageeditor in one of the folders of a PATH value. */
    static Optional<Path> find(String pathVariable) {
        if (pathVariable == null) return Optional.empty();
        for (String folder : pathVariable.split(File.pathSeparator)) {
            if (folder.isBlank()) continue;
            Path candidate = Path.of(folder, PROGRAM);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    public boolean available() {
        return program.isPresent();
    }

    /** @throws IOException if the editor is missing or cannot be started */
    public void open(Path file) throws IOException {
        Path editor = program.orElseThrow(() -> new IOException(PROGRAM + " is not installed"));
        starter.start(List.of(editor.toString(), file.toAbsolutePath().toString()));
    }
}
