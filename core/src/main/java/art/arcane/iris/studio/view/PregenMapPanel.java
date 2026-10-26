package art.arcane.iris.studio.view;

import art.arcane.iris.localization.DesktopUiMessages;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.studio.map.StudioTheme;
import art.arcane.volmlib.util.localization.MessageArgument;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

final class PregenMapPanel extends JPanel {
    private static final double MIN_ZOOM = 0.25D;
    private static final double MAX_ZOOM = 128D;
    private static final double ZOOM_STEP = 1.25D;

    private final PregenRenderSnapshot.Bounds bounds;
    private final PregenMapState state;
    private final PregenMapState.View view;
    private final BufferedImage image;
    private final String[] shortcuts;
    private volatile boolean disposed;
    private Runnable viewListener;
    private Point drag;
    private Point hover;
    private double centerX = 0.5D;
    private double centerZ = 0.5D;
    private double zoom = 1D;
    private int coveredLeftWidth;
    private boolean gridVisible;
    private boolean helpVisible;

    PregenMapPanel(PregenMapState state) {
        this.state = state;
        bounds = state.bounds();
        image = new BufferedImage(state.width(), state.height(), BufferedImage.TYPE_INT_RGB);
        image.setAccelerationPriority(0F);
        view = state.attach(((DataBufferInt) image.getRaster().getDataBuffer()).getData());
        shortcuts = IrisLanguage.plain(DesktopUiMessages.PREGEN_SHORTCUT_DETAILS).split("\n");
        setBackground(StudioTheme.EDITOR_BACKGROUND);
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        setFocusable(true);
        installNavigation();
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    void submit(int chunkX, int chunkZ, Color color) {
        if (!disposed) {
            state.submit(chunkX, chunkZ, color);
        }
    }

    boolean flush() {
        return !disposed && view.flush();
    }

    void disposeMap() {
        disposed = true;
        viewListener = null;
        ToolTipManager.sharedInstance().unregisterComponent(this);
        view.close();
    }

    void setViewListener(Runnable listener) {
        viewListener = listener;
    }

    void setCoveredLeftWidth(int width) {
        coveredLeftWidth = Math.max(0, width);
        changed();
    }

    void resetView() {
        centerX = 0.5D;
        centerZ = 0.5D;
        zoom = 1D;
        changed();
    }

    double zoom() {
        return zoom;
    }

    void zoomIn() {
        zoomAt(ZOOM_STEP, (getWidth() + coveredLeftWidth) / 2D, getHeight() / 2D);
    }

    void zoomOut() {
        zoomAt(1D / ZOOM_STEP, (getWidth() + coveredLeftWidth) / 2D, getHeight() / 2D);
    }

    void zoomAt(double factor, double anchorX, double anchorY) {
        Rectangle2D.Double before = mapArea();
        double mapX = (anchorX - before.x) / before.width;
        double mapZ = (anchorY - before.y) / before.height;
        zoom = Math.clamp(zoom * factor, MIN_ZOOM, MAX_ZOOM);
        Rectangle2D.Double after = mapArea();
        centerX += mapX - (anchorX - after.x) / after.width;
        centerZ += mapZ - (anchorY - after.y) / after.height;
        changed();
    }

    void panBy(double pixelsX, double pixelsY) {
        Rectangle2D.Double area = mapArea();
        centerX -= pixelsX / area.width;
        centerZ -= pixelsY / area.height;
        changed();
    }

    void toggleGrid() {
        gridVisible = !gridVisible;
        repaint();
    }

    boolean isGridVisible() {
        return gridVisible;
    }

    void toggleHelp() {
        helpVisible = !helpVisible;
        repaint();
    }

    void dismissHelp() {
        helpVisible = false;
        repaint();
    }

    String hoverCoordinates() {
        return hover == null ? null : coordinatesAt(hover.x, hover.y);
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        return coordinatesAt(event.getX(), event.getY());
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            Rectangle2D.Double area = mapArea();
            canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            AffineTransform transform = AffineTransform.getTranslateInstance(area.x, area.y);
            transform.scale(area.width / image.getWidth(), area.height / image.getHeight());
            canvas.drawImage(image, transform, null);
            if (gridVisible) {
                paintGrid(canvas, area);
            }
            canvas.setColor(StudioTheme.CONTROL_BORDER);
            canvas.draw(area);
            paintHover(canvas, area);
            if (helpVisible) {
                paintHelp(canvas);
            }
        } finally {
            canvas.dispose();
        }
    }

    static Rectangle fit(PregenRenderSnapshot.Bounds bounds, int width, int height) {
        int availableWidth = Math.max(1, width - 2);
        int availableHeight = Math.max(1, height - 2);
        double scale = Math.min((double) availableWidth / bounds.width(), (double) availableHeight / bounds.height());
        int mapWidth = Math.max(1, (int) Math.floor(bounds.width() * scale));
        int mapHeight = Math.max(1, (int) Math.floor(bounds.height() * scale));
        return new Rectangle((width - mapWidth) / 2, (height - mapHeight) / 2, mapWidth, mapHeight);
    }

    private Rectangle2D.Double mapArea() {
        Rectangle fit = fit(bounds, Math.max(1, getWidth() - coveredLeftWidth), getHeight());
        double width = fit.width * zoom;
        double height = fit.height * zoom;
        return new Rectangle2D.Double(coveredLeftWidth + fit.getCenterX() - centerX * width,
                fit.getCenterY() - centerZ * height, width, height);
    }

