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

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds the Swing panels floating over a native window, the way AWT does when it cuts them out of that window. Only
 * for the Swing thread.
 */
final class FloatingPanels {
    private FloatingPanels() {
    }

    /**
     * A floating panel that hides what lies behind most of it, and only lets it show through some parts.
     */
    interface PartlySeeThrough {
        /**
         * @return where what lies behind shows through, in the panel's own coordinates
         */
        List<Rectangle> seeThroughParts();
    }

    /**
     * @return where what the window shows has to show through the panels over it, in the window's coordinates
     */
    static List<Rectangle> seeThroughOver(Component window) {
        List<Rectangle> parts = new ArrayList<>();
        Rectangle windowBounds = new Rectangle(0, 0, window.getWidth(), window.getHeight());
        Component child = window;
        Container parent = child.getParent();
        while (parent != null) {
            for (int index = parent.getComponentZOrder(child) - 1; index >= 0; index--) {
                Component above = parent.getComponent(index);
                if (above.isLightweight() && above.isShowing()) {
                    addSeeThroughParts(above, window, windowBounds, parts);
                }
            }
            if (!parent.isLightweight()) {
                break;
            }
            child = parent;
            parent = parent.getParent();
        }
        return parts;
    }

    private static void addSeeThroughParts(Component panel, Component window, Rectangle windowBounds, List<Rectangle> parts) {
        List<Rectangle> panelParts = panel instanceof PartlySeeThrough partly
                ? partly.seeThroughParts()
                : List.of(new Rectangle(0, 0, panel.getWidth(), panel.getHeight()));
        for (Rectangle part : panelParts) {
            Rectangle inWindow = SwingUtilities.convertRectangle(panel, part, window).intersection(windowBounds);
            if (!inWindow.isEmpty()) {
                parts.add(inWindow);
            }
        }
    }
}
