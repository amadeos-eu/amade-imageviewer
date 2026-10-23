package eu.amadeos.apps.imageviewer.ui.main;

import eu.amadeos.apps.imageviewer.domain.Album;
import eu.amadeos.apps.imageviewer.domain.GeoPosition;
import eu.amadeos.apps.imageviewer.domain.ImageFiles;
import eu.amadeos.apps.imageviewer.domain.Orientation;
import eu.amadeos.apps.imageviewer.domain.PhotoInfo;
import eu.amadeos.apps.imageviewer.domain.Settings;
import eu.amadeos.apps.imageviewer.domain.Slideshow;
import eu.amadeos.apps.imageviewer.metadata.MetadataReader;
import eu.amadeos.apps.imageviewer.repository.ViewerPreferences;
import eu.amadeos.apps.imageviewer.service.ImageLoader;
import eu.amadeos.apps.imageviewer.service.ImageTransforms;
import eu.amadeos.apps.imageviewer.service.RotationSaver;
import eu.amadeos.apps.imageviewer.service.Trash;
import eu.amadeos.commons.desktop.DesktopLauncher;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.EdtExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.random.RandomGenerator;

/**
 * Pages through the album, loads pictures in the background (the neighbours in advance), keeps the user's turns,
 * runs the slideshow and turns menu choices into settings.
 */
public final class MainPresenter {
    private static final Logger LOG = LoggerFactory.getLogger(MainPresenter.class);
    /** Pictures kept decoded: the shown one, its neighbours and the one before. */
    private static final int CACHED = 4;

    /** What the presenter works with besides the view; one record keeps the constructor readable. */
    public record Services(ViewerPreferences preferences, ImageLoader loader, MetadataReader metadata,
                           RotationSaver saver, Trash trash, DesktopLauncher launcher, Executor background,
                           Executor ui, Ticker ticker, RandomGenerator random, Path pictures, Runnable exit) {
    }

    private final MainView view;
    private final Messages msg;
    private final LocaleSettings locales;
    private final Services services;
    private final Map<Path, Picture> cache = new LinkedHashMap<>(8, 0.75f, true);
    private final Set<Path> loading = new HashSet<>();
    /** The user's turns, per file, while the program runs. */
    private final Map<Path, Orientation> turns = new HashMap<>();
    /** Pictures whose map the user allowed with "Show Map". */
    private final Set<Path> mapsAllowed = new HashSet<>();
    private Settings settings = Settings.DEFAULT;
    private Album album = Album.empty();
    private Picture shown;
    private Slideshow slideshow;
    private boolean fullScreen;
    private boolean fullScreenForSlideshow;

    /** @param locales null in tests: no language menu */
    public MainPresenter(MainView view, Messages msg, LocaleSettings locales, Services services) {
        this.view = view;
        this.msg = msg;
        this.locales = locales;
        this.services = services;
    }

    // ---------------------------------------------------------------- start and open

    /** Shows the settings and opens what the command line names (files or folders). */
    public void start(List<Path> paths) {
        EdtExecutor.requireEdt();
        settings = services.preferences().settings();
        view.showSettings(settings);
        if (paths.isEmpty()) {
            view.showNoPicture(msg.get("window.title"), msg.get("empty.hint"));
        } else {
            open(paths);
        }
    }

    /** Image → Open: the file dialog, in the folder of the shown picture or in Pictures. */
    public void open() {
        Path folder = album.current().map(Path::getParent).orElse(services.pictures());
        view.chooseFiles(folder).ifPresent(this::open);
    }

    // --8<-- [start:open]
    /** Opens files or folders (from the dialog, the command line or dropped on the window). */
    public void open(List<Path> paths) {
        Album opened;
        try {
            opened = Album.open(paths);
        } catch (IOException e) {
            LOG.warn("Could not open {}", paths, e);
            view.showError(msg.format("error.open", e.getMessage()));
            return;
        }
        if (opened.isEmpty()) {
            view.showError(msg.get("error.no-pictures"));
            return;
        }
        stopSlideshow();
        album = opened;
        cache.clear();
        view.showAlbum(album.files(), album.index());
        showCurrent();
    }
    // --8<-- [end:open]

    // ---------------------------------------------------------------- paging

