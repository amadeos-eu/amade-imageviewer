package eu.amadeos.apps.imageviewer.ui.main;

import eu.amadeos.apps.imageviewer.OnEdt;
import eu.amadeos.apps.imageviewer.TestImages;
import eu.amadeos.apps.imageviewer.bootstrap.ImageViewerModule;
import eu.amadeos.apps.imageviewer.metadata.MetadataReader;
import eu.amadeos.apps.imageviewer.repository.ViewerPreferences;
import eu.amadeos.apps.imageviewer.service.ImageLoader;
import eu.amadeos.apps.imageviewer.service.RotationSaver;
import eu.amadeos.apps.imageviewer.service.Trash;
import eu.amadeos.commons.desktop.DesktopLauncher;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.process.CommandRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** A folder of test pictures and the services of a presenter, all synchronous. */
final class Fixture {
    static final Messages MSG = Messages.load(ImageViewerModule.class, ImageViewerModule.MESSAGES, Locale.US);

    final Path dir;
    final Path folder;
    final FakeTicker ticker = new FakeTicker();
    final List<String> browsed = new ArrayList<>();
    final ViewerPreferences preferences;
    int exits;

    Fixture(Path dir) throws Exception {
        this.dir = dir;
        this.folder = Files.createDirectories(dir.resolve("Pictures/Summer"));
        this.preferences = new ViewerPreferences(dir.resolve("imageviewer.properties"));
    }

    /** a.png (40 × 20), b.jpg with metadata and a position, c.png; d.txt is no picture. */
    Fixture withPictures() throws Exception {
        TestImages.png(folder.resolve("a.png"), TestImages.halves(40, 20));
        TestImages.jpeg(folder.resolve("b.jpg"), TestImages.halves(64, 32),
                new TestImages.Exif().camera("Canon", "Canon EOS R6").orientation(6).taken("2026:08:14 20:47:00")
                        .exposure(1, 250, 80, 100).gps(47.6603, 9.1754, 398).build(),
                TestImages.xmp("Evening at the Lake", "Last ferry", List.of("lake"), "Ralf", "CC BY 4.0"));
        TestImages.png(folder.resolve("c.png"), TestImages.halves(30, 30));
        Files.writeString(folder.resolve("d.txt"), "notes");
        return this;
    }

    Path file(String name) {
        return folder.resolve(name);
    }

    MainPresenter.Services services() {
        DesktopLauncher launcher = new DesktopLauncher() {
            @Override
            public void browse(String uri) {
                browsed.add(uri);
            }
        };
        ImageLoader loader = new ImageLoader();
        return new MainPresenter.Services(preferences, loader, new MetadataReader(),
                new RotationSaver(CommandRunner.system(), loader), new Trash(dir.resolve("Trash"), Clock.systemUTC()),
                launcher, Runnable::run, Runnable::run, ticker, new Random(3), dir.resolve("Pictures"), () -> exits++);
    }

    MainPresenter presenter(MainView view) {
        return new MainPresenter(view, MSG, null, services());
    }

    static void edt(OnEdtTest test) throws Exception {
        OnEdt.run(() -> {
            try {
                test.run();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @FunctionalInterface
    interface OnEdtTest {
        void run() throws Exception;
    }
}
