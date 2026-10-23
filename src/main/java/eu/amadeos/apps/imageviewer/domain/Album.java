package eu.amadeos.apps.imageviewer.domain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The pictures the viewer pages through and the one shown: the folder of the opened picture, a folder, or the
 * pictures given on the command line. Paging stops at both ends.
 */
public final class Album {
    private final List<Path> files;
    private int index;

    public Album(List<Path> files, int index) {
        this.files = new ArrayList<>(files);
        this.index = files.isEmpty() ? -1 : Math.clamp(index, 0, files.size() - 1);
    }

    public static Album empty() {
        return new Album(List.of(), 0);
    }

    // --8<-- [start:open]
    /**
     * For one picture: its folder, at that picture. For a folder: its pictures, at the first. For several
     * paths: those pictures, at the first.
     */
    public static Album open(List<Path> paths) throws IOException {
        if (paths.size() == 1) {
            Path path = paths.getFirst().toAbsolutePath().normalize();
            if (Files.isDirectory(path)) return new Album(ImageFiles.inFolder(path), 0);
            List<Path> folder = path.getParent() == null ? List.of() : ImageFiles.inFolder(path.getParent());
            if (!folder.contains(path)) {
                List<Path> withFile = new ArrayList<>(folder);
                withFile.add(path);
                withFile.sort(ImageFiles.NATURAL);
                folder = withFile;
            }
            return new Album(folder, folder.indexOf(path));
        }
        List<Path> files = new ArrayList<>();
        for (Path path : paths) {
            Path p = path.toAbsolutePath().normalize();
            if (Files.isDirectory(p)) files.addAll(ImageFiles.inFolder(p));
            else files.add(p);
        }
        return new Album(files, 0);
    }
    // --8<-- [end:open]

    public List<Path> files() {
        return List.copyOf(files);
    }

    public boolean isEmpty() {
        return files.isEmpty();
    }

    public int size() {
        return files.size();
    }

    /** The position of the shown picture, from 0; -1 when empty. */
    public int index() {
        return index;
    }

    public Optional<Path> current() {
        return index < 0 ? Optional.empty() : Optional.of(files.get(index));
    }

    public boolean hasNext() {
        return index >= 0 && index < files.size() - 1;
    }

    public boolean hasPrevious() {
        return index > 0;
    }

    public boolean next() {
        return select(index + 1);
    }

    public boolean previous() {
        return select(index - 1);
    }

    public boolean first() {
        return select(0);
    }

    public boolean last() {
        return select(files.size() - 1);
    }

    /** Shows the picture at {@code position}; false if there is none or it is already shown. */
    public boolean select(int position) {
        if (position < 0 || position >= files.size() || position == index) return false;
        index = position;
        return true;
    }

    /** The picture {@code offset} places away, if there is one: for loading it in advance. */
    public Optional<Path> peek(int offset) {
        int position = index + offset;
        return position < 0 || position >= files.size() ? Optional.empty() : Optional.of(files.get(position));
    }

    /** Takes the shown picture out (it went to the trash); the next one is shown, or the previous at the end. */
    public void removeCurrent() {
        if (index < 0) return;
        files.remove(index);
        if (index >= files.size()) index = files.size() - 1;
    }
}