    public void next() {
        go(album.next());
    }

    public void previous() {
        go(album.previous());
    }

    public void first() {
        go(album.first());
    }

    public void last() {
        go(album.last());
    }

    /** A click in the film strip. */
    public void select(int index) {
        go(album.select(index));
    }

    private void go(boolean moved) {
        if (!moved) {
            if (!album.isEmpty()) view.beep();
            return;
        }
        if (slideshow != null) {
            slideshow.jumpedTo(album.index());
            if (!slideshow.paused()) services.ticker().start(settings.slideSeconds(), this::slideshowTick);
        }
        view.showAlbum(album.files(), album.index());
        showCurrent();
    }

    // --8<-- [start:load]
    private void showCurrent() {
        Optional<Path> current = album.current();
        if (current.isEmpty()) {
            shown = null;
            view.showNoPicture(msg.get("window.title"), msg.get("empty.hint"));
            return;
        }
        Path path = current.get();
        Picture cached = cache.get(path);
        if (cached != null) {
            display(cached);
        } else {
            load(path, true);
        }
    }

    /** Decodes in the background; shows the picture if it is still the current one when it is ready. */
    private void load(Path path, boolean show) {
        if (!loading.add(path)) return;
        services.background().execute(() -> {
            Picture picture = null;
            String error = null;
            try {
                long bytes = Files.size(path);
                picture = new Picture(path, services.loader().load(path), services.metadata().read(path), bytes);
            } catch (ImageLoader.UnsupportedFormatException e) {
                error = msg.get("error.format");
            } catch (IOException | RuntimeException | OutOfMemoryError e) {
                LOG.warn("Could not load {}", path, e);
                error = e instanceof OutOfMemoryError ? msg.get("error.memory") : e.getMessage();
            }
            Picture loaded = picture;
            String failure = error;
            services.ui().execute(() -> {
                loading.remove(path);
                if (loaded != null) remember(loaded);
                if (!path.equals(album.current().orElse(null))) return;
                if (loaded != null) {
                    display(loaded);
                } else if (show) {
                    shown = null;
                    view.showNoPicture(path.getFileName().toString(), msg.format("error.load",
                            path.getFileName(), failure));
                }
            });
        });
    }
    // --8<-- [end:load]

    private void remember(Picture picture) {
        cache.put(picture.path(), picture);
        while (cache.size() > CACHED) cache.remove(cache.keySet().iterator().next());
    }

    private void display(Picture picture) {
        shown = picture;
        refresh();
        // The neighbours in advance, so that paging does not wait.
        album.peek(1).filter(p -> !cache.containsKey(p)).ifPresent(p -> load(p, false));
        album.peek(-1).filter(p -> !cache.containsKey(p)).ifPresent(p -> load(p, false));
    }

    /** Shows the current picture again after a turn or a setting changed. */
    private void refresh() {
        if (shown == null) return;
        Path path = shown.path();
        Orientation user = turns.getOrDefault(path, Orientation.NORMAL);
        String title = settings.titleFromMetadata()
                ? shown.info().title().orElse(path.getFileName().toString())
                : path.getFileName().toString();
        boolean canSave = user != Orientation.NORMAL && ImageFiles.canSaveRotation(path);
        view.showPicture(new Shown(shown, shown.info().orientation().then(user), album.index(), album.size(), title,
                settings.alwaysShowMaps() || mapsAllowed.contains(path), canSave));
    }

    // ---------------------------------------------------------------- turning

    public void rotateLeft() {
        turn(Orientation::rotateLeft);
    }

    public void rotateRight() {
        turn(Orientation::rotateRight);
    }

    public void flipHorizontally() {
        turn(Orientation::flipHorizontally);
    }

    public void flipVertically() {
        turn(Orientation::flipVertically);
    }

    private void turn(java.util.function.UnaryOperator<Orientation> step) {
        if (shown == null) {
            view.beep();
            return;
        }
        Path path = shown.path();
        Orientation next = step.apply(turns.getOrDefault(path, Orientation.NORMAL));
        if (next == Orientation.NORMAL) turns.remove(path);
        else turns.put(path, next);
        refresh();
    }

