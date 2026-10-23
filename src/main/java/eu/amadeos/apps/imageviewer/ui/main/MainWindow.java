package eu.amadeos.apps.imageviewer.ui.main;

import eu.amadeos.apps.imageviewer.bootstrap.AmadeImageViewerApplication;
import eu.amadeos.apps.imageviewer.domain.ImageFiles;
import eu.amadeos.apps.imageviewer.domain.PhotoInfo;
import eu.amadeos.apps.imageviewer.domain.Settings;
import eu.amadeos.commons.desktop.ImageSelection;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.print.PaperSize;
import eu.amadeos.commons.print.PrintJobs;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.commons.swing.StandardGlyph;
import eu.amadeos.commons.swing.ToolButtons;
import eu.amadeos.commons.swing.ToolIcon;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.print.PrinterException;
import java.io.File;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The window: toolbar, the picture, the information sidebar on the right, the film strip and the status bar
 * below. Full screen and the slideshow use a {@link FullScreenWindow} of their own.
 */
public final class MainWindow extends JFrame implements MainView {
    private final Messages msg;
    private final LocaleSettings locales;
    private final Thumbnails thumbnails;
    private final Supplier<MapView> maps;
    private final ImageCanvas canvas = new ImageCanvas();
    private final JLabel status = new JLabel(" ");
    private final Map<String, Action> actions = new HashMap<>();
    private FilmStrip filmStrip;
    private InfoSidebar sidebar;
    private JToggleButton infoButton;
    private MainPresenter presenter;
    private MainMenuBar menuBar;
    private FullScreenWindow fullScreen;
    private Settings settings = Settings.DEFAULT;
    private Shown shown;
    private double scale = 1;

    public MainWindow(Messages msg, LocaleSettings locales, Thumbnails thumbnails, Supplier<MapView> maps) {
        super(msg.get("window.title"));
        EdtExecutor.requireEdt();
        this.msg = msg;
        this.locales = locales;
        this.thumbnails = thumbnails;
        this.maps = maps;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        java.net.URL icon = getClass().getResource("/icons/amade-imageviewer.png");
        if (icon != null) setIconImage(new javax.swing.ImageIcon(icon).getImage());
    }

