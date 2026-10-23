package eu.amadeos.apps.imageviewer.ui.main;

import eu.amadeos.apps.imageviewer.domain.Orientation;

/**
 * What the window shows of a picture.
 *
 * @param orientation  how it is turned on screen: its EXIF orientation followed by the user's turns
 * @param position     its place in the album, from 0
 * @param title        the window title: the file name, or the title from the metadata
 * @param mapAllowed   the map of its position may load
 * @param canSave      the user turned it, and the turn can be saved into the file
 */
public record Shown(Picture picture, Orientation orientation, int position, int count, String title,
                    boolean mapAllowed, boolean canSave) {

    public int width() {
        return orientation.swapsSides() ? picture.image().getHeight() : picture.image().getWidth();
    }

    public int height() {
        return orientation.swapsSides() ? picture.image().getWidth() : picture.image().getHeight();
    }
}
