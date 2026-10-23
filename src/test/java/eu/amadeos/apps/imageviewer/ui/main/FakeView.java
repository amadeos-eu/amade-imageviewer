package eu.amadeos.apps.imageviewer.ui.main;

import eu.amadeos.apps.imageviewer.domain.PhotoInfo;
import eu.amadeos.apps.imageviewer.domain.Settings;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Records what the presenter shows. */
final class FakeView implements MainView {
    Settings settings;
    List<Path> album = List.of();
    int albumIndex = -1;
    Shown shown;
    String noPicture;
    Boolean fullScreen;
    boolean slideshowRunning;
    boolean slideshowPaused;
    final List<ZoomCommand> zooms = new ArrayList<>();
    final List<String> errors = new ArrayList<>();
    final List<String> infos = new ArrayList<>();
    final List<String> copiedText = new ArrayList<>();
    BufferedImage copiedImage;
    BufferedImage printed;
    List<PhotoInfo.Tag> metadata;
    Optional<List<Path>> chosen = Optional.empty();
    Path chooserFolder;
    boolean confirm = true;
    int beeps;
    boolean closed;
    boolean aboutShown;

    @Override
    public void showSettings(Settings newSettings) {
        settings = newSettings;
    }

    @Override
    public void showAlbum(List<Path> files, int index) {
        album = files;
        albumIndex = index;
    }

    @Override
    public void showPicture(Shown newShown) {
        shown = newShown;
        noPicture = null;
    }

    @Override
    public void showNoPicture(String title, String message) {
        shown = null;
        noPicture = message;
    }

    @Override
    public void showFullScreen(boolean on) {
        fullScreen = on;
    }

    @Override
    public void showSlideshow(boolean running, boolean paused) {
        slideshowRunning = running;
        slideshowPaused = paused;
    }

    @Override
    public void zoom(ZoomCommand command) {
        zooms.add(command);
    }

    @Override
    public Optional<List<Path>> chooseFiles(Path folder) {
        chooserFolder = folder;
        return chosen;
    }

    @Override
    public boolean confirmTrash(String fileName) {
        return confirm;
    }

    @Override
    public void copyImage(BufferedImage image) {
        copiedImage = image;
    }

    @Override
    public void copyText(String text) {
        copiedText.add(text);
    }

    @Override
    public void print(BufferedImage image, String jobName) {
        printed = image;
    }

    @Override
    public void showAllMetadata(String fileName, List<PhotoInfo.Tag> tags) {
        metadata = tags;
    }

    @Override
    public void beep() {
        beeps++;
    }

    @Override
    public void showError(String message) {
        errors.add(message);
    }

    @Override
    public void showInfo(String message) {
        infos.add(message);
    }

    @Override
    public void showAbout() {
        aboutShown = true;
    }

    @Override
    public void close() {
        closed = true;
    }

    String fileName() {
        return shown == null ? null : shown.picture().path().getFileName().toString();
    }
}
