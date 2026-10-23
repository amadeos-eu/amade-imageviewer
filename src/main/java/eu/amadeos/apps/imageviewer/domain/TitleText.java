package eu.amadeos.apps.imageviewer.domain;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Cleans texts from metadata. Many cameras write their own name or a placeholder into the image description,
 * which is no title.
 */
public final class TitleText {
    // --8<-- [start:junk]
    private static final Set<String> PLACEHOLDERS = Set.of("olympus digital camera", "sony dsc", "digital camera",
            "default", "image", "picture", "photo", "untitled", "dcim", "exif_jpeg_picture", "exif_jpeg_422",
            "minolta digital camera", "konica minolta digital camera", "samsung digital camera",
            "kodak digital still camera", "sanyo digital camera", "camera", "hdr", "jpeg", "screenshot");
    // --8<-- [end:junk]

    private TitleText() {
    }

    /** The text without surrounding blanks and control characters, or empty if nothing worth showing is left. */
    public static Optional<String> clean(String text) {
        if (text == null) return Optional.empty();
        String cleaned = text.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").strip();
        if (cleaned.isEmpty()) return Optional.empty();
        if (PLACEHOLDERS.contains(cleaned.toLowerCase(Locale.ROOT))) return Optional.empty();
        if (cleaned.chars().allMatch(c -> c == '?' || c == '.' || c == ' ' || c == '-' || c == '_')) {
            return Optional.empty();
        }
        return Optional.of(cleaned);
    }

    /** The first of the texts that is worth showing. */
    public static Optional<String> first(List<String> candidates) {
        for (String candidate : candidates) {
            Optional<String> cleaned = clean(candidate);
            if (cleaned.isPresent()) return cleaned;
        }
        return Optional.empty();
    }
}
