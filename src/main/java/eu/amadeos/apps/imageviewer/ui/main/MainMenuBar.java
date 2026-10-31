package eu.amadeos.apps.imageviewer.ui.main;

import eu.amadeos.apps.imageviewer.domain.Settings;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.EdtExecutor;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import javax.swing.border.BevelBorder;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** Image, Edit, View, Go, Options and Help. */
public final class MainMenuBar extends JMenuBar {
    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;

    private final Messages msg;
    /** amade-imageeditor is installed. */
    private final boolean canEdit;
    private final Map<String, JMenuItem> items = new HashMap<>();
    private final Map<Integer, JRadioButtonMenuItem> seconds = new HashMap<>();
    private boolean updating;

    public MainMenuBar(MainPresenter presenter, Messages msg, LocaleSettings locales) {
        EdtExecutor.requireEdt();
        this.msg = msg;
        this.canEdit = presenter.canEdit();
        setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

        JMenu image = menu("menu.image");
        image.add(item("menu.image.open", key(KeyEvent.VK_O, CTRL), presenter::open));
        image.add(item("menu.image.edit", key(KeyEvent.VK_E, CTRL), presenter::edit));
        image.add(item("menu.image.save", key(KeyEvent.VK_S, CTRL), presenter::saveRotation));
        image.add(item("menu.image.print", key(KeyEvent.VK_P, CTRL), presenter::print));
        image.addSeparator();
        image.add(item("menu.image.trash", key(KeyEvent.VK_DELETE, 0), presenter::moveToTrash));
        image.addSeparator();
        image.add(item("menu.image.exit", key(KeyEvent.VK_Q, CTRL), presenter::exit));

        JMenu edit = menu("menu.edit");
        edit.add(item("menu.edit.copy", key(KeyEvent.VK_C, CTRL), presenter::copyImage));
        edit.add(item("menu.edit.copy-path", key(KeyEvent.VK_C, CTRL | InputEvent.SHIFT_DOWN_MASK), presenter::copyPath));

        JMenu view = menu("menu.view");
        view.add(item("menu.view.zoom-in", key(KeyEvent.VK_PLUS, CTRL), () -> presenter.zoom(ZoomCommand.IN)));
        view.add(item("menu.view.zoom-out", key(KeyEvent.VK_MINUS, CTRL), () -> presenter.zoom(ZoomCommand.OUT)));
        view.add(item("menu.view.fit", key(KeyEvent.VK_0, CTRL), () -> presenter.zoom(ZoomCommand.FIT)));
        view.add(item("menu.view.actual", key(KeyEvent.VK_1, CTRL), () -> presenter.zoom(ZoomCommand.ACTUAL)));
        view.addSeparator();
        view.add(item("menu.view.rotate-left", key(KeyEvent.VK_L, CTRL), presenter::rotateLeft));
        view.add(item("menu.view.rotate-right", key(KeyEvent.VK_R, CTRL), presenter::rotateRight));
        view.add(item("menu.view.flip-horizontal", null, presenter::flipHorizontally));
        view.add(item("menu.view.flip-vertical", null, presenter::flipVertically));
        view.addSeparator();
        view.add(check("menu.view.sidebar", key(KeyEvent.VK_F9, 0), presenter::setSidebar));
        view.add(check("menu.view.film-strip", null, presenter::setFilmStrip));
        view.addSeparator();
        view.add(item("menu.view.full-screen", key(KeyEvent.VK_F11, 0), presenter::toggleFullScreen));
        view.add(item("menu.view.slideshow", key(KeyEvent.VK_F5, 0), presenter::slideshow));

        JMenu go = menu("menu.go");
        go.add(item("menu.go.previous", key(KeyEvent.VK_LEFT, 0), presenter::previous));
        go.add(item("menu.go.next", key(KeyEvent.VK_RIGHT, 0), presenter::next));
        go.add(item("menu.go.first", key(KeyEvent.VK_HOME, 0), presenter::first));
        go.add(item("menu.go.last", key(KeyEvent.VK_END, 0), presenter::last));

        JMenu options = menu("menu.options");
        options.add(check("menu.options.title", null, presenter::setTitleFromMetadata));
        options.add(check("menu.options.maps", null, presenter::setAlwaysShowMaps));
        JMenu slideshow = Mnemonics.apply(new JMenu(), msg.get("menu.options.slideshow"));
        slideshow.setName("menu.options.slideshow");
        ButtonGroup group = new ButtonGroup();
        for (int s : Settings.SLIDE_SECONDS) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(msg.format("slideshow.seconds", s));
            item.setName("slideshow.seconds." + s);
            item.addActionListener(e -> {
                if (!updating) presenter.setSlideSeconds(s);
            });
            group.add(item);
            seconds.put(s, item);
            slideshow.add(item);
        }
        slideshow.addSeparator();
        slideshow.add(check("menu.options.shuffle", null, presenter::setShuffle));
        slideshow.add(check("menu.options.loop", null, presenter::setLoop));
        options.add(slideshow);
        if (locales != null) {
            options.addSeparator();
            options.add(languageMenu(presenter, locales));
        }

