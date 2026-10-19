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

package com.volmit.iris.core.gui;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The map window's list of views, grouped under headers, each row showing the key that opens it.
 */
final class MapViewList extends JList<MapViewList.Row> {
    private static final long serialVersionUID = -4130872409914437415L;
    private static final int ROW_HEIGHT = 26;

    private final DefaultListModel<Row> rows = new DefaultListModel<>();
    private int lastViewRow;

    MapViewList(Consumer<MapView> onViewChosen) {
        fillRows();
        setModel(rows);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setFixedCellHeight(ROW_HEIGHT);
        setCellRenderer(new RowRenderer());
        setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
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
        private String shortcut;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused) {
            Row row = (Row) value;
            super.getListCellRendererComponent(list, row.isHeader() ? "" : row.view().label(), index, selected && !row.isHeader(), false);
            if (row.isHeader()) {
                setText(row.group().label().toUpperCase(Locale.ROOT));
                setFont(StudioTheme.sectionFont());
                setForeground(StudioTheme.MUTED_TEXT);
                setBorder(BorderFactory.createEmptyBorder(index == 0 ? 0 : 6, 6, 0, 6));
                setToolTipText(null);
                shortcut = null;
                return this;
            }
            setFont(StudioTheme.font());
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            setToolTipText(row.view().description());
            shortcut = row.view().shortcutText();
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (shortcut == null) {
                return;
            }
            Graphics2D canvas = (Graphics2D) graphics.create();
            try {
                canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                canvas.setFont(StudioTheme.smallFont());
                canvas.setColor(StudioTheme.MUTED_TEXT);
                int textWidth = canvas.getFontMetrics().stringWidth(shortcut);
                int baseline = (getHeight() + canvas.getFontMetrics().getAscent() - canvas.getFontMetrics().getDescent()) / 2;
                canvas.drawString(shortcut, getWidth() - getInsets().right - textWidth, baseline);
            } finally {
                canvas.dispose();
            }
        }
    }
}
