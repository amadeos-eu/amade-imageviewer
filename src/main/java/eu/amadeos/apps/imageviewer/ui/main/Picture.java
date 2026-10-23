package eu.amadeos.apps.imageviewer.ui.main;

import eu.amadeos.apps.imageviewer.domain.PhotoInfo;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

/**
 * A loaded picture: the pixels as stored, its metadata and its size on disk.
 */
public record Picture(Path path, BufferedImage image, PhotoInfo info, long bytes) {
}
