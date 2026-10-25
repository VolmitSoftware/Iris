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

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.UnaryOperator;

/**
 * A 3D view of one scene, like an object or a stretch of noise. Drag to orbit around what is in the middle; right-drag,
 * middle-drag or shift-drag to pan; scroll to zoom toward the cursor. Arrow keys orbit and plus and minus zoom too. Frames are drawn on a background thread, one at a time, always
 * for the latest camera. While the next scene loads, the last picture stays up; only a slow load dims it under the
 * loading animation.
 */
final class OrbitViewport extends JComponent {
    private static final long serialVersionUID = -3175226517409913524L;
    private static final double WHEEL_ZOOM_EXPONENT = 0.12D;
    private static final double KEY_ZOOM_FACTOR = 1.1D;
    private static final int KEY_ORBIT_PIXELS = 12;
    private static final float LOADING_DIM_OPACITY = 0.88F;
    private static final double LOADING_BLOCK_SIZE = 16D;
    private static final int LOADING_TEXT_GAP = 46;
    private static final double FRAME_TIME_SMOOTHING = 0.2D;
    private static final double NANOS_PER_SECOND = 1_000_000_000D;
    private static final int FRAME_RATE_MARGIN = 10;

    private final ExecutorService renderThread;
    private final GpuCanvas canvas;
    private final FrameRateLabel frameRateLabel = new FrameRateLabel();
    private OrbitScene scene;
    private OrbitCamera camera = OrbitCamera.START;
    private OrbitPicture picture;
    private OrbitCamera pictureCamera;
    private String message;
    private EmptyState emptyState;
    private boolean rendering;
    private boolean renderAgain;
    private Point dragPoint;
    private Drag drag = Drag.ORBIT;
    private int coveredLeftWidth;
    private final Timer loadingTicker = new Timer(LoadingAnimation.FRAME_MILLIS, event -> showLoadingProgress());
    private final Timer loadingCloser = singleShot(event -> finishLoading());
    private String loadingText;
    private long loadingSinceMillis;
    private double smoothedFrameNanos;
    private Runnable viewChanged = () -> {
    };

