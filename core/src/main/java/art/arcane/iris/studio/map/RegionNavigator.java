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
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The region view's sidebar: every region of the pack, or all of them at once, to fill the map with.
 */
final class RegionNavigator extends JPanel {
    private static final long serialVersionUID = 3150735486722101466L;
    private static final RegionRow ALL_REGIONS = new RegionRow(null, "All regions", -1);
    private static final int ROW_HEIGHT = 24;
    private static final int SWATCH_SIZE = 12;
    private static final int SWATCH_GAP = 8;
    private static final int ROW_PADDING = 10;

    private final Consumer<String> onRegionChosen;
    private final JTextField searchField = new JTextField();
    private final DefaultListModel<RegionRow> rows = new DefaultListModel<>();
    private final JList<RegionRow> list = new JList<>(rows);
    private List<RegionRow> regions = List.of();
    private String chosenKey;
    private boolean updatingRows;

    /**
     * @param onRegionChosen receives the chosen region's key, or null for all regions
     */
    RegionNavigator(Consumer<String> onRegionChosen) {
        super(new BorderLayout());
        this.onRegionChosen = onRegionChosen;
        StudioToolbar.makeSearchField(searchField, "Search regions...");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                showMatchingRows();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                showMatchingRows();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                showMatchingRows();
            }
        });
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(StudioTheme.scale(ROW_HEIGHT));
        list.setCellRenderer(new RegionRowRenderer());
        list.addListSelectionListener(event -> {
            RegionRow row = list.getSelectedValue();
            if (!event.getValueIsAdjusting() && !updatingRows && row != null && !Objects.equals(row.key(), chosenKey)) {
                chosenKey = row.key();
                onRegionChosen.accept(chosenKey);
            }
        });
        add(StudioToolbar.padded(searchField, 8, 8, 8, 8), BorderLayout.NORTH);
        add(StudioToolbar.scroll(list), BorderLayout.CENTER);
        showMatchingRows();
    }

    void showRegions(List<RegionRow> nextRegions) {
        regions = List.copyOf(nextRegions);
        showMatchingRows();
    }

    private void showMatchingRows() {
        String filter = searchField.getText().trim().toLowerCase(Locale.ROOT);
        updatingRows = true;
        try {
            rows.clear();
            rows.addElement(ALL_REGIONS);
            for (RegionRow region : regions) {
                if (filter.isEmpty() || region.name().toLowerCase(Locale.ROOT).contains(filter)
                        || region.key().toLowerCase(Locale.ROOT).contains(filter)) {
                    rows.addElement(region);
                }
            }
            selectChosenRow();
        } finally {
            updatingRows = false;
        }
    }

    private void selectChosenRow() {
        for (int index = 0; index < rows.size(); index++) {
            if (Objects.equals(rows.get(index).key(), chosenKey)) {
                list.setSelectedIndex(index);
                return;
            }
        }
        list.clearSelection();
    }

    /**
     * @param color the region's map colour, or -1 for the all regions row
     */
    record RegionRow(String key, String name, int color) {
    }

    private static final class RegionRowRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = -6114522950207432516L;
        private Color swatch;

        @Override
        public Component getListCellRendererComponent(JList<?> owner, Object value, int index, boolean selected, boolean focused) {
            RegionRow row = (RegionRow) value;
            super.getListCellRendererComponent(owner, row.name(), index, selected, false);
            setFont(row.key() == null ? StudioTheme.boldFont() : StudioTheme.font());
            setBorder(StudioTheme.emptyBorder(0, ROW_PADDING + SWATCH_SIZE + SWATCH_GAP, 0, ROW_PADDING));
            setToolTipText(row.key());
            swatch = row.color() < 0 ? null : new Color(row.color());
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (swatch == null) {
                return;
            }
            Graphics2D canvas = (Graphics2D) graphics.create();
            try {
                canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int size = StudioTheme.scale(SWATCH_SIZE);
                int x = StudioTheme.scale(ROW_PADDING);
                int y = (getHeight() - size) / 2;
                int arc = StudioTheme.scale(4);
                canvas.setColor(swatch);
                canvas.fillRoundRect(x, y, size, size, arc, arc);
                canvas.setColor(StudioTheme.CONTROL_BORDER);
                canvas.drawRoundRect(x, y, size - 1, size - 1, arc, arc);
            } finally {
                canvas.dispose();
            }
        }
    }
}
