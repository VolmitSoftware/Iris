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

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * IntelliJ's profiler gauge, how heavy the measured bake is, a meter and the time it took, all in the weight's colour.
 */
final class BakeMeter extends JComponent {
    private static final long serialVersionUID = 4120925066373218716L;
    private static final String PROFILER_ICON = "iris/icons/profiler.svg";
    private static final String TIMING_LABEL = "Timing";
    private static final String WIDEST_TIME = "999 ms";
    private static final int ICON_SIZE = 16;
    private static final int GAP = 6;
    private static final int BAR_HEIGHT = 4;
    private static final int HEIGHT = 20;

    private final FlatSVGIcon icon = new FlatSVGIcon(PROFILER_ICON, ICON_SIZE, ICON_SIZE, BakeMeter.class.getClassLoader());
    private BakeTiming timing;
    private boolean timingUnderWay;

    BakeMeter() {
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(drawn -> color()));
    }

    void showTiming() {
        timing = null;
        timingUnderWay = true;
        setToolTipText("Baking every variant five times over");
        repaint();
    }

    void show(BakeTiming measured) {
        timing = measured;
        timingUnderWay = false;
        setToolTipText(measured.explain());
        repaint();
    }

    void clear() {
        timing = null;
        timingUnderWay = false;
        setToolTipText(null);
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(0, StudioTheme.scale(HEIGHT));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            icon.paintIcon(this, canvas, 0, (getHeight() - icon.getIconHeight()) / 2);
            int labelX = icon.getIconWidth() + StudioTheme.scale(GAP);
            int barX = drawLabel(canvas, labelX) + StudioTheme.scale(GAP);
            int timeX = getWidth() - timeWidth(canvas);
            drawBar(canvas, barX, timeX - StudioTheme.scale(GAP) - barX);
            drawTime(canvas, timeX);
        } finally {
            canvas.dispose();
        }
    }

    private Color color() {
        return timing == null ? StudioTheme.MUTED_TEXT : weight().color();
    }

    private BakeWeight weight() {
        return BakeWeight.of(timing.totalMillis());
    }

    // Returns where the label ends.
    private int drawLabel(Graphics2D canvas, int x) {
        canvas.setFont(StudioTheme.boldFont());
        FontMetrics metrics = canvas.getFontMetrics();
        canvas.setColor(color());
        canvas.drawString(labelText(), x, baseline(metrics));
        int widestLabelWidth = metrics.stringWidth(TIMING_LABEL);
        for (BakeWeight each : BakeWeight.values()) {
            widestLabelWidth = Math.max(widestLabelWidth, metrics.stringWidth(each.label()));
        }
        return x + widestLabelWidth;
    }

    private String labelText() {
        if (timing != null) {
            return weight().label();
        }
        return timingUnderWay ? TIMING_LABEL : "—";
    }

    private void drawBar(Graphics2D canvas, int x, int width) {
        int barHeight = StudioTheme.scale(BAR_HEIGHT);
        int y = (getHeight() - barHeight) / 2;
        canvas.setColor(StudioTheme.HOVER_BACKGROUND);
        canvas.fillRoundRect(x, y, width, barHeight, barHeight, barHeight);
        if (timing == null) {
            return;
        }
        int filledWidth = (int) Math.round(width * BakeWeight.meterFill(timing.totalMillis()));
        canvas.setColor(color());
        canvas.fillRoundRect(x, y, Math.max(barHeight, filledWidth), barHeight, barHeight, barHeight);
    }

    private int timeWidth(Graphics2D canvas) {
        return canvas.getFontMetrics(StudioTheme.smallFont()).stringWidth(WIDEST_TIME);
    }

    private void drawTime(Graphics2D canvas, int x) {
        if (timing == null) {
            return;
        }
        canvas.setFont(StudioTheme.smallFont());
        FontMetrics metrics = canvas.getFontMetrics();
        String time = BakeTiming.formatMillis(timing.totalMillis());
        canvas.setColor(StudioTheme.SECONDARY_TEXT);
        canvas.drawString(time, x + timeWidth(canvas) - metrics.stringWidth(time), baseline(metrics));
    }

    private int baseline(FontMetrics metrics) {
        return (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2;
    }
}
