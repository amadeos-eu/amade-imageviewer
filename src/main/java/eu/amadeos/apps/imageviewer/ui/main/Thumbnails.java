package eu.amadeos.apps.imageviewer.ui.main;

import eu.amadeos.apps.imageviewer.metadata.MetadataReader;
import eu.amadeos.apps.imageviewer.service.ImageLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * The small pictures of the film strip, made in the background when a cell first shows and kept for the
 * last few hundred files. Pictures that cannot be read get no thumbnail and are not tried again.
 */
public final class Thumbnails {
    private static final Logger LOG = LoggerFactory.getLogger(Thumbnails.class);
    public static final int SIZE = 88;
    private static final int KEPT = 300;

    private final ImageLoader loader;
    private final MetadataReader metadata;
    private final Executor background;
    private final Executor ui;
    private final Map<Path, BufferedImage> done = new LinkedHashMap<>(64, 0.75f, true);
    private final Set<Path> failed = new HashSet<>();
    private final Set<Path> pending = new HashSet<>();

    public Thumbnails(ImageLoader loader, MetadataReader metadata, Executor background, Executor ui) {
        this.loader = loader;
        this.metadata = metadata;
        this.background = background;
        this.ui = ui;
    }

    /** The thumbnail if it is ready; else it is made and {@code ready} runs on the EDT when it is. */
    public Optional<BufferedImage> get(Path file, Runnable ready) {
        BufferedImage image = done.get(file);
        if (image != null || failed.contains(file) || !pending.add(file)) return Optional.ofNullable(image);
        background.execute(() -> {
            BufferedImage thumb = null;
            try {
                thumb = loader.thumbnail(file, SIZE, metadata.orientation(file));
            } catch (Exception | OutOfMemoryError e) {
                LOG.debug("No thumbnail for {}: {}", file, e.getMessage());
            }
            BufferedImage made = thumb;
            ui.execute(() -> {
                pending.remove(file);
                if (made == null) {
                    failed.add(file);
                } else {
                    done.put(file, made);
                    while (done.size() > KEPT) done.remove(done.keySet().iterator().next());
                }
                ready.run();
            });
        });
        return Optional.empty();
    }

    /** Forget the thumbnail, e.g. after the file was turned. */
    public void forget(Path file) {
        done.remove(file);
        failed.remove(file);
    }
}
