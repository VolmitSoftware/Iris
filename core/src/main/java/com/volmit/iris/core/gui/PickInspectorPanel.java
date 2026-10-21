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

import com.volmit.iris.core.gui.components.PickDisplay;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.text.DefaultCaret;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The region view's inspector: how the noise picks the regions, or for a chosen region how its biomes are picked,
 * measured against what is in view and put in words.
 */
final class RegionInspectorPanel extends JPanel {
    private static final long serialVersionUID = -2874105263188240317L;
    private static final String MESSAGE_CARD = "message";
    private static final String DETAILS_CARD = "details";
    private static final int PANEL_WIDTH = 330;
    private static final String PARAGRAPH_BREAK = "\n\n";

    private final JLabel title = new JLabel();
    private final JLabel detail = new JLabel();
    private final Map<PickDisplay, JToggleButton> displayButtons = new EnumMap<>(PickDisplay.class);
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);
    private final JTextArea message = wrappingText();
    private final JPanel details = new ViewportWidthPanel();
    private final RegionHeightChart heightChart = new RegionHeightChart();

    /**
     * @param onDisplayChosen receives how the map should draw the picks
     */
    RegionInspectorPanel(Consumer<PickDisplay> onDisplayChosen) {
        super(new BorderLayout());
        setPreferredSize(StudioTheme.scaledSize(PANEL_WIDTH, 0));
        setBackground(StudioTheme.PANEL_BACKGROUND);
        add(buildHeader(onDisplayChosen), BorderLayout.NORTH);
        cardPanel.setOpaque(false);
        cardPanel.add(buildMessageCard(), MESSAGE_CARD);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setBackground(StudioTheme.PANEL_BACKGROUND);
        heightChart.setAlignmentX(LEFT_ALIGNMENT);
        cardPanel.add(StudioToolbar.scroll(details), DETAILS_CARD);
        add(cardPanel, BorderLayout.CENTER);
        showMeasuring("Regions");
    }

    void showMeasuring(String name) {
        showHeader(name, "Measuring what is in view…");
        showMessage("");
    }

    void showRegionPicks(NoisePickSpread spread, List<String> notes) {
        showHeader("Regions", "Region noise, split by rarity");
        nameFirstDisplay("Regions");
        showDetails(List.of(new NoisePickChart(spread, "Regions")), notes);
    }

    void showBiomePicks(String regionName, List<NoisePickSpread> spreads, List<String> notes) {
        showHeader(regionName, "Biome terrain · biome noise, split by rarity");
        nameFirstDisplay("Biomes");
        showDetails(spreads.stream().map(spread -> (JComponent) new NoisePickChart(spread, "Biomes")).toList(), notes);
    }

    void showCoverage(String regionName, RegionHeightCoverage coverage, List<String> notes) {
        showHeader(regionName, "Hybrid terrain · biomes by height");
        nameFirstDisplay("Biomes");
        heightChart.show(coverage);
        showDetails(List.of(heightChart), notes);
    }

    private JPanel buildHeader(Consumer<PickDisplay> onDisplayChosen) {
        JPanel header = new JPanel(new GridLayout(0, 1, 0, StudioTheme.scale(6)));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, StudioTheme.SEPARATOR),
                StudioTheme.emptyBorder(10, 16, 12, 16)));
        title.setFont(StudioTheme.titleFont());
        detail.setFont(StudioTheme.smallFont());
        detail.setForeground(StudioTheme.MUTED_TEXT);
        header.add(title);
        header.add(detail);
        header.add(sectionLabel("Show"));
        header.add(buildDisplayButtons(onDisplayChosen));
        return header;
    }

    private JPanel buildDisplayButtons(Consumer<PickDisplay> onDisplayChosen) {
        JPanel row = new JPanel(new GridLayout(1, 0, StudioTheme.scale(4), 0));
        row.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        for (PickDisplay display : PickDisplay.values()) {
            JToggleButton button = new JToggleButton(displayName(display));
            button.setFocusable(false);
            button.setSelected(display == PickDisplay.PICKS);
            button.addActionListener(event -> onDisplayChosen.accept(display));
            group.add(button);
            row.add(button);
            displayButtons.put(display, button);
        }
        return row;
    }

    private static String displayName(PickDisplay display) {
        return switch (display) {
            case PICKS -> "Regions";
            case NOISE -> "Noise";
            case BOTH -> "Both";
        };
    }

    private void nameFirstDisplay(String name) {
        displayButtons.get(PickDisplay.PICKS).setText(name);
    }

    private JPanel buildMessageCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(false);
        message.setBorder(StudioTheme.emptyBorder(12, 16, 12, 16));
        card.add(message, BorderLayout.NORTH);
        return card;
    }

    private void showHeader(String name, String detailText) {
        title.setText(name);
        detail.setText(detailText);
    }

    private void showMessage(String text) {
        message.setText(text);
        cards.show(cardPanel, MESSAGE_CARD);
    }

    private void showDetails(List<JComponent> charts, List<String> notes) {
        details.removeAll();
        charts.forEach(details::add);
        if (!notes.isEmpty()) {
            JTextArea notesText = wrappingText();
            notesText.setAlignmentX(LEFT_ALIGNMENT);
            notesText.setBorder(StudioTheme.emptyBorder(4, 16, 12, 16));
            notesText.setText(String.join(PARAGRAPH_BREAK, notes));
            details.add(notesText);
        }
        details.revalidate();
        details.repaint();
        details.scrollRectToVisible(new Rectangle());
        cards.show(cardPanel, DETAILS_CARD);
    }

    private static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text.toUpperCase(Locale.ROOT));
        label.setForeground(StudioTheme.MUTED_TEXT);
        label.setFont(StudioTheme.sectionFont());
        return label;
    }

    private static JTextArea wrappingText() {
        JTextArea text = new JTextArea();
        text.setEditable(false);
        text.setFocusable(false);
        text.setOpaque(false);
        ((DefaultCaret) text.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(StudioTheme.font());
        text.setForeground(StudioTheme.SECONDARY_TEXT);
        return text;
    }

    /**
     * Follows the scroll pane's width, so the charts stretch to it and the notes wrap inside it.
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
