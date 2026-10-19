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

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * Draws the floating cards (legends, hover details, hints) on top of a map, styled like IntelliJ's popups.
 */
final class StudioOverlay {
    private StudioOverlay() {
    }

    static void drawCard(Graphics2D canvas, int x, int y, int width, int height) {
        Object antialiasing = canvas.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        RoundRectangle2D card = new RoundRectangle2D.Double(x + 0.5D, y + 0.5D, width - 1D, height - 1D,
                StudioTheme.OVERLAY_ARC, StudioTheme.OVERLAY_ARC);
        canvas.setColor(StudioTheme.OVERLAY_BACKGROUND);
        canvas.fill(card);
        canvas.setColor(StudioTheme.OVERLAY_BORDER);
        canvas.draw(card);
        canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
    }
}
