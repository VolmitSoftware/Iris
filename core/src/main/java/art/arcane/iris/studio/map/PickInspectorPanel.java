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

import art.arcane.iris.studio.map.components.PickDisplay;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.DefaultCaret;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The inspector of the region and biome views: how noise picks the regions or biomes in view, measured against what
 * is in view and put in words, with a switch for how the map draws the picks. What the user opens or closes stays so
 * while the view is measured again.
 */
final class PickInspectorPanel extends JPanel {
    private static final long serialVersionUID = -2874105263188240317L;
    private static final String MESSAGE_CARD = "message";
    private static final String DETAILS_CARD = "details";
    private static final int PANEL_WIDTH = 330;

    private final JLabel title = new JLabel();
    private final JLabel detail = new JLabel();
    private final Map<PickDisplay, JToggleButton> displayButtons = new EnumMap<>(PickDisplay.class);
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);
    private final JTextArea message = wrappingText();
    private final JPanel details = new ViewportWidthPanel();
    private final JScrollPane detailsScroll = StudioToolbar.scroll(details);
    private final Set<String> expandedSectionKeys = new HashSet<>();
    private final Set<String> fullTableKeys = new HashSet<>();
    private final String picksName;
    private boolean sectionsChosenByUser;
    private String shownDetailsTitle;

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
        cardPanel.add(detailsScroll, DETAILS_CARD);
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

    void showRegionPicks(NoisePickSpread spread) {
        showHeader("Regions", "Region noise, split by rarity");
        nameFirstDisplay("Regions");
        showDetails(List.of(noiseChart(spread, "Regions", "regions")));
    }

    void showBiomePicks(String regionName, List<NoisePickSpread> spreads) {
        showHeader(regionName, "Biome noise, split by rarity");
        nameFirstDisplay("Biomes");
        showDetails(spreads.stream().map(spread -> (JComponent) noiseChart(spread, "Biomes", spread.title())).toList());
    }

    /**
     * One collapsible section per region in view, each with how it picks its biomes. Until the user opens or closes
     * one, only the first is open.
     */
    void showRegionSections(String detailText, List<Section> sections) {
        showHeader(picksName, detailText);
        nameFirstDisplay(picksName);
        if (!sectionsChosenByUser) {
            expandedSectionKeys.clear();
            sections.stream().findFirst().ifPresent(first -> expandedSectionKeys.add(first.key()));
        }
        List<JComponent> components = new ArrayList<>();
        for (Section section : sections) {
            components.add(new SectionPanel(section, expandedSectionKeys.contains(section.key())));
        }
        showDetails(components);
    }

    /**
     * A chart whose long table remembers, by this key, whether the user asked for all its rows.
     */
    NoisePickChart noiseChart(NoisePickSpread spread, String optionNoun, String key) {
        return new NoisePickChart(spread, optionNoun, fullTableKeys.contains(key), showsAll -> {
            if (showsAll) {
                fullTableKeys.add(key);
            } else {
                fullTableKeys.remove(key);
            }
        });
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

    private void rememberSectionExpanded(String key, boolean expanded) {
        sectionsChosenByUser = true;
        if (expanded) {
            expandedSectionKeys.add(key);
        } else {
            expandedSectionKeys.remove(key);
        }
    }

    private void showMessage(String text) {
        message.setText(text);
        cards.show(cardPanel, MESSAGE_CARD);
    }

    // Measuring the same subject again keeps the scroll position, so panning the map doesn't jump the list.
    private void showDetails(List<JComponent> charts) {
        boolean sameSubject = Objects.equals(title.getText(), shownDetailsTitle);
        int scrollPosition = sameSubject ? detailsScroll.getVerticalScrollBar().getValue() : 0;
        shownDetailsTitle = title.getText();
        details.removeAll();
        charts.forEach(details::add);
        details.revalidate();
        details.repaint();
        cards.show(cardPanel, DETAILS_CARD);
        SwingUtilities.invokeLater(() -> detailsScroll.getVerticalScrollBar().setValue(scrollPosition));
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
     * @param share   how much of the view the region covers
     * @param summary what the closed section says about its content, like "7 biomes"
     */
    record Section(String key, String name, int color, double share, String summary, List<JComponent> charts) {
    }

    /**
     * A region's header, which opens and closes the charts below it.
     */
    private final class SectionPanel extends JPanel {
        private static final long serialVersionUID = 2318546098861530717L;

        private final JPanel body = new JPanel();

        private SectionPanel(Section section, boolean expanded) {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
            setAlignmentX(LEFT_ALIGNMENT);
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            body.setOpaque(false);
            body.setAlignmentX(LEFT_ALIGNMENT);
            section.charts().forEach(body::add);
            body.setVisible(expanded);
            add(new SectionHeader(section, body::isVisible, () -> toggle(section)));
            add(body);
        }

        private void toggle(Section section) {
            body.setVisible(!body.isVisible());
            rememberSectionExpanded(section.key(), body.isVisible());
            revalidate();
            repaint();
        }
    }

    /**
     * A region's name in its colour behind an open or closed chevron, with what it holds and how much of the view it
     * covers; clicking it opens or closes the region.
     */
    private static final class SectionHeader extends JComponent {
        private static final long serialVersionUID = -4730226930154581123L;
        private static final int HEIGHT = 32;
        private static final int MARGIN = 16;
        private static final int CHEVRON_WIDTH = 16;
        // The chevron icon's glyph sits in its middle, with this much empty space either side.
        private static final int CHEVRON_GLYPH_INSET = 4;
        private static final int SWATCH_SIZE = 12;
        private static final int SWATCH_GAP = 8;
        private static final int SUMMARY_GAP = 8;

        private final Section section;
        private final BooleanSupplier expanded;

        private SectionHeader(Section section, BooleanSupplier expanded, Runnable onToggle) {
            this.section = section;
            this.expanded = expanded;
            setAlignmentX(LEFT_ALIGNMENT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    onToggle.run();
                    repaint();
                }
            });
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
                int left = StudioTheme.scale(MARGIN - CHEVRON_GLYPH_INSET);
                paintChevron(canvas, left);
                left += StudioTheme.scale(CHEVRON_WIDTH - CHEVRON_GLYPH_INSET + SWATCH_GAP);
                int swatch = StudioTheme.scale(SWATCH_SIZE);
                canvas.setColor(new Color(section.color()));
                int arc = StudioTheme.scale(4);
                canvas.fillRoundRect(left, (getHeight() - swatch) / 2, swatch, swatch, arc, arc);
                left += swatch + StudioTheme.scale(SWATCH_GAP);

                canvas.setFont(StudioTheme.smallFont());
                FontMetrics small = canvas.getFontMetrics();
                String share = NoisePickChart.percent(section.share());
                int shareLeft = getWidth() - StudioTheme.scale(MARGIN) - small.stringWidth(share);
                canvas.setFont(StudioTheme.boldFont());
                FontMetrics bold = canvas.getFontMetrics();
                int baseline = (getHeight() + bold.getAscent() - bold.getDescent()) / 2;
                canvas.setColor(StudioTheme.TEXT);
                canvas.drawString(section.name(), left, baseline);
                left += bold.stringWidth(section.name()) + StudioTheme.scale(SUMMARY_GAP);

                canvas.setFont(StudioTheme.smallFont());
                canvas.setColor(StudioTheme.MUTED_TEXT);
                if (section.summary() != null && left + small.stringWidth(section.summary()) < shareLeft - StudioTheme.scale(SUMMARY_GAP)) {
                    canvas.drawString(section.summary(), left, baseline);
                }
                canvas.setColor(StudioTheme.SECONDARY_TEXT);
                canvas.drawString(share, shareLeft, baseline);
            } finally {
                canvas.dispose();
            }
        }

        private void paintChevron(Graphics2D canvas, int left) {
            Icon chevron = UIManager.getIcon(expanded.getAsBoolean() ? "Tree.expandedIcon" : "Tree.collapsedIcon");
            if (chevron == null) {
                return;
            }
            chevron.paintIcon(this, canvas, left + (StudioTheme.scale(CHEVRON_WIDTH) - chevron.getIconWidth()) / 2,
                    (getHeight() - chevron.getIconHeight()) / 2);
        }
    }
}
