package eu.amadeos.apps.imageviewer.repository;

import eu.amadeos.apps.imageviewer.domain.MapTiles;
import eu.amadeos.commons.net.Network;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Semaphore;

/**
 * Map tiles from tile.openstreetmap.org, kept on disk in the cache directory
 * ({@code ~/.cache/amadeos/imageviewer/tiles/z/x/y.png}). It follows the tile usage policy of the OpenStreetMap
 * Foundation: a User-Agent that names the program, at most two downloads at a time, nothing fetched in
 * advance, and every tile kept for a week before it is asked for again.
 *
 * <p>Only the map view calls it, and the map view only exists once the user allowed maps.
 */
public class TileStore {
    private static final Logger LOG = LoggerFactory.getLogger(TileStore.class);
    /** How long a tile on disk is used without asking the server again. */
    public static final Duration KEEP = Duration.ofDays(7);

    /** Downloads one URI; tests answer without a network. */
    @FunctionalInterface
    public interface Download {
        byte[] get(URI uri) throws IOException, InterruptedException;
    }

    private final Path directory;
    private final Download download;
    private final Clock clock;
    private final Semaphore connections = new Semaphore(2);

    public TileStore(Path directory, Download download, Clock clock) {
        this.directory = directory;
        this.download = download;
        this.clock = clock;
    }

    // --8<-- [start:http]
    /** Downloads with Java's HTTP client, as {@code amade-imageviewer/<version>}. */
    public static Download http(String version) {
        HttpClient client = Network.proxy().httpClient()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        String agent = "amade-imageviewer/" + version + " (amadeOS; +https://github.com/amadeos-eu/amade-imageviewer)";
        return uri -> {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", agent)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode() + " for " + uri);
            return response.body();
        };
    }
    // --8<-- [end:http]

    Path file(int zoom, int x, int y) {
        return directory.resolve(zoom + "/" + x + "/" + y + ".png");
    }

    /** The tile if it is on disk, however old; never downloads. */
    public Optional<BufferedImage> cached(int zoom, int x, int y) {
        return read(file(zoom, x, y));
    }

    // --8<-- [start:tile]
    /**
     * The tile: from disk if it is younger than a week, else downloaded and kept. If the download fails, an
     * older tile on disk is better than none. Blocks; call it off the event dispatch thread.
     */
    public Optional<BufferedImage> tile(int zoom, int x, int y) throws InterruptedException {
        if (!MapTiles.exists(zoom, x, y)) return Optional.empty();
        Path file = file(zoom, x, y);
        if (fresh(file)) {
            Optional<BufferedImage> image = read(file);
            if (image.isPresent()) return image;
        }
        connections.acquire();
        try {
            byte[] png = download.get(MapTiles.uri(zoom, x, y));
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null) throw new IOException("Not a picture: " + MapTiles.uri(zoom, x, y));
            store(file, png);
            return Optional.of(image);
        } catch (IOException e) {
            LOG.info("Map tile {}/{}/{} not loaded: {}", zoom, x, y, e.getMessage());
            return read(file);
        } finally {
            connections.release();
        }
    }
    // --8<-- [end:tile]

    private boolean fresh(Path file) {
        try {
            Instant modified = Files.getLastModifiedTime(file).toInstant();
            return modified.plus(KEEP).isAfter(clock.instant());
        } catch (IOException e) {
            return false;
        }
    }

    private static Optional<BufferedImage> read(Path file) {
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            return Optional.ofNullable(ImageIO.read(file.toFile()));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private void store(Path file, byte[] png) {
        try {
            Files.createDirectories(file.getParent());
            Path part = file.resolveSibling(file.getFileName() + ".part");
            Files.write(part, png);
            Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.from(clock.instant()));
        } catch (IOException e) {
            LOG.debug("Map tile not kept: {}", e.getMessage());
        }
    }
}
