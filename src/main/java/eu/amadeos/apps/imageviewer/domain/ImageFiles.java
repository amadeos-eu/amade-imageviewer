package eu.amadeos.apps.imageviewer.domain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/** Which files are pictures, and the order in which a folder shows them. */
public final class ImageFiles {
    /** What the viewer reads: the JDK's formats and WebP. */
    public static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "jpe", "jfif", "png", "gif", "bmp", "wbmp",
            "tif", "tiff", "webp");
    /** JPEGs, which are turned without loss by jpegtran. */
    private static final Set<String> JPEG = Set.of("jpg", "jpeg", "jpe", "jfif");
    /** Pictures that are written again unchanged except for the turn. */
    private static final Set<String> REWRITABLE = Set.of("png", "bmp");

    /** File names in natural order: case does not count, numbers by their value (IMG_9 before IMG_10). */
    public static final Comparator<Path> NATURAL = Comparator.comparing((Path p) -> p.getFileName().toString(),
            ImageFiles::compareNatural).thenComparing(p -> p.getFileName().toString());

    private ImageFiles() {
    }

    public static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean isImage(Path file) {
        return EXTENSIONS.contains(extension(file));
    }

    public static boolean isJpeg(Path file) {
        return JPEG.contains(extension(file));
    }

    /** True if a turn can be saved into the file: JPEG without loss, PNG and BMP as they are lossless. */
    public static boolean canSaveRotation(Path file) {
        return isJpeg(file) || REWRITABLE.contains(extension(file));
    }

    /** The pictures in a folder, not in its subfolders, without hidden files, in natural order. */
    public static List<Path> inFolder(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(ImageFiles::isImage)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .filter(Files::isRegularFile)
                    .sorted(NATURAL)
                    .toList();
        }
    }

    // --8<-- [start:natural]
    static int compareNatural(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i), cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i, sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                String na = a.substring(si, i).replaceFirst("^0+(?=.)", "");
                String nb = b.substring(sj, j).replaceFirst("^0+(?=.)", "");
                int byValue = na.length() != nb.length() ? Integer.compare(na.length(), nb.length()) : na.compareTo(nb);
                if (byValue != 0) return byValue;
            } else {
                int byLetter = Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));
                if (byLetter != 0) return byLetter;
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }
    // --8<-- [end:natural]
}
