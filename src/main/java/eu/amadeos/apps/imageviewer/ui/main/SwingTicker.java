package eu.amadeos.apps.imageviewer.ui.main;

import javax.swing.Timer;

/** The slideshow clock: a Swing timer, so the ticks come on the event dispatch thread. */
public final class SwingTicker implements Ticker {
    private Timer timer;

    @Override
    public void start(int seconds, Runnable tick) {
        stop();
        timer = new Timer(seconds * 1000, e -> tick.run());
        timer.start();
    }

    @Override
    public void stop() {
        if (timer != null) timer.stop();
        timer = null;
    }
}
