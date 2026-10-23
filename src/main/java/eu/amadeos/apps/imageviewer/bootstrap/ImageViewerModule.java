package eu.amadeos.apps.imageviewer.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import eu.amadeos.apps.imageviewer.metadata.MetadataReader;
import eu.amadeos.apps.imageviewer.repository.TileStore;
import eu.amadeos.apps.imageviewer.repository.ViewerPreferences;
import eu.amadeos.apps.imageviewer.service.ImageLoader;
import eu.amadeos.apps.imageviewer.service.RotationSaver;
import eu.amadeos.apps.imageviewer.service.Trash;
import eu.amadeos.apps.imageviewer.ui.main.MainPresenter;
import eu.amadeos.apps.imageviewer.ui.main.MainWindow;
import eu.amadeos.apps.imageviewer.ui.main.MapView;
import eu.amadeos.apps.imageviewer.ui.main.SwingTicker;
import eu.amadeos.apps.imageviewer.ui.main.Thumbnails;
import eu.amadeos.commons.app.Ui;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.XdgDirectories;
import eu.amadeos.commons.desktop.DesktopLauncher;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.process.CommandRunner;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;

/**
 * amade-imageviewer bindings. Theme, language, directories and the other shared services come from
 * {@link eu.amadeos.commons.app.CommonsModule}.
 */
public final class ImageViewerModule extends AbstractModule {
    /** The texts, {@code messages.properties} and its translations. */
    public static final String MESSAGES = "eu.amadeos.apps.imageviewer.messages";

    @Override
    protected void configure() {
        bind(ViewerPreferences.class).in(Scopes.SINGLETON);
        bind(ImageLoader.class).in(Scopes.SINGLETON);
        bind(MetadataReader.class).in(Scopes.SINGLETON);
    }

    /** The texts in the language {@code AmadeApplication} chose. */
    @Provides
    @Singleton
    Messages messages(Locale locale) {
        return Messages.load(ImageViewerModule.class, MESSAGES, locale);
    }

    // --8<-- [start:tiles]
    /** Map tiles, cached in {@code ~/.cache/amadeos/imageviewer/tiles}. */
    @Provides
    @Singleton
    TileStore tiles(AppDirectories directories) {
        return new TileStore(directories.cache().resolve("tiles"),
                TileStore.http(AmadeImageViewerApplication.version()), Clock.systemDefaultZone());
    }
    // --8<-- [end:tiles]

    @Provides
    @Singleton
    Thumbnails thumbnails(ImageLoader loader, MetadataReader metadata, ExecutorService background,
                          @Ui Executor ui) {
        return new Thumbnails(loader, metadata, background, ui);
    }

    @Provides
    @Singleton
    MainWindow window(Messages msg, LocaleSettings locales, Thumbnails thumbnails, TileStore tiles,
                      ExecutorService background, @Ui Executor ui, MainPresenterHolder presenter) {
        return new MainWindow(msg, locales, thumbnails, () -> {
            MapView map = new MapView(tiles, background, ui);
            map.onCredit(() -> presenter.get().openMapCopyright());
            return map;
        });
    }

    @Provides
    @Singleton
    MainPresenter presenter(MainWindow window, Messages msg, LocaleSettings locales, ViewerPreferences preferences,
                            ImageLoader loader, MetadataReader metadata, XdgDirectories xdg,
                            DesktopLauncher launcher, ExecutorService background, @Ui Executor ui,
                            MainPresenterHolder holder) {
        MainPresenter presenter = new MainPresenter(window, msg, locales, new MainPresenter.Services(preferences,
                loader, metadata, new RotationSaver(CommandRunner.system(), loader),
                new Trash(xdg.dataHome().resolve("Trash"), Clock.systemDefaultZone()), launcher, background, ui,
                new SwingTicker(), new SecureRandom(), xdg.pictures(), () -> System.exit(0)));
        holder.set(presenter);
        return presenter;
    }

    /** The map is made by the window before the presenter exists; its link reaches the presenter through this. */
    @Singleton
    static final class MainPresenterHolder {
        private MainPresenter presenter;

        MainPresenter get() {
            return presenter;
        }

        void set(MainPresenter value) {
            presenter = value;
        }
    }
}
