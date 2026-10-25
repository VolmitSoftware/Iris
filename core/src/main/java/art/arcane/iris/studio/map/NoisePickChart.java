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

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * One noise that picks regions or biomes: the spread of its values in view above the bands that pick each option,
 * and a table of how much of the noise each option is given and how much it gets in view. A long table shows only
 * the options with the most ground in view until asked for all; a noise with one option to pick is a single line.
 */
final class NoisePickChart extends JComponent {
    private static final long serialVersionUID = 1873365510472853316L;
    private static final int PREFERRED_WIDTH = 300;
    private static final int MARGIN = 16;
    private static final int HISTOGRAM_HEIGHT = 64;
    private static final int BAND_HEIGHT = 12;
    private static final int TITLE_GAP = 6;
    private static final int SECTION_GAP = 12;
    private static final int BOTTOM_GAP = 14;
    private static final Color HISTOGRAM_BAR = new Color(0x9D, 0xA0, 0xA8, 150);
    private static final Color BAND_DIVIDER = new Color(255, 255, 255, 70);
    private static final String WIDEST_SHARE = "▲ 100.0%";
    private static final int SHORT_TABLE_ROWS = 6;

    private final NoisePickSpread spread;
    private final List<NoisePickSpread.Row> rows;
    private final String optionNoun;
    private final Consumer<Boolean> onShowAllRowsChanged;
    private boolean showsAllRows;
    private Rectangle showAllToggleBounds;

    /**
     * @param optionNoun           what the noise picks, as a table heading: "Regions" or "Biomes"
     * @param showsAllRows         whether a long table starts with all its rows
     * @param onShowAllRowsChanged receives whether the table shows all its rows after the user asked for more or fewer
     */
    NoisePickChart(NoisePickSpread spread, String optionNoun, boolean showsAllRows, Consumer<Boolean> onShowAllRowsChanged) {
        this.spread = spread;
        this.rows = spread.rows();
        this.optionNoun = optionNoun;
        this.showsAllRows = showsAllRows;
        this.onShowAllRowsChanged = onShowAllRowsChanged;
        setAlignmentX(LEFT_ALIGNMENT);
        listenForShowAllToggle();
    }

