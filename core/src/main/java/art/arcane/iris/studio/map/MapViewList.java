/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.studio.map;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The map window's list of views, grouped under headers, each row with a picture of the view and the key that opens
 * it.
 */
final class MapViewList extends JList<MapViewList.Row> {
    private static final long serialVersionUID = -4130872409914437415L;
    private static final int ROW_HEIGHT = 28;
    private static final int NO_ROW = -1;

    private final DefaultListModel<Row> rows = new DefaultListModel<>();
    private int lastViewRow;
    private int hoveredRow = NO_ROW;

    MapViewList(Consumer<MapView> onViewChosen) {
        fillRows();
        setModel(rows);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setFixedCellHeight(StudioTheme.scale(ROW_HEIGHT));
        setCellRenderer(new RowRenderer());
        setBorder(StudioTheme.emptyBorder(4, 6, 4, 6));
        addListSelectionListener(event -> {
            if (event.getValueIsAdjusting() || skipHeaderRow()) {
                return;
            }
            Row selected = getSelectedValue();
            if (selected == null) {
                return;
            }
            lastViewRow = getSelectedIndex();
            onViewChosen.accept(selected.view());
        });
        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                hover(viewRowAt(event));
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hover(NO_ROW);
            }
        };
        addMouseListener(hover);
        addMouseMotionListener(hover);
    }

    private int viewRowAt(MouseEvent event) {
        int row = locationToIndex(event.getPoint());
        if (row < 0 || rows.get(row).isHeader() || !getCellBounds(row, row).contains(event.getPoint())) {
            return NO_ROW;
        }
        return row;
    }

    private void hover(int row) {
        if (row == hoveredRow) {
            return;
        }
        repaintRow(hoveredRow);
        hoveredRow = row;
        repaintRow(hoveredRow);
    }

    private void repaintRow(int row) {
        Rectangle bounds = row == NO_ROW ? null : getCellBounds(row, row);
        if (bounds != null) {
            repaint(bounds);
        }
    }

    void select(MapView view) {
        for (int row = 0; row < rows.size(); row++) {
            if (rows.get(row).view() == view) {
                lastViewRow = row;
                setSelectedIndex(row);
                ensureIndexIsVisible(row);
                return;
            }
        }
    }

    private void fillRows() {
        MapView.Group currentGroup = null;
        for (MapView view : MapView.values()) {
            if (view.group() != currentGroup) {
                currentGroup = view.group();
                rows.addElement(Row.header(currentGroup));
            }
            rows.addElement(Row.of(view));
        }
    }

    // Headers can't be chosen: clicking one keeps the current view, and the arrow keys carry on past it.
    private boolean skipHeaderRow() {
        int row = getSelectedIndex();
        if (row < 0 || !rows.get(row).isHeader()) {
            return false;
        }
        int step = row >= lastViewRow ? 1 : -1;
        int next = nextViewRow(row, step);
        if (next < 0) {
            next = nextViewRow(row, -step);
        }
        setSelectedIndex(next < 0 ? lastViewRow : next);
        ensureIndexIsVisible(getSelectedIndex());
        return true;
    }

    private int nextViewRow(int from, int step) {
        for (int row = from + step; row >= 0 && row < rows.size(); row += step) {
            if (!rows.get(row).isHeader()) {
                return row;
            }
        }
        return -1;
    }

    record Row(MapView.Group group, MapView view) {
        static Row header(MapView.Group group) {
            return new Row(group, null);
        }

        static Row of(MapView view) {
            return new Row(view.group(), view);
        }

        boolean isHeader() {
            return view == null;
        }
    }

    private static final class RowRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 6302156810211578474L;
        private static final int ICON_TEXT_GAP = 8;
        private static final int KEYCAP_SIZE = 17;
        private static final int KEYCAP_ARC = 5;
        private String shortcut;
        private boolean hovered;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused) {
            Row row = (Row) value;
            super.getListCellRendererComponent(list, row.isHeader() ? "" : row.view().label(), index, selected && !row.isHeader(), false);
            if (row.isHeader()) {
                setText(row.group().label().toUpperCase(Locale.ROOT));
                setFont(StudioTheme.sectionFont());
                setForeground(StudioTheme.MUTED_TEXT);
                setBorder(StudioTheme.emptyBorder(index == 0 ? 2 : 10, 8, 0, 6));
                setIcon(null);
                setToolTipText(null);
                shortcut = null;
                hovered = false;
                setOpaque(true);
                return this;
            }
            setFont(StudioTheme.font());
            setBorder(StudioTheme.emptyBorder(0, 8, 0, 8));
            setIcon(MapViewIcon.of(row.view()));
            setIconTextGap(StudioTheme.scale(ICON_TEXT_GAP));
            setToolTipText(row.view().description());
            shortcut = row.view().shortcutText();
            hovered = !selected && list instanceof MapViewList viewList && viewList.hoveredRow == index;
            setOpaque(!hovered);
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D canvas = (Graphics2D) graphics.create();
            try {
                canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                if (hovered) {
                    paintHoverBackground(canvas);
                }
                super.paintComponent(graphics);
                if (shortcut != null) {
                    paintKeycap(canvas);
                }
            } finally {
                canvas.dispose();
            }
        }

        // Shaped like the selection the look and feel paints, so moving between them doesn't jump.
        private void paintHoverBackground(Graphics2D canvas) {
            Insets inset = UIManager.getInsets("List.selectionInsets");
            int left = inset == null ? 0 : StudioTheme.scale(inset.left);
            int right = inset == null ? 0 : StudioTheme.scale(inset.right);
            int arc = StudioTheme.scale(UIManager.getInt("List.selectionArc"));
            canvas.setColor(StudioTheme.HOVER_BACKGROUND);
            canvas.fillRoundRect(left, 0, getWidth() - left - right, getHeight(), arc, arc);
        }

        private void paintKeycap(Graphics2D canvas) {
            canvas.setFont(StudioTheme.smallFont());
            int textWidth = canvas.getFontMetrics().stringWidth(shortcut);
            int height = StudioTheme.scale(KEYCAP_SIZE);
            int width = Math.max(height, textWidth + StudioTheme.scale(8));
            int x = getWidth() - getInsets().right - width;
            int y = (getHeight() - height) / 2;
            int arc = StudioTheme.scale(KEYCAP_ARC);
            canvas.setColor(StudioTheme.CONTROL_BORDER);
            canvas.drawRoundRect(x, y, width - 1, height - 1, arc, arc);
            canvas.setColor(StudioTheme.SECONDARY_TEXT);
            int baseline = y + (height + canvas.getFontMetrics().getAscent() - canvas.getFontMetrics().getDescent()) / 2;
            canvas.drawString(shortcut, x + (width - textWidth) / 2, baseline);
        }
    }
}
