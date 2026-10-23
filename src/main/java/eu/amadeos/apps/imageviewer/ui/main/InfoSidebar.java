package eu.amadeos.apps.imageviewer.ui.main;

import eu.amadeos.apps.imageviewer.domain.GeoPosition;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The information sidebar: the sections of {@link InfoRows} and, for photos with a position, the map, or before
 * it is allowed, the note that loading it sends the position to openstreetmap.org.
 */
public final class InfoSidebar extends JPanel {
    /** What the sidebar's buttons do. */
    public interface Actions {
        void showMap();

        void setAlwaysShowMaps(boolean on);

        void openMapInBrowser();

        void copyPosition();

        void showAllMetadata();
    }

    static final int WIDTH = 300;

    private final Messages msg;
    private final InfoRows rows;
    private final Actions actions;
    private final Supplier<MapView> maps;
    private final JPanel content = new JPanel();
    private MapView map;

    public InfoSidebar(Messages msg, Actions actions, Supplier<MapView> maps) {
        super(new BorderLayout());
        this.msg = msg;
        this.rows = new InfoRows(msg);
        this.actions = actions;
        this.maps = maps;
        setName("sidebar");
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
        JScrollPane scroll = new JScrollPane(content, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
        setBorder(AmadeUI.raised());
        setPreferredSize(new Dimension(Math.round(WIDTH * AmadeUI.fontScale()), 10));
    }

    // --8<-- [start:show]
    public void show(Optional<Shown> shown) {
        content.removeAll();
        if (shown.isEmpty()) {
            content.add(note(msg.get("info.none")));
        } else {
            for (InfoRows.Section section : rows.sections(shown.get())) {
                content.add(heading(section.title()));
                content.add(rowPanel(section.rows()));
            }
            shown.get().picture().info().position().ifPresent(position -> location(position, shown.get().mapAllowed()));
            content.add(Box.createVerticalStrut(10));
            content.add(link(msg.get("info.all"), "info.all", actions::showAllMetadata));
        }
        content.add(Box.createVerticalGlue());
        content.revalidate();
        content.repaint();
    }

    /** Under the Location rows: the map, or the question whether it may load. */
    private void location(GeoPosition position, boolean allowed) {
        content.add(Box.createVerticalStrut(6));
        if (allowed) {
            if (map == null) map = maps.get();
            map.show(position);
            JPanel frame = new JPanel(new BorderLayout());
            frame.setBorder(AmadeUI.sunken());
            frame.add(map);
            fixWidth(frame);
            content.add(frame);
        } else {
            content.add(mapQuestion());
        }
        content.add(Box.createVerticalStrut(6));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.add(button("info.map.browser", actions::openMapInBrowser));
        buttons.add(Box.createHorizontalStrut(6));
        buttons.add(button("info.map.copy", actions::copyPosition));
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        content.add(buttons);
    }
    // --8<-- [end:show]

    // --8<-- [start:question]
    private JComponent mapQuestion() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setName("info.map.question");
        AmadeUI.style(panel, (p, theme) -> p.setBackground(theme.sunkenBg()));
        panel.setBorder(BorderFactory.createCompoundBorder(AmadeUI.sunken(), BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        panel.add(note(msg.get("info.map.privacy")), BorderLayout.NORTH);
        JPanel show = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        show.setOpaque(false);
        show.add(button("info.map.show", actions::showMap));
        panel.add(show, BorderLayout.CENTER);
        JCheckBox always = Mnemonics.apply(new JCheckBox(), msg.get("info.map.always"));
        always.setName("info.map.always");
        always.setOpaque(false);
        always.setFocusable(false);
        always.addActionListener(e -> actions.setAlwaysShowMaps(always.isSelected()));
        panel.add(always, BorderLayout.SOUTH);
        fixWidth(panel);
        return panel;
    }
    // --8<-- [end:question]

    private JComponent heading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        AmadeUI.style(label, (l, theme) -> l.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(8, 0, 2, 0), BorderFactory.createMatteBorder(0, 0, 1, 0, theme.shadow()))));
        label.setAlignmentX(LEFT_ALIGNMENT);
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
        return label;
    }

    private JComponent rowPanel(List<InfoRows.Row> list) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(1, 0, 1, 8);
        int labelWidth = Math.round(92 * AmadeUI.fontScale());
        for (int i = 0; i < list.size(); i++) {
            c.gridy = i;
            c.gridx = 0;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            JLabel label = new JLabel(list.get(i).label());
            AmadeUI.style(label, (l, theme) -> l.setForeground(theme.textInactive()));
            label.setPreferredSize(new Dimension(labelWidth, label.getPreferredSize().height));
            panel.add(label, c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            panel.add(value(list.get(i).value()), c);
        }
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        return panel;
    }

    /** A value that wraps and can be selected and copied, like a label without its limits. */
    private static JTextArea value(String text) {
        JTextArea area = new JTextArea(text);
        area.setName("info.value");
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setBorder(BorderFactory.createEmptyBorder());
        area.setFont(new JLabel().getFont());
        area.setFocusable(false);
        area.setColumns(14);
        return area;
    }

    private static JTextArea note(String text) {
        JTextArea area = value(text);
        area.setName("info.note");
        area.setAlignmentX(LEFT_ALIGNMENT);
        return area;
    }

    private JButton button(String key, Runnable action) {
        JButton button = Mnemonics.apply(new JButton(), msg.get(key));
        button.setName(key);
        // Space and the arrow keys stay with the picture.
        button.setFocusable(false);
        button.addActionListener(e -> action.run());
        return button;
    }

    private JComponent link(String text, String name, Runnable action) {
        JButton button = new JButton("<html><u>" + text + "</u></html>");
        button.setName(name);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusable(false);
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        AmadeUI.style(button, (b, theme) -> b.setForeground(theme.selectionBackground()));
        button.addActionListener(e -> action.run());
        button.setAlignmentX(LEFT_ALIGNMENT);
        return button;
    }

    private static void fixWidth(JComponent component) {
        component.setAlignmentX(LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
    }

    /** The texts shown, for tests. */
    List<String> texts() {
        List<String> texts = new java.util.ArrayList<>();
        collect(content, texts);
        return texts;
    }

    private static void collect(Component c, List<String> texts) {
        if (c instanceof JLabel l) texts.add(l.getText());
        if (c instanceof JTextArea a) texts.add(a.getText());
        if (c instanceof JButton b) texts.add(b.getName());
        if (c instanceof MapView) texts.add("map");
        if (c instanceof java.awt.Container container && !(c instanceof MapView)) {
            for (Component child : container.getComponents()) collect(child, texts);
        }
    }

    Optional<MapView> map() {
        return Optional.ofNullable(map);
    }
}
