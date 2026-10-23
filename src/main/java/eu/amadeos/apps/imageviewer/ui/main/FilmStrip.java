package eu.amadeos.apps.imageviewer.ui.main;

import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

/** The row of small pictures under the big one. A click shows that picture. */
public final class FilmStrip extends JComponent {
    private static final int CELL_W = Thumbnails.SIZE + 12;
    // Thumbnails are up to SIZE high (square pictures, portraits), so the cell is too.
    private static final int CELL_H = Thumbnails.SIZE + 12;

    private final DefaultListModel<Path> model = new DefaultListModel<>();
    private final JList<Path> list = new JList<>(model);
    private final Thumbnails thumbnails;
    private IntConsumer onSelect = i -> { };
    private boolean updating;

    public FilmStrip(Thumbnails thumbnails) {
        this.thumbnails = thumbnails;
        setLayout(new BorderLayout());
        list.setName("filmStrip");
        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(1);
        list.setFixedCellWidth(CELL_W);
        list.setFixedCellHeight(CELL_H);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // The keyboard belongs to the picture: arrow keys page there, not in this list.
        list.setFocusable(false);
        list.setCellRenderer(new Cell());
        AmadeUI.style(list, (l, theme) -> l.setBackground(theme.sunkenBg()));
        list.addListSelectionListener(e -> {
            if (!updating && !e.getValueIsAdjusting() && list.getSelectedIndex() >= 0) {
                onSelect.accept(list.getSelectedIndex());
            }
        });
        JScrollPane scroll = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED) {
            // JScrollPane leaves the scrollbar out of its preferred height when the list could fit, so with many
            // pictures the bar would cover the bottom of the cells. Always keep room for it below the row.
            @Override
            public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                Insets in = getInsets();
                size.height = in.top + CELL_H + getHorizontalScrollBar().getPreferredSize().height + in.bottom;
                return size;
            }
        };
        scroll.setBorder(AmadeUI.sunken());
        scroll.getHorizontalScrollBar().setUnitIncrement(CELL_W);
        add(scroll, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
    }

    public void onSelect(IntConsumer listener) {
        onSelect = listener;
    }

    public void show(List<Path> files, int index) {
        updating = true;
        try {
            boolean same = files.size() == model.size();
            for (int i = 0; same && i < files.size(); i++) same = Objects.equals(files.get(i), model.get(i));
            if (!same) {
                model.clear();
                model.addAll(files);
            }
            if (index >= 0) {
                list.setSelectedIndex(index);
                list.ensureIndexIsVisible(index);
            } else {
                list.clearSelection();
            }
        } finally {
            updating = false;
        }
    }

    JScrollPane scrollPane() {
        return (JScrollPane) getComponent(0);
    }

    JList<Path> thumbnailList() {
        return list;
    }

    private final class Cell extends JComponent implements ListCellRenderer<Path> {
        private Path file;
        private boolean selected;

        @Override
        public Component getListCellRendererComponent(JList<? extends Path> l, Path value, int index,
                                                      boolean isSelected, boolean focus) {
            file = value;
            selected = isSelected;
            setToolTipText(value.getFileName().toString());
            return this;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0;
            BufferedImage thumb = thumbnails.get(file, list::repaint).orElse(null);
            int box = Thumbnails.SIZE;
            int x0 = (getWidth() - box) / 2, y0 = (getHeight() - box * 3 / 4) / 2;
            if (thumb != null) {
                int x = (getWidth() - thumb.getWidth()) / 2;
                int y = (getHeight() - thumb.getHeight()) / 2;
                g.drawImage(thumb, x, y, null);
            } else {
                g.setColor(new Color(0, 0, 0, 40));
                g.fillRect(x0 + 8, y0 + 8, box - 16, box * 3 / 4 - 16);
            }
            if (selected) {
                g.setColor(AmadeUI.theme().selectionBackground());
                g.setStroke(new BasicStroke(3));
                g.drawRect(2, 2, getWidth() - 5, getHeight() - 5);
            }
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(CELL_W, CELL_H);
        }
    }
}
