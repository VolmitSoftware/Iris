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

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.jawt.JAWT;
import org.lwjgl.system.jawt.JAWTDrawingSurface;
import org.lwjgl.system.jawt.JAWTDrawingSurfaceInfo;
import org.lwjgl.system.jawt.JAWTX11DrawingSurfaceInfo;

import javax.swing.SwingUtilities;
import java.awt.Canvas;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

import static org.lwjgl.system.jawt.JAWTFunctions.*;

/**
 * A native window inside a 3D view that the graphics card draws frames straight into. Swing can't paint over it, so
 * panels floating over the view are cut out of it, and the view paints what lies behind them itself. The mouse belongs
 * to the view behind it, as if this window weren't there.
 */
final class GpuCanvas extends Canvas {
    private static final long serialVersionUID = 2874650379461920137L;

    private final transient Component view;
    private final transient Runnable onExposed;
    private volatile long window;
    private volatile long visualId;

    /**
     * @param onExposed runs on the Swing thread when what the window showed was lost and has to be drawn again
     */
    GpuCanvas(Component view, Runnable onExposed) {
        this.view = view;
        this.onExposed = onExposed;
        setFocusable(false);
        setBackground(StudioTheme.EDITOR_BACKGROUND);
        handMouseToView();
    }

    /**
     * @return the X window frames go into, or 0 while there is none
     */
    long window() {
        return window;
    }

    long visualId() {
        return visualId;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        readNativeWindow();
    }

    // A frame on its way into the window would otherwise draw into one that no longer exists.
    @Override
    public void removeNotify() {
        long goingWindow = window;
        window = 0L;
        if (goingWindow != 0L) {
            GpuContext.forgetWindow(goingWindow);
        }
        super.removeNotify();
    }

    // Erasing to the background first would flash it between frames.
    @Override
    public void update(Graphics graphics) {
        onExposed.run();
    }

    @Override
    public void paint(Graphics graphics) {
        onExposed.run();
    }

    private void readNativeWindow() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            JAWT awt = JAWT.calloc(stack).version(JAWT_VERSION_1_4);
            if (!JAWT_GetAWT(awt)) {
                return;
            }
            JAWTDrawingSurface surface = JAWT_GetDrawingSurface(this, awt.GetDrawingSurface());
            if (surface == null) {
                return;
            }
            try {
                readLocked(surface);
            } finally {
                JAWT_FreeDrawingSurface(surface, awt.FreeDrawingSurface());
            }
        }
    }

    private void readLocked(JAWTDrawingSurface surface) {
        if ((JAWT_DrawingSurface_Lock(surface, surface.Lock()) & JAWT_LOCK_ERROR) != 0) {
            return;
        }
        try {
            JAWTDrawingSurfaceInfo info = JAWT_DrawingSurface_GetDrawingSurfaceInfo(surface, surface.GetDrawingSurfaceInfo());
            if (info == null) {
                return;
            }
            try {
                JAWTX11DrawingSurfaceInfo x11 = JAWTX11DrawingSurfaceInfo.create(info.platformInfo());
                visualId = x11.visualID();
                window = x11.drawable();
            } finally {
                JAWT_DrawingSurface_FreeDrawingSurfaceInfo(info, surface.FreeDrawingSurfaceInfo());
            }
        } finally {
            JAWT_DrawingSurface_Unlock(surface, surface.Unlock());
        }
    }

    private void handMouseToView() {
        MouseAdapter handOver = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                handOver(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                handOver(event);
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                handOver(event);
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                handOver(event);
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                handOver(event);
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent event) {
                handOver(event);
            }
        };
        addMouseListener(handOver);
        addMouseMotionListener(handOver);
        addMouseWheelListener(handOver);
    }

    private void handOver(MouseEvent event) {
        view.dispatchEvent(SwingUtilities.convertMouseEvent(this, event, view));
    }
}
