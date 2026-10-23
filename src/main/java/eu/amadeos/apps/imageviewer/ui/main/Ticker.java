package eu.amadeos.apps.imageviewer.ui.main;

/** The clock of the slideshow. The window uses a Swing timer; tests tick by hand. */
public interface Ticker {
    /** Calls {@code tick} on the event dispatch thread every {@code seconds}, from now on; restarts if running. */
    void start(int seconds, Runnable tick);

    void stop();
}
