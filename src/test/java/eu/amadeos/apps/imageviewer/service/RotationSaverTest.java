package eu.amadeos.apps.imageviewer.service;

import eu.amadeos.apps.imageviewer.TestImages;
import eu.amadeos.apps.imageviewer.domain.Orientation;
import eu.amadeos.commons.process.CommandRunner;
import eu.amadeos.commons.process.ProgramMissingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RotationSaverTest {
    @TempDir
    Path dir;

    private final ImageLoader loader = new ImageLoader();

    /** The tests that run the real jpegtran skip themselves where it is not installed (libjpeg-turbo-progs). */
    private static void assumeJpegtran() {
        boolean installed = Stream.of(System.getenv().getOrDefault("PATH", "").split(File.pathSeparator))
                .anyMatch(dir -> !dir.isEmpty() && Files.isExecutable(Path.of(dir, "jpegtran")));
        assumeTrue(installed, "jpegtran is not installed: sudo apt install libjpeg-turbo-progs");
    }

    private static boolean red(int rgb) {
        return (rgb >> 16 & 0xff) > 200 && (rgb & 0xff) < 80;
    }

    private static boolean blue(int rgb) {
        return (rgb & 0xff) > 200 && (rgb >> 16 & 0xff) < 80;
    }

    @Test
    void pngIsWrittenTurned() throws Exception {
        Path file = TestImages.png(dir.resolve("a.png"), TestImages.halves(40, 20));
        new RotationSaver(CommandRunner.system(), loader).save(file, Orientation.NORMAL, Orientation.ROTATE_90);
        BufferedImage turned = ImageIO.read(file.toFile());
        assertEquals(20, turned.getWidth());
        assertEquals(40, turned.getHeight());
        // Left (red) went to the top.
        assertTrue(red(turned.getRGB(10, 5)));
        assertTrue(blue(turned.getRGB(10, 35)));
        assertFalse(Files.exists(dir.resolve(".a.png.part")));
    }

    // --8<-- [start:jpegtran-test]
    @Test
    void jpegIsTurnedByJpegtranAndItsOrientationReset() throws Exception {
        assumeJpegtran();
        Path file = TestImages.jpeg(dir.resolve("b.jpg"), TestImages.halves(64, 32),
                new TestImages.Exif().camera("Canon", "EOS").orientation(6).build());
        boolean trimmed = new RotationSaver(CommandRunner.system(), loader)
                .save(file, Orientation.ROTATE_90, Orientation.ROTATE_90);
        assertFalse(trimmed);
        byte[] bytes = Files.readAllBytes(file);
        assertEquals(Optional.of(1), ExifOrientation.read(bytes));
        BufferedImage turned = ImageIO.read(file.toFile());
        // EXIF 6 and a turn right are a half turn: blue is now left.
        assertEquals(64, turned.getWidth());
        assertTrue(blue(turned.getRGB(8, 16)));
        assertTrue(red(turned.getRGB(56, 16)));
    }
    // --8<-- [end:jpegtran-test]

    @Test
    void sizesThatAreNoBlockMultipleAreTrimmed() throws Exception {
        assumeJpegtran();
        Path file = TestImages.jpeg(dir.resolve("c.jpg"), TestImages.halves(70, 30));
        boolean trimmed = new RotationSaver(CommandRunner.system(), loader)
                .save(file, Orientation.NORMAL, Orientation.ROTATE_90);
        assertTrue(trimmed);
        BufferedImage turned = ImageIO.read(file.toFile());
        assertTrue(turned.getWidth() <= 30 && turned.getHeight() <= 70);
    }

    @Test
    void theCommandAsksForAPerfectTurnFirst() throws Exception {
        List<List<String>> commands = new ArrayList<>();
        CommandRunner fake = command -> {
            commands.add(command);
            Files.copy(Path.of(command.get(command.size() - 1)), Path.of(command.get(command.size() - 2)));
            return new ProcessBuilder("true").start();
        };
        Path file = TestImages.jpeg(dir.resolve("d.jpg"), TestImages.halves(16, 16));
        new RotationSaver(fake, loader).save(file, Orientation.NORMAL, Orientation.MIRROR);
        assertEquals(List.of("jpegtran", "-copy", "all", "-perfect", "-flip", "horizontal", "-outfile"),
                commands.getFirst().subList(0, 7));
    }

    @Test
    void missingJpegtranIsReported() throws Exception {
        CommandRunner missing = command -> {
            throw new ProgramMissingException(command.getFirst(), new IOException("not found"));
        };
        Path file = TestImages.jpeg(dir.resolve("e.jpg"), TestImages.halves(16, 16));
        byte[] before = Files.readAllBytes(file);
        assertThrows(RotationSaver.JpegtranMissingException.class,
                () -> new RotationSaver(missing, loader).save(file, Orientation.NORMAL, Orientation.ROTATE_90));
        assertEquals(java.util.Arrays.toString(before), java.util.Arrays.toString(Files.readAllBytes(file)));
    }

    @Test
    void otherFormatsAreRefused() throws Exception {
        Path gif = dir.resolve("f.gif");
        ImageIO.write(TestImages.halves(8, 8), "gif", gif.toFile());
        assertThrows(IOException.class,
                () -> new RotationSaver(CommandRunner.system(), loader).save(gif, Orientation.NORMAL, Orientation.ROTATE_90));
    }
}
