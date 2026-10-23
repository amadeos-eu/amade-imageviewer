package eu.amadeos.apps.imageviewer.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * What a picture's metadata (EXIF, IPTC, XMP) tells about it. Every field is optional; most pictures from the
 * internet have none of them.
 *
 * @param title        from XMP dc:title, the IPTC object name or headline, or the Windows title
 * @param caption      a description, from XMP, IPTC or EXIF
 * @param exposureTime as written, e.g. "1/250" or "2"
 * @param tags         every entry of every metadata directory, for "All Metadata"
 */
public record PhotoInfo(Optional<String> title, Optional<String> caption, List<String> keywords,
                        Optional<String> author, Optional<String> copyright, Optional<LocalDateTime> taken,
                        Optional<String> camera, Optional<String> lens, Optional<String> exposureTime,
                        Optional<Double> fNumber, Optional<Integer> iso, Optional<Double> focalLength,
                        Optional<Integer> focalLength35, Optional<Boolean> flash, Orientation orientation,
                        Optional<GeoPosition> position, List<Tag> tags) {

    /** One metadata entry: the directory ("Exif IFD0", "GPS", "XMP"), the name and the value as text. */
    public record Tag(String directory, String name, String value) {
    }

    public static final PhotoInfo NONE = new PhotoInfo(Optional.empty(), Optional.empty(), List.of(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Orientation.NORMAL, Optional.empty(), List.of());

    public PhotoInfo {
        keywords = List.copyOf(keywords);
        tags = List.copyOf(tags);
    }

    /** True if the Camera section has something to show. */
    public boolean hasCamera() {
        return taken.isPresent() || camera.isPresent() || lens.isPresent() || exposureTime.isPresent()
                || fNumber.isPresent() || iso.isPresent() || focalLength.isPresent() || flash.isPresent();
    }

    /** True if the Description section has something to show. */
    public boolean hasDescription() {
        return title.isPresent() || caption.isPresent() || !keywords.isEmpty() || author.isPresent()
                || copyright.isPresent();
    }
}
