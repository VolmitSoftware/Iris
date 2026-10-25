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
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * How many frames a second a 3D view keeps up, right-aligned, floating over the view.
 */
final class FrameRateLabel extends JComponent {
    private static final long serialVersionUID = -6402187316475095871L;
    private static final String WIDEST_TEXT = "8888 fps";

    private String text = "";

    FrameRateLabel() {
        setOpaque(false);
    }

    void showRate(double framesPerSecond) {
        String next = Math.round(framesPerSecond) + " fps";
        if (!next.equals(text)) {
            text = next;
            repaint();
        }
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics metrics = getFontMetrics(StudioTheme.smallFont());
        return new Dimension(metrics.stringWidth(WIDEST_TEXT), metrics.getHeight());
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D canvas = (Graphics2D) graphics;
        canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        canvas.setFont(StudioTheme.smallFont());
        canvas.setColor(StudioTheme.MUTED_TEXT);
        FontMetrics metrics = canvas.getFontMetrics();
        canvas.drawString(text, getWidth() - metrics.stringWidth(text), metrics.getAscent());
    }
}
