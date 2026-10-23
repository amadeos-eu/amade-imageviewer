package eu.amadeos.apps.imageviewer.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SlideshowTest {
    @Test
    void goesToTheEndAndStopsWithoutLoop() {
        Slideshow show = new Slideshow(4, 2, false, false, new Random(1));
        assertEquals(OptionalInt.of(3), show.advance());
        assertEquals(OptionalInt.empty(), show.advance());
    }

    @Test
    void startsAgainWithLoop() {
        Slideshow show = new Slideshow(3, 1, false, true, new Random(1));
        assertEquals(OptionalInt.of(2), show.advance());
        assertEquals(OptionalInt.of(0), show.advance());
        assertEquals(OptionalInt.of(1), show.advance());
    }

    @Test
    void shuffledShowsEveryPictureOnceStartingAfterTheShownOne() {
        Slideshow show = new Slideshow(10, 4, true, false, new Random(7));
        List<Integer> seen = new ArrayList<>(List.of(4));
        for (OptionalInt next = show.advance(); next.isPresent(); next = show.advance()) seen.add(next.getAsInt());
        assertEquals(10, seen.size());
        assertEquals(10, new HashSet<>(seen).size());
    }

    @Test
    void shuffledLoopDoesNotRepeatAcrossRounds() {
        for (int seed = 0; seed < 30; seed++) {
            Slideshow show = new Slideshow(3, 0, true, true, new Random(seed));
            int previous = 0;
            for (int i = 0; i < 12; i++) {
                int next = show.advance().getAsInt();
                assertNotEquals(previous, next, "seed " + seed);
                previous = next;
            }
        }
    }

    @Test
    void jumpingContinuesFromThere() {
        Slideshow show = new Slideshow(5, 0, false, false, new Random(1));
        show.jumpedTo(3);
        assertEquals(OptionalInt.of(4), show.advance());
    }
}
