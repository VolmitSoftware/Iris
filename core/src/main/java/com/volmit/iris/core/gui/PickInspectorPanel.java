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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The inspector of the region and biome views: how noise picks the regions or biomes in view, measured against what
 * is in view and put in words, with a switch for how the map draws the picks.
 */
final class PickInspectorPanel extends JPanel {
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
    private final String picksName;

    /**
     * @param picksName       what the map shows when it draws the picks: "Regions" or "Biomes"
     * @param onDisplayChosen receives how the map should draw the picks
     */
    PickInspectorPanel(String picksName, Consumer<PickDisplay> onDisplayChosen) {
        super(new BorderLayout());
        this.picksName = picksName;
        setPreferredSize(StudioTheme.scaledSize(PANEL_WIDTH, 0));
        setBackground(StudioTheme.PANEL_BACKGROUND);
        add(buildHeader(onDisplayChosen), BorderLayout.NORTH);
        cardPanel.setOpaque(false);
        cardPanel.add(buildMessageCard(), MESSAGE_CARD);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setBackground(StudioTheme.PANEL_BACKGROUND);
        cardPanel.add(StudioToolbar.scroll(details), DETAILS_CARD);
        add(cardPanel, BorderLayout.CENTER);
        showMeasuring(picksName);
    }

    void selectDisplay(PickDisplay display) {
        displayButtons.get(display).setSelected(true);
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
        showDetails(List.of(heightChart(coverage)), notes);
    }

    /**
     * One section per region in view, each with how it picks its biomes, then notes about all of them.
     */
    void showRegionSections(String detailText, List<Section> sections, List<String> notes) {
        showHeader(picksName, detailText);
        nameFirstDisplay(picksName);
        List<JComponent> components = new ArrayList<>();
        for (Section section : sections) {
            components.add(new SectionHeader(section));
            components.addAll(section.charts());
            if (!section.notes().isEmpty()) {
                components.add(notesText(section.notes()));
            }
        }
        showDetails(components, notes);
    }

    static RegionHeightChart heightChart(RegionHeightCoverage coverage) {
        RegionHeightChart chart = new RegionHeightChart();
        chart.setAlignmentX(LEFT_ALIGNMENT);
        chart.show(coverage);
        return chart;
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

    private String displayName(PickDisplay display) {
        return switch (display) {
            case PICKS -> picksName;
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
            details.add(notesText(notes));
        }
        details.revalidate();
        details.repaint();
        details.scrollRectToVisible(new Rectangle());
        cards.show(cardPanel, DETAILS_CARD);
    }

    private static JTextArea notesText(List<String> notes) {
        JTextArea text = wrappingText();
        text.setAlignmentX(LEFT_ALIGNMENT);
        text.setBorder(StudioTheme.emptyBorder(4, 16, 12, 16));
        text.setText(String.join(PARAGRAPH_BREAK, notes));
        return text;
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
     * One region of the biome view's inspector.
     *
     * @param share how much of the view the region covers
     */
    record Section(String name, int color, double share, List<JComponent> charts, List<String> notes) {
    }

    /**
     * A region's name in its colour above its charts, with how much of the view it covers.
     */
    private static final class SectionHeader extends JComponent {
        private static final long serialVersionUID = -4730226930154581123L;
        private static final int HEIGHT = 34;
        private static final int MARGIN = 16;
        private static final int SWATCH_SIZE = 12;
        private static final int SWATCH_GAP = 8;

        private final Section section;

        private SectionHeader(Section section) {
            this.section = section;
            setAlignmentX(LEFT_ALIGNMENT);
        }

        @Override
        public Dimension getPreferredSize() {
            return StudioTheme.scaledSize(PANEL_WIDTH, HEIGHT);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D canvas = (Graphics2D) graphics.create();
            try {
                canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                canvas.setColor(StudioTheme.SEPARATOR);
                canvas.fillRect(0, 0, getWidth(), 1);
                int margin = StudioTheme.scale(MARGIN);
                int swatch = StudioTheme.scale(SWATCH_SIZE);
                canvas.setColor(new Color(section.color()));
                int arc = StudioTheme.scale(4);
                canvas.fillRoundRect(margin, (getHeight() - swatch) / 2, swatch, swatch, arc, arc);
                canvas.setFont(StudioTheme.boldFont());
                FontMetrics metrics = canvas.getFontMetrics();
                int baseline = (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2;
                canvas.setColor(StudioTheme.TEXT);
                canvas.drawString(section.name(), margin + swatch + StudioTheme.scale(SWATCH_GAP), baseline);
                String share = NoisePickChart.percent(section.share()) + " of view";
                canvas.setFont(StudioTheme.smallFont());
                canvas.setColor(StudioTheme.SECONDARY_TEXT);
                canvas.drawString(share, getWidth() - margin - canvas.getFontMetrics().stringWidth(share), baseline);
            } finally {
                canvas.dispose();
            }
        }
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
