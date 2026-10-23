package eu.amadeos.apps.imageviewer.ui.main;

/** A slideshow clock that ticks when the test says so. */
final class FakeTicker implements Ticker {
    Integer seconds;
    private Runnable tick;

    @Override
    public void start(int newSeconds, Runnable newTick) {
        seconds = newSeconds;
        tick = newTick;
    }

    @Override
    public void stop() {
        seconds = null;
        tick = null;
    }

    boolean running() {
        return tick != null;
    }

    void tick() {
        if (tick != null) tick.run();
    }
}
