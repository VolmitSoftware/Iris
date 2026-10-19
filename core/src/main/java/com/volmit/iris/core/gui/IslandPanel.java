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

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * A rounded panel set apart from the window edges, like the tool windows of IntelliJ's islands theme. Its right edge
 * meets a split pane divider, which provides the gap there.
 */
final class IslandPanel extends JPanel {
    private static final long serialVersionUID = 7313084027614358519L;
    private static final int GAP = 6;
    private static final int CORNER_ARC = 12;
    private static final int INNER_PADDING = 4;

    IslandPanel(JComponent content) {
        super(new BorderLayout());
        setOpaque(false);
        setBorder(StudioTheme.emptyBorder(GAP + INNER_PADDING, GAP + INNER_PADDING, GAP + INNER_PADDING, INNER_PADDING));
        add(content, BorderLayout.CENTER);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            canvas.setColor(StudioTheme.PANEL_BACKGROUND);
            int gap = StudioTheme.scale(GAP);
            int arc = StudioTheme.scale(CORNER_ARC);
            canvas.fill(new RoundRectangle2D.Double(gap, gap, getWidth() - gap, getHeight() - gap * 2D, arc, arc));
        } finally {
            canvas.dispose();
        }
    }
}
