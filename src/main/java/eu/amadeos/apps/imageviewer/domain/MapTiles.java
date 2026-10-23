package eu.amadeos.apps.imageviewer.domain;

import java.net.URI;

/**
 * The tile grid of web maps (Web Mercator, "slippy map"): at zoom z the world is 2^z × 2^z tiles of 256 × 256
 * pixels, numbered from the north-west corner.
 */
public final class MapTiles {
    public static final int SIZE = 256;
    public static final int MIN_ZOOM = 2;
    public static final int MAX_ZOOM = 19;
    public static final String SERVER = "https://tile.openstreetmap.org";

    private MapTiles() {
    }

    // --8<-- [start:mercator]
    /** The x of a longitude, in tiles (the fraction is the place within the tile). */
    public static double x(double longitude, int zoom) {
        return (longitude + 180) / 360 * (1 << zoom);
    }

    /** The y of a latitude, in tiles. */
    public static double y(double latitude, int zoom) {
        double lat = Math.toRadians(Math.clamp(latitude, -85.0511, 85.0511));
        return (1 - Math.log(Math.tan(lat) + 1 / Math.cos(lat)) / Math.PI) / 2 * (1 << zoom);
    }
    // --8<-- [end:mercator]

    public static double longitude(double x, int zoom) {
        return x / (1 << zoom) * 360 - 180;
    }

    public static double latitude(double y, int zoom) {
        double n = Math.PI - 2 * Math.PI * y / (1 << zoom);
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }

    /** True if the tile exists at that zoom. */
    public static boolean exists(int zoom, int x, int y) {
        int count = 1 << zoom;
        return zoom >= 0 && zoom <= MAX_ZOOM && x >= 0 && y >= 0 && x < count && y < count;
    }

    public static URI uri(int zoom, int x, int y) {
        return URI.create(SERVER + "/" + zoom + "/" + x + "/" + y + ".png");
    }
}
