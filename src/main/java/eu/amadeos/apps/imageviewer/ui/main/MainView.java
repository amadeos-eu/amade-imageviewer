package eu.amadeos.apps.imageviewer.ui.main;

import eu.amadeos.apps.imageviewer.domain.PhotoInfo;
import eu.amadeos.apps.imageviewer.domain.Settings;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** The window as the presenter sees it. All methods run on the EDT. */
public interface MainView {
    /** Shows the sidebar, film strip and the menu states. */
    void showSettings(Settings settings);

    /** The pictures of the film strip, and which one is shown. */
    void showAlbum(List<Path> files, int index);

    void showPicture(Shown shown);

    /** No picture: nothing opened yet, or it cannot be read ({@code message} says why). */
    void showNoPicture(String title, String message);

    void showFullScreen(boolean fullScreen);

    /** The slideshow runs (or not) and is paused (or not): the ⏯ button and the "Pause" sign. */
    void showSlideshow(boolean running, boolean paused);

    void zoom(ZoomCommand command);

    /** The file dialog; empty if cancelled. */
    Optional<List<Path>> chooseFiles(Path folder);

    boolean confirmTrash(String fileName);

    void copyImage(BufferedImage image);

    void copyText(String text);

    void print(BufferedImage image, String jobName);

    void showAllMetadata(String fileName, List<PhotoInfo.Tag> tags);

    void beep();

    void showError(String message);

    void showInfo(String message);

    void showAbout();

    void close();
}
