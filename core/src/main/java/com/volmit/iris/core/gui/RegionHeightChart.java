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

import com.volmit.iris.core.gui.RegionHeightCoverage.BiomeRange;
import com.volmit.iris.core.gui.RegionHeightCoverage.HeightBand;
import com.volmit.iris.engine.object.InferredType;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Ground height runs left to right. On top, how much of the ground in view sits at each height; below, one bar per
 * biome over the heights it allows. Heights no biome covers are red, heights several ranged biomes share are hatched.
 */
final class RegionHeightChart extends JComponent {
    private static final long serialVersionUID = 6218894112903760545L;
    private static final List<InferredType> ROW_GROUPS = List.of(InferredType.LAND, InferredType.SHORE, InferredType.SEA);
    private static final int CHART_WIDTH = 330;
    private static final int PADDING = 12;
    private static final int NAME_COLUMN = 104;
    private static final int NAME_GAP = 8;
    private static final int HISTOGRAM_HEIGHT = 54;
    private static final int AXIS_HEIGHT = 20;
    private static final int GROUP_HEADER_HEIGHT = 22;
    private static final int ROW_HEIGHT = 18;
    private static final int BAR_HEIGHT = 10;
    private static final int MINIMUM_TICK_SPACING = 38;
    private static final int[] TICK_STEPS = {5, 10, 20, 25, 50, 100, 200, 250, 500, 1000};
    private static final int HATCH_SPACING = 5;
    private static final double RANGE_PADDING_FRACTION = 0.04D;
    private static final Color LAND_GROUND = new ThemeColor(0x9DA0A8, 0x6F737A);
    private static final Color SEA_GROUND = new ThemeColor(0x7FA3D9, 0x4A6FA8);
    private static final Color GAP_FILL = new ThemeColor(new Color(0xDB, 0x3B, 0x4B, 46), new Color(0xF7, 0x54, 0x64, 52));
    private static final Color SHARED_HATCH = new ThemeColor(new Color(0, 0, 0, 70), new Color(255, 255, 255, 70));
    private static final Color SEA_LEVEL = new ThemeColor(new Color(0x31, 0x5F, 0xBD, 150), new Color(0x54, 0x8A, 0xF7, 150));

    private RegionHeightCoverage coverage;
    private int lowestShown;
    private int highestShown;

    RegionHeightChart() {
        setToolTipText("");
        setOpaque(false);
    }

    void show(RegionHeightCoverage nextCoverage) {
        coverage = nextCoverage;
        fitShownHeights();
        revalidate();
        repaint();
    }

    // The chart spans the ground in view and every range edge, so a biome placed above or below the view still shows.
    private void fitShownHeights() {
        if (coverage == null) {
            return;
        }
        int lowest = Math.min(0, coverage.lowestHeight());
        int highest = Math.max(0, coverage.highestHeight());
        for (InferredType type : ROW_GROUPS) {
            for (BiomeRange range : coverage.ranges(type)) {
                if (range.hasLowerBound()) {
                    lowest = Math.min(lowest, range.minHeight());
                }
                if (range.hasUpperBound()) {
                    highest = Math.max(highest, range.maxHeight());
                }
            }
        }
        int padding = Math.max(2, (int) Math.round((highest - lowest) * RANGE_PADDING_FRACTION));
        lowestShown = lowest - padding;
        highestShown = highest + padding;
    }

