package eu.amadeos.apps.imageviewer.service;

import eu.amadeos.apps.imageviewer.domain.ImageFiles;
import eu.amadeos.apps.imageviewer.domain.Orientation;
import eu.amadeos.commons.process.CommandRunner;
import eu.amadeos.commons.process.ProgramMissingException;
import eu.amadeos.commons.process.ProgramResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes a turn into the file. JPEGs are turned by {@code jpegtran} (package libjpeg-turbo-progs), which moves
 * the compressed blocks instead of decoding and encoding again, so nothing is lost; afterwards the EXIF
 * orientation says "normal". PNG and BMP are lossless anyway and are written again by ImageIO.
 */
public class RotationSaver {
    private final CommandRunner runner;
    private final ImageLoader loader;

    public RotationSaver(CommandRunner runner, ImageLoader loader) {
        this.runner = runner;
        this.loader = loader;
    }

    /** jpegtran is not installed. */
    public static final class JpegtranMissingException extends IOException {
        public JpegtranMissingException() {
            super("jpegtran is not installed");
        }
    }

    /**
     * Turns the file so that it looks the way it is shown now.
     *
     * @param stored how the file is turned by its EXIF orientation
     * @param user   the user's rotations and flips on top of that
     * @return true if the picture lost a strip of up to 15 pixels at the right or bottom edge, because its size
     *         is not a multiple of the JPEG block size
     */
    public boolean save(Path file, Orientation stored, Orientation user) throws IOException, InterruptedException {
        Orientation total = stored.then(user);
        if (ImageFiles.isJpeg(file)) return saveJpeg(file, total);
        if (!ImageFiles.canSaveRotation(file)) throw new IOException("Cannot save turns into " + file.getFileName());
        BufferedImage turned = ImageTransforms.apply(loader.load(file), total);
        Path part = partFile(file);
        if (!ImageIO.write(turned, ImageFiles.extension(file), part.toFile())) {
            Files.deleteIfExists(part);
            throw new IOException("No writer for " + file.getFileName());
        }
        Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return false;
    }

    // --8<-- [start:jpeg]
    private boolean saveJpeg(Path file, Orientation total) throws IOException, InterruptedException {
        Path part = partFile(file);
        boolean trimmed = false;
        try {
            if (total != Orientation.NORMAL) {
                // -perfect refuses pictures whose edge blocks cannot be turned; then -trim drops those blocks.
                ProgramResult result = jpegtran(total, "-perfect", file, part);
                if (!result.succeeded()) {
                    result = jpegtran(total, "-trim", file, part);
                    if (!result.succeeded()) throw new IOException(result.failure("jpegtran"));
                    trimmed = true;
                }
            } else {
                Files.copy(file, part, StandardCopyOption.REPLACE_EXISTING);
            }
            byte[] jpeg = Files.readAllBytes(part);
            if (ExifOrientation.write(jpeg, 1)) Files.write(part, jpeg);
            Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return trimmed;
        } finally {
            Files.deleteIfExists(part);
        }
    }

    private ProgramResult jpegtran(Orientation turn, String edges, Path in, Path out)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("jpegtran", "-copy", "all", edges));
        command.addAll(turn.jpegtranOptions());
        command.addAll(List.of("-outfile", out.toString(), in.toString()));
        try {
            return runner.run(command, Duration.ofSeconds(60));
        } catch (ProgramMissingException e) {
            throw new JpegtranMissingException();
        }
    }
    // --8<-- [end:jpeg]

    private static Path partFile(Path file) {
        return file.resolveSibling("." + file.getFileName() + ".part");
    }
}
