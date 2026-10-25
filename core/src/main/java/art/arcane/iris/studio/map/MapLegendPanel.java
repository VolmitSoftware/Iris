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

import art.arcane.iris.studio.map.components.MapStyle;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The biomes or regions on screen with how much of it each covers; clicking one highlights it on the map.
 */
final class MapLegendPanel extends JPanel {
    private static final long serialVersionUID = 4471297416386034128L;
    private static final int PANEL_WIDTH = 250;
    private static final int ROW_HEIGHT = 26;
    private static final int SWATCH_SIZE = 12;
    private static final int SWATCH_GAP = 8;
    private static final int ROW_PADDING = 10;
    private static final int PERCENT_WIDTH = 42;
    private static final String DRAWING_MESSAGE = "Drawing the map…";

    private final JLabel title = new JLabel();
    private final DefaultListModel<MapCensus.Share> rows = new DefaultListModel<>();
    private final JList<MapCensus.Share> list = new JList<>(rows) {
        // Long names shorten with an ellipsis instead of pushing the percentages out of sight.
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }
    };
    private final Set<String> sharedNames = new HashSet<>();
    private MapStyle.Highlight highlight;

    MapLegendPanel(Consumer<MapCensus.Share> onRowClicked) {
        this();
        highlightOnClick(onRowClicked);
    }

    /**
     * A legend whose rows only tell what is on screen; clicking them does nothing.
     */
    static MapLegendPanel listingOnly() {
        return new MapLegendPanel();
    }

    private MapLegendPanel() {
        super(new BorderLayout());
        setPreferredSize(StudioTheme.scaledSize(PANEL_WIDTH, 0));
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, StudioTheme.SEPARATOR));
        setBackground(StudioTheme.PANEL_BACKGROUND);

        title.setFont(StudioTheme.sectionFont());
        title.setForeground(StudioTheme.MUTED_TEXT);
        title.setBorder(StudioTheme.emptyBorder(10, 16, 6, 16));
        add(title, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFocusable(false);
        list.setFixedCellHeight(StudioTheme.scale(ROW_HEIGHT));
        list.setCellRenderer(new ShareRenderer());
        list.setBackground(StudioTheme.PANEL_BACKGROUND);
        list.setBorder(StudioTheme.emptyBorder(0, 6, 4, 6));
        add(StudioToolbar.scroll(list), BorderLayout.CENTER);
    }

    private void highlightOnClick(Consumer<MapCensus.Share> onRowClicked) {
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                MapCensus.Share share = shareAt(event.getPoint());
                if (share != null) {
                    onRowClicked.accept(share);
                }
            }
        });
        JLabel hint = new JLabel("Click to highlight · Esc clears");
        hint.setFont(StudioTheme.smallFont());
        hint.setForeground(StudioTheme.MUTED_TEXT);
        hint.setBorder(StudioTheme.emptyBorder(6, 16, 8, 16));
        add(hint, BorderLayout.SOUTH);
    }

    void show(String subjectTitle, List<MapCensus.Share> shares, MapStyle.Highlight nextHighlight) {
        title.setText(subjectTitle.toUpperCase(Locale.ROOT));
        highlight = nextHighlight;
        rememberSharedNames(shares);
        if (!sameRows(shares)) {
            rows.clear();
            rows.addAll(shares);
        }
        list.repaint();
    }

    // Two biomes can share a display name; those rows show their key so they can be told apart.
    private void rememberSharedNames(List<MapCensus.Share> shares) {
        Set<String> seen = new HashSet<>();
        sharedNames.clear();
        for (MapCensus.Share share : shares) {
            if (!seen.add(share.label().name())) {
                sharedNames.add(share.label().name());
            }
        }
    }

    private String rowText(MapCensus.Share share) {
        String name = share.label().name();
        return sharedNames.contains(name) ? name + " (" + share.label().key() + ")" : name;
    }

    private boolean sameRows(List<MapCensus.Share> shares) {
        if (rows.size() != shares.size()) {
            return false;
        }
        for (int index = 0; index < shares.size(); index++) {
            if (!rows.get(index).equals(shares.get(index))) {
                return false;
            }
        }
        return true;
    }

    private MapCensus.Share shareAt(Point point) {
        int index = list.locationToIndex(point);
        if (index < 0) {
            return null;
        }
        Rectangle bounds = list.getCellBounds(index, index);
        return bounds != null && bounds.contains(point) ? rows.get(index) : null;
    }

    private boolean isHighlighted(MapCensus.Share share) {
        return highlight != null
                && highlight.kind() == share.label().kind()
                && highlight.key().equals(share.label().key());
    }

    @Override
    protected void paintChildren(Graphics graphics) {
        super.paintChildren(graphics);
        if (!rows.isEmpty()) {
            return;
        }
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            canvas.setFont(StudioTheme.font());
            canvas.setColor(StudioTheme.MUTED_TEXT);
            canvas.drawString(DRAWING_MESSAGE, StudioTheme.scale(16),
                    title.getHeight() + StudioTheme.scale(4) + canvas.getFontMetrics().getAscent());
        } finally {
            canvas.dispose();
        }
    }

    private final class ShareRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = -2350212934617052618L;
        private Color swatch = Color.BLACK;
        private String percent = "";

        @Override
        public Component getListCellRendererComponent(JList<?> owner, Object value, int index, boolean selected, boolean focused) {
            MapCensus.Share share = (MapCensus.Share) value;
            super.getListCellRendererComponent(owner, rowText(share), index, isHighlighted(share), false);
            setFont(StudioTheme.font());
            setBorder(StudioTheme.emptyBorder(0, ROW_PADDING + SWATCH_SIZE + SWATCH_GAP, 0, ROW_PADDING + PERCENT_WIDTH));
            setToolTipText(share.label().key());
            swatch = new Color(share.color());
            percent = formatPercent(share.fraction());
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D canvas = (Graphics2D) graphics.create();
            try {
                canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int size = StudioTheme.scale(SWATCH_SIZE);
                int swatchX = StudioTheme.scale(ROW_PADDING);
                int swatchY = (getHeight() - size) / 2;
                int arc = StudioTheme.scale(4);
                canvas.setColor(swatch);
                canvas.fillRoundRect(swatchX, swatchY, size, size, arc, arc);
                canvas.setColor(StudioTheme.CONTROL_BORDER);
                canvas.drawRoundRect(swatchX, swatchY, size - 1, size - 1, arc, arc);
                canvas.setFont(StudioTheme.smallFont());
                canvas.setColor(StudioTheme.SECONDARY_TEXT);
                int textWidth = canvas.getFontMetrics().stringWidth(percent);
                int baseline = (getHeight() + canvas.getFontMetrics().getAscent() - canvas.getFontMetrics().getDescent()) / 2;
                canvas.drawString(percent, getWidth() - StudioTheme.scale(ROW_PADDING) - textWidth, baseline);
            } finally {
                canvas.dispose();
            }
        }
    }

    private static String formatPercent(double fraction) {
        double percent = fraction * 100D;
        if (percent < 0.1D) {
            return "<0.1%";
        }
        return percent < 10D
                ? String.format(Locale.ROOT, "%.1f%%", percent)
                : String.format(Locale.ROOT, "%.0f%%", percent);
    }
}
