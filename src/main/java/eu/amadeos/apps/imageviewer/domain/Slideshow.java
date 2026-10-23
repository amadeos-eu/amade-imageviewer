package eu.amadeos.apps.imageviewer.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.random.RandomGenerator;

/**
 * The order of a running slideshow: from the shown picture to the end of the album, or, shuffled, the shown
 * picture first and then all others in random order. With {@code loop} it starts again from the beginning
 * (shuffled anew) instead of ending.
 */
public final class Slideshow {
    private final int size;
    private final boolean shuffle;
    private final boolean loop;
    private final RandomGenerator random;
    private final List<Integer> order = new ArrayList<>();
    private int position;
    private boolean paused;

    public Slideshow(int size, int start, boolean shuffle, boolean loop, RandomGenerator random) {
        this.size = size;
        this.shuffle = shuffle;
        this.loop = loop;
        this.random = random;
        if (shuffle) {
            fillShuffled(start);
        } else {
            for (int i = 0; i < size; i++) order.add(i);
            position = Math.clamp(start, 0, Math.max(0, size - 1));
        }
    }

    private void fillShuffled(int first) {
        order.clear();
        for (int i = 0; i < size; i++) {
            if (i != first) order.add(i);
        }
        Collections.shuffle(order, random);
        if (first >= 0 && first < size) order.addFirst(first);
        position = 0;
    }

    /** The picture to show next, or empty when the slideshow is over. */
    public OptionalInt advance() {
        if (size == 0) return OptionalInt.empty();
        if (position + 1 < order.size()) {
            position++;
            return OptionalInt.of(order.get(position));
        }
        if (!loop) return OptionalInt.empty();
        if (shuffle) {
            int last = order.get(position);
            fillShuffled(-1);
            // Not the same picture twice in a row across the new round.
            if (size > 1 && order.getFirst() == last) Collections.swap(order, 0, size - 1);
        } else {
            position = 0;
        }
        return OptionalInt.of(order.get(position));
    }

    /** The user jumped to another picture: the slideshow goes on from there. */
    public void jumpedTo(int index) {
        int at = order.indexOf(index);
        if (at >= 0) position = at;
    }

    public boolean paused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }
}