    // --8<-- [start:save]
    /** Image → Save Rotation: writes the turn into the file, without loss for JPEG. */
    public void saveRotation() {
        Orientation user = shown == null ? Orientation.NORMAL : turns.getOrDefault(shown.path(), Orientation.NORMAL);
        if (shown == null || user == Orientation.NORMAL || !ImageFiles.canSaveRotation(shown.path())) {
            view.beep();
            return;
        }
        Picture picture = shown;
        services.background().execute(() -> {
            String error = null;
            boolean trimmed = false;
            try {
                trimmed = services.saver().save(picture.path(), picture.info().orientation(), user);
            } catch (RotationSaver.JpegtranMissingException e) {
                error = msg.get("error.jpegtran");
            } catch (IOException e) {
                LOG.warn("Could not save the rotation of {}", picture.path(), e);
                error = msg.format("error.save", e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                error = msg.format("error.save", e.getMessage());
            }
            String failure = error;
            boolean lostEdge = trimmed;
            services.ui().execute(() -> {
                if (failure != null) {
                    view.showError(failure);
                    return;
                }
                // The file is turned now: forget the turn and read it again.
                turns.remove(picture.path());
                cache.remove(picture.path());
                if (picture.path().equals(album.current().orElse(null))) showCurrent();
                if (lostEdge) view.showInfo(msg.get("info.trimmed"));
            });
        });
    }
    // --8<-- [end:save]

    // ---------------------------------------------------------------- image menu

    public void moveToTrash() {
        if (shown == null || album.current().isEmpty()) {
            view.beep();
            return;
        }
        Path path = album.current().get();
        if (!view.confirmTrash(path.getFileName().toString())) return;
        try {
            services.trash().move(path);
        } catch (IOException e) {
            LOG.warn("Could not move {} to the trash", path, e);
            view.showError(msg.format("error.trash", e.getMessage()));
            return;
        }
        cache.remove(path);
        turns.remove(path);
        album.removeCurrent();
        view.showAlbum(album.files(), album.index());
        if (album.isEmpty()) stopSlideshow();
        showCurrent();
    }

    public void copyImage() {
        if (shown == null) {
            view.beep();
            return;
        }
        view.copyImage(ImageTransforms.apply(shown.image(), currentOrientation()));
    }

    public void copyPath() {
        album.current().ifPresentOrElse(p -> view.copyText(p.toString()), view::beep);
    }

    public void print() {
        if (shown == null) {
            view.beep();
            return;
        }
        view.print(ImageTransforms.apply(shown.image(), currentOrientation()),
                shown.path().getFileName().toString());
    }

    private Orientation currentOrientation() {
        return shown.info().orientation().then(turns.getOrDefault(shown.path(), Orientation.NORMAL));
    }

    public void zoom(ZoomCommand command) {
        if (shown == null) {
            view.beep();
            return;
        }
        view.zoom(command);
    }

    // ---------------------------------------------------------------- sidebar and map

    /** "Show Map" in the sidebar: this picture's map may load. */
    public void allowMap() {
        if (shown == null) return;
        mapsAllowed.add(shown.path());
        refresh();
    }

    public void openMapInBrowser() {
        position().ifPresent(p -> browse(p.openStreetMapUrl(17)));
    }

    public void copyPosition() {
        position().ifPresentOrElse(p -> view.copyText(p.decimalText()), view::beep);
    }

    /** The attribution under the map leads to the licence page, as the OpenStreetMap licence asks. */
    public void openMapCopyright() {
        browse("https://www.openstreetmap.org/copyright");
    }

    private void browse(String url) {
        try {
            services.launcher().browse(url);
        } catch (IOException e) {
            view.showError(msg.format("error.browser", e.getMessage()));
        }
    }

    private Optional<GeoPosition> position() {
        return shown == null ? Optional.empty() : shown.info().position();
    }

    public void showAllMetadata() {
        if (shown == null) {
            view.beep();
            return;
        }
        List<PhotoInfo.Tag> tags = shown.info().tags();
        if (tags.isEmpty()) {
            view.showInfo(msg.get("info.no-metadata"));
            return;
        }
        view.showAllMetadata(shown.path().getFileName().toString(), tags);
    }

    // ---------------------------------------------------------------- settings

    public Settings settings() {
        return settings;
    }

    public void setSidebar(boolean on) {
        change(settings.withSidebar(on));
    }

    public void toggleSidebar() {
        setSidebar(!settings.sidebar());
    }

    public void setFilmStrip(boolean on) {
        change(settings.withFilmStrip(on));
    }

    public void setTitleFromMetadata(boolean on) {
        change(settings.withTitleFromMetadata(on));
    }

    public void setAlwaysShowMaps(boolean on) {
        change(settings.withAlwaysShowMaps(on));
    }

    public void setSlideSeconds(int seconds) {
        change(settings.withSlideSeconds(seconds));
        if (slideshow != null && !slideshow.paused()) services.ticker().start(settings.slideSeconds(), this::slideshowTick);
    }

    public void setShuffle(boolean on) {
        change(settings.withShuffle(on));
    }

    public void setLoop(boolean on) {
        change(settings.withLoop(on));
    }

    private void change(Settings changed) {
        if (changed.equals(settings)) return;
        settings = changed;
        services.preferences().setSettings(changed);
        view.showSettings(changed);
        refresh();
    }

    public void setLanguage(Optional<Language> language) {
        try {
            locales.setApplicationLanguage(language);
        } catch (IOException e) {
            LOG.warn("Could not save the language", e);
            view.showError(msg.format("error.save-language", e.getMessage()));
            return;
        }
        if (locales.restartRequired()) {
            view.showInfo(Messages.commons(msg.locale()).format("language.restart", "amade-imageviewer"));
        }
    }

    // ---------------------------------------------------------------- full screen and slideshow

    public boolean fullScreen() {
        return fullScreen;
    }

    public void toggleFullScreen() {
        setFullScreen(!fullScreen);
    }

    public void setFullScreen(boolean on) {
        if (on == fullScreen) return;
        if (!on) stopSlideshow();
        fullScreen = on;
        fullScreenForSlideshow = false;
        view.showFullScreen(on);
    }

    public boolean slideshowRunning() {
        return slideshow != null;
    }

    // --8<-- [start:slideshow]
    /** F5: from the shown picture, in full screen. In a running slideshow it pauses or goes on instead. */
    public void slideshow() {
        if (slideshow != null) {
            togglePause();
            return;
        }
        if (album.size() < 2) {
            view.beep();
            return;
        }
        slideshow = new Slideshow(album.size(), album.index(), settings.shuffle(), settings.loop(),
                services.random());
        if (!fullScreen) {
            fullScreen = true;
            fullScreenForSlideshow = true;
            view.showFullScreen(true);
        }
        services.ticker().start(settings.slideSeconds(), this::slideshowTick);
        view.showSlideshow(true, false);
    }

    void slideshowTick() {
        if (slideshow == null || slideshow.paused()) return;
        OptionalInt next = slideshow.advance();
        if (next.isEmpty()) {
            boolean leave = fullScreenForSlideshow;
            stopSlideshow();
            if (leave) setFullScreen(false);
            return;
        }
        if (album.select(next.getAsInt())) {
            view.showAlbum(album.files(), album.index());
            showCurrent();
        }
    }

    public void togglePause() {
        if (slideshow == null) return;
        slideshow.setPaused(!slideshow.paused());
        if (slideshow.paused()) services.ticker().stop();
        else services.ticker().start(settings.slideSeconds(), this::slideshowTick);
        view.showSlideshow(true, slideshow.paused());
    }

    /** The space bar: pause or go on in a slideshow, else the next picture. */
    public void space() {
        if (slideshow != null) togglePause();
        else next();
    }

    /** Esc ends the slideshow and the full screen. */
    public void escape() {
        stopSlideshow();
        setFullScreen(false);
    }

    private void stopSlideshow() {
        if (slideshow == null) return;
        slideshow = null;
        services.ticker().stop();
        view.showSlideshow(false, false);
    }
    // --8<-- [end:slideshow]

    // ---------------------------------------------------------------- help and exit

    public void about() {
        view.showAbout();
    }

    public void exit() {
        services.ticker().stop();
        view.close();
        services.exit().run();
    }

    /** The user's turn of a file, for tests. */
    Orientation turnOf(Path file) {
        return turns.getOrDefault(file, Orientation.NORMAL);
    }
}
