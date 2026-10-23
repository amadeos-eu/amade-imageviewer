package eu.amadeos.apps.imageviewer.ui.main;

import eu.amadeos.apps.imageviewer.domain.PhotoInfo;
import eu.amadeos.commons.i18n.Messages;

import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The texts of the sidebar, in the user's language: the sections Description, Camera, File and Location, each
 * with only the rows that have a value. Knows nothing of Swing, so tests read it directly.
 */
public final class InfoRows {
    /** One row: the label and the value. */
    public record Row(String label, String value) {
    }

    /** A titled list of rows. */
    public record Section(String title, List<Row> rows) {
    }

    private final Messages msg;
    private final Locale locale;

    public InfoRows(Messages msg) {
        this.msg = msg;
        this.locale = msg.locale();
    }

    // --8<-- [start:sections]
    public List<Section> sections(Shown shown) {
        PhotoInfo info = shown.picture().info();
        List<Section> sections = new ArrayList<>();
        List<Row> description = new ArrayList<>();
        add(description, "info.title", info.title());
        add(description, "info.caption", info.caption());
        if (!info.keywords().isEmpty()) add(description, "info.keywords", Optional.of(String.join(", ", info.keywords())));
        add(description, "info.author", info.author());
        add(description, "info.copyright", info.copyright());
        if (!description.isEmpty()) sections.add(new Section(msg.get("info.section.description"), description));

        List<Row> camera = new ArrayList<>();
        add(camera, "info.taken", info.taken().map(t ->
                t.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))));
        add(camera, "info.camera", info.camera());
        add(camera, "info.lens", info.lens());
        add(camera, "info.exposure", exposure(info));
        add(camera, "info.focal-length", focalLength(info));
        add(camera, "info.flash", info.flash().map(f -> msg.get(f ? "info.flash.on" : "info.flash.off")));
        if (!camera.isEmpty()) sections.add(new Section(msg.get("info.section.camera"), camera));

        Path path = shown.picture().path();
        List<Row> file = new ArrayList<>();
        add(file, "info.name", Optional.of(path.getFileName().toString()));
        add(file, "info.folder", Optional.ofNullable(path.getParent()).map(InfoRows::homeRelative));
        // Pixel counts as plain digits: "4000 × 3000", not "4,000 × 3,000".
        add(file, "info.size", Optional.of(msg.format("info.size.value", bytes(shown.picture().bytes()),
                String.valueOf(shown.width()), String.valueOf(shown.height()))));
        add(file, "info.type", Optional.of(type(path)));
        sections.add(new Section(msg.get("info.section.file"), file));

        info.position().ifPresent(position -> {
            List<Row> location = new ArrayList<>();
            location.add(new Row(msg.get("info.position"), position.displayText(locale)));
            position.altitude().ifPresent(a -> location.add(new Row(msg.get("info.altitude"),
                    NumberFormat.getIntegerInstance(locale).format(Math.round(a)) + " m")));
            sections.add(new Section(msg.get("info.section.location"), location));
        });
        return sections;
    }
    // --8<-- [end:sections]

    /** "1/250 s  f/8  ISO 100", with only the parts the camera wrote. */
    Optional<String> exposure(PhotoInfo info) {
        List<String> parts = new ArrayList<>();
        info.exposureTime().ifPresent(t -> parts.add(t.replace(".", decimalSeparator()) + " s"));
        info.fNumber().ifPresent(f -> parts.add("f/" + number(f)));
        info.iso().ifPresent(iso -> parts.add("ISO " + iso));
        return parts.isEmpty() ? Optional.empty() : Optional.of(String.join("  ", parts));
    }

    /** "35 mm", or "4.3 mm (24 mm KB)" when the camera also wrote the 35 mm equivalent. */
    Optional<String> focalLength(PhotoInfo info) {
        if (info.focalLength().isEmpty()) return info.focalLength35().map(f -> msg.format("info.focal-length.35", f));
        String real = number(info.focalLength().get()) + " mm";
        Optional<Integer> equivalent = info.focalLength35()
                .filter(f -> Math.abs(f - info.focalLength().get()) > 0.5);
        return Optional.of(equivalent.map(f -> real + " (" + msg.format("info.focal-length.35", f) + ")").orElse(real));
    }

    private String number(double value) {
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setMaximumFractionDigits(1);
        return format.format(value);
    }

    private String decimalSeparator() {
        return String.valueOf(java.text.DecimalFormatSymbols.getInstance(locale).getDecimalSeparator());
    }

    /** "6.4 MB", "820 KB", "512 bytes". */
    String bytes(long bytes) {
        if (bytes < 1024) return msg.format("info.bytes", bytes);
        if (bytes < 1024 * 1024) return number(bytes / 1024.0) + " KB";
        if (bytes < 1024L * 1024 * 1024) return number(bytes / (1024.0 * 1024)) + " MB";
        return number(bytes / (1024.0 * 1024 * 1024)) + " GB";
    }

    private static String type(Path path) {
        String extension = eu.amadeos.apps.imageviewer.domain.ImageFiles.extension(path);
        return switch (extension) {
            case "jpg", "jpeg", "jpe", "jfif" -> "JPEG";
            case "tif", "tiff" -> "TIFF";
            case "" -> "?";
            default -> extension.toUpperCase(Locale.ROOT);
        };
    }

    /** "~/Pictures/2026" for folders in the home folder. */
    static String homeRelative(Path folder) {
        String home = System.getProperty("user.home", "");
        String text = folder.toString();
        if (!home.isEmpty() && (text.equals(home) || text.startsWith(home + "/"))) return "~" + text.substring(home.length());
        return text;
    }

    private void add(List<Row> rows, String key, Optional<String> value) {
        value.ifPresent(v -> rows.add(new Row(msg.get(key), v)));
    }
}
