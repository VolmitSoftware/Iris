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

import javax.swing.CellRendererPane;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.LayerUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.TreePath;
import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the folders around the top row of a scrolled tree pinned at the top, so a long folder never hides where it
 * is. The next folder pushes the last header up as it arrives, and clicking a header jumps to its folder.
 */
final class StickyFolderHeaders extends LayerUI<JScrollPane> {
    private static final long serialVersionUID = -2205474187531254372L;
    private static final int SHADOW_HEIGHT = 4;
    private static final Color SHADOW = new ThemeColor(new Color(0, 0, 0, 28), new Color(0, 0, 0, 90));
    private static final Color NO_SHADOW = new Color(0, 0, 0, 0);
    // The folder handle sits one indent to the left of the folder's name, as the tree draws it.
    private static final int HANDLE_NUDGE = 1;

    private final JTree tree;
    private final CellRendererPane rendererPane = new CellRendererPane();
    private transient JLayer<JScrollPane> layer;
    private int hoveredHeader = -1;

    private StickyFolderHeaders(JTree tree) {
        this.tree = tree;
    }

    static JLayer<JScrollPane> around(JTree tree, JScrollPane scrollPane) {
        // Copying the scrolled pixels would drag the pinned headers along, so every scroll repaints instead.
        scrollPane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        StickyFolderHeaders headers = new StickyFolderHeaders(tree);
        JLayer<JScrollPane> layer = new JLayer<>(scrollPane, headers);
        scrollPane.getViewport().addChangeListener(event -> layer.repaint());
        return layer;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void installUI(JComponent component) {
        super.installUI(component);
        layer = (JLayer<JScrollPane>) component;
        layer.setLayerEventMask(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
        tree.add(rendererPane);
    }

    @Override
    public void uninstallUI(JComponent component) {
        layer.setLayerEventMask(0);
        tree.remove(rendererPane);
        layer = null;
        super.uninstallUI(component);
    }

    @Override
    public void paint(Graphics graphics, JComponent component) {
        super.paint(graphics, component);
        List<Header> headers = pinnedHeaders();
        if (headers.isEmpty()) {
            return;
        }
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            Rectangle viewport = viewportBounds();
            canvas.clipRect(viewport.x, viewport.y, viewport.width, viewport.height);
            for (int index = 0; index < headers.size(); index++) {
                paintHeader(canvas, headers.get(index), index == hoveredHeader, viewport);
            }
            Header last = headers.get(headers.size() - 1);
            int shadowTop = viewport.y + last.top() + tree.getRowHeight();
            canvas.setPaint(new GradientPaint(0, shadowTop, SHADOW, 0, shadowTop + StudioTheme.scale(SHADOW_HEIGHT), NO_SHADOW));
            canvas.fillRect(viewport.x, shadowTop, viewport.width, StudioTheme.scale(SHADOW_HEIGHT));
        } finally {
            canvas.dispose();
        }
    }

    private void paintHeader(Graphics2D canvas, Header header, boolean hovered, Rectangle viewport) {
        int rowHeight = tree.getRowHeight();
        int top = viewport.y + header.top();
        Rectangle row = tree.getRowBounds(header.row());
        int left = viewport.x + row.x - scrolledView().x;
        canvas.setColor(hovered ? StudioTheme.HOVER_BACKGROUND : tree.getBackground());
        canvas.fillRect(viewport.x, top, viewport.width, rowHeight);
        paintFolderHandle(canvas, left, top + rowHeight / 2);
        Component renderer = tree.getCellRenderer().getTreeCellRendererComponent(
                tree, header.path().getLastPathComponent(), false, true, false, header.row(), false);
        rendererPane.paintComponent(canvas, renderer, tree, left, top, viewport.x + viewport.width - left, rowHeight, true);
    }

    private void paintFolderHandle(Graphics2D canvas, int folderLeft, int middleY) {
        Icon handle = UIManager.getIcon("Tree.expandedIcon");
        if (handle == null || !(tree.getUI() instanceof BasicTreeUI treeUI)) {
            return;
        }
        int middleX = folderLeft - treeUI.getRightChildIndent() + HANDLE_NUDGE;
        handle.paintIcon(tree, canvas, middleX - handle.getIconWidth() / 2, middleY - handle.getIconHeight() / 2);
    }

    @Override
    protected void processMouseEvent(MouseEvent event, JLayer<? extends JScrollPane> source) {
        if (event.getID() == MouseEvent.MOUSE_EXITED) {
            hover(-1);
            return;
        }
        int index = headerAt(event);
        if (index < 0) {
            return;
        }
        event.consume();
        if (event.getID() == MouseEvent.MOUSE_CLICKED && SwingUtilities.isLeftMouseButton(event)) {
            jumpTo(pinnedHeaders().get(index), index);
        }
    }

    @Override
    protected void processMouseMotionEvent(MouseEvent event, JLayer<? extends JScrollPane> source) {
        hover(headerAt(event));
    }

    private void hover(int index) {
        if (index == hoveredHeader) {
            return;
        }
        hoveredHeader = index;
        tree.setCursor(index >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : null);
        layer.repaint();
    }

    // The folder lands just below the headers of the folders around it.
    private void jumpTo(Header header, int level) {
        Rectangle row = tree.getRowBounds(header.row());
        JViewport viewport = layer.getView().getViewport();
        int top = Math.max(0, row.y - level * tree.getRowHeight());
        int bottomLimit = Math.max(0, tree.getHeight() - viewport.getExtentSize().height);
        viewport.setViewPosition(new Point(viewport.getViewPosition().x, Math.min(top, bottomLimit)));
    }

    private int headerAt(MouseEvent event) {
        if (layer == null || event.getComponent() == null) {
            return -1;
        }
        Point point = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), layer);
        Rectangle viewport = viewportBounds();
        if (!viewport.contains(point)) {
            return -1;
        }
        List<Header> headers = pinnedHeaders();
        for (int index = headers.size() - 1; index >= 0; index--) {
            int top = viewport.y + headers.get(index).top();
            if (point.y >= top && point.y < top + tree.getRowHeight()) {
                return index;
            }
        }
        return -1;
    }

    /**
     * For each depth, the folder holding the row just under the headers above it, as long as that folder's own row
     * has scrolled out of sight.
     */
    private List<Header> pinnedHeaders() {
        List<Header> headers = new ArrayList<>();
        int rowHeight = tree.getRowHeight();
        if (layer == null || rowHeight <= 0 || tree.getRowCount() == 0) {
            return headers;
        }
        Rectangle view = scrolledView();
        for (int level = 0; ; level++) {
            int slotTop = level * rowHeight;
            int row = tree.getClosestRowForLocation(view.x, view.y + slotTop);
            TreePath path = row < 0 ? null : tree.getPathForRow(row);
            if (path == null || !isInsideFolderAt(path, level)) {
                return headers;
            }
            TreePath folder = ancestorAt(path, level);
            Rectangle lastRow = tree.getRowBounds(lastRowInside(folder, row));
            int top = Math.min(slotTop, lastRow.y + lastRow.height - view.y - rowHeight);
            headers.add(new Header(folder, tree.getRowForPath(folder), top));
            if (top < slotTop) {
                return headers;
            }
        }
    }

    // The hidden root is the path's first element, so the folder at depth 0 is its second.
    private static boolean isInsideFolderAt(TreePath path, int level) {
        return path.getPathCount() > level + 2;
    }

    private static TreePath ancestorAt(TreePath path, int level) {
        TreePath ancestor = path;
        while (ancestor.getPathCount() > level + 2) {
            ancestor = ancestor.getParentPath();
        }
        return ancestor;
    }

    // A folder's rows sit together, so the last one is found by halving the rows after a row known to be inside.
    private int lastRowInside(TreePath folder, int rowInside) {
        int low = rowInside;
        int high = tree.getRowCount() - 1;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (folder.isDescendant(tree.getPathForRow(middle))) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }

    private Rectangle scrolledView() {
        return layer.getView().getViewport().getViewRect();
    }

    private Rectangle viewportBounds() {
        JViewport viewport = layer.getView().getViewport();
        Point origin = SwingUtilities.convertPoint(viewport, 0, 0, layer);
        return new Rectangle(origin.x, origin.y, viewport.getWidth(), viewport.getHeight());
    }

    /**
     * A pinned folder, its row in the tree and how far below the top of the visible rows it is drawn.
     */
    private record Header(TreePath path, int row, int top) {
    }
}