    /**
     * @param initialMessage what the view says until it is given something to show
     */
    OrbitViewport(String renderThreadName, String initialMessage) {
        message = initialMessage;
        renderThread = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, renderThreadName);
            thread.setDaemon(true);
            return thread;
        });
        setOpaque(true);
        setBackground(StudioTheme.EDITOR_BACKGROUND);
        setFocusable(true);
        frameRateLabel.setVisible(false);
        add(frameRateLabel);
        canvas = GpuLibrary.isLoaded() && GlxDisplay.isSwingOnX() ? new GpuCanvas(this, this::requestFrame) : null;
        if (canvas != null) {
            canvas.setVisible(false);
            add(canvas);
        }
        installMouseControls();
        installKeyControls();
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                requestFrame();
                viewChanged.run();
            }
        });
    }

    // The theme changed, and the last frame was drawn on the old theme's background.
    @Override
    public void updateUI() {
        super.updateUI();
        requestFrame();
    }

    @Override
    public void doLayout() {
        if (canvas != null) {
            canvas.setBounds(0, 0, getWidth(), getHeight());
        }
        Dimension label = frameRateLabel.getPreferredSize();
        int margin = StudioTheme.scale(FRAME_RATE_MARGIN);
        frameRateLabel.setBounds(getWidth() - margin - label.width, margin, label.width, label.height);
    }

    // The frame rate floats over the window frames are drawn into.
    @Override
    public boolean isOptimizedDrawingEnabled() {
        return false;
    }

    // The last picture stays up until the first one of this scene is drawn.
    void show(OrbitScene nextScene) {
        scene = nextScene;
        camera = nextScene.startingCamera();
        message = nextScene.isEmpty() ? "No visible blocks" : null;
        emptyState = null;
        if (nextScene.isEmpty()) {
            forgetPicture();
            finishLoading();
        }
        requestFrame();
        repaint();
        viewChanged.run();
    }

    /**
     * @param listener runs on the Swing thread whenever what the view shows of its scene moves: the camera, or the
     *                 view's size or covered part
     */
    void onViewChange(Runnable listener) {
        viewChanged = listener;
    }

    OrbitCamera camera() {
        return camera;
    }

    int coveredLeftWidth() {
        return coveredLeftWidth;
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
        emptyState = null;
        loadingCloser.stop();
        loadingTicker.start();
    }

    // A load that finishes just after the animation appeared would otherwise only flash it.
    private void finishLoadingOnceShownLongEnough() {
        long remainingMillis = LoadingAnimation.remainingShowMillis(loadingSinceMillis);
        if (loadingText == null || remainingMillis == 0L) {
            finishLoading();
            return;
        }
        loadingCloser.setInitialDelay((int) remainingMillis);
        loadingCloser.restart();
    }

    // Swing paints the animation over the whole view, so it is shown over a picture rather than over the window.
    private void showLoadingProgress() {
        if (canvas != null && canvas.isVisible() && !drawsOnScreen()) {
            requestFrame();
        }
        updateFrameRateLabel();
        repaint();
    }

    private boolean isLoadingShown() {
        return loadingText != null && LoadingAnimation.isDue(loadingSinceMillis);
    }

    // Every picture finishes loading, so only a load still under way asks for a frame, or each frame would ask for the next.
    private void finishLoading() {
        if (loadingText == null) {
            return;
        }
        loadingText = null;
        loadingTicker.stop();
        loadingCloser.stop();
        updateFrameRateLabel();
        requestFrame();
        repaint();
    }

    private static Timer singleShot(ActionListener action) {
        Timer timer = new Timer(0, action);
        timer.setRepeats(false);
        return timer;
    }

    /**
     * Swaps in another version of the same scene, like an object with its top layers cut away, keeping the camera.
     */
    void replaceScene(OrbitScene nextScene) {
        scene = nextScene;
        message = nextScene.isEmpty() ? "No visible blocks" : null;
        emptyState = null;
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

    boolean isShowingScene() {
        return scene != null;
    }

    void showMessage(String text) {
        scene = null;
        message = text;
        emptyState = null;
        forgetPicture();
        finishLoading();
        repaint();
    }

    void showEmptyState(EmptyState state) {
        scene = null;
        message = null;
        emptyState = state;
        forgetPicture();
        finishLoading();
        repaint();
    }

    private void forgetPicture() {
        picture = null;
        hideCanvas();
        updateFrameRateLabel();
    }

    private void hideCanvas() {
        if (canvas != null && canvas.isVisible()) {
            canvas.setVisible(false);
        }
    }

    private void updateFrameRateLabel() {
        frameRateLabel.setVisible(picture != null && smoothedFrameNanos != 0D && !isLoadingShown());
    }

    /**
     * Keeps the scene clear of a panel floating over the viewport's left side.
     */
    void setCoveredLeftWidth(int width) {
        if (width == coveredLeftWidth) {
            return;
        }
        coveredLeftWidth = width;
        requestFrame();
        viewChanged.run();
    }

    // Turns around the scene's middle again, keeping the angle and zoom.
    void centerPivot() {
        moveCamera(current -> new OrbitCamera(current.yaw(), current.pitch(), current.zoom(), Vec3.ZERO));
    }

    void resetCamera() {
        moveCamera(current -> scene == null ? OrbitCamera.START : scene.startingCamera());
    }

    void close() {
        loadingTicker.stop();
        loadingCloser.stop();
        renderThread.shutdownNow();
    }

    private void moveCamera(UnaryOperator<OrbitCamera> move) {
        camera = move.apply(camera);
        requestFrame();
        viewChanged.run();
    }

    private void requestFrame() {
        if (scene == null || scene.isEmpty() || getWidth() < 1 || getHeight() < 1) {
            return;
        }
        if (rendering) {
            renderAgain = true;
            return;
        }
        rendering = true;
        OrbitScene frameScene = scene;
        OrbitCamera frameCamera = camera;
        int width = getWidth();
        int height = getHeight();
        int covered = coveredLeftWidth;
        ScreenTarget screen = screenTarget();
        long requestedAtNanos = System.nanoTime();
        renderThread.execute(() -> {
            OrbitPicture drawn = draw(frameScene, screen, frameCamera, width, height, covered);
            boolean missedScreen = screen != null && !drawn.onScreen();
            EventQueue.invokeLater(() -> finishFrame(frameScene, frameCamera, drawn, missedScreen, requestedAtNanos));
        });
    }

    private static OrbitPicture draw(OrbitScene scene, ScreenTarget screen, OrbitCamera camera, int width, int height, int covered) {
        if (screen != null) {
            Optional<OrbitPicture> onScreen = scene.renderOnScreen(screen, camera, width, height, covered);
            if (onScreen.isPresent()) {
                return onScreen.get();
            }
        }
        return scene.render(camera, width, height, covered);
    }

    /**
     * Frames go straight into the window when the scene can be drawn that way, Swing has nothing to paint over the
     * whole view, and the screen's pixels are the view's own. The window is shown before the frame is drawn into it.
     *
     * @return the window, or nothing to draw a picture for Swing instead
     */
    private ScreenTarget screenTarget() {
        if (!drawsOnScreen()) {
            return null;
        }
        if (!canvas.isVisible()) {
            canvas.setVisible(true);
        }
        return new ScreenTarget(canvas, FloatingPanels.seeThroughOver(canvas));
    }

    private boolean drawsOnScreen() {
        return canvas != null && canvas.window() != 0L && scene != null && !scene.isEmpty() && scene.canRenderOnScreen()
                && !isLoadingShown() && hasUnscaledPixels();
    }

    private boolean hasUnscaledPixels() {
        GraphicsConfiguration configuration = getGraphicsConfiguration();
        return configuration != null && configuration.getDefaultTransform().isIdentity();
    }

    /**
     * Until the card is opened, nothing knows whether frames can go into the window; the first picture opens it. A
     * frame that should have gone into the window but couldn't isn't tried again right away. The next frame is asked
     * for before this one's patches are painted, so the card draws while Swing paints.
     */
    private void finishFrame(OrbitScene frameScene, OrbitCamera frameCamera, OrbitPicture drawn, boolean missedScreen,
                             long requestedAtNanos) {
        rendering = false;
        measureFrameTime(System.nanoTime() - requestedAtNanos);
        boolean shown = frameScene == scene;
        if (shown) {
            takePicture(drawn, frameCamera);
        }
        boolean canGoOnScreen = !missedScreen && canvas != null && !canvas.isVisible() && drawsOnScreen();
        if (renderAgain || drawn.catchingUp() || canGoOnScreen) {
            renderAgain = false;
            requestFrame();
        }
        if (shown) {
            paintPicture(drawn);
        }
    }

    // A whole picture replaces the window until frames go into it again.
    private void takePicture(OrbitPicture drawn, OrbitCamera frameCamera) {
        picture = drawn;
        pictureCamera = frameCamera;
        if (!drawn.onScreen()) {
            hideCanvas();
        }
        frameRateLabel.showRate(NANOS_PER_SECOND / smoothedFrameNanos);
        updateFrameRateLabel();
        finishLoadingOnceShownLongEnough();
    }

    private void paintPicture(OrbitPicture drawn) {
        if (!drawn.onScreen()) {
            repaint();
            return;
        }
        // Repainting would merge the patches into one box spanning most of the view, and paint everything in it.
        for (PicturePatch patch : drawn.patches()) {
            paintImmediately(patch.bounds());
        }
    }

    private void measureFrameTime(long frameNanos) {
        smoothedFrameNanos = smoothedFrameNanos == 0D
                ? frameNanos
                : smoothedFrameNanos + (frameNanos - smoothedFrameNanos) * FRAME_TIME_SMOOTHING;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        graphics.setColor(getBackground());
        graphics.fillRect(0, 0, getWidth(), getHeight());
        if (picture != null) {
            for (PicturePatch patch : picture.patches()) {
                graphics.drawImage(patch.image(), patch.x(), patch.y(), null);
            }
        }
        if (isLoadingShown()) {
            drawLoading((Graphics2D) graphics);
        }
        if (message != null) {
            drawMessage((Graphics2D) graphics);
        }
        if (emptyState != null) {
            emptyState.paint((Graphics2D) graphics, this, coveredLeftWidth + (getWidth() - coveredLeftWidth) / 2, getHeight() / 2);
        }
    }

    private void drawLoading(Graphics2D graphics) {
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            if (picture != null) {
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
                drag = Drag.startedBy(event);
                if (drag == Drag.ORBIT) {
                    pivotOnSurfaceInMiddle();
                }
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
                if (drag == Drag.PAN) {
                    panBy(draggedX, draggedY);
                } else {
                    moveCamera(current -> current.orbitedBy(draggedX, draggedY, limits()));
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent event) {
                zoomBy(Math.exp(-event.getPreciseWheelRotation() * WHEEL_ZOOM_EXPONENT), event.getX(), event.getY());
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    private void installKeyControls() {
        bindKey(KeyEvent.VK_LEFT, "orbit-left", current -> current.orbitedBy(-KEY_ORBIT_PIXELS, 0, limits()));
        bindKey(KeyEvent.VK_RIGHT, "orbit-right", current -> current.orbitedBy(KEY_ORBIT_PIXELS, 0, limits()));
        bindKey(KeyEvent.VK_UP, "orbit-up", current -> current.orbitedBy(0, KEY_ORBIT_PIXELS, limits()));
        bindKey(KeyEvent.VK_DOWN, "orbit-down", current -> current.orbitedBy(0, -KEY_ORBIT_PIXELS, limits()));
        bindKey(KeyEvent.VK_EQUALS, "zoom-in", () -> zoomTowardMiddle(KEY_ZOOM_FACTOR));
        bindKey(KeyEvent.VK_ADD, "zoom-in-keypad", () -> zoomTowardMiddle(KEY_ZOOM_FACTOR));
        bindKey(KeyEvent.VK_MINUS, "zoom-out", () -> zoomTowardMiddle(1D / KEY_ZOOM_FACTOR));
        bindKey(KeyEvent.VK_SUBTRACT, "zoom-out-keypad", () -> zoomTowardMiddle(1D / KEY_ZOOM_FACTOR));
    }

    private void bindKey(int keyCode, String name, UnaryOperator<OrbitCamera> move) {
        bindKey(keyCode, name, () -> moveCamera(move));
    }

    private OrbitLimits limits() {
        return scene == null ? OrbitLimits.DEFAULT : scene.limits();
    }

    private OrbitProjection currentProjection() {
        return scene.projection(camera, getWidth(), getHeight(), coveredLeftWidth);
    }

    // The ground follows the cursor, like dragging a map.
    private void panBy(int draggedX, int draggedY) {
        if (scene == null) {
            return;
        }
        Vec3 dragged = currentProjection().acrossPicture(-draggedX, -draggedY);
        moveCamera(current -> current.pannedBy(dragged));
    }

    private void zoomTowardMiddle(double factor) {
        if (scene == null) {
            return;
        }
        OrbitProjection projection = currentProjection();
        zoomBy(factor, projection.screenCenterX(), projection.screenCenterY());
    }

    // What is under the cursor stays under it.
    private void zoomBy(double factor, double cursorX, double cursorY) {
        if (scene == null) {
            return;
        }
        OrbitProjection projection = currentProjection();
        Vec3 towardCursor = projection.acrossPicture(cursorX - projection.screenCenterX(), cursorY - projection.screenCenterY());
        OrbitLimits limits = scene.limits();
        moveCamera(current -> current.zoomedBy(factor, limits, towardCursor));
    }

    /**
     * Slides the pivot along the line of sight onto whatever is drawn in the middle of the view, so orbiting turns
     * around it instead of a point in the air. The picture doesn't move, since the slide is along the line of sight.
     */
    private void pivotOnSurfaceInMiddle() {
        if (scene == null || picture == null || !camera.equals(pictureCamera)
                || picture.width() != getWidth() || picture.height() != getHeight()) {
            return;
        }
        OrbitProjection projection = currentProjection();
        picture.depthInMiddle().ifPresent(depth -> {
            camera = camera.pannedBy(projection.towardCamera().times(depth));
            pictureCamera = camera;
        });
    }

    private enum Drag {
        ORBIT,
        PAN;

        // Like most 3D editors: the left button orbits, the right or middle button, or shift, pans.
        static Drag startedBy(MouseEvent event) {
            boolean pans = event.isShiftDown() || SwingUtilities.isMiddleMouseButton(event) || SwingUtilities.isRightMouseButton(event);
            return pans ? PAN : ORBIT;
        }
    }
}
