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
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * A rounded panel floating over the window content with a gap all around it, like the tool windows of IntelliJ's
 * islands theme. The gap belongs to the content behind it, except for the strip along the right edge, which grabs the
 * island to resize it.
 */
public final class IslandPanel extends JPanel implements FloatingPanels.PartlySeeThrough {
    private static final long serialVersionUID = 7313084027614358519L;
    private static final int GAP = 6;
    private static final int CORNER_ARC = 12;
    private static final int INNER_PADDING = 4;

    public IslandPanel(JComponent content) {
        super(new BorderLayout());
        setOpaque(false);
        int inset = GAP + INNER_PADDING;
        setBorder(StudioTheme.emptyBorder(inset, inset, inset, inset));
        add(content, BorderLayout.CENTER);
    }

    boolean isOnResizeEdge(int x, int y) {
        int edgeWidth = StudioTheme.scale(GAP + INNER_PADDING);
        return x >= getWidth() - edgeWidth && x < getWidth() && y >= 0 && y < getHeight();
    }

    // Clicks in the gap reach the content behind the island instead of the island itself.
    @Override
    public boolean contains(int x, int y) {
        return island().contains(x, y) || isOnResizeEdge(x, y);
    }

    // The gap all around, reaching in as far as the rounded corners do.
    @Override
    public List<Rectangle> seeThroughParts() {
        int width = getWidth();
        int height = getHeight();
        int gap = StudioTheme.scale(GAP);
        int edge = gap + (int) Math.ceil(StudioTheme.scale((double) CORNER_ARC) / 2D);
        return List.of(
                new Rectangle(0, 0, width, edge),
                new Rectangle(0, height - edge, width, edge),
                new Rectangle(0, edge, gap, height - edge * 2),
                new Rectangle(width - gap, edge, gap, height - edge * 2));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            canvas.setColor(StudioTheme.PANEL_BACKGROUND);
            canvas.fill(island());
        } finally {
            canvas.dispose();
        }
    }

    private RoundRectangle2D island() {
        double gap = StudioTheme.scale(GAP);
        double arc = StudioTheme.scale(CORNER_ARC);
        return new RoundRectangle2D.Double(gap, gap, getWidth() - gap * 2D, getHeight() - gap * 2D, arc, arc);
    }
}
