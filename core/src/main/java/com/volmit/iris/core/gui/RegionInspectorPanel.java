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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.util.List;

/**
 * The region view's inspector: for the chosen region, which of its biomes covers which ground height, and what that
 * means in words.
 */
final class RegionInspectorPanel extends JPanel {
    private static final long serialVersionUID = -2874105263188240317L;
    private static final String MESSAGE_CARD = "message";
    private static final String COVERAGE_CARD = "coverage";
    private static final int PANEL_WIDTH = 330;
    private static final String PARAGRAPH_BREAK = "\n\n";

    private final JLabel title = new JLabel();
    private final JLabel detail = new JLabel();
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);
    private final JTextArea message = wrappingText();
    private final RegionHeightChart chart = new RegionHeightChart();
    private final JTextArea notes = wrappingText();

    RegionInspectorPanel() {
        super(new BorderLayout());
        setPreferredSize(StudioTheme.scaledSize(PANEL_WIDTH, 0));
        setBackground(StudioTheme.PANEL_BACKGROUND);
        add(buildHeader(), BorderLayout.NORTH);
        cardPanel.setOpaque(false);
        cardPanel.add(buildMessageCard(), MESSAGE_CARD);
        cardPanel.add(StudioToolbar.scroll(buildCoverageCard()), COVERAGE_CARD);
        add(cardPanel, BorderLayout.CENTER);
        showNothingChosen();
    }

    void showNothingChosen() {
        showHeader("Regions", "Every region in its own colour");
        showMessage("Pick a region on the left to fill the map with its biomes and see which biome covers which height.");
    }

    void showMeasuring(String regionName) {
        showHeader(regionName, "Measuring the ground in view…");
    }

    void showBiomeTerrain(String regionName) {
        showHeader(regionName, "Biome terrain");
        showMessage("This dimension uses BIOME terrain, where biomes are placed by noise and ignore height. "
                + "Set the dimension's terrainMode to HYBRID to choose biomes by the height of the ground.");
    }

    void showCoverage(String regionName, RegionHeightCoverage coverage) {
        showHeader(regionName, "Hybrid terrain · biomes by ground height");
        chart.show(coverage);
        notes.setText(notesText(coverage.notes()));
        cards.show(cardPanel, COVERAGE_CARD);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new GridLayout(0, 1, 0, StudioTheme.scale(2)));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, StudioTheme.SEPARATOR),
                StudioTheme.emptyBorder(10, 16, 9, 16)));
        title.setFont(StudioTheme.titleFont());
        detail.setFont(StudioTheme.smallFont());
        detail.setForeground(StudioTheme.MUTED_TEXT);
        header.add(title);
        header.add(detail);
        return header;
    }

    private JPanel buildMessageCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(false);
        message.setBorder(StudioTheme.emptyBorder(12, 16, 12, 16));
        card.add(message, BorderLayout.NORTH);
        return card;
    }

    private JPanel buildCoverageCard() {
        JPanel card = new ViewportWidthPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(StudioTheme.PANEL_BACKGROUND);
        chart.setAlignmentX(Component.LEFT_ALIGNMENT);
        notes.setAlignmentX(Component.LEFT_ALIGNMENT);
        notes.setBorder(StudioTheme.emptyBorder(0, 16, 12, 16));
        card.add(chart);
        card.add(notes);
        return card;
    }

    private void showHeader(String regionName, String detailText) {
        title.setText(regionName);
        detail.setText(detailText);
    }

    private void showMessage(String text) {
        message.setText(text);
        cards.show(cardPanel, MESSAGE_CARD);
    }

    private static String notesText(List<String> lines) {
        if (lines.isEmpty()) {
            return "Every ground height in view has a biome, and no two ranged biomes share one.";
        }
        return String.join(PARAGRAPH_BREAK, lines);
    }

    private static JTextArea wrappingText() {
        JTextArea text = new JTextArea();
        text.setEditable(false);
        text.setFocusable(false);
        text.setOpaque(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(StudioTheme.font());
        text.setForeground(StudioTheme.SECONDARY_TEXT);
        return text;
    }

    /**
     * Follows the scroll pane's width, so the chart stretches to it and the notes wrap inside it.
     */
    private static final class ViewportWidthPanel extends JPanel implements Scrollable {
        private static final long serialVersionUID = 6086542174870301352L;
        private static final int SCROLL_STEP = 18;

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return StudioTheme.scale(SCROLL_STEP);
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
