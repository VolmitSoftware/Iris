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

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.UnaryOperator;

/**
 * The 3D view of one object. Drag to orbit, shift-drag or middle-drag to pan, scroll to zoom; arrow keys orbit and
 * plus and minus zoom too. Frames are drawn on a background thread, one at a time, always for the latest camera.
 * While the next object loads, the last picture stays up; only a slow load dims it under the loading animation.
 */
final class ObjectViewport extends JComponent {
    private static final long serialVersionUID = -3175226517409913524L;
    private static final double WHEEL_ZOOM_EXPONENT = 0.12D;
    private static final double KEY_ZOOM_FACTOR = 1.1D;
    private static final int KEY_ORBIT_PIXELS = 12;
    private static final float LOADING_DIM_OPACITY = 0.88F;
    private static final double LOADING_BLOCK_SIZE = 16D;
    private static final int LOADING_TEXT_GAP = 46;

    private final ExecutorService renderThread = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Iris Object Viewer");
        thread.setDaemon(true);
        return thread;
    });
    private ObjectMesh mesh;
    private ObjectCamera camera = ObjectCamera.START;
    private BufferedImage frame;
    private String message = "Choose an object";
    private boolean rendering;
    private boolean renderAgain;
    private Point dragPoint;
    private int coveredLeftWidth;
    private final Timer loadingTicker = new Timer(LoadingAnimation.FRAME_MILLIS, event -> repaint());
    private String loadingText;
    private long loadingSinceMillis;

    ObjectViewport() {
        setOpaque(true);
        setBackground(StudioTheme.EDITOR_BACKGROUND);
        setFocusable(true);
        installMouseControls();
        installKeyControls();
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                requestFrame();
            }
        });
    }

    // The theme changed, and the last frame was drawn on the old theme's background.
    @Override
    public void updateUI() {
        super.updateUI();
        requestFrame();
    }

    // The last picture stays up until the first one of this mesh is drawn.
    void show(ObjectMesh nextMesh) {
        mesh = nextMesh;
        camera = ObjectCamera.START;
        message = nextMesh.isEmpty() ? "No visible blocks" : null;
        if (nextMesh.isEmpty()) {
            frame = null;
            finishLoading();
        }
        requestFrame();
        repaint();
    }

    /**
     * Marks the view as loading something new. A load already under way keeps its start, so stepping quickly through
     * objects still shows the animation once the wait adds up.
     */
    void showLoading(String text) {
        if (loadingText == null) {
            loadingSinceMillis = System.currentTimeMillis();
        }
        loadingText = text;
        message = null;
        loadingTicker.start();
    }

    private void finishLoading() {
        loadingText = null;
        loadingTicker.stop();
    }

    /**
     * Swaps in another mesh of the same object, like one with its top layers cut away, keeping the camera.
     */
    void replaceMesh(ObjectMesh nextMesh) {
        mesh = nextMesh;
        message = nextMesh.isEmpty() ? "No visible blocks" : null;
        requestFrame();
        repaint();
    }

    void bindKey(int keyCode, String name, Runnable action) {
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(keyCode, 0), name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
    }

    void showMessage(String text) {
        mesh = null;
        message = text;
        frame = null;
        finishLoading();
        repaint();
    }

    /**
     * Keeps the object clear of a panel floating over the viewport's left side.
     */
    void setCoveredLeftWidth(int width) {
        if (width == coveredLeftWidth) {
            return;
        }
        coveredLeftWidth = width;
        requestFrame();
    }

    void resetCamera() {
        moveCamera(current -> ObjectCamera.START);
    }

    void close() {
        loadingTicker.stop();
        renderThread.shutdownNow();
    }

    private void moveCamera(UnaryOperator<ObjectCamera> move) {
        camera = move.apply(camera);
        requestFrame();
    }

    private void requestFrame() {
        if (mesh == null || mesh.isEmpty() || getWidth() < 1 || getHeight() < 1) {
            return;
        }
        if (rendering) {
            renderAgain = true;
            return;
        }
        rendering = true;
        ObjectMesh frameMesh = mesh;
        ObjectCamera frameCamera = camera;
        int width = getWidth();
        int height = getHeight();
        int covered = coveredLeftWidth;
        renderThread.execute(() -> {
            BufferedImage image = ObjectRenderer.render(frameMesh, frameCamera, width, height, covered);
            EventQueue.invokeLater(() -> finishFrame(frameMesh, image));
        });
    }

    private void finishFrame(ObjectMesh frameMesh, BufferedImage image) {
        rendering = false;
        if (frameMesh == mesh) {
            frame = image;
            finishLoading();
            repaint();
        }
        if (renderAgain) {
            renderAgain = false;
            requestFrame();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        graphics.setColor(getBackground());
        graphics.fillRect(0, 0, getWidth(), getHeight());
        if (frame != null) {
            graphics.drawImage(frame, 0, 0, null);
        }
        if (loadingText != null && LoadingAnimation.isDue(loadingSinceMillis)) {
            drawLoading((Graphics2D) graphics);
        }
        if (message != null) {
            drawMessage((Graphics2D) graphics);
        }
    }

    private void drawLoading(Graphics2D graphics) {
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            if (frame != null) {
                canvas.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, LOADING_DIM_OPACITY));
                canvas.setColor(getBackground());
                canvas.fillRect(0, 0, getWidth(), getHeight());
                canvas.setComposite(AlphaComposite.SrcOver);
            }
            int centerX = coveredLeftWidth + (getWidth() - coveredLeftWidth) / 2;
            int centerY = getHeight() / 2;
            long shownMillis = System.currentTimeMillis() - loadingSinceMillis - LoadingAnimation.SHOW_AFTER_MILLIS;
            LoadingAnimation.paint(canvas, centerX, centerY, StudioTheme.scale(LOADING_BLOCK_SIZE), shownMillis);
            canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            canvas.setFont(StudioTheme.font());
            canvas.setColor(StudioTheme.MUTED_TEXT);
            String text = loadingText + "...";
            int textWidth = canvas.getFontMetrics().stringWidth(text);
            canvas.drawString(text, centerX - textWidth / 2, centerY + StudioTheme.scale(LOADING_TEXT_GAP));
        } finally {
            canvas.dispose();
        }
    }

    private void drawMessage(Graphics2D canvas) {
        canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        canvas.setFont(StudioTheme.font());
        canvas.setColor(StudioTheme.MUTED_TEXT);
        int textWidth = canvas.getFontMetrics().stringWidth(message);
        canvas.drawString(message, (getWidth() - textWidth) / 2, getHeight() / 2);
    }

    private void installMouseControls() {
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                dragPoint = event.getPoint();
                requestFocusInWindow();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                dragPoint = null;
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (dragPoint == null) {
                    return;
                }
                int draggedX = event.getX() - dragPoint.x;
                int draggedY = event.getY() - dragPoint.y;
                dragPoint = event.getPoint();
                boolean panning = event.isShiftDown() || SwingUtilities.isMiddleMouseButton(event);
                moveCamera(current -> panning
                        ? current.pannedBy(2D * draggedX / Math.max(1, getWidth() - coveredLeftWidth), -2D * draggedY / getHeight())
                        : current.orbitedBy(draggedX, draggedY));
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent event) {
                moveCamera(current -> current.zoomedBy(Math.exp(-event.getPreciseWheelRotation() * WHEEL_ZOOM_EXPONENT)));
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    private void installKeyControls() {
        bindKey(KeyEvent.VK_LEFT, "orbit-left", current -> current.orbitedBy(-KEY_ORBIT_PIXELS, 0));
        bindKey(KeyEvent.VK_RIGHT, "orbit-right", current -> current.orbitedBy(KEY_ORBIT_PIXELS, 0));
        bindKey(KeyEvent.VK_UP, "orbit-up", current -> current.orbitedBy(0, KEY_ORBIT_PIXELS));
        bindKey(KeyEvent.VK_DOWN, "orbit-down", current -> current.orbitedBy(0, -KEY_ORBIT_PIXELS));
        bindKey(KeyEvent.VK_EQUALS, "zoom-in", current -> current.zoomedBy(KEY_ZOOM_FACTOR));
        bindKey(KeyEvent.VK_ADD, "zoom-in-keypad", current -> current.zoomedBy(KEY_ZOOM_FACTOR));
        bindKey(KeyEvent.VK_MINUS, "zoom-out", current -> current.zoomedBy(1D / KEY_ZOOM_FACTOR));
        bindKey(KeyEvent.VK_SUBTRACT, "zoom-out-keypad", current -> current.zoomedBy(1D / KEY_ZOOM_FACTOR));
    }

    private void bindKey(int keyCode, String name, UnaryOperator<ObjectCamera> move) {
        bindKey(keyCode, name, () -> moveCamera(move));
    }
}