    @Override
    public Dimension getPreferredSize() {
        if (hasSingleOption()) {
            return new Dimension(StudioTheme.scale(PREFERRED_WIDTH), StudioTheme.scale(SECTION_GAP) + tableHeight()
                    + StudioTheme.scale(BOTTOM_GAP));
        }
        int height = StudioTheme.scale(SECTION_GAP) + spreadHeight() + StudioTheme.scale(SECTION_GAP) + tableHeight()
                + StudioTheme.scale(BOTTOM_GAP);
        return new Dimension(StudioTheme.scale(PREFERRED_WIDTH), height);
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
            int margin = StudioTheme.scale(MARGIN);
            int width = getWidth() - margin * 2;
            int top = StudioTheme.scale(SECTION_GAP);
            if (hasSingleOption()) {
                drawSingleOption(canvas, margin, top, width);
                return;
            }
            drawSpread(canvas, margin, top, width);
            drawTable(canvas, margin, top + spreadHeight() + StudioTheme.scale(SECTION_GAP), width);
        } finally {
            canvas.dispose();
        }
    }

    private int spreadHeight() {
        return sectionMetrics().getHeight() + StudioTheme.scale(TITLE_GAP) + StudioTheme.scale(HISTOGRAM_HEIGHT)
                + StudioTheme.scale(2) + StudioTheme.scale(BAND_HEIGHT) + StudioTheme.scale(3) + smallMetrics().getHeight();
    }

    private boolean hasSingleOption() {
        return rows.size() == 1;
    }

    private boolean isLongTable() {
        return rows.size() > SHORT_TABLE_ROWS;
    }

    private int tableHeight() {
        int toggleRows = isLongTable() ? 1 : 0;
        return sectionMetrics().getHeight() + StudioTheme.scale(TITLE_GAP) + rowHeight() * (shownRows().size() + toggleRows);
    }

    // A short table keeps the options with the most ground in view, still in the order the noise meets them.
    private List<NoisePickSpread.Row> shownRows() {
        if (showsAllRows || !isLongTable()) {
            return rows;
        }
        Set<NoisePickSpread.Row> largest = rows.stream()
                .sorted(Comparator.comparingDouble(NoisePickSpread.Row::actualShare).reversed())
                .limit(SHORT_TABLE_ROWS)
                .collect(Collectors.toSet());
        return rows.stream().filter(largest::contains).toList();
    }

    private void listenForShowAllToggle() {
        MouseAdapter toggle = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (showAllToggleBounds == null || !showAllToggleBounds.contains(event.getPoint())) {
                    return;
                }
                showsAllRows = !showsAllRows;
                onShowAllRowsChanged.accept(showsAllRows);
                revalidate();
                repaint();
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                boolean overToggle = showAllToggleBounds != null && showAllToggleBounds.contains(event.getPoint());
                setCursor(overToggle ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : null);
            }
        };
        addMouseListener(toggle);
        addMouseMotionListener(toggle);
    }

    private void drawSingleOption(Graphics2D canvas, int x, int top, int width) {
        drawSectionTitle(canvas, spread.title(), x, top + sectionMetrics().getAscent());
        int rowTop = top + sectionMetrics().getHeight() + StudioTheme.scale(TITLE_GAP);
        NoisePickSpread.Row row = rows.getFirst();
        String only = "picked everywhere";
        int nameRight = x + width - textMetrics().stringWidth(only) - StudioTheme.scale(10);
        int baseline = drawOptionName(canvas, row, x, rowTop, nameRight);
        canvas.setColor(StudioTheme.MUTED_TEXT);
        drawRightAligned(canvas, only, x + width, baseline);
    }

    private void drawSpread(Graphics2D canvas, int x, int top, int width) {
        drawSectionTitle(canvas, spread.title(), x, top + sectionMetrics().getAscent());
        int histogramTop = top + sectionMetrics().getHeight() + StudioTheme.scale(TITLE_GAP);
        int histogramHeight = StudioTheme.scale(HISTOGRAM_HEIGHT);
        drawHistogram(canvas, x, histogramTop, width, histogramHeight);
        int bandTop = histogramTop + histogramHeight + StudioTheme.scale(2);
        drawBands(canvas, x, width, histogramTop, bandTop);

        canvas.setFont(StudioTheme.smallFont());
        canvas.setColor(StudioTheme.SECONDARY_TEXT);
        FontMetrics metrics = canvas.getFontMetrics();
        int labelY = bandTop + StudioTheme.scale(BAND_HEIGHT) + StudioTheme.scale(3) + metrics.getAscent();
        canvas.drawString("0", x, labelY);
        canvas.drawString("0.5", x + (width - metrics.stringWidth("0.5")) / 2, labelY);
        canvas.drawString("1", x + width - metrics.stringWidth("1"), labelY);
    }

    private void drawHistogram(Graphics2D canvas, int x, int top, int width, int height) {
        long[] histogram = spread.histogram();
        long peak = 1L;
        for (long count : histogram) {
            peak = Math.max(peak, count);
        }
        canvas.setColor(HISTOGRAM_BAR);
        for (int bin = 0; bin < histogram.length; bin++) {
            int barLeft = x + (bin * width) / histogram.length;
            int barRight = x + ((bin + 1) * width) / histogram.length;
            int barHeight = (int) Math.round(height * (histogram[bin] / (double) peak));
            canvas.fillRect(barLeft, top + height - barHeight, Math.max(1, barRight - barLeft - 1), barHeight);
        }
    }

    private void drawBands(Graphics2D canvas, int x, int width, int histogramTop, int bandTop) {
        int bandHeight = StudioTheme.scale(BAND_HEIGHT);
        for (NoisePickSpread.Band band : spread.bands()) {
            int bandLeft = x + (int) Math.round(band.fromNoise() * width);
            int bandRight = x + (int) Math.round(band.toNoise() * width);
            canvas.setColor(new Color(band.option().color()));
            canvas.fillRect(bandLeft, bandTop, Math.max(1, bandRight - bandLeft), bandHeight);
            if (band.toNoise() < 1D) {
                canvas.setColor(BAND_DIVIDER);
                canvas.drawLine(bandRight, histogramTop, bandRight, bandTop + bandHeight - 1);
            }
        }
    }

    private void drawTable(Graphics2D canvas, int x, int top, int width) {
        int columnGap = StudioTheme.scale(10);
        int actualRight = x + width;
        int weightRight = actualRight - textMetrics().stringWidth(WIDEST_SHARE) - columnGap;
        int nameRight = weightRight - textMetrics().stringWidth(WIDEST_SHARE) - columnGap;
        int titleBaseline = top + sectionMetrics().getAscent();
        drawSectionTitle(canvas, optionNoun, x, titleBaseline);
        canvas.setFont(StudioTheme.sectionFont());
        drawRightAligned(canvas, "WEIGHT", weightRight, titleBaseline);
        drawRightAligned(canvas, "IN VIEW", actualRight, titleBaseline);

        int rowHeight = rowHeight();
        int rowTop = top + sectionMetrics().getHeight() + StudioTheme.scale(TITLE_GAP);
        for (NoisePickSpread.Row row : shownRows()) {
            int baseline = drawOptionName(canvas, row, x, rowTop, nameRight);
            canvas.setColor(StudioTheme.SECONDARY_TEXT);
            drawRightAligned(canvas, percent(row.weightShare()), weightRight, baseline);
            boolean farFromWeight = row.isFarBelowWeight() || row.isFarAboveWeight();
            canvas.setColor(farFromWeight ? StudioTheme.WARNING : StudioTheme.TEXT);
            drawRightAligned(canvas, actualText(row), actualRight, baseline);
            rowTop += rowHeight;
        }
        showAllToggleBounds = isLongTable() ? drawShowAllToggle(canvas, x, rowTop) : null;
    }

    // Returns the row's text baseline.
    private int drawOptionName(Graphics2D canvas, NoisePickSpread.Row row, int x, int rowTop, int nameRight) {
        FontMetrics text = textMetrics();
        int rowHeight = rowHeight();
        int swatchSize = text.getAscent() - text.getDescent();
        int baseline = rowTop + (rowHeight + text.getAscent() - text.getDescent()) / 2;
        int arc = StudioTheme.scale(3);
        canvas.setColor(new Color(row.option().color()));
        canvas.fillRoundRect(x, rowTop + (rowHeight - swatchSize) / 2, swatchSize, swatchSize, arc, arc);
        canvas.setFont(StudioTheme.font());
        canvas.setColor(StudioTheme.TEXT);
        int nameLeft = x + swatchSize + StudioTheme.scale(7);
        canvas.drawString(fit(canvas, distinctName(row, rows), nameRight - nameLeft), nameLeft, baseline);
        return baseline;
    }

    private Rectangle drawShowAllToggle(Graphics2D canvas, int x, int rowTop) {
        String text = showsAllRows
                ? "Show fewer"
                : "Show all " + rows.size() + " " + optionNoun.toLowerCase(Locale.ROOT);
        FontMetrics metrics = textMetrics();
        int baseline = rowTop + (rowHeight() + metrics.getAscent() - metrics.getDescent()) / 2;
        canvas.setFont(StudioTheme.font());
        canvas.setColor(StudioTheme.LINK);
        canvas.drawString(text, x, baseline);
        return new Rectangle(x, rowTop, metrics.stringWidth(text), rowHeight());
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

    private static void drawSectionTitle(Graphics2D canvas, String title, int x, int y) {
        canvas.setFont(StudioTheme.sectionFont());
        canvas.setColor(StudioTheme.MUTED_TEXT);
        canvas.drawString(title.toUpperCase(Locale.ROOT), x, y);
    }

    private static void drawRightAligned(Graphics2D canvas, String text, int right, int y) {
        canvas.drawString(text, right - canvas.getFontMetrics().stringWidth(text), y);
    }

    // Two biomes in different folders can share a name, so their keys tell them apart.
    private static String distinctName(NoisePickSpread.Row row, List<NoisePickSpread.Row> rows) {
        String name = row.option().name();
        boolean shared = rows.stream().anyMatch(other -> other != row && other.option().name().equals(name));
        return shared ? name + " (" + row.option().key() + ")" : name;
    }

    private static String actualText(NoisePickSpread.Row row) {
        if (row.isFarBelowWeight()) {
            return "▼ " + percent(row.actualShare());
        }
        if (row.isFarAboveWeight()) {
            return "▲ " + percent(row.actualShare());
        }
        return percent(row.actualShare());
    }

    static String percent(double share) {
        if (share > 0D && share < 0.001D) {
            return "<0.1%";
        }
        return String.format(Locale.ROOT, "%.1f%%", share * 100D);
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
}
