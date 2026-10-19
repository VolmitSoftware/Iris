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
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;

/**
 * The chosen object's size and the blocks it is made of, most used first, like the overview in Procedural Objects.
 */
final class ObjectOverviewPanel extends JPanel {
    private static final long serialVersionUID = -8811407613645718290L;
    private static final int PANEL_WIDTH = 280;
    private static final int ROW_HEIGHT = 26;
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 8;
    private static final int ROW_PADDING = 10;
    private static final int SHARE_WIDTH = 92;
    private static final int SHARE_BAR_HEIGHT = 4;

    private final JLabel title = new JLabel(" ");
    private final JLabel path = new JLabel(" ");
    private final JLabel size = statValue();
    private final JLabel blockCount = statValue();
    private final JLabel blockTypes = statValue();
    private final JLabel filled = statValue();
    private final JTextField filterField = new JTextField();
    private final DefaultListModel<ObjectSummary.BlockUse> rows = new DefaultListModel<>();
    private final JList<ObjectSummary.BlockUse> list = new JList<>(rows) {
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }
    };
    private ObjectSummary summary;

    ObjectOverviewPanel() {
        super(new BorderLayout());
        setPreferredSize(StudioTheme.scaledSize(PANEL_WIDTH, 0));
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, StudioTheme.SEPARATOR));
        setBackground(StudioTheme.PANEL_BACKGROUND);
        add(buildHeader(), BorderLayout.NORTH);
        add(buildBlockList(), BorderLayout.CENTER);
    }

    void show(ObjectSummary nextSummary) {
        summary = nextSummary;
        title.setText(nextSummary.name());
        path.setText(nextSummary.key());
        path.setToolTipText(nextSummary.key());
        size.setText(nextSummary.sizeX() + " × " + nextSummary.sizeY() + " × " + nextSummary.sizeZ());
        blockCount.setText(formatCount(nextSummary.blockCount()));
        blockTypes.setText(formatCount(nextSummary.blockUses().size()));
        filled.setText(String.format(Locale.ROOT, "%.1f%%", nextSummary.filledFraction() * 100D));
        refilter();
    }

    void clear() {
        summary = null;
        title.setText(" ");
        path.setText(" ");
        for (JLabel value : List.of(size, blockCount, blockTypes, filled)) {
            value.setText("—");
        }
        rows.clear();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.setBorder(StudioTheme.emptyBorder(12, 16, 8, 16));
        title.setFont(StudioTheme.titleFont());
        path.setFont(StudioTheme.smallFont());
        path.setForeground(StudioTheme.SECONDARY_TEXT);
        header.add(leftAligned(title));
        header.add(leftAligned(path));

        JPanel stats = new JPanel(new GridLayout(2, 2, StudioTheme.scale(12), StudioTheme.scale(8)));
        stats.setOpaque(false);
        stats.setBorder(StudioTheme.emptyBorder(12, 0, 4, 0));
        stats.add(stat("Size · X × Y × Z", size));
        stats.add(stat("Blocks", blockCount));
        stats.add(stat("Block types", blockTypes));
        stats.add(stat("Filled volume", filled));
        filled.setToolTipText("How much of the object's bounding box its blocks fill");
        header.add(leftAligned(stats));
        clear();
        return header;
    }

    private JPanel buildBlockList() {
        JPanel blocks = new JPanel(new BorderLayout());
        blocks.setOpaque(false);
        StudioToolbar.makeSearchField(filterField, "Find a block...");
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                refilter();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                refilter();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                refilter();
            }
        });
        list.setFocusable(false);
        list.setFixedCellHeight(StudioTheme.scale(ROW_HEIGHT));
        list.setCellRenderer(new BlockUseRenderer());
        list.setBackground(StudioTheme.PANEL_BACKGROUND);
        list.setBorder(StudioTheme.emptyBorder(0, 6, 4, 6));
        blocks.add(StudioToolbar.padded(filterField, 4, 12, 6, 12), BorderLayout.NORTH);
        blocks.add(StudioToolbar.scroll(list), BorderLayout.CENTER);
        return blocks;
    }

    private void refilter() {
        rows.clear();
        if (summary == null) {
            return;
        }
        String filter = filterField.getText().trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        for (ObjectSummary.BlockUse use : summary.blockUses()) {
            if (use.blockType().contains(filter)) {
                rows.addElement(use);
            }
        }
    }

    private static JPanel stat(String label, JLabel value) {
        JPanel stat = new JPanel(new BorderLayout());
        stat.setOpaque(false);
        JLabel name = new JLabel(label);
        name.setFont(StudioTheme.smallFont());
        name.setForeground(StudioTheme.MUTED_TEXT);
        stat.add(name, BorderLayout.NORTH);
        stat.add(value, BorderLayout.CENTER);
        return stat;
    }

    private static JLabel statValue() {
        JLabel value = new JLabel("—");
        value.setFont(StudioTheme.boldFont());
        return value;
    }

    private static Component leftAligned(JPanel panel) {
        panel.setAlignmentX(LEFT_ALIGNMENT);
        return panel;
    }

    private static Component leftAligned(JLabel label) {
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private static String formatCount(int count) {
        return String.format(Locale.ROOT, "%,d", count);
    }

    private final class BlockUseRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 2785337045812613941L;
        private transient BufferedImage icon;
        private double share;
        private String percent = "";

        @Override
        public Component getListCellRendererComponent(JList<?> owner, Object value, int index, boolean selected, boolean focused) {
            ObjectSummary.BlockUse use = (ObjectSummary.BlockUse) value;
            super.getListCellRendererComponent(owner, use.blockType(), index, false, false);
            setFont(StudioTheme.font());
            setBorder(StudioTheme.emptyBorder(0, ROW_PADDING + ICON_SIZE + ICON_GAP, 0, ROW_PADDING + SHARE_WIDTH));
            setToolTipText(use.blockType() + " · " + formatCount(use.count()) + " blocks");
            icon = use.icon();
            share = summary == null || summary.blockCount() == 0 ? 0D : use.count() / (double) summary.blockCount();
            percent = share > 0D && share < 0.001D ? "<0.1%" : String.format(Locale.ROOT, "%.1f%%", share * 100D);
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D canvas = (Graphics2D) graphics.create();
            try {
                int iconSize = StudioTheme.scale(ICON_SIZE);
                int iconX = StudioTheme.scale(ROW_PADDING);
                int iconY = (getHeight() - iconSize) / 2;
                if (icon != null) {
                    canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    canvas.drawImage(icon, iconX, iconY, iconSize, iconSize, null);
                } else {
                    canvas.setColor(StudioTheme.CONTROL_BORDER);
                    canvas.drawRect(iconX, iconY, iconSize - 1, iconSize - 1);
                }
                drawShare(canvas);
            } finally {
                canvas.dispose();
            }
        }

        private void drawShare(Graphics2D canvas) {
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            canvas.setFont(StudioTheme.smallFont());
            int padding = StudioTheme.scale(ROW_PADDING);
            int percentWidth = canvas.getFontMetrics().stringWidth("100.0%");
            int barWidth = StudioTheme.scale(SHARE_WIDTH) - percentWidth - StudioTheme.scale(6);
            int barX = getWidth() - padding - StudioTheme.scale(SHARE_WIDTH);
            int barHeight = StudioTheme.scale(SHARE_BAR_HEIGHT);
            int barY = (getHeight() - barHeight) / 2;
            canvas.setColor(StudioTheme.HOVER_BACKGROUND);
            canvas.fillRoundRect(barX, barY, barWidth, barHeight, barHeight, barHeight);
            canvas.setColor(StudioTheme.ACCENT);
            canvas.fillRoundRect(barX, barY, Math.max(barHeight, (int) Math.round(barWidth * share)), barHeight, barHeight, barHeight);
            canvas.setColor(StudioTheme.SECONDARY_TEXT);
            int textWidth = canvas.getFontMetrics().stringWidth(percent);
            int baseline = (getHeight() + canvas.getFontMetrics().getAscent() - canvas.getFontMetrics().getDescent()) / 2;
            canvas.drawString(percent, getWidth() - padding - textWidth, baseline);
        }
    }
}
