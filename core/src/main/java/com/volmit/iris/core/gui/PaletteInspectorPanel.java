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
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


final class PaletteInspectorPanel extends JPanel {
    private static final long serialVersionUID = 4473102938518712001L;
    private static final int PANEL_WIDTH = 310;
    private static final Color HISTOGRAM_BAR = new Color(0x9D, 0xA0, 0xA8, 150);
    private static final Color BAND_DIVIDER = new Color(255, 255, 255, 70);

    private final Listener listener;
    private final JLabel titleLabel = new JLabel();
    private final JLabel detailLabel = new JLabel();
    private final JLabel depthLabel = new JLabel("", SwingConstants.CENTER);
    private final JButton shallowerButton = new JButton("‹");
    private final JButton deeperButton = new JButton("›");
    private final JPanel depthRow = new JPanel(new BorderLayout(StudioTheme.scale(6), 0));
    private final StatisticsView statisticsView = new StatisticsView();
    private PaletteLayerScale.View selectedView = PaletteLayerScale.View.BLOCKS;
    private int depth;
    private int deepestDepth;

    PaletteInspectorPanel(Listener listener) {
        super(new BorderLayout());
        this.listener = listener;
        setPreferredSize(StudioTheme.scaledSize(PANEL_WIDTH, 0));
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, StudioTheme.SEPARATOR));
        add(buildControls(), BorderLayout.NORTH);
        add(StudioToolbar.scroll(statisticsView), BorderLayout.CENTER);
        setVisible(false);
    }

    void showPreview(PaletteLayerPreview preview) {
        titleLabel.setText(preview.biomeKey() + " · layer " + preview.layer());
        detailLabel.setText(thicknessText(preview) + " · zoom " + formatZoom(preview.zoom())
                + " (×" + formatZoom(preview.zoom() * preview.zoom()) + " effective)");
        depth = preview.depth();
        deepestDepth = Math.max(0, preview.maxThickness() - 1);
        refreshDepthControls();
        statisticsView.show(preview);
        setVisible(true);
    }

    void showTextureStatus(String status) {
        statisticsView.showTextureStatus(status);
    }

    void hideInspector() {
        setVisible(false);
    }

    PaletteLayerScale.View selectedView() {
        return selectedView;
    }

    private JPanel buildControls() {
        JPanel controls = new JPanel(new GridLayout(0, 1, 0, StudioTheme.scale(6)));
        controls.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, StudioTheme.SEPARATOR),
                StudioTheme.emptyBorder(12, 14, 12, 14)
        ));
        titleLabel.setFont(StudioTheme.titleFont());
        detailLabel.setForeground(StudioTheme.SECONDARY_TEXT);
        detailLabel.setFont(StudioTheme.smallFont());
        controls.add(titleLabel);
        controls.add(detailLabel);

        controls.add(sectionLabel("Show"));
        controls.add(buildViewButtons());
        controls.add(sectionLabel("Depth"));
        controls.add(buildDepthRow());
        return controls;
    }

    private JPanel buildViewButtons() {
        JPanel row = new JPanel(new GridLayout(1, 0, StudioTheme.scale(4), 0));
        row.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        for (PaletteLayerScale.View view : PaletteLayerScale.View.values()) {
            JToggleButton button = new JToggleButton(view.toString());
            button.setFocusable(false);
            button.setSelected(view == selectedView);
            button.addActionListener(event -> {
                selectedView = view;
                listener.onViewChanged(view);
            });
            group.add(button);
            row.add(button);
        }
        return row;
    }

    private JPanel buildDepthRow() {
        depthRow.setOpaque(false);
        shallowerButton.setToolTipText("Show the block one higher up");
        deeperButton.setToolTipText("Show the block one further down");
        shallowerButton.addActionListener(event -> changeDepth(-1));
        deeperButton.addActionListener(event -> changeDepth(1));
        depthRow.add(shallowerButton, BorderLayout.WEST);
        depthRow.add(depthLabel, BorderLayout.CENTER);
        depthRow.add(deeperButton, BorderLayout.EAST);
        return depthRow;
    }

    private void changeDepth(int step) {
        int nextDepth = Math.clamp(depth + step, 0, deepestDepth);
        if (nextDepth == depth) {
            return;
        }
        depth = nextDepth;
        refreshDepthControls();
        listener.onDepthChanged(depth);
    }

    private void refreshDepthControls() {
        depthLabel.setText(depthText(depth, deepestDepth));
        shallowerButton.setEnabled(depth > 0);
        deeperButton.setEnabled(depth < deepestDepth);
    }

    private static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text.toUpperCase(Locale.ROOT));
        label.setForeground(StudioTheme.MUTED_TEXT);
        label.setFont(StudioTheme.sectionFont());
        return label;
    }

    private static String thicknessText(PaletteLayerPreview preview) {
        if (preview.minThickness() == preview.maxThickness()) {
            return preview.maxThickness() + (preview.maxThickness() == 1 ? " block thick" : " blocks thick");
        }
        return preview.minThickness() + "–" + preview.maxThickness() + " blocks thick";
    }

    private static String depthText(int depth, int deepestDepth) {
        if (deepestDepth == 0) {
            return "Top block only";
        }
        return depth == 0 ? "Top block" : depth + (depth == 1 ? " block down" : " blocks down");
    }

    private static String formatZoom(double zoom) {
        return zoom == Math.rint(zoom) ? Long.toString((long) zoom) : String.format(Locale.ROOT, "%.2f", zoom);
    }

    private static String percent(double share) {
        if (share > 0D && share < 0.001D) {
            return "<0.1%";
        }
        return String.format(Locale.ROOT, "%.1f%%", share * 100D);
    }

    interface Listener {
        void onViewChanged(PaletteLayerScale.View view);

        void onDepthChanged(int depth);
    }

    private static final class StatisticsView extends JComponent {
        private static final long serialVersionUID = -2318840125766150871L;
        private static final int MARGIN = 14;
        private static final int HISTOGRAM_HEIGHT = 84;
        private static final int BAND_HEIGHT = 12;
        private static final int SECTION_GAP = 18;
        private static final int TITLE_GAP = 6;
        private static final int NOTE_GAP = 4;
        private static final String WIDEST_WEIGHT = "100.0%";
        private static final String WIDEST_ACTUAL = "▲ 100.0%";

        private PaletteLayerPreview preview;
        private String textureStatus;

        private StatisticsView() {
            setOpaque(true);
            setBackground(StudioTheme.PANEL_BACKGROUND);
        }

        private void show(PaletteLayerPreview nextPreview) {
            preview = nextPreview;
            revalidate();
            repaint();
        }

        private void showTextureStatus(String status) {
            textureStatus = status;
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            int preferredWidth = StudioTheme.scale(PANEL_WIDTH - 20);
            if (preview == null) {
                return new Dimension(preferredWidth, 0);
            }
            int margin = StudioTheme.scale(MARGIN);
            int width = Math.max(getWidth(), preferredWidth) - margin * 2;
            int height = margin + valueSpreadHeight() + StudioTheme.scale(SECTION_GAP) + blockTableHeight()
                    + StudioTheme.scale(SECTION_GAP) + notesHeight(width) + margin;
            return new Dimension(preferredWidth, height);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D canvas = (Graphics2D) graphics.create();
            try {
                canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                canvas.setColor(StudioTheme.PANEL_BACKGROUND);
                canvas.fillRect(0, 0, getWidth(), getHeight());
                if (preview == null) {
                    return;
                }
                int margin = StudioTheme.scale(MARGIN);
                int width = getWidth() - margin * 2;
                int top = margin;
                drawValueSpread(canvas, margin, top, width);
                top += valueSpreadHeight() + StudioTheme.scale(SECTION_GAP);
                drawBlockTable(canvas, margin, top, width);
                top += blockTableHeight() + StudioTheme.scale(SECTION_GAP);
                drawNotes(canvas, margin, top, width);
            } finally {
                canvas.dispose();
            }
        }

        private int valueSpreadHeight() {
            return sectionMetrics().getHeight() + StudioTheme.scale(TITLE_GAP) + StudioTheme.scale(HISTOGRAM_HEIGHT)
                    + StudioTheme.scale(2) + StudioTheme.scale(BAND_HEIGHT) + StudioTheme.scale(3) + smallMetrics().getHeight();
        }

        private int blockTableHeight() {
            return sectionMetrics().getHeight() + StudioTheme.scale(TITLE_GAP) + rowHeight() * preview.entries().size();
        }

        private int notesHeight(int width) {
            FontMetrics metrics = smallMetrics();
            int height = 0;
            for (String note : notes()) {
                height += wrap(note, metrics, width).size() * metrics.getHeight() + StudioTheme.scale(NOTE_GAP);
            }
            return height;
        }

        private int rowHeight() {
            return textMetrics().getHeight() + StudioTheme.scale(4);
        }

        private FontMetrics textMetrics() {
            return getFontMetrics(StudioTheme.font());
        }

        private FontMetrics smallMetrics() {
            return getFontMetrics(StudioTheme.smallFont());
        }

        private FontMetrics sectionMetrics() {
            return getFontMetrics(StudioTheme.sectionFont());
        }

        private void drawValueSpread(Graphics2D canvas, int x, int top, int width) {
            drawSectionTitle(canvas, "Noise values this layer produces", x, top + sectionMetrics().getAscent());
            int histogramTop = top + sectionMetrics().getHeight() + StudioTheme.scale(TITLE_GAP);
            int histogramHeight = StudioTheme.scale(HISTOGRAM_HEIGHT);
            int bandHeight = StudioTheme.scale(BAND_HEIGHT);
            long[] histogram = preview.histogram();
            long peak = 1L;
            for (long count : histogram) {
                peak = Math.max(peak, count);
            }
            canvas.setColor(HISTOGRAM_BAR);
            for (int bin = 0; bin < histogram.length; bin++) {
                int barLeft = x + (bin * width) / histogram.length;
                int barRight = x + ((bin + 1) * width) / histogram.length;
                int barHeight = (int) Math.round(histogramHeight * (histogram[bin] / (double) peak));
                canvas.fillRect(barLeft, histogramTop + histogramHeight - barHeight, Math.max(1, barRight - barLeft - 1), barHeight);
            }

            int bandTop = histogramTop + histogramHeight + StudioTheme.scale(2);
            for (PaletteLayerPreview.Entry entry : preview.entries()) {
                if (Double.isNaN(entry.lowestNoise())) {
                    continue;
                }
                int bandLeft = x + (int) Math.round(entry.lowestNoise() * width);
                int bandRight = x + (int) Math.round(entry.highestNoise() * width);
                canvas.setColor(new Color(entry.color()));
                canvas.fillRect(bandLeft, bandTop, Math.max(1, bandRight - bandLeft), bandHeight);
                if (entry.highestNoise() < 1D) {
                    canvas.setColor(BAND_DIVIDER);
                    canvas.drawLine(bandRight, histogramTop, bandRight, bandTop + bandHeight - 1);
                }
            }

            canvas.setFont(StudioTheme.smallFont());
            canvas.setColor(StudioTheme.SECONDARY_TEXT);
            FontMetrics metrics = canvas.getFontMetrics();
            int labelY = bandTop + bandHeight + StudioTheme.scale(3) + metrics.getAscent();
            canvas.drawString("0", x, labelY);
            canvas.drawString("0.5", x + (width - metrics.stringWidth("0.5")) / 2, labelY);
            canvas.drawString("1", x + width - metrics.stringWidth("1"), labelY);
        }

        private void drawBlockTable(Graphics2D canvas, int x, int top, int width) {
            int columnGap = StudioTheme.scale(10);
            int actualRight = x + width;
            int weightRight = actualRight - textMetrics().stringWidth(WIDEST_ACTUAL) - columnGap;
            int nameRight = weightRight - textMetrics().stringWidth(WIDEST_WEIGHT) - columnGap;
            int titleBaseline = top + sectionMetrics().getAscent();
            drawSectionTitle(canvas, "Blocks", x, titleBaseline);
            drawRightAligned(canvas, "weight", weightRight, titleBaseline);
            drawRightAligned(canvas, "actual", actualRight, titleBaseline);

            int rowHeight = rowHeight();
            FontMetrics text = textMetrics();
            int swatchSize = text.getAscent() - text.getDescent();
            int rowTop = top + sectionMetrics().getHeight() + StudioTheme.scale(TITLE_GAP);
            for (PaletteLayerPreview.Entry entry : preview.entries()) {
                int baseline = rowTop + (rowHeight + text.getAscent() - text.getDescent()) / 2;
                drawSwatch(canvas, entry, x, rowTop + (rowHeight - swatchSize) / 2, swatchSize);
                canvas.setFont(StudioTheme.font());
                canvas.setColor(entry.found() ? StudioTheme.TEXT : StudioTheme.ERROR);
                String name = entry.found() ? shortBlockName(entry.block()) : shortBlockName(entry.block()) + " (not found)";
                int nameLeft = x + swatchSize + StudioTheme.scale(7);
                canvas.drawString(fit(canvas, name, nameRight - nameLeft), nameLeft, baseline);
                canvas.setColor(StudioTheme.SECONDARY_TEXT);
                drawRightAligned(canvas, percent(entry.weightShare()), weightRight, baseline);
                canvas.setColor(actualColor(entry));
                drawRightAligned(canvas, actualText(entry), actualRight, baseline);
                rowTop += rowHeight;
            }
        }

        private static void drawSwatch(Graphics2D canvas, PaletteLayerPreview.Entry entry, int x, int y, int size) {
            if (entry.texture() != null) {
                canvas.drawImage(entry.texture().toImage(), x, y, size, size, null);
                return;
            }
            canvas.setColor(new Color(entry.color()));
            int arc = StudioTheme.scale(3);
            canvas.fillRoundRect(x, y, size, size, arc, arc);
        }

        private void drawNotes(Graphics2D canvas, int x, int top, int width) {
            canvas.setFont(StudioTheme.smallFont());
            canvas.setColor(StudioTheme.SECONDARY_TEXT);
            FontMetrics metrics = canvas.getFontMetrics();
            int lineY = top + metrics.getAscent();
            for (String note : notes()) {
                for (String line : wrap(note, metrics, width)) {
                    canvas.drawString(line, x, lineY);
                    lineY += metrics.getHeight();
                }
                lineY += StudioTheme.scale(NOTE_GAP);
            }
        }

        private List<String> notes() {
            List<String> notes = new ArrayList<>();
            if (textureStatus != null) {
                notes.add(textureStatus);
            }
            notes.add("▼ gets under half its weight, ▲ gets over double.");
            notes.add(String.format(Locale.ROOT, "Measured on a %d × %d grid spread over %,.0f blocks around 0, 0.",
                    PaletteLayerPreview.STATISTICS_GRID_SIZE, PaletteLayerPreview.STATISTICS_GRID_SIZE,
                    PaletteLayerPreview.statisticsAreaBlocks()));
            if (preview.absentShare() > 0D) {
                notes.add("The layer doesn't reach this depth on " + percent(preview.absentShare())
                        + " of the ground. That ground is dark on the map and left out of the shares.");
            }
            if (preview.outOfRangeShare() > 0D) {
                notes.add(percent(preview.outOfRangeShare()) + " of the noise values fall outside 0 to 1."
                        + " Those spots get the first block.");
            }
            if (preview.hasSlopeCondition()) {
                notes.add("This layer has a slope condition, which the preview ignores.");
            }
            return notes;
        }

        private static String actualText(PaletteLayerPreview.Entry entry) {
            if (entry.isFarBelowWeight()) {
                return "▼ " + percent(entry.actualShare());
            }
            if (entry.isFarAboveWeight()) {
                return "▲ " + percent(entry.actualShare());
            }
            return percent(entry.actualShare());
        }

        private static Color actualColor(PaletteLayerPreview.Entry entry) {
            return entry.isFarBelowWeight() || entry.isFarAboveWeight() ? StudioTheme.WARNING : StudioTheme.TEXT;
        }

        private static void drawSectionTitle(Graphics2D canvas, String title, int x, int y) {
            canvas.setFont(StudioTheme.sectionFont());
            canvas.setColor(StudioTheme.MUTED_TEXT);
            canvas.drawString(title.toUpperCase(Locale.ROOT), x, y);
        }

        private static void drawRightAligned(Graphics2D canvas, String text, int right, int y) {
            canvas.drawString(text, right - canvas.getFontMetrics().stringWidth(text), y);
        }

        private static String shortBlockName(String block) {
            return block.startsWith("minecraft:") ? block.substring("minecraft:".length()) : block;
        }

        private static String fit(Graphics2D canvas, String text, int maxWidth) {
            FontMetrics metrics = canvas.getFontMetrics();
            if (metrics.stringWidth(text) <= maxWidth) {
                return text;
            }
            String shortened = text;
            while (shortened.length() > 1 && metrics.stringWidth(shortened + "…") > maxWidth) {
                shortened = shortened.substring(0, shortened.length() - 1);
            }
            return shortened + "…";
        }

        private static List<String> wrap(String text, FontMetrics metrics, int maxWidth) {
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : text.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (metrics.stringWidth(candidate) > maxWidth && !line.isEmpty()) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
            }
            return lines;
        }
    }
}
