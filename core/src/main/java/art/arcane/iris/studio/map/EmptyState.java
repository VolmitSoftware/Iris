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

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * What an empty view says, drawn in the middle of it the way IntelliJ fills an empty tool window: a large icon, what
 * is missing, and where to add it.
 *
 * @param location the pack folder or file the hint points to, drawn as code
 */
record EmptyState(Icon icon, String title, String hint, String location, String footer) {
    private static final int ICON_GAP = 18;
    private static final int TITLE_GAP = 8;
    private static final int LOCATION_GAP = 10;
    private static final int FOOTER_GAP = 14;
    private static final int CHIP_PADDING_X = 8;
    private static final int CHIP_PADDING_Y = 4;
    private static final int CHIP_ARC = 8;

    /**
     * Draws the empty state centred on the given point.
     */
    void paint(Graphics2D graphics, Component owner, int centerX, int centerY) {
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int top = centerY - height(canvas) / 2;
            icon.paintIcon(owner, canvas, centerX - icon.getIconWidth() / 2, top);
            top += icon.getIconHeight() + StudioTheme.scale(ICON_GAP);
            top = drawCentered(canvas, title, StudioTheme.titleFont(), StudioTheme.TEXT, centerX, top);
            top = drawCentered(canvas, hint, StudioTheme.font(), StudioTheme.SECONDARY_TEXT, centerX,
                    top + StudioTheme.scale(TITLE_GAP));
            top = drawLocationChip(canvas, centerX, top + StudioTheme.scale(LOCATION_GAP));
            drawCentered(canvas, footer, StudioTheme.smallFont(), StudioTheme.MUTED_TEXT, centerX, top + StudioTheme.scale(FOOTER_GAP));
        } finally {
            canvas.dispose();
        }
    }

    private int height(Graphics2D canvas) {
        return icon.getIconHeight() + StudioTheme.scale(ICON_GAP)
                + canvas.getFontMetrics(StudioTheme.titleFont()).getHeight()
                + StudioTheme.scale(TITLE_GAP) + canvas.getFontMetrics(StudioTheme.font()).getHeight()
                + StudioTheme.scale(LOCATION_GAP) + chipHeight(canvas)
                + StudioTheme.scale(FOOTER_GAP) + canvas.getFontMetrics(StudioTheme.smallFont()).getHeight();
    }

    private int chipHeight(Graphics2D canvas) {
        return canvas.getFontMetrics(StudioTheme.monospacedFont()).getHeight() + StudioTheme.scale(CHIP_PADDING_Y) * 2;
    }

    private int drawLocationChip(Graphics2D canvas, int centerX, int top) {
        canvas.setFont(StudioTheme.monospacedFont());
        FontMetrics metrics = canvas.getFontMetrics();
        int width = metrics.stringWidth(location) + StudioTheme.scale(CHIP_PADDING_X) * 2;
        int height = chipHeight(canvas);
        int left = centerX - width / 2;
        RoundRectangle2D chip = new RoundRectangle2D.Double(left + 0.5D, top + 0.5D, width - 1D, height - 1D,
                StudioTheme.scale(CHIP_ARC), StudioTheme.scale(CHIP_ARC));
        canvas.setColor(StudioTheme.PANEL_BACKGROUND);
        canvas.fill(chip);
        canvas.setColor(StudioTheme.CONTROL_BORDER);
        canvas.draw(chip);
        canvas.setColor(StudioTheme.TEXT);
        canvas.drawString(location, left + StudioTheme.scale(CHIP_PADDING_X), top + StudioTheme.scale(CHIP_PADDING_Y) + metrics.getAscent());
        return top + height;
    }

    private static int drawCentered(Graphics2D canvas, String text, Font font, Color color, int centerX, int top) {
        canvas.setFont(font);
        canvas.setColor(color);
        FontMetrics metrics = canvas.getFontMetrics();
        canvas.drawString(text, centerX - metrics.stringWidth(text) / 2, top + metrics.getAscent());
        return top + metrics.getHeight();
    }
}