    public void bind(MainPresenter newPresenter) {
        EdtExecutor.requireEdt();
        presenter = newPresenter;
        menuBar = new MainMenuBar(presenter, msg, locales);
        setJMenuBar(menuBar);

        JPanel picture = new JPanel(new BorderLayout());
        picture.setBorder(AmadeUI.sunken());
        picture.add(canvas, BorderLayout.CENTER);
        canvas.setName("canvas");
        canvas.setListener(canvasListener(false));
        sidebar = new InfoSidebar(msg, sidebarActions(), maps);
        JPanel centre = new JPanel(new BorderLayout(4, 0));
        centre.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
        centre.add(picture, BorderLayout.CENTER);
        centre.add(sidebar, BorderLayout.EAST);

        filmStrip = new FilmStrip(thumbnails);
        filmStrip.onSelect(presenter::select);
        status.setName("status");
        status.setBorder(BorderFactory.createCompoundBorder(AmadeUI.sunken(), BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));
        statusBar.add(status);
        JPanel south = new JPanel(new BorderLayout());
        south.add(filmStrip, BorderLayout.CENTER);
        south.add(statusBar, BorderLayout.SOUTH);

        add(toolBar(), BorderLayout.NORTH);
        add(centre, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        bindKeys(getRootPane(), false);
        setTransferHandler(dropHandler());
        canvas.setTransferHandler(getTransferHandler());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                presenter.exit();
            }
        });
        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                if (getFocusOwner() == null || getFocusOwner() == MainWindow.this) canvas.requestFocusInWindow();
            }
        });
        setSize(Math.round(1080 * AmadeUI.fontScale()), Math.round(760 * AmadeUI.fontScale()));
        setMinimumSize(new Dimension(480, 360));
        setLocationRelativeTo(null);
    }

    // ---------------------------------------------------------------- toolbar and keys

    private JToolBar toolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(ToolButtons.button(action("image.open", StandardGlyph.OPEN, presenter::open)));
        bar.addSeparator();
        bar.add(ToolButtons.button(action("go.previous", ViewerGlyph.PREVIOUS, presenter::previous)));
        bar.add(ToolButtons.button(action("go.next", ViewerGlyph.NEXT, presenter::next)));
        bar.addSeparator();
        bar.add(ToolButtons.button(action("view.zoom-out", StandardGlyph.ZOOM_OUT, () -> presenter.zoom(ZoomCommand.OUT))));
        bar.add(ToolButtons.button(action("view.zoom-in", StandardGlyph.ZOOM_IN, () -> presenter.zoom(ZoomCommand.IN))));
        bar.add(ToolButtons.button(action("view.fit", ViewerGlyph.FIT, () -> presenter.zoom(ZoomCommand.FIT))));
        bar.add(ToolButtons.button(action("view.actual", ViewerGlyph.ACTUAL_SIZE, () -> presenter.zoom(ZoomCommand.ACTUAL))));
        bar.addSeparator();
        bar.add(ToolButtons.button(action("view.rotate-left", ViewerGlyph.ROTATE_LEFT, presenter::rotateLeft)));
        bar.add(ToolButtons.button(action("view.rotate-right", ViewerGlyph.ROTATE_RIGHT, presenter::rotateRight)));
        bar.addSeparator();
        bar.add(ToolButtons.button(action("view.slideshow", ViewerGlyph.SLIDESHOW, presenter::slideshow)));
        bar.add(ToolButtons.button(action("view.full-screen", ViewerGlyph.FULL_SCREEN, presenter::toggleFullScreen)));
        bar.add(Box.createHorizontalGlue());
        Action info = action("view.sidebar", ViewerGlyph.INFO, () -> presenter.setSidebar(infoButton.isSelected()));
        infoButton = ToolButtons.toggle(info);
        bar.add(infoButton);
        return bar;
    }

    private Action action(String key, ToolIcon.Glyph glyph, Runnable run) {
        AbstractAction action = new AbstractAction(msg.get("tool." + key)) {
            @Override
            public void actionPerformed(ActionEvent e) {
                run.run();
            }
        };
        action.putValue(Action.ACTION_COMMAND_KEY, "tool." + key);
        action.putValue(Action.SHORT_DESCRIPTION, msg.get("tool." + key));
        action.putValue(Action.LARGE_ICON_KEY, new ToolIcon(glyph));
        actions.put(key, action);
        return action;
    }

    // --8<-- [start:keys]
    /**
     * Keys that are not in the menus, and in full screen (which has no menu bar) the menu keys as well.
     */
    private void bindKeys(JRootPane root, boolean full) {
        Map<KeyStroke, Runnable> keys = new LinkedHashMap<>();
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), presenter::space);
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), presenter::escape);
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), presenter::previous);
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), presenter::previous);
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), presenter::next);
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, 0), () -> presenter.zoom(ZoomCommand.IN));
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, 0), () -> presenter.zoom(ZoomCommand.IN));
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, 0), () -> presenter.zoom(ZoomCommand.IN));
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, 0), () -> presenter.zoom(ZoomCommand.OUT));
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, 0), () -> presenter.zoom(ZoomCommand.OUT));
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, InputEvent.CTRL_DOWN_MASK), () -> presenter.zoom(ZoomCommand.IN));
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK), () -> presenter.zoom(ZoomCommand.IN));
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, InputEvent.CTRL_DOWN_MASK), () -> presenter.zoom(ZoomCommand.OUT));
        if (full) {
            keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), presenter::previous);
            keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), presenter::next);
            keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), presenter::first);
            keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_END, 0), presenter::last);
            keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), presenter::slideshow);
            keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0), presenter::toggleFullScreen);
            keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, InputEvent.CTRL_DOWN_MASK), () -> presenter.zoom(ZoomCommand.IN));
            keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK), () -> presenter.zoom(ZoomCommand.OUT));
            keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK), () -> presenter.zoom(ZoomCommand.FIT));
            keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK), () -> presenter.zoom(ZoomCommand.ACTUAL));
            keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK), presenter::rotateLeft);
            keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK), presenter::rotateRight);
            keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), presenter::copyImage);
            keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK), presenter::exit);
        }
        int i = 0;
        for (Map.Entry<KeyStroke, Runnable> key : keys.entrySet()) {
            String name = "key." + i++;
            root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(key.getKey(), name);
            root.getActionMap().put(name, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    key.getValue().run();
                }
            });
        }
    }
    // --8<-- [end:keys]

    private ImageCanvas.Listener canvasListener(boolean full) {
        return new ImageCanvas.Listener() {
            @Override
            public void browse(int direction) {
                if (direction > 0) presenter.next();
                else presenter.previous();
            }

            @Override
            public void doubleClicked() {
                presenter.toggleFullScreen();
            }

            @Override
            public void clicked(double x) {
                // In full screen the left and right thirds page, as on a tablet.
                if (!full) return;
                if (x < 1 / 3.0) presenter.previous();
                else if (x > 2 / 3.0) presenter.next();
            }

            @Override
            public void scaleChanged(double newScale) {
                scale = newScale;
                updateStatus();
            }
        };
    }

    private InfoSidebar.Actions sidebarActions() {
        return new InfoSidebar.Actions() {
            @Override
            public void showMap() {
                presenter.allowMap();
            }

            @Override
            public void setAlwaysShowMaps(boolean on) {
                presenter.setAlwaysShowMaps(on);
            }

            @Override
            public void openMapInBrowser() {
                presenter.openMapInBrowser();
            }

            @Override
            public void copyPosition() {
                presenter.copyPosition();
            }

            @Override
            public void showAllMetadata() {
                presenter.showAllMetadata();
            }
        };
    }

    // --8<-- [start:drop]
    /** Pictures or folders dropped from the file manager open like from the Open dialog. */
    private TransferHandler dropHandler() {
        return new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                try {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    presenter.open(files.stream().map(File::toPath).toList());
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        };
    }
    // --8<-- [end:drop]

    // ---------------------------------------------------------------- MainView

    @Override
    public void showSettings(Settings newSettings) {
        settings = newSettings;
        menuBar.update(newSettings);
        infoButton.setSelected(newSettings.sidebar());
        sidebar.setVisible(newSettings.sidebar());
        filmStrip.setVisible(newSettings.filmStrip());
        if (fullScreen != null) fullScreen.showSlideshow(presenter.slideshowRunning(), false, newSettings.slideSeconds());
        revalidate();
    }

    @Override
    public void showAlbum(List<Path> files, int index) {
        filmStrip.show(files, index);
    }

    @Override
    public void showPicture(Shown newShown) {
        shown = newShown;
        canvas.show(newShown.picture().image(), newShown.orientation());
        if (fullScreen != null) fullScreen.canvas().show(newShown.picture().image(), newShown.orientation());
        sidebar.show(Optional.of(newShown));
        setTitle(msg.format("window.title.picture", newShown.title()));
        menuBar.enable(Optional.of(newShown));
        enableTools();
        updateStatus();
    }

    @Override
    public void showNoPicture(String title, String message) {
        shown = null;
        canvas.showMessage(message);
        if (fullScreen != null) fullScreen.canvas().showMessage(message);
        sidebar.show(Optional.empty());
        setTitle(title.equals(msg.get("window.title")) ? title : msg.format("window.title.picture", title));
        menuBar.enable(Optional.empty());
        enableTools();
        updateStatus();
    }

    private void enableTools() {
        boolean picture = shown != null;
        for (String key : List.of("view.zoom-out", "view.zoom-in", "view.fit", "view.actual", "view.rotate-left",
                "view.rotate-right", "view.full-screen")) {
            actions.get(key).setEnabled(picture);
        }
        actions.get("go.previous").setEnabled(picture && shown.position() > 0);
        actions.get("go.next").setEnabled(picture && shown.position() < shown.count() - 1);
        actions.get("view.slideshow").setEnabled(picture && shown.count() > 1);
    }

    private void updateStatus() {
        if (shown == null) {
            status.setText(" ");
            return;
        }
        String percent = NumberFormat.getIntegerInstance(msg.locale()).format(Math.round(activeCanvas().scale() * 100));
        status.setText(msg.format("status", shown.picture().path().getFileName().toString(),
                String.valueOf(shown.width()), String.valueOf(shown.height()), percent, shown.position() + 1,
                shown.count()));
    }

    private ImageCanvas activeCanvas() {
        return fullScreen != null ? fullScreen.canvas() : canvas;
    }

    // --8<-- [start:full-screen]
    @Override
    public void showFullScreen(boolean on) {
        if (on && fullScreen == null) {
            fullScreen = new FullScreenWindow(msg, new FullScreenWindow.Controls() {
                @Override
                public void previous() {
                    presenter.previous();
                }

                @Override
                public void next() {
                    presenter.next();
                }

                @Override
                public void slideshow() {
                    presenter.slideshow();
                }

                @Override
                public void leave() {
                    presenter.escape();
                }

                @Override
                public void setSlideSeconds(int seconds) {
                    presenter.setSlideSeconds(seconds);
                }
            });
            fullScreen.canvas().setListener(canvasListener(true));
            bindKeys(fullScreen.getRootPane(), true);
            if (shown != null) fullScreen.canvas().show(shown.picture().image(), shown.orientation());
            fullScreen.showSlideshow(presenter.slideshowRunning(), false, settings.slideSeconds());
            fullScreen.open(getGraphicsConfiguration());
        } else if (!on && fullScreen != null) {
            fullScreen.close();
            fullScreen = null;
            toFront();
            canvas.requestFocusInWindow();
        }
        updateStatus();
    }
    // --8<-- [end:full-screen]

    @Override
    public void showSlideshow(boolean running, boolean paused) {
        if (fullScreen != null) fullScreen.showSlideshow(running, paused, settings.slideSeconds());
    }

    @Override
    public void zoom(ZoomCommand command) {
        activeCanvas().zoom(command);
    }

    @Override
    public Optional<List<Path>> chooseFiles(Path folder) {
        JFileChooser chooser = new JFileChooser(folder == null ? null : folder.toFile());
        chooser.setDialogTitle(msg.get("dialog.open.title"));
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || ImageFiles.isImage(f.toPath());
            }

            @Override
            public String getDescription() {
                return msg.get("dialog.open.filter");
            }
        });
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return Optional.empty();
        File[] files = chooser.getSelectedFiles();
        if (files.length == 0 && chooser.getSelectedFile() != null) files = new File[]{chooser.getSelectedFile()};
        List<Path> paths = new ArrayList<>();
        for (File f : files) paths.add(f.toPath());
        return paths.isEmpty() ? Optional.empty() : Optional.of(paths);
    }

    @Override
    public boolean confirmTrash(String fileName) {
        return JOptionPane.showConfirmDialog(dialogParent(), msg.format("dialog.trash.text", fileName),
                msg.get("dialog.trash.title"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE)
                == JOptionPane.YES_OPTION;
    }

    @Override
    public void copyImage(BufferedImage image) {
        ImageSelection.copy(image);
    }

    @Override
    public void copyText(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    @Override
    public void print(BufferedImage image, String jobName) {
        PaperSize paper = PaperSize.forLocale(msg.locale());
        try {
            PrintJobs.printWithDialog(new PicturePrinter(image).book(paper), paper, jobName);
        } catch (PrinterException e) {
            showError(msg.format("error.print", e.getMessage()));
        }
    }

    @Override
    public void showAllMetadata(String fileName, List<PhotoInfo.Tag> tags) {
        DefaultTableModel model = new DefaultTableModel(new Object[]{msg.get("metadata.directory"),
                msg.get("metadata.name"), msg.get("metadata.value")}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (PhotoInfo.Tag tag : tags) model.addRow(new Object[]{tag.directory(), tag.name(), tag.value()});
        JTable table = new JTable(model);
        table.setName("metadata.table");
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(110);
        table.getColumnModel().getColumn(1).setPreferredWidth(180);
        table.getColumnModel().getColumn(2).setPreferredWidth(330);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(Math.round(640 * AmadeUI.fontScale()), Math.round(420 * AmadeUI.fontScale())));
        JOptionPane.showMessageDialog(dialogParent(), scroll, msg.format("metadata.title", fileName),
                JOptionPane.PLAIN_MESSAGE);
    }

    @Override
    public void beep() {
        Toolkit.getDefaultToolkit().beep();
    }

    @Override
    public void showError(String message) {
        JOptionPane.showMessageDialog(dialogParent(), plain(message), msg.get("dialog.error.title"),
                JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void showInfo(String message) {
        JOptionPane.showMessageDialog(dialogParent(), plain(message), msg.get("window.title"),
                JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void showAbout() {
        JOptionPane.showMessageDialog(this,
                "<html><center><h2>amade-imageviewer " + AmadeImageViewerApplication.version() + "</h2>"
                        + "<p>" + msg.get("dialog.about.text") + "</p><br>"
                        + "<small>" + msg.get("dialog.about.libraries") + "</small><br><br>"
                        + "<small>&copy; 2026 datazuul.com &bull; EUPL-1.2</small></center></html>",
                msg.get("dialog.about.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void close() {
        if (fullScreen != null) {
            fullScreen.close();
            fullScreen = null;
        }
        dispose();
    }

    /** Dialogs open over the full-screen window while it shows. */
    private java.awt.Component dialogParent() {
        return fullScreen != null ? fullScreen : this;
    }

    private static JTextArea plain(String message) {
        JTextArea text = new JTextArea(message);
        text.setEditable(false);
        text.setOpaque(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setColumns(40);
        return text;
    }

    // ---------------------------------------------------------------- for tests

    MainMenuBar menu() {
        return menuBar;
    }

    ImageCanvas canvas() {
        return canvas;
    }

    InfoSidebar sidebar() {
        return sidebar;
    }

    FilmStrip filmStrip() {
        return filmStrip;
    }

    String statusText() {
        return status.getText();
    }

    Optional<FullScreenWindow> fullScreenWindow() {
        return Optional.ofNullable(fullScreen);
    }

    Action toolAction(String key) {
        return actions.get(key);
    }

    double shownScale() {
        return scale;
    }
}
