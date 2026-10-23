package eu.amadeos.apps.imageviewer.repository;

import com.google.inject.Inject;
import eu.amadeos.apps.imageviewer.domain.Settings;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.PropertiesFile;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

/**
 * Settings of amade-imageviewer in {@code ~/.config/amadeos/imageviewer/imageviewer.properties}. Each change is
 * written at once; unknown or broken values fall back to the defaults.
 */
public class ViewerPreferences {
    public static final String FILE = "imageviewer.properties";

    private final Path file;
    private final Properties properties;

    @Inject
    public ViewerPreferences(AppDirectories directories) {
        this(directories.configFile(FILE));
    }

    public ViewerPreferences(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        this.properties = PropertiesFile.load(file);
    }

    public Settings settings() {
        Settings d = Settings.DEFAULT;
        int seconds = d.slideSeconds();
        try {
            seconds = Integer.parseInt(properties.getProperty("slideshow.seconds", "").strip());
        } catch (NumberFormatException e) {
            // keep the default
        }
        return new Settings(flag("sidebar", d.sidebar()), flag("film-strip", d.filmStrip()),
                flag("title-from-metadata", d.titleFromMetadata()), flag("maps.always", d.alwaysShowMaps()),
                seconds, flag("slideshow.shuffle", d.shuffle()), flag("slideshow.loop", d.loop()));
    }

    private boolean flag(String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : !"false".equals(value.strip());
    }

    public void setSettings(Settings settings) {
        properties.setProperty("sidebar", String.valueOf(settings.sidebar()));
        properties.setProperty("film-strip", String.valueOf(settings.filmStrip()));
        properties.setProperty("title-from-metadata", String.valueOf(settings.titleFromMetadata()));
        properties.setProperty("maps.always", String.valueOf(settings.alwaysShowMaps()));
        properties.setProperty("slideshow.seconds", String.valueOf(settings.slideSeconds()));
        properties.setProperty("slideshow.shuffle", String.valueOf(settings.shuffle()));
        properties.setProperty("slideshow.loop", String.valueOf(settings.loop()));
        PropertiesFile.store(file, properties, "amade-imageviewer");
    }
}
