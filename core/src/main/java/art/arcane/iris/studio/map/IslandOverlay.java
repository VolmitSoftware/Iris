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
import javax.swing.JLayeredPane;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Lays an island over content that fills the whole area behind it. Dragging the island's right edge resizes it,
 * always leaving the content some room beside it.
 */
final class IslandOverlay extends JLayeredPane {
    private static final long serialVersionUID = -4182209135360718342L;

    private final IslandPanel island;
    private final JComponent background;
    private final int minimumIslandWidth;
    private final int minimumUncoveredWidth;
    private int islandWidth;

    IslandOverlay(IslandPanel island, JComponent background, int islandWidth, int minimumIslandWidth, int minimumUncoveredWidth) {
        this.island = island;
        this.background = background;
        this.islandWidth = islandWidth;
        this.minimumIslandWidth = minimumIslandWidth;
        this.minimumUncoveredWidth = minimumUncoveredWidth;
        add(background, JLayeredPane.DEFAULT_LAYER);
        add(island, JLayeredPane.PALETTE_LAYER);
        installResizing();
    }

    @Override
    public void doLayout() {
        background.setBounds(0, 0, getWidth(), getHeight());
        island.setBounds(0, 0, clampedIslandWidth(), getHeight());
    }

    private int clampedIslandWidth() {
        int widest = Math.max(minimumIslandWidth, getWidth() - minimumUncoveredWidth);
        return Math.clamp(islandWidth, minimumIslandWidth, widest);
    }

    private void installResizing() {
        MouseAdapter resizer = new MouseAdapter() {
            private boolean dragging;

            @Override
            public void mouseMoved(MouseEvent event) {
                island.setCursor(island.isOnResizeEdge(event.getX(), event.getY())
                        ? Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)
                        : Cursor.getDefaultCursor());
            }

            @Override
            public void mouseExited(MouseEvent event) {
                if (!dragging) {
                    island.setCursor(Cursor.getDefaultCursor());
                }
            }

            @Override
            public void mousePressed(MouseEvent event) {
                dragging = island.isOnResizeEdge(event.getX(), event.getY());
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (!dragging) {
                    return;
                }
                islandWidth = event.getX();
                islandWidth = clampedIslandWidth();
                revalidate();
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                dragging = false;
            }
        };
        island.addMouseListener(resizer);
        island.addMouseMotionListener(resizer);
    }
}
