package eu.amadeos.apps.imageviewer.domain;

import java.util.List;

/**
 * What the viewer remembers between starts.
 *
 * @param sidebar            the information sidebar is shown
 * @param filmStrip          the film strip below the picture is shown
 * @param titleFromMetadata  the window title is the picture's title from its metadata, if it has one
 * @param alwaysShowMaps     maps load without asking first; off by default, as loading sends the position
 * @param slideSeconds       how long the slideshow shows each picture
 * @param shuffle            the slideshow shows the pictures in random order
 * @param loop               the slideshow starts again after the last picture
 */
public record Settings(boolean sidebar, boolean filmStrip, boolean titleFromMetadata, boolean alwaysShowMaps,
                       int slideSeconds, boolean shuffle, boolean loop) {
    /** The intervals the slideshow offers. */
    public static final List<Integer> SLIDE_SECONDS = List.of(3, 5, 10, 30);

    public static final Settings DEFAULT = new Settings(false, true, false, false, 5, false, true);

    public Settings {
        if (!SLIDE_SECONDS.contains(slideSeconds)) slideSeconds = 5;
    }

    public Settings withSidebar(boolean value) {
        return new Settings(value, filmStrip, titleFromMetadata, alwaysShowMaps, slideSeconds, shuffle, loop);
    }

    public Settings withFilmStrip(boolean value) {
        return new Settings(sidebar, value, titleFromMetadata, alwaysShowMaps, slideSeconds, shuffle, loop);
    }

    public Settings withTitleFromMetadata(boolean value) {
        return new Settings(sidebar, filmStrip, value, alwaysShowMaps, slideSeconds, shuffle, loop);
    }

    public Settings withAlwaysShowMaps(boolean value) {
        return new Settings(sidebar, filmStrip, titleFromMetadata, value, slideSeconds, shuffle, loop);
    }

    public Settings withSlideSeconds(int value) {
        return new Settings(sidebar, filmStrip, titleFromMetadata, alwaysShowMaps, value, shuffle, loop);
    }

    public Settings withShuffle(boolean value) {
        return new Settings(sidebar, filmStrip, titleFromMetadata, alwaysShowMaps, slideSeconds, value, loop);
    }

    public Settings withLoop(boolean value) {
        return new Settings(sidebar, filmStrip, titleFromMetadata, alwaysShowMaps, slideSeconds, shuffle, value);
    }
}
