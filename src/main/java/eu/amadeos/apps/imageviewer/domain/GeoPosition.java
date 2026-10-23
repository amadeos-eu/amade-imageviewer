package eu.amadeos.apps.imageviewer.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * Where a photo was taken.
 *
 * @param altitude metres above sea level, if the camera wrote it
 */
public record GeoPosition(double latitude, double longitude, Optional<Double> altitude) {
    public GeoPosition {
        if (!(latitude >= -90 && latitude <= 90) || !(longitude >= -180 && longitude <= 180)) {
            throw new IllegalArgumentException("Not a position: " + latitude + ", " + longitude);
        }
    }

    // --8<-- [start:links]
    /** The position on openstreetmap.org, with a marker. */
    public String openStreetMapUrl(int zoom) {
        return String.format(Locale.ROOT, "https://www.openstreetmap.org/?mlat=%.6f&mlon=%.6f#map=%d/%.6f/%.6f",
                latitude, longitude, zoom, latitude, longitude);
    }

    /** As other programs paste it: "47.660300, 9.175400". */
    public String decimalText() {
        return String.format(Locale.ROOT, "%.6f, %.6f", latitude, longitude);
    }
    // --8<-- [end:links]

    /** "47.6603° N, 9.1754° E", with the decimal separator of the language. */
    public String displayText(Locale locale) {
        return String.format(locale, "%.4f° %s, %.4f° %s", Math.abs(latitude), latitude >= 0 ? "N" : "S",
                Math.abs(longitude), longitude >= 0 ? "E" : "W");
    }
}
