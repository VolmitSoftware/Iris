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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;

/**
 * Draws the floating cards (legends, hover details, hints) on top of a map, styled like IntelliJ's popups.
 */
final class StudioOverlay {
    private static final Color FOOTER_BACKGROUND = new ThemeColor(0xF7F8FA, 0x27292C);

    private StudioOverlay() {
    }

    static void drawCard(Graphics2D canvas, int x, int y, int width, int height) {
        drawCard(canvas, x, y, width, height, 0);
    }

    /**
     * Draws a card whose bottom {@code footerHeight} pixels are a darker bar, like the shortcut bar of IntelliJ's popups.
     */
    static void drawCard(Graphics2D canvas, int x, int y, int width, int height, int footerHeight) {
        Graphics2D card = (Graphics2D) canvas.create();
        card.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        RoundRectangle2D outline = new RoundRectangle2D.Double(x + 0.5D, y + 0.5D, width - 1D, height - 1D,
                StudioTheme.scale(StudioTheme.OVERLAY_ARC), StudioTheme.scale(StudioTheme.OVERLAY_ARC));
        card.setColor(StudioTheme.OVERLAY_BACKGROUND);
        card.fill(outline);
        if (footerHeight > 0) {
            int footerTop = y + height - footerHeight;
            Shape cardClip = card.getClip();
            card.clip(outline);
            card.setColor(FOOTER_BACKGROUND);
            card.fillRect(x, footerTop, width, footerHeight);
            card.setColor(StudioTheme.OVERLAY_BORDER);
            card.fillRect(x, footerTop, width, 1);
            card.setClip(cardClip);
        }
        card.setColor(StudioTheme.OVERLAY_BORDER);
        card.draw(outline);
        card.dispose();
    }
}