    @Override
    public Dimension getPreferredSize() {
        int rows = 0;
        int groups = 0;
        if (coverage != null) {
            for (InferredType type : ROW_GROUPS) {
                int count = coverage.ranges(type).size();
                rows += count;
                groups += count > 0 ? 1 : 0;
            }
        }
        int height = PADDING * 2 + HISTOGRAM_HEIGHT + AXIS_HEIGHT + groups * GROUP_HEADER_HEIGHT + rows * ROW_HEIGHT;
        return StudioTheme.scaledSize(CHART_WIDTH, height);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        if (coverage == null) {
            return;
        }
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int top = StudioTheme.scale(PADDING);
            int rowsTop = top + StudioTheme.scale(HISTOGRAM_HEIGHT + AXIS_HEIGHT);
            int bottom = getHeight() - StudioTheme.scale(PADDING);
            drawBands(canvas, top, bottom);
            drawGround(canvas, top);
            drawAxis(canvas, top + StudioTheme.scale(HISTOGRAM_HEIGHT));
            drawRows(canvas, rowsTop);
            drawSeaLevel(canvas, top, bottom);
        } finally {
            canvas.dispose();
        }
    }

    private void drawBands(Graphics2D canvas, int top, int bottom) {
        int histogramBottom = top + StudioTheme.scale(HISTOGRAM_HEIGHT);
        for (InferredType type : RegionHeightCoverage.MEASURED_TYPES) {
            for (HeightBand band : coverage.bands(type)) {
                int left = xOf(band.fromHeight());
                int width = Math.max(1, xOf(band.toHeight() + 1) - left);
                if (band.isGap()) {
                    canvas.setColor(GAP_FILL);
                    canvas.fillRect(left, top, width, bottom - top);
                } else if (band.isShared() && band.biomes().stream().allMatch(BiomeRange::isBounded)) {
                    hatch(canvas, new Rectangle(left, top, width, histogramBottom - top));
                }
            }
        }
    }

    private static void hatch(Graphics2D canvas, Rectangle area) {
        Shape previousClip = canvas.getClip();
        canvas.clip(area);
        canvas.setColor(SHARED_HATCH);
        int spacing = StudioTheme.scale(HATCH_SPACING);
        for (int offset = -area.height; offset < area.width; offset += spacing) {
            canvas.drawLine(area.x + offset, area.y + area.height, area.x + offset + area.height, area.y);
        }
        canvas.setClip(previousClip);
    }

    private void drawGround(Graphics2D canvas, int top) {
        int histogramHeight = StudioTheme.scale(HISTOGRAM_HEIGHT);
        int plotLeft = plotLeft();
        int plotRight = plotRight();
        int[] columnCounts = new int[plotRight - plotLeft];
        int highestColumn = 1;
        for (int column = 0; column < columnCounts.length; column++) {
            int fromHeight = heightAt(plotLeft + column);
            int toHeight = Math.max(fromHeight, heightAt(plotLeft + column + 1) - 1);
            for (int height = fromHeight; height <= toHeight; height++) {
                columnCounts[column] += coverage.count(height);
            }
            highestColumn = Math.max(highestColumn, columnCounts[column]);
        }
        for (int column = 0; column < columnCounts.length; column++) {
            if (columnCounts[column] == 0) {
                continue;
            }
            int barHeight = Math.max(1, (int) Math.round(columnCounts[column] / (double) highestColumn * histogramHeight));
            canvas.setColor(heightAt(plotLeft + column) < 0 ? SEA_GROUND : LAND_GROUND);
            canvas.fillRect(plotLeft + column, top + histogramHeight - barHeight, 1, barHeight);
        }
    }

    private void drawAxis(Graphics2D canvas, int axisTop) {
        canvas.setFont(StudioTheme.smallFont());
        FontMetrics metrics = canvas.getFontMetrics();
        canvas.setColor(StudioTheme.CONTROL_BORDER);
        canvas.drawLine(plotLeft(), axisTop, plotRight(), axisTop);
        int step = tickStep();
        int firstTick = Math.floorDiv(lowestShown, step) * step + step;
        for (int height = firstTick; height < highestShown; height += step) {
            int x = xOf(height);
            String label = height > 0 ? "+" + height : String.valueOf(height);
            canvas.setColor(StudioTheme.CONTROL_BORDER);
            canvas.drawLine(x, axisTop, x, axisTop + StudioTheme.scale(3));
            canvas.setColor(StudioTheme.MUTED_TEXT);
            canvas.drawString(label, x - metrics.stringWidth(label) / 2, axisTop + StudioTheme.scale(4) + metrics.getAscent());
        }
        canvas.setColor(StudioTheme.MUTED_TEXT);
        canvas.drawString("Ground in view", StudioTheme.scale(PADDING), axisTop - StudioTheme.scale(4));
    }

    private int tickStep() {
        double pixelsPerBlock = (plotRight() - plotLeft()) / (double) Math.max(1, highestShown - lowestShown);
        for (int step : TICK_STEPS) {
            if (step * pixelsPerBlock >= StudioTheme.scale(MINIMUM_TICK_SPACING)) {
                return step;
            }
        }
        return TICK_STEPS[TICK_STEPS.length - 1];
    }

    private void drawRows(Graphics2D canvas, int rowsTop) {
        int y = rowsTop;
        for (InferredType type : ROW_GROUPS) {
            List<BiomeRange> ranges = coverage.ranges(type);
            if (ranges.isEmpty()) {
                continue;
            }
            canvas.setFont(StudioTheme.sectionFont());
            canvas.setColor(StudioTheme.MUTED_TEXT);
            canvas.drawString(groupTitle(type), StudioTheme.scale(PADDING), y + StudioTheme.scale(GROUP_HEADER_HEIGHT) - StudioTheme.scale(7));
            y += StudioTheme.scale(GROUP_HEADER_HEIGHT);
            for (BiomeRange range : ranges) {
                drawRow(canvas, range, y);
                y += StudioTheme.scale(ROW_HEIGHT);
            }
        }
    }

    private void drawRow(Graphics2D canvas, BiomeRange range, int rowTop) {
        int rowHeight = StudioTheme.scale(ROW_HEIGHT);
        canvas.setFont(StudioTheme.smallFont());
        FontMetrics metrics = canvas.getFontMetrics();
        canvas.setColor(StudioTheme.SECONDARY_TEXT);
        canvas.drawString(ellipsize(range.name(), metrics, StudioTheme.scale(NAME_COLUMN)), StudioTheme.scale(PADDING),
                rowTop + (rowHeight + metrics.getAscent() - metrics.getDescent()) / 2);
        int left = xOf(Math.max(range.minHeight(), lowestShown));
        int right = xOf(Math.min(range.maxHeight(), highestShown) + 1);
        int barHeight = StudioTheme.scale(BAR_HEIGHT);
        int barTop = rowTop + (rowHeight - barHeight) / 2;
        int arc = StudioTheme.scale(4);
        canvas.setColor(new Color(range.color()));
        canvas.fillRoundRect(left, barTop, Math.max(2, right - left), barHeight, arc, arc);
        canvas.setColor(StudioTheme.CONTROL_BORDER);
        canvas.drawRoundRect(left, barTop, Math.max(2, right - left) - 1, barHeight - 1, arc, arc);
    }

    private void drawSeaLevel(Graphics2D canvas, int top, int bottom) {
        int x = xOf(0);
        canvas.setColor(SEA_LEVEL);
        float dash = StudioTheme.scale(3);
        canvas.setStroke(new BasicStroke(1F, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10F, new float[]{dash, dash}, 0F));
        canvas.drawLine(x, top, x, bottom);
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        if (coverage == null) {
            return null;
        }
        BiomeRange row = rowAt(event.getY());
        if (row != null) {
            return row.name() + ": " + (row.isBounded() ? RegionHeightCoverage.heightText(row.minHeight(), row.maxHeight()) : "any height");
        }
        int height = heightAt(event.getX());
        for (InferredType type : RegionHeightCoverage.MEASURED_TYPES) {
            for (HeightBand band : coverage.bands(type)) {
                if (band.isGap() && height >= band.fromHeight() && height <= band.toHeight()) {
                    return "No biome covers " + RegionHeightCoverage.heightText(band.fromHeight(), band.toHeight());
                }
            }
        }
        return null;
    }

    private BiomeRange rowAt(int y) {
        int rowTop = StudioTheme.scale(PADDING + HISTOGRAM_HEIGHT + AXIS_HEIGHT);
        for (InferredType type : ROW_GROUPS) {
            List<BiomeRange> ranges = coverage.ranges(type);
            if (ranges.isEmpty()) {
                continue;
            }
            rowTop += StudioTheme.scale(GROUP_HEADER_HEIGHT);
            for (BiomeRange range : ranges) {
                if (y >= rowTop && y < rowTop + StudioTheme.scale(ROW_HEIGHT)) {
                    return range;
                }
                rowTop += StudioTheme.scale(ROW_HEIGHT);
            }
        }
        return null;
    }

    private int plotLeft() {
        return StudioTheme.scale(PADDING + NAME_COLUMN + NAME_GAP);
    }

    private int plotRight() {
        return Math.max(plotLeft() + 1, getWidth() - StudioTheme.scale(PADDING));
    }

    private int xOf(int height) {
        double fraction = (height - lowestShown) / (double) Math.max(1, highestShown - lowestShown);
        return plotLeft() + (int) Math.round(fraction * (plotRight() - plotLeft()));
    }

    private int heightAt(int x) {
        double fraction = (x - plotLeft()) / (double) Math.max(1, plotRight() - plotLeft());
        return (int) Math.floor(lowestShown + fraction * (highestShown - lowestShown));
    }

    private static String groupTitle(InferredType type) {
        return switch (type) {
            case LAND -> "LAND";
            case SHORE -> "SHORE";
            default -> "SEA";
        };
    }

    private static String ellipsize(String text, FontMetrics metrics, int maximumWidth) {
        if (metrics.stringWidth(text) <= maximumWidth) {
            return text;
        }
        String ellipsis = "…";
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end) + ellipsis) > maximumWidth) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }
}
