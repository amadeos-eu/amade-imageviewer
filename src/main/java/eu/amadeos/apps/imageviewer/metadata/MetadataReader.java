package eu.amadeos.apps.imageviewer.metadata;

import com.adobe.internal.xmp.XMPConst;
import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPMeta;
import com.adobe.internal.xmp.properties.XMPProperty;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.lang.Rational;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.iptc.IptcDirectory;
import com.drew.metadata.xmp.XmpDirectory;
import eu.amadeos.apps.imageviewer.domain.GeoPosition;
import eu.amadeos.apps.imageviewer.domain.Orientation;
import eu.amadeos.apps.imageviewer.domain.PhotoInfo;
import eu.amadeos.apps.imageviewer.domain.TitleText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Reads EXIF, IPTC and XMP with the metadata-extractor library and gathers what the sidebar shows. Where the
 * formats overlap, XMP comes first (programs like digiKam or darktable write it), then IPTC, then EXIF.
 */
public class MetadataReader {
    private static final Logger LOG = LoggerFactory.getLogger(MetadataReader.class);
    private static final DateTimeFormatter EXIF_DATE = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ROOT);

    /** The metadata of the file; {@link PhotoInfo#NONE} if it has none or cannot be read. */
    public PhotoInfo read(Path file) {
        Metadata metadata;
        try {
            metadata = ImageMetadataReader.readMetadata(file.toFile());
        } catch (ImageProcessingException | IOException | RuntimeException e) {
            LOG.debug("No metadata in {}: {}", file, e.getMessage());
            return PhotoInfo.NONE;
        }
        return of(metadata);
    }

    // --8<-- [start:of]
    PhotoInfo of(Metadata metadata) {
        ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
        ExifSubIFDDirectory exif = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        IptcDirectory iptc = metadata.getFirstDirectoryOfType(IptcDirectory.class);
        XmpDirectory xmpDirectory = metadata.getFirstDirectoryOfType(XmpDirectory.class);
        XMPMeta xmp = xmpDirectory == null ? null : xmpDirectory.getXMPMeta();

        Optional<String> title = TitleText.first(Arrays.asList(
                localized(xmp, "title"),
                string(iptc, IptcDirectory.TAG_OBJECT_NAME),
                string(iptc, IptcDirectory.TAG_HEADLINE),
                description(ifd0, ExifDirectoryBase.TAG_WIN_TITLE)));
        Optional<String> caption = TitleText.first(Arrays.asList(
                localized(xmp, "description"),
                string(iptc, IptcDirectory.TAG_CAPTION),
                string(ifd0, ExifDirectoryBase.TAG_IMAGE_DESCRIPTION),
                description(ifd0, ExifDirectoryBase.TAG_WIN_COMMENT)))
                .filter(c -> !c.equals(title.orElse(null)));
        Optional<String> author = TitleText.first(Arrays.asList(
                arrayItems(xmp, "creator").stream().findFirst().orElse(null),
                string(iptc, IptcDirectory.TAG_BY_LINE),
                string(ifd0, ExifDirectoryBase.TAG_ARTIST),
                description(ifd0, ExifDirectoryBase.TAG_WIN_AUTHOR)));
        Optional<String> copyright = TitleText.first(Arrays.asList(
                localized(xmp, "rights"),
                string(iptc, IptcDirectory.TAG_COPYRIGHT_NOTICE),
                string(ifd0, ExifDirectoryBase.TAG_COPYRIGHT)));

        return new PhotoInfo(title, caption, keywords(xmp, iptc, ifd0), author, copyright,
                taken(exif, ifd0), camera(ifd0), TitleText.clean(string(exif, ExifDirectoryBase.TAG_LENS_MODEL)),
                exposure(exif), positive(exif, ExifDirectoryBase.TAG_FNUMBER),
                integer(exif, ExifDirectoryBase.TAG_ISO_EQUIVALENT), positive(exif, ExifDirectoryBase.TAG_FOCAL_LENGTH),
                integer(exif, ExifDirectoryBase.TAG_35MM_FILM_EQUIV_FOCAL_LENGTH).filter(f -> f > 0),
                flash(exif), orientation(ifd0), position(metadata), tags(metadata));
    }
    // --8<-- [end:of]

    /** Only the EXIF orientation, for thumbnails. */
    public Orientation orientation(Path file) {
        try {
            return orientation(ImageMetadataReader.readMetadata(file.toFile())
                    .getFirstDirectoryOfType(ExifIFD0Directory.class));
        } catch (ImageProcessingException | IOException | RuntimeException e) {
            return Orientation.NORMAL;
        }
    }

    private static Orientation orientation(ExifIFD0Directory ifd0) {
        Integer value = ifd0 == null ? null : ifd0.getInteger(ExifDirectoryBase.TAG_ORIENTATION);
        return value == null ? Orientation.NORMAL : Orientation.ofExif(value);
    }

    private static Optional<String> camera(ExifIFD0Directory ifd0) {
        Optional<String> make = TitleText.clean(string(ifd0, ExifDirectoryBase.TAG_MAKE));
        Optional<String> model = TitleText.clean(string(ifd0, ExifDirectoryBase.TAG_MODEL));
        if (model.isEmpty()) return make;
        // Most models already start with the maker: "Canon EOS R6", "NIKON D750".
        if (make.isEmpty() || model.get().toLowerCase(Locale.ROOT).startsWith(firstWord(make.get()))) return model;
        return Optional.of(make.get() + " " + model.get());
    }

    private static String firstWord(String text) {
        return text.toLowerCase(Locale.ROOT).split("[\\s,]+")[0];
    }

    private static Optional<LocalDateTime> taken(ExifSubIFDDirectory exif, ExifIFD0Directory ifd0) {
        for (String text : Arrays.asList(string(exif, ExifDirectoryBase.TAG_DATETIME_ORIGINAL),
                string(exif, ExifDirectoryBase.TAG_DATETIME_DIGITIZED), string(ifd0, ExifDirectoryBase.TAG_DATETIME))) {
            if (text == null || text.length() < 19) continue;
            try {
                return Optional.of(LocalDateTime.parse(text.substring(0, 19), EXIF_DATE));
            } catch (DateTimeParseException e) {
                // "0000:00:00 00:00:00" and other placeholders
            }
        }
        return Optional.empty();
    }

    // --8<-- [start:exposure]
    /** "1/250" for short exposures, "2" or "2.5" for long ones (in seconds). */
    static Optional<String> exposure(ExifSubIFDDirectory exif) {
        Rational time = exif == null ? null : exif.getRational(ExifDirectoryBase.TAG_EXPOSURE_TIME);
        if (time == null || time.getNumerator() <= 0 || time.getDenominator() <= 0) return Optional.empty();
        double seconds = time.doubleValue();
        if (seconds < 0.5) return Optional.of("1/" + Math.round(1 / seconds));
        return Optional.of(String.valueOf(Math.round(seconds * 10) / 10.0).replaceFirst("\\.0$", ""));
    }
    // --8<-- [end:exposure]

    private static Optional<Boolean> flash(ExifSubIFDDirectory exif) {
        Integer value = exif == null ? null : exif.getInteger(ExifDirectoryBase.TAG_FLASH);
        return value == null ? Optional.empty() : Optional.of((value & 1) == 1);
    }

    private static Optional<GeoPosition> position(Metadata metadata) {
        for (GpsDirectory gps : metadata.getDirectoriesOfType(GpsDirectory.class)) {
            GeoLocation location = gps.getGeoLocation();
            // 0, 0 is what phones write before they have a fix.
            if (location == null || location.isZero()) continue;
            Optional<Double> altitude = Optional.empty();
            Rational height = gps.getRational(GpsDirectory.TAG_ALTITUDE);
            if (height != null && height.getDenominator() != 0) {
                Integer ref = gps.getInteger(GpsDirectory.TAG_ALTITUDE_REF);
                altitude = Optional.of(ref != null && ref == 1 ? -height.doubleValue() : height.doubleValue());
            }
            try {
                return Optional.of(new GeoPosition(location.getLatitude(), location.getLongitude(), altitude));
            } catch (IllegalArgumentException e) {
                LOG.debug("Ignoring a broken GPS position: {}", location);
            }
        }
        return Optional.empty();
    }

    private static List<String> keywords(XMPMeta xmp, IptcDirectory iptc, ExifIFD0Directory ifd0) {
        Set<String> keywords = new LinkedHashSet<>(arrayItems(xmp, "subject"));
        if (keywords.isEmpty() && iptc != null && iptc.getStringArray(IptcDirectory.TAG_KEYWORDS) != null) {
            keywords.addAll(Arrays.asList(iptc.getStringArray(IptcDirectory.TAG_KEYWORDS)));
        }
        String windows = description(ifd0, ExifDirectoryBase.TAG_WIN_KEYWORDS);
        if (keywords.isEmpty() && windows != null) keywords.addAll(Arrays.asList(windows.split(";")));
        return keywords.stream().map(TitleText::clean).flatMap(Optional::stream).distinct().toList();
    }

    private static List<PhotoInfo.Tag> tags(Metadata metadata) {
        List<PhotoInfo.Tag> tags = new ArrayList<>();
        for (Directory directory : metadata.getDirectories()) {
            if (directory instanceof XmpDirectory xmp) {
                xmp.getXmpProperties().forEach((name, value) ->
                        tags.add(new PhotoInfo.Tag(directory.getName(), name, value)));
                continue;
            }
            for (Tag tag : directory.getTags()) {
                String value = tag.getDescription();
                if (value != null && !value.isBlank()) {
                    tags.add(new PhotoInfo.Tag(directory.getName(), tag.getTagName(), value.strip()));
                }
            }
        }
        return tags;
    }

    // ---------------------------------------------------------------- small readers

    private static String string(Directory directory, int tag) {
        return directory == null ? null : directory.getString(tag);
    }

    /** The decoded text, e.g. of the Windows tags that are stored as UTF-16 bytes. */
    private static String description(Directory directory, int tag) {
        return directory == null || !directory.containsTag(tag) ? null : directory.getDescription(tag);
    }

    private static Optional<Integer> integer(Directory directory, int tag) {
        return directory == null ? Optional.empty() : Optional.ofNullable(directory.getInteger(tag));
    }

    private static Optional<Double> positive(Directory directory, int tag) {
        Rational value = directory == null ? null : directory.getRational(tag);
        if (value == null || value.getDenominator() == 0 || value.doubleValue() <= 0) return Optional.empty();
        return Optional.of(value.doubleValue());
    }

    private static String localized(XMPMeta xmp, String name) {
        if (xmp == null) return null;
        try {
            XMPProperty property = xmp.getLocalizedText(XMPConst.NS_DC, name, null, XMPConst.X_DEFAULT);
            return property == null ? null : property.getValue();
        } catch (XMPException e) {
            return null;
        }
    }

    private static List<String> arrayItems(XMPMeta xmp, String name) {
        if (xmp == null) return List.of();
        List<String> items = new ArrayList<>();
        try {
            int count = xmp.countArrayItems(XMPConst.NS_DC, name);
            for (int i = 1; i <= count; i++) {
                XMPProperty item = xmp.getArrayItem(XMPConst.NS_DC, name, i);
                if (item != null && item.getValue() != null) items.add(item.getValue());
            }
        } catch (XMPException e) {
            return List.of();
        }
        return items;
    }
}
