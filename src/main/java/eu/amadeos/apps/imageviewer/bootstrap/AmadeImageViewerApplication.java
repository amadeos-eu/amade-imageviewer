package eu.amadeos.apps.imageviewer.bootstrap;

import eu.amadeos.apps.imageviewer.ui.main.MainPresenter;
import eu.amadeos.apps.imageviewer.ui.main.MainWindow;
import eu.amadeos.commons.app.AmadeApplication;
import eu.amadeos.commons.app.AppInfo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class AmadeImageViewerApplication {
    public static final AppInfo APP = new AppInfo("amade-imageviewer", "Image Viewer", "imageviewer");

    private AmadeImageViewerApplication() {
    }

    /** The version from the jar's manifest; "dev" when run from the IDE. */
    public static String version() {
        String version = AmadeImageViewerApplication.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }

    // --8<-- [start:main]
    public static void main(String[] args) {
        if (args.length > 0 && (args[0].equals("--version") || args[0].equals("-v"))) {
            System.out.println("amade-imageviewer " + version());
            return;
        }
        List<Path> paths = paths(args);
        AmadeApplication.launch(APP, injector -> {
            MainWindow window = injector.getInstance(MainWindow.class);
            MainPresenter presenter = injector.getInstance(MainPresenter.class);
            window.bind(presenter);
            window.setVisible(true);
            presenter.start(paths);
        }, new ImageViewerModule());
    }
    // --8<-- [end:main]

    /** The files and folders on the command line; the desktop entry passes them with %F, as paths. */
    static List<Path> paths(String[] args) {
        List<Path> paths = new ArrayList<>();
        for (String arg : args) {
            if (arg.isBlank() || arg.startsWith("-")) continue;
            paths.add(Path.of(arg.startsWith("file://") ? java.net.URI.create(arg).getPath() : arg));
        }
        return paths;
    }
}
