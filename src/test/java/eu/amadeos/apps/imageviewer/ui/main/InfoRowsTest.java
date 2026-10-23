package eu.amadeos.apps.imageviewer.ui.main;

import eu.amadeos.apps.imageviewer.TestImages;
import eu.amadeos.apps.imageviewer.bootstrap.ImageViewerModule;
import eu.amadeos.apps.imageviewer.domain.GeoPosition;
import eu.amadeos.apps.imageviewer.domain.Orientation;
import eu.amadeos.apps.imageviewer.domain.PhotoInfo;
import eu.amadeos.commons.i18n.Messages;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfoRowsTest {
    private static PhotoInfo photo() {
        return new PhotoInfo(Optional.of("Evening at the Lake"), Optional.of("Last ferry"), List.of("lake", "sunset"),
                Optional.of("Ralf"), Optional.of("CC BY 4.0"), Optional.of(LocalDateTime.of(2026, 8, 14, 20, 47)),
                Optional.of("Canon EOS R6"), Optional.of("RF 24-105mm F4 L"), Optional.of("1/250"), Optional.of(8.0),
                Optional.of(100), Optional.of(4.3), Optional.of(24), Optional.of(true), Orientation.ROTATE_90,
                Optional.of(new GeoPosition(47.6603, 9.1754, Optional.of(398.4))), List.of());
    }

    private static Shown shown(PhotoInfo info, long bytes) {
        Picture picture = new Picture(Path.of("/tmp/x/IMG_2041.JPG"), TestImages.halves(40, 30), info, bytes);
        return new Shown(picture, info.orientation(), 0, 1, "IMG_2041.JPG", false, false);
    }

    private static List<String> flat(List<InfoRows.Section> sections) {
        return sections.stream().flatMap(s -> java.util.stream.Stream.concat(java.util.stream.Stream.of("# " + s.title()),
                s.rows().stream().map(r -> r.label() + ": " + r.value()))).toList();
    }

    @Test
    void allSectionsInEnglish() {
        List<String> rows = flat(new InfoRows(Fixture.MSG).sections(shown(photo(), 6_710_886)));
        assertEquals(List.of("# Description", "Title: Evening at the Lake", "Caption: Last ferry",
                "Keywords: lake, sunset", "Author: Ralf", "Copyright: CC BY 4.0",
                "# Camera", "Taken: Aug 14, 2026, 8:47 PM", "Camera: Canon EOS R6", "Lens: RF 24-105mm F4 L",
                "Exposure: 1/250 s  f/8  ISO 100", "Focal length: 4.3 mm (24 mm equiv.)", "Flash: Fired",
                "# File", "Name: IMG_2041.JPG", "Folder: /tmp/x", "Size: 6.4 MB, 30 × 40", "Type: JPEG",
                "# Location", "Position: 47.6603° N, 9.1754° E", "Altitude: 398 m"),
                rows.stream().map(r -> r.replace(' ', ' ')).toList());
    }

    @Test
    void germanNumbersAndOnlyTheFileSectionForPlainPictures() {
        Messages de = Messages.load(ImageViewerModule.class, ImageViewerModule.MESSAGES, Locale.GERMANY);
        List<String> rows = flat(new InfoRows(de).sections(shown(PhotoInfo.NONE, 2048)));
        assertEquals(List.of("# Datei", "Name: IMG_2041.JPG", "Ordner: /tmp/x", "Größe: 2 KB, 40 × 30", "Typ: JPEG"), rows);
        assertEquals("1,5 MB", new InfoRows(de).bytes(1_572_864));
        assertEquals("512 Byte", new InfoRows(de).bytes(512));
    }

    @Test
    void focalLengthWithoutEquivalentAndExposureParts() {
        InfoRows rows = new InfoRows(Fixture.MSG);
        PhotoInfo p = photo();
        PhotoInfo fullFrame = new PhotoInfo(p.title(), p.caption(), p.keywords(), p.author(), p.copyright(), p.taken(),
                p.camera(), p.lens(), Optional.of("2.5"), Optional.empty(), Optional.empty(), Optional.of(35.0),
                Optional.of(35), p.flash(), p.orientation(), p.position(), p.tags());
        assertEquals(Optional.of("35 mm"), rows.focalLength(fullFrame));
        assertEquals(Optional.of("2.5 s"), rows.exposure(fullFrame));
    }

    @Test
    void homeFoldersAreShortened() {
        String home = System.getProperty("user.home");
        assertEquals("~/Pictures", InfoRows.homeRelative(Path.of(home, "Pictures")));
        assertTrue(InfoRows.homeRelative(Path.of("/opt")).startsWith("/opt"));
    }
}