    private String coordinatesAt(int x, int y) {
        if (disposed || x < coveredLeftWidth) {
            return null;
        }
        Rectangle2D.Double area = mapArea();
        if (!area.contains(x, y)) {
            return null;
        }
        long chunkX = bounds.minX() + (long) Math.floor((x - area.x) * bounds.width() / area.width);
        long chunkZ = bounds.minZ() + (long) Math.floor((y - area.y) * bounds.height() / area.height);
        return IrisLanguage.plain(DesktopUiMessages.PREGEN_CHUNK_COORDINATES,
                MessageArgument.trusted("x", chunkX), MessageArgument.trusted("z", chunkZ));
    }

    private void installNavigation() {
        MouseAdapter navigation = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) {
                    requestFocusInWindow();
                    drag = event.getPoint();
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                drag = null;
                setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (drag != null) {
                    hover = event.getPoint();
                    panBy(event.getX() - drag.x, event.getY() - drag.y);
                    drag = hover;
                }
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                hover = event.getPoint();
                changed();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hover = null;
                changed();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent event) {
                zoomAt(Math.pow(ZOOM_STEP, -event.getPreciseWheelRotation()), event.getX(), event.getY());
            }
        };
        addMouseListener(navigation);
        addMouseMotionListener(navigation);
        addMouseWheelListener(navigation);
    }

    private void paintGrid(Graphics2D canvas, Rectangle2D.Double area) {
        if (!area.intersects(0, 0, getWidth(), getHeight())) {
            return;
        }
        double chunkWidth = area.width / bounds.width();
        double chunkHeight = area.height / bounds.height();
        long step = Math.min(chunkWidth, chunkHeight) >= 24D ? 1 : 32;
        while (step * Math.min(chunkWidth, chunkHeight) < 24D) {
            step *= 2;
        }
        canvas.setColor(StudioTheme.CONTROL_BORDER);
        long minX = Math.max(bounds.minX(), (long) Math.floor(bounds.minX() - area.x / chunkWidth));
        long maxX = Math.min((long) bounds.maxX() + 1, (long) Math.ceil(bounds.minX() + (getWidth() - area.x) / chunkWidth));
        long minZ = Math.max(bounds.minZ(), (long) Math.floor(bounds.minZ() - area.y / chunkHeight));
        long maxZ = Math.min((long) bounds.maxZ() + 1, (long) Math.ceil(bounds.minZ() + (getHeight() - area.y) / chunkHeight));
        int top = (int) Math.max(0, area.y);
        int bottom = (int) Math.min(getHeight(), area.getMaxY());
        int left = (int) Math.max(0, area.x);
        int right = (int) Math.min(getWidth(), area.getMaxX());
        for (long x = Math.ceilDiv(minX, step) * step; x <= maxX; x += step) {
            int screenX = (int) Math.round(area.x + (x - bounds.minX()) * chunkWidth);
            canvas.drawLine(screenX, top, screenX, bottom);
        }
        for (long z = Math.ceilDiv(minZ, step) * step; z <= maxZ; z += step) {
            int screenZ = (int) Math.round(area.y + (z - bounds.minZ()) * chunkHeight);
            canvas.drawLine(left, screenZ, right, screenZ);
        }
    }

    private void paintHover(Graphics2D canvas, Rectangle2D.Double area) {
        if (hover == null || hover.x < coveredLeftWidth || !area.contains(hover)) {
            return;
        }
        double chunkWidth = area.width / bounds.width();
        double chunkHeight = area.height / bounds.height();
        if (Math.min(chunkWidth, chunkHeight) < 4D) {
            return;
        }
        double x = area.x + Math.floor((hover.x - area.x) / chunkWidth) * chunkWidth;
        double y = area.y + Math.floor((hover.y - area.y) / chunkHeight) * chunkHeight;
        canvas.setColor(StudioTheme.ACCENT);
        canvas.draw(new Rectangle2D.Double(x, y, chunkWidth, chunkHeight));
    }

    private void paintHelp(Graphics2D canvas) {
        canvas.setFont(StudioTheme.font());
        FontMetrics metrics = canvas.getFontMetrics();
        int padding = StudioTheme.scale(14);
        int width = 0;
        for (String line : shortcuts) {
            width = Math.max(width, metrics.stringWidth(line));
        }
        width += padding * 2;
        int height = metrics.getHeight() * shortcuts.length + padding * 2;
        int x = Math.max(coveredLeftWidth + padding, getWidth() - width - padding);
        int arc = StudioTheme.scale(10);
        canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        canvas.setColor(StudioTheme.PANEL_BACKGROUND);
        canvas.fillRoundRect(x, padding, width, height, arc, arc);
        canvas.setColor(StudioTheme.CONTROL_BORDER);
        canvas.drawRoundRect(x, padding, width, height, arc, arc);
        canvas.setColor(StudioTheme.TEXT);
        int baseline = padding * 2 + metrics.getAscent();
        for (String line : shortcuts) {
            canvas.drawString(line, x + padding, baseline);
            baseline += metrics.getHeight();
        }
    }

    private void changed() {
        if (viewListener != null) {
            viewListener.run();
        }
        repaint();
    }
}
