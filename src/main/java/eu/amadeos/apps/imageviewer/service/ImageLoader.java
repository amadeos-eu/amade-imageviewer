package eu.amadeos.apps.imageviewer.service;

import eu.amadeos.apps.imageviewer.domain.Orientation;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

/** Decodes pictures with ImageIO: the JDK's readers, and through TwelveMonkeys WebP and difficult JPEGs. */
public class ImageLoader {

    /** The whole picture, as stored (not yet turned). Of an animated GIF, the first frame. */
    public BufferedImage load(Path file) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            ImageReader reader = reader(in, file);
            try {
                reader.setInput(in, true, true);
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    // --8<-- [start:thumbnail]
    /**
     * A small picture for the film strip, at most {@code box} pixels wide and high, turned the way it is shown.
     * Only every n-th pixel of large pictures is read, which makes it quick even for 50 megapixels.
     */
    public BufferedImage thumbnail(Path file, int box, Orientation orientation) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            ImageReader reader = reader(in, file);
            try {
                reader.setInput(in, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                int step = Math.max(1, Math.min(width, height) / (box * 2));
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(step, step, 0, 0);
                BufferedImage small = reader.read(0, param);
                return ImageTransforms.scaleToFit(ImageTransforms.apply(small, orientation), box);
            } finally {
                reader.dispose();
            }
        }
    }
    // --8<-- [end:thumbnail]

    private static ImageReader reader(ImageInputStream in, Path file) throws IOException {
        if (in == null) throw new IOException("Cannot read " + file);
        Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
        if (!readers.hasNext()) throw new UnsupportedFormatException(file);
        return readers.next();
    }

    /** No reader knows the file. */
    public static final class UnsupportedFormatException extends IOException {
        public UnsupportedFormatException(Path file) {
            super("Unknown picture format: " + file.getFileName());
        }
    }
}