        JMenu help = menu("menu.help");
        help.add(item("menu.help.about", null, presenter::about));
    }

    void update(Settings settings) {
        updating = true;
        try {
            checked("menu.view.sidebar", settings.sidebar());
            checked("menu.view.film-strip", settings.filmStrip());
            checked("menu.options.title", settings.titleFromMetadata());
            checked("menu.options.maps", settings.alwaysShowMaps());
            checked("menu.options.shuffle", settings.shuffle());
            checked("menu.options.loop", settings.loop());
            seconds.get(settings.slideSeconds()).setSelected(true);
        } finally {
            updating = false;
        }
    }

    // --8<-- [start:enable]
    /** Greys out what needs a picture, or another one to go to, or a turn to save. */
    void enable(Optional<Shown> shown) {
        boolean picture = shown.isPresent();
        for (String key : List.of("menu.image.print", "menu.image.trash", "menu.edit.copy", "menu.edit.copy-path",
                "menu.view.zoom-in", "menu.view.zoom-out", "menu.view.fit", "menu.view.actual",
                "menu.view.rotate-left", "menu.view.rotate-right", "menu.view.flip-horizontal",
                "menu.view.flip-vertical")) {
            items.get(key).setEnabled(picture);
        }
        int position = shown.map(Shown::position).orElse(-1);
        int count = shown.map(Shown::count).orElse(0);
        items.get("menu.image.save").setEnabled(shown.map(Shown::canSave).orElse(false));
        items.get("menu.image.edit").setEnabled(picture && canEdit);
        items.get("menu.image.edit").setToolTipText(canEdit ? null : msg.get("menu.image.edit.missing"));
        items.get("menu.go.previous").setEnabled(position > 0);
        items.get("menu.go.first").setEnabled(position > 0);
        items.get("menu.go.next").setEnabled(position >= 0 && position < count - 1);
        items.get("menu.go.last").setEnabled(position >= 0 && position < count - 1);
        items.get("menu.view.slideshow").setEnabled(count > 1);
    }
    // --8<-- [end:enable]

    JMenuItem item(String name) {
        return items.get(name);
    }

    private void checked(String key, boolean on) {
        items.get(key).setSelected(on);
    }

    /** "Desktop setting (Deutsch)" and the five languages; the choice applies at the next start. */
    private JMenu languageMenu(MainPresenter presenter, LocaleSettings locales) {
        JMenu menu = Mnemonics.apply(new JMenu(), msg.get("menu.options.language"));
        menu.setName("menu.options.language");
        Messages commons = Messages.commons(msg.locale());
        List<Optional<Language>> choices = new ArrayList<>();
        choices.add(Optional.empty());
        for (Language language : Language.values()) choices.add(Optional.of(language));
        ButtonGroup group = new ButtonGroup();
        Optional<Language> current = locales.applicationLanguage();
        for (Optional<Language> choice : choices) {
            String label = choice.map(Language::nativeName).orElseGet(() -> commons.format("language.follow-desktop",
                    locales.desktopResolution().language().nativeName()));
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(label, choice.equals(current));
            item.setName(choice.map(Language::code).orElse(LocaleSettings.FOLLOW_DESKTOP));
            item.addActionListener(e -> presenter.setLanguage(choice));
            group.add(item);
            menu.add(item);
            if (choice.isEmpty()) menu.addSeparator();
        }
        return menu;
    }

    private static KeyStroke key(int code, int modifiers) {
        return KeyStroke.getKeyStroke(code, modifiers);
    }

    private JMenu menu(String key) {
        JMenu menu = Mnemonics.apply(new JMenu(), msg.get(key));
        menu.setName(key);
        add(menu);
        return menu;
    }

    private JMenuItem item(String key, KeyStroke accelerator, Runnable action) {
        JMenuItem item = Mnemonics.apply(new JMenuItem(), msg.get(key));
        item.setName(key);
        if (accelerator != null) item.setAccelerator(accelerator);
        item.addActionListener(e -> action.run());
        items.put(key, item);
        return item;
    }

    private JCheckBoxMenuItem check(String key, KeyStroke accelerator, Consumer<Boolean> action) {
        JCheckBoxMenuItem item = Mnemonics.apply(new JCheckBoxMenuItem(), msg.get(key));
        item.setName(key);
        if (accelerator != null) item.setAccelerator(accelerator);
        item.addActionListener(e -> {
            if (!updating) action.accept(item.isSelected());
        });
        items.put(key, item);
        return item;
    }
}
