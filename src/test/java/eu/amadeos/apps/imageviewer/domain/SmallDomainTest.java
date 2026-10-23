package eu.amadeos.apps.imageviewer.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Zoom steps, map tiles, positions, titles and settings. */
class SmallDomainTest {
    @Test
    void zoomStepsGoThroughTheList() {
        assertEquals(1.5, ZoomSteps.in(1.0));
        assertEquals(2 / 3.0, ZoomSteps.out(1.0));
        assertEquals(0.5, ZoomSteps.in(0.4));
        assertEquals(1 / 3.0, ZoomSteps.out(0.4));
        assertEquals(ZoomSteps.MAX, ZoomSteps.in(ZoomSteps.MAX));
        assertEquals(ZoomSteps.MIN, ZoomSteps.out(ZoomSteps.MIN));
    }

    @Test
    void fitShrinksButDoesNotEnlarge() {
        assertEquals(0.25, ZoomSteps.fit(4000, 3000, 1000, 1000, false));
        assertEquals(1.0, ZoomSteps.fit(200, 100, 1000, 1000, false));
        assertEquals(5.0, ZoomSteps.fit(200, 100, 1000, 1000, true));
        assertEquals(1.0, ZoomSteps.fit(0, 0, 100, 100, false));
    }

    @Test
    void tilesOfAKnownPlace() {
        // Konstanz at zoom 15 lies in tile 17219/11436.
        assertEquals(17219, (int) MapTiles.x(9.1754, 15));
        assertEquals(11436, (int) MapTiles.y(47.6603, 15));
        assertEquals(9.1754, MapTiles.longitude(MapTiles.x(9.1754, 15), 15), 1e-9);
        assertEquals(47.6603, MapTiles.latitude(MapTiles.y(47.6603, 15), 15), 1e-9);
        assertEquals("https://tile.openstreetmap.org/15/17219/11436.png", MapTiles.uri(15, 17219, 11436).toString());
        assertTrue(MapTiles.exists(2, 3, 3));
        assertFalse(MapTiles.exists(2, 4, 0));
        assertFalse(MapTiles.exists(20, 0, 0));
    }

    @Test
    void positionTexts() {
        GeoPosition p = new GeoPosition(47.6603, 9.1754, Optional.of(398.0));
        assertEquals("https://www.openstreetmap.org/?mlat=47.660300&mlon=9.175400#map=17/47.660300/9.175400",
                p.openStreetMapUrl(17));
        assertEquals("47.660300, 9.175400", p.decimalText());
        assertEquals("47,6603° N, 9,1754° E", p.displayText(Locale.GERMANY));
        assertEquals("33.8568° S, 151.2153° W", new GeoPosition(-33.8568, -151.2153, Optional.empty())
                .displayText(Locale.US));
        assertThrows(IllegalArgumentException.class, () -> new GeoPosition(91, 0, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new GeoPosition(Double.NaN, 0, Optional.empty()));
    }

    @Test
    void cameraPlaceholdersAreNoTitles() {
        assertEquals(Optional.empty(), TitleText.clean("OLYMPUS DIGITAL CAMERA         "));
        assertEquals(Optional.empty(), TitleText.clean("  "));
        assertEquals(Optional.empty(), TitleText.clean("???"));
        assertEquals(Optional.empty(), TitleText.clean(null));
        assertEquals(Optional.of("Evening at the Lake"), TitleText.clean(" Evening at the Lake\u0000"));
        assertEquals(Optional.of("Ferry"), TitleText.first(java.util.Arrays.asList(null, "SONY DSC", "Ferry")));
    }

    @Test
    void settingsKeepKnownIntervalsOnly() {
        assertEquals(5, Settings.DEFAULT.withSlideSeconds(7).slideSeconds());
        assertEquals(30, Settings.DEFAULT.withSlideSeconds(30).slideSeconds());
        assertEquals(List.of(3, 5, 10, 30), Settings.SLIDE_SECONDS);
        assertFalse(Settings.DEFAULT.alwaysShowMaps());
        assertFalse(Settings.DEFAULT.sidebar());
        assertTrue(Settings.DEFAULT.withSidebar(true).sidebar());
    }
}
