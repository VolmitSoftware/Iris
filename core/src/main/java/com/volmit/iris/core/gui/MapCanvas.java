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

import com.volmit.iris.Iris;
import com.volmit.iris.core.gui.components.IrisRenderer;
import com.volmit.iris.core.gui.components.RenderType;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.engine.object.IrisRegion;
import com.volmit.iris.util.format.Form;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The tile map of the map window: draws the chosen view of the world and lets you drag, zoom and inspect it.
 */
final class MapCanvas extends JPanel {
    private static final long serialVersionUID = 2094606939770332040L;
    private static final Color PLAYER_COLOR = StudioTheme.SUCCESS;
    private static final Color PLAYER_HALO = new Color(0x5F, 0xB8, 0x65, 48);
    private static final Color MOB_COLOR = StudioTheme.ERROR;
    private static final Color GRID_MINOR = new Color(255, 255, 255, 18);
    private static final Color GRID_MAJOR = new Color(255, 255, 255, 38);
    private static final Color TILE_EMPTY_A = new Color(0x232427);
    private static final Color TILE_EMPTY_B = new Color(0x26272B);
    private static final int CARD_PADDING = 12;
    private static final int NOTIFICATION_MILLIS = 2_500;
    private static final double DEFAULT_BLOCKS_PER_PIXEL = 4D;
    private static final double WHEEL_ZOOM_EXPONENT = 0.08D;
    private static final double KEYBOARD_ZOOM_FACTOR = 1.189207115002721D;

    private final UUID openerId;
    private final Listener listener;
    private final VisionRenderController controller;
    private final StudioStatusBar statusBar = new StudioStatusBar();
    private final Runnable hotloadHook;
    private final Timer hoverTimer;
    private final Timer markerTimer;
    private final Timer notificationTimer;
    private final Map<String, Long> notifications = new LinkedHashMap<>();

    private Engine engine;
    private IrisRenderer renderer;
    private VisionOverlay overlay;
    private VisionRenderController.Frame renderFrame;
    private RenderType currentType = RenderType.BIOME;
    private String viewLabel = MapView.BIOMES.label();
    private List<GuiMarker> players = List.of();
    private List<GuiMarker> entities = List.of();
    private HoverInfo hoverInfo;
    private Point cursor;
    private Point dragOrigin;
    private double centerX;
    private double centerZ;
    private double heightMaximum;
    private double fluidHeight;
    private long contentRevision = 1L;
    private long previousPaintStartedNanos;
    private double blocksPerPixel = DEFAULT_BLOCKS_PER_PIXEL;
    private int paintCadenceFps;
    private boolean grid;
    private boolean entitiesVisible;
    private boolean follow;
    private boolean help;
    private boolean debug;
    private boolean detailedHover;
    private boolean active = true;
    private boolean closed;

    MapCanvas(Engine engine, UUID openerId, Listener listener) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.openerId = openerId;
        this.listener = Objects.requireNonNull(listener, "listener");
        this.renderer = new IrisRenderer(engine);
        this.overlay = new VisionOverlay(engine, openerId);
        this.controller = new VisionRenderController(this::onTilesChanged);
        this.hotloadHook = () -> EventQueue.invokeLater(this::refreshContent);
        captureHeightRange();

        setBackground(StudioTheme.EDITOR_BACKGROUND);
        setFocusable(true);
        installMouseHandlers();
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                requestRender();
            }
        });

        this.hoverTimer = singleShotTimer(90, this::requestHover);
        this.markerTimer = new Timer(650, event -> refreshMarkers());
        this.markerTimer.setCoalesce(true);
        this.notificationTimer = new Timer(200, event -> expireNotifications());
        this.notificationTimer.setCoalesce(true);
        GuiHost.registerHotloadHook(hotloadHook);
        markerTimer.start();
        notificationTimer.start();
    }

    StudioStatusBar statusBar() {
        return statusBar;
    }

    Engine engine() {
        return engine;
    }

    double centerX() {
        return centerX;
    }

    double centerZ() {
        return centerZ;
    }

    boolean isGridVisible() {
        return grid;
    }

    boolean areEntitiesVisible() {
        return entitiesVisible;
    }

    boolean isFollowing() {
        return follow;
    }

    void start() {
        refreshMarkers();
        requestRender();
    }

    void centerOn(double worldX, double worldZ) {
        centerX = worldX;
        centerZ = worldZ;
        requestRender();
    }

    void showView(MapView view) {
        if (!view.isTileMap()) {
            return;
        }
        viewLabel = view.label();
        if (view.renderType() == currentType) {
            return;
        }
        currentType = view.renderType();
        requestRender();
    }

    // Tiles keep loading in the background only for the view on screen; a hidden canvas stops asking for new ones.
    void setActive(boolean nextActive) {
        active = nextActive;
        if (active) {
            requestRender();
        }
    }

    void refreshContent() {
        if (closed) {
            return;
        }
        if (!ensureEngine()) {
            notifyUser("No active Iris engine");
            return;
        }
        renderer = new IrisRenderer(engine);
        overlay = new VisionOverlay(engine, openerId);
        contentRevision++;
        captureHeightRange();
        requestRender();
        notifyUser("Refreshing");
    }

    void toggleGrid() {
        grid = !grid;
        repaint();
        notifyUser(grid ? "Grid on" : "Grid off");
        listener.onTogglesChanged();
    }

    void toggleEntities() {
        entitiesVisible = !entitiesVisible;
        if (entitiesVisible) {
            refreshMarkers();
        } else {
            entities = List.of();
            repaint();
        }
        notifyUser(entitiesVisible ? "Entities on" : "Entities off");
        listener.onTogglesChanged();
    }

    void toggleFollow() {
        follow = !follow;
        if (follow && players.isEmpty()) {
            follow = false;
            notifyUser("No player in this world");
        } else if (follow) {
            GuiMarker player = players.get(0);
            centerOn(player.worldX(), player.worldZ());
            notifyUser("Following " + player.label());
        } else {
            notifyUser("Stopped following");
        }
        listener.onTogglesChanged();
    }

    void toggleHelp() {
        help = !help;
        repaint();
    }

    void toggleDebug() {
        debug = !debug;
        repaint();
    }

    void setDetailedHover(boolean detailed) {
        if (detailedHover != detailed) {
            detailedHover = detailed;
            repaint();
        }
    }

    void zoomIn() {
        changeZoom(1D / KEYBOARD_ZOOM_FACTOR, cursor);
    }

    void zoomOut() {
        changeZoom(KEYBOARD_ZOOM_FACTOR, cursor);
    }

    void resetZoom() {
        changeZoom(DEFAULT_BLOCKS_PER_PIXEL / blocksPerPixel, null);
        notifyUser("Zoom reset");
    }

    void close() {
        if (closed) {
            return;
        }
        closed = true;
        hoverTimer.stop();
        markerTimer.stop();
        notificationTimer.stop();
        GuiHost.unregisterHotloadHook(hotloadHook);
        controller.close();
    }

    private static Timer singleShotTimer(int delay, Runnable task) {
        Timer timer = new Timer(delay, event -> task.run());
        timer.setRepeats(false);
        timer.setCoalesce(true);
        return timer;
    }

    private void installMouseHandlers() {
        MouseAdapter handler = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                cursor = event.getPoint();
                setDetailedHover(event.isShiftDown());
                hoverTimer.restart();
                refreshStatus();
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                cursor = event.getPoint();
                if (dragOrigin == null) {
                    return;
                }
                centerX += (dragOrigin.x - event.getX()) * blocksPerPixel;
                centerZ += (dragOrigin.y - event.getY()) * blocksPerPixel;
                dragOrigin = event.getPoint();
                stopFollowing();
                hoverInfo = null;
                requestRender();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent event) {
                cursor = event.getPoint();
                changeZoom(Math.pow(2D, event.getPreciseWheelRotation() * WHEEL_ZOOM_EXPONENT), cursor);
                event.consume();
            }

            @Override
            public void mousePressed(MouseEvent event) {
                requestFocusInWindow();
                cursor = event.getPoint();
                if (event.isPopupTrigger()) {
                    showContextMenu(event);
                } else if (SwingUtilities.isLeftMouseButton(event)) {
                    dragOrigin = event.getPoint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                dragOrigin = null;
                if (event.isPopupTrigger()) {
                    showContextMenu(event);
                }
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                cursor = event.getPoint();
                if (!SwingUtilities.isLeftMouseButton(event)) {
                    return;
                }
                if (event.isControlDown()) {
                    teleportTo(event.getPoint());
                } else if (event.isAltDown()) {
                    openInEditor(event.getPoint());
                } else if (event.getClickCount() == 2) {
                    showPalettesAt(event.getPoint());
                }
            }

            @Override
            public void mouseEntered(MouseEvent event) {
                cursor = event.getPoint();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                cursor = null;
                hoverInfo = null;
                hoverTimer.stop();
                refreshStatus();
                repaint();
            }
        };
        addMouseListener(handler);
        addMouseMotionListener(handler);
        addMouseWheelListener(handler);
    }

    private void showContextMenu(MouseEvent event) {
        Point point = event.getPoint();
        int blockX = floorWorldCoordinate(screenToWorldX(point.x));
        int blockZ = floorWorldCoordinate(screenToWorldZ(point.y));
        JPopupMenu menu = new JPopupMenu();
        menu.add(menuItem("Show palettes here", () -> showPalettesAt(point)));
        menu.add(menuItem("Teleport here", () -> teleportTo(point)));
        menu.add(menuItem(currentType == RenderType.REGION ? "Open region file" : "Open biome file", () -> openInEditor(point)));
        menu.addSeparator();
        menu.add(menuItem("Copy coordinates (" + blockX + ", " + blockZ + ")", () -> copyCoordinates(blockX, blockZ)));
        menu.show(this, point.x, point.y);
    }

    private static JMenuItem menuItem(String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(event -> action.run());
        return item;
    }

    private void copyCoordinates(int blockX, int blockZ) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(blockX + " " + blockZ), null);
        notifyUser("Copied " + blockX + ", " + blockZ);
    }

    private void showPalettesAt(Point point) {
        listener.onShowPalettesAt(screenToWorldX(point.x), screenToWorldZ(point.y));
    }

    void findSurfaceBiome(double worldX, double worldZ, Consumer<String> onFound) {
        Engine probeEngine = engine;
        controller.submitProbe(renderFrame, () -> {
            IrisBiome biome = probeEngine.getSurfaceBiome(floorWorldCoordinate(worldX), floorWorldCoordinate(worldZ));
            return biome == null ? null : biome.getLoadKey();
        }, onFound);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        long started = System.nanoTime();
        if (previousPaintStartedNanos > 0L) {
            long interval = started - previousPaintStartedNanos;
            paintCadenceFps = interval > 2_000_000_000L
                    ? 0
                    : (int) Math.min(240L, Math.round(1_000_000_000D / Math.max(1L, interval)));
        }
        previousPaintStartedNanos = started;
        super.paintComponent(graphics);
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            renderTiles(canvas);
            if (grid) {
                renderGrid(canvas);
            }
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            renderMarkers(canvas);
            renderLegend(canvas);
            if (help) {
                renderHelp(canvas);
            } else if (debug) {
                renderDebug(canvas);
            }
            renderHover(canvas);
            renderNotifications(canvas);
        } catch (Throwable error) {
            Iris.debug("Vision paint failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
        } finally {
            canvas.dispose();
        }
    }

    private void renderTiles(Graphics2D canvas) {
        VisionRenderController.Frame frame = renderFrame;
        canvas.setColor(StudioTheme.EDITOR_BACKGROUND);
        canvas.fillRect(0, 0, getWidth(), getHeight());
        if (frame == null) {
            return;
        }
        canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        double frameTileSpan = VisionRenderController.TILE_PIXELS * frame.renderBlocksPerPixel();
        for (VisionRenderController.VisibleTile tile : frame.tiles()) {
            double worldX = tile.tileX() * frameTileSpan;
            double worldZ = tile.tileZ() * frameTileSpan;
            int screenX = (int) Math.round(worldToScreenX(worldX));
            int screenY = (int) Math.round(worldToScreenZ(worldZ));
            int tileWidth = Math.max(1, (int) Math.round(worldToScreenX(worldX + frameTileSpan)) - screenX);
            int tileHeight = Math.max(1, (int) Math.round(worldToScreenZ(worldZ + frameTileSpan)) - screenY);
            BufferedImage image = tile.image();
            if (image != null) {
                canvas.drawImage(image, screenX, screenY, tileWidth, tileHeight, null);
                continue;
            }
            canvas.setColor(((tile.tileX() ^ tile.tileZ()) & 1L) == 0L ? TILE_EMPTY_A : TILE_EMPTY_B);
            canvas.fillRect(screenX, screenY, tileWidth, tileHeight);
            drawFallback(canvas, controller.fallback(frame, tile), screenX, screenY, tileWidth, tileHeight);
        }
    }

    private static void drawFallback(Graphics2D canvas, VisionRenderController.Fallback fallback, int screenX, int screenY,
                                     int tileWidth, int tileHeight) {
        if (fallback == null) {
            return;
        }
        for (VisionRenderController.FallbackPiece piece : fallback.pieces()) {
            int left = screenX + (int) Math.round(piece.offsetX() * tileWidth);
            int top = screenY + (int) Math.round(piece.offsetZ() * tileHeight);
            int right = screenX + (int) Math.round((piece.offsetX() + piece.size()) * tileWidth);
            int bottom = screenY + (int) Math.round((piece.offsetZ() + piece.size()) * tileHeight);
            canvas.drawImage(piece.image(), left, top, right, bottom,
                    piece.sourceX(), piece.sourceZ(), piece.sourceX() + piece.sourceSize(), piece.sourceZ() + piece.sourceSize(), null);
        }
    }

    private void renderGrid(Graphics2D canvas) {
        double chunkPixels = 16D / blocksPerPixel;
        if (chunkPixels >= 8D) {
            canvas.setColor(GRID_MINOR);
            drawWorldGrid(canvas, 16D, chunkPixels);
        }
        canvas.setColor(GRID_MAJOR);
        double regionPixels = 512D / blocksPerPixel;
        if (regionPixels >= 8D) {
            drawWorldGrid(canvas, 512D, regionPixels);
        }
    }

    private void drawWorldGrid(Graphics2D canvas, double blockSpacing, double pixelSpacing) {
        double firstX = Math.floor(screenToWorldX(0D) / blockSpacing) * blockSpacing;
        double firstZ = Math.floor(screenToWorldZ(0D) / blockSpacing) * blockSpacing;
        for (double screen = worldToScreenX(firstX); screen <= getWidth(); screen += pixelSpacing) {
            int screenX = (int) Math.round(screen);
            canvas.drawLine(screenX, 0, screenX, getHeight());
        }
        for (double screen = worldToScreenZ(firstZ); screen <= getHeight(); screen += pixelSpacing) {
            int screenY = (int) Math.round(screen);
            canvas.drawLine(0, screenY, getWidth(), screenY);
        }
    }

    private void renderMarkers(Graphics2D canvas) {
        if (entitiesVisible) {
            canvas.setColor(MOB_COLOR);
            for (GuiMarker marker : entities) {
                int screenX = (int) Math.round(worldToScreenX(marker.worldX()));
                int screenY = (int) Math.round(worldToScreenZ(marker.worldZ()));
                canvas.fillOval(screenX - 3, screenY - 3, 6, 6);
            }
        }

        canvas.setFont(StudioTheme.smallFont());
        for (GuiMarker marker : players) {
            int screenX = (int) Math.round(worldToScreenX(marker.worldX()));
            int screenY = (int) Math.round(worldToScreenZ(marker.worldZ()));
            canvas.setColor(PLAYER_HALO);
            canvas.fillOval(screenX - 12, screenY - 12, 24, 24);
            canvas.setColor(PLAYER_COLOR);
            canvas.fillOval(screenX - 5, screenY - 5, 10, 10);
            canvas.setColor(StudioTheme.TEXT);
            int labelWidth = canvas.getFontMetrics().stringWidth(marker.label());
            canvas.drawString(marker.label(), screenX - labelWidth / 2, screenY - 15);
        }
        if (entitiesVisible && detailedHover) {
            renderNearestEntity(canvas);
        }
    }

    private void renderNearestEntity(Graphics2D canvas) {
        Point point = cursor;
        if (point == null || entities.isEmpty()) {
            return;
        }
        double worldX = screenToWorldX(point.x);
        double worldZ = screenToWorldZ(point.y);
        GuiMarker nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (GuiMarker marker : entities) {
            double deltaX = marker.worldX() - worldX;
            double deltaZ = marker.worldZ() - worldZ;
            double distance = deltaX * deltaX + deltaZ * deltaZ;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = marker;
            }
        }
        if (nearest == null) {
            return;
        }
        ArrayList<String> lines = new ArrayList<>();
        lines.add(nearest.label());
        lines.add("Position " + ((int) nearest.worldX()) + ", " + ((int) nearest.worldY()) + ", " + ((int) nearest.worldZ()));
        if (nearest.maxHealth() > 0D) {
            lines.add("Health " + Form.f(nearest.health(), 1) + " / " + Form.f(nearest.maxHealth(), 1));
        }
        drawCard(canvas, getWidth() - CARD_PADDING, CARD_PADDING, 1D, 0D, lines);
    }

    private void renderLegend(Graphics2D canvas) {
        if (currentType != RenderType.HEIGHT) {
            return;
        }
        int width = 244;
        int height = 54;
        int x = getWidth() - width - CARD_PADDING;
        int y = getHeight() - height - CARD_PADDING;
        StudioOverlay.drawCard(canvas, x, y, width, height);
        int gradientX = x + CARD_PADDING;
        int gradientY = y + CARD_PADDING;
        int gradientWidth = width - CARD_PADDING * 2;
        for (int pixel = 0; pixel < gradientWidth; pixel++) {
            double sampleHeight = heightMaximum * pixel / Math.max(1D, gradientWidth - 1D);
            canvas.setColor(new Color(IrisRenderer.heightColor(sampleHeight, heightMaximum, fluidHeight)));
            canvas.drawLine(gradientX + pixel, gradientY, gradientX + pixel, gradientY + 14);
        }
        canvas.setFont(StudioTheme.smallFont());
        canvas.setColor(StudioTheme.SECONDARY_TEXT);
        canvas.drawString("0", gradientX, gradientY + 32);
        String fluid = Integer.toString((int) Math.round(fluidHeight));
        int fluidX = gradientX + (int) Math.round(gradientWidth * fluidHeight / Math.max(1D, heightMaximum));
        canvas.drawString(fluid, Math.max(gradientX, fluidX - canvas.getFontMetrics().stringWidth(fluid) / 2), gradientY + 32);
        String maximum = Integer.toString((int) Math.round(heightMaximum));
        canvas.drawString(maximum, gradientX + gradientWidth - canvas.getFontMetrics().stringWidth(maximum), gradientY + 32);
    }

    private void renderHover(Graphics2D canvas) {
        HoverInfo info = hoverInfo;
        if (info == null) {
            return;
        }
        ArrayList<String> lines = new ArrayList<>();
        lines.add(info.biomeName());
        lines.add(info.regionName());
        lines.add("Block " + ((int) info.worldX()) + ", " + ((int) info.worldZ()));
        if (detailedHover) {
            lines.add("Chunk " + ((int) info.worldX() >> 4) + ", " + ((int) info.worldZ() >> 4));
            lines.add("Region file " + ((int) info.worldX() >> 9) + ", " + ((int) info.worldZ() >> 9));
            lines.add("Key " + info.biomeKey());
            lines.add("File " + info.biomeFile());
        } else {
            lines.add("Hold Shift for more, double-click for palettes");
        }
        int screenX = (int) Math.round(worldToScreenX(info.worldX())) + 16;
        int screenY = (int) Math.round(worldToScreenZ(info.worldZ()));
        drawCard(canvas, screenX, screenY, 0D, 0D, lines);
    }

    private void renderHelp(Graphics2D canvas) {
        List<String> keys = List.of("Drag", "Wheel / + -", "\\", "Double-click", "Right-click", "Ctrl+Click", "Alt+Click",
                "Shift", "R", "F", "G", "E", "M", "1 to 0, P", "/");
        List<String> descriptions = List.of(
                "Move the map",
                "Zoom in and out",
                "Reset zoom",
                "Show the palettes of that spot",
                "More actions for that spot",
                "Teleport there",
                "Open the biome file",
                "More details while hovering",
                "Redraw the map",
                "Follow your player",
                "Chunk and region grid",
                "Show entities",
                "Next view",
                "Pick a view",
                "Hide this help"
        );
        canvas.setFont(StudioTheme.boldFont());
        int keyWidth = 0;
        for (String key : keys) {
            keyWidth = Math.max(keyWidth, canvas.getFontMetrics().stringWidth(key));
        }
        canvas.setFont(StudioTheme.font());
        int descriptionWidth = 0;
        for (String description : descriptions) {
            descriptionWidth = Math.max(descriptionWidth, canvas.getFontMetrics().stringWidth(description));
        }
        int lineHeight = 22;
        int width = keyWidth + descriptionWidth + CARD_PADDING * 3 + 8;
        int height = keys.size() * lineHeight + CARD_PADDING * 2;
        StudioOverlay.drawCard(canvas, CARD_PADDING, CARD_PADDING, width, height);
        for (int index = 0; index < keys.size(); index++) {
            int y = CARD_PADDING * 2 + 10 + index * lineHeight;
            canvas.setFont(StudioTheme.boldFont());
            canvas.setColor(StudioTheme.LINK);
            canvas.drawString(keys.get(index), CARD_PADDING * 2, y);
            canvas.setFont(StudioTheme.font());
            canvas.setColor(StudioTheme.SECONDARY_TEXT);
            canvas.drawString(descriptions.get(index), CARD_PADDING * 2 + keyWidth + 18, y);
        }
    }

    private void renderDebug(Graphics2D canvas) {
        VisionRenderController.Progress progress = controller.progress(renderFrame);
        VisionRenderController.Frame frame = renderFrame;
        ArrayList<String> lines = new ArrayList<>();
        lines.add("Tiles " + progress.ready() + " / " + progress.total() + " drawn");
        lines.add("Render threads busy " + progress.activeWorkers());
        lines.add("Tile scale " + (frame == null ? "-" : Form.f(frame.renderBlocksPerPixel(), 0)) + " blocks/px");
        lines.add(paintCadenceFps + " paints per second");
        drawCard(canvas, CARD_PADDING, getHeight() - CARD_PADDING, 0D, 1D, lines);
    }

    private void renderNotifications(Graphics2D canvas) {
        if (notifications.isEmpty()) {
            return;
        }
        String text = String.join("  ·  ", notifications.keySet());
        canvas.setFont(StudioTheme.font());
        int textWidth = canvas.getFontMetrics().stringWidth(text);
        int width = textWidth + CARD_PADDING * 2;
        int height = 34;
        int x = (getWidth() - width) / 2;
        int y = getHeight() - height - 16;
        StudioOverlay.drawCard(canvas, x, y, width, height);
        canvas.setColor(StudioTheme.TEXT);
        canvas.drawString(text, x + CARD_PADDING, y + 22);
    }

    private void drawCard(Graphics2D canvas, int anchorX, int anchorY, double pushX, double pushY, List<String> lines) {
        canvas.setFont(StudioTheme.font());
        int lineHeight = canvas.getFontMetrics().getHeight();
        int width = 0;
        for (int index = 0; index < lines.size(); index++) {
            canvas.setFont(index == 0 ? StudioTheme.boldFont() : StudioTheme.font());
            width = Math.max(width, canvas.getFontMetrics().stringWidth(lines.get(index)));
        }
        width += CARD_PADDING * 2;
        int height = lines.size() * lineHeight + CARD_PADDING * 2 - 2;
        int x = (int) Math.round(anchorX - width * pushX);
        int y = (int) Math.round(anchorY - height * pushY);
        x = Math.max(CARD_PADDING, Math.min(getWidth() - width - CARD_PADDING, x));
        y = Math.max(CARD_PADDING, Math.min(getHeight() - height - CARD_PADDING, y));
        StudioOverlay.drawCard(canvas, x, y, width, height);
        for (int index = 0; index < lines.size(); index++) {
            boolean title = index == 0;
            canvas.setFont(title ? StudioTheme.boldFont() : StudioTheme.font());
            canvas.setColor(title ? StudioTheme.TEXT : StudioTheme.SECONDARY_TEXT);
            canvas.drawString(lines.get(index), x + CARD_PADDING, y + CARD_PADDING + lineHeight - 3 + index * lineHeight);
        }
    }

    private void onTilesChanged() {
        repaint();
        refreshStatus();
    }

    private void requestRender() {
        if (closed || !active || getWidth() < 1 || getHeight() < 1 || !ensureEngine()) {
            repaint();
            return;
        }
        hoverInfo = null;
        VisionRenderController.RenderSpec spec = new VisionRenderController.RenderSpec(
                renderer,
                currentType,
                contentRevision,
                centerX,
                centerZ,
                blocksPerPixel,
                getWidth(),
                getHeight()
        );
        renderFrame = controller.request(spec);
        repaint();
        refreshStatus();
    }

    private void refreshStatus() {
        int viewWidthBlocks = (int) (blocksPerPixel * getWidth());
        int viewHeightBlocks = (int) (blocksPerPixel * getHeight());
        statusBar.showSummary(viewLabel + "   " + formatScale(blocksPerPixel) + " blocks/px   "
                + Form.f(viewWidthBlocks) + " × " + Form.f(viewHeightBlocks) + " blocks");
        VisionRenderController.Progress progress = controller.progress(renderFrame);
        boolean drawing = progress.completion() < 1D;
        statusBar.showActivity(drawing ? "Drawing " + progress.ready() + " of " + progress.total() + " tiles" : "");
        statusBar.showProgress(progress.completion());
        Point point = cursor;
        double worldX = point == null ? centerX : screenToWorldX(point.x);
        double worldZ = point == null ? centerZ : screenToWorldZ(point.y);
        statusBar.showPosition("X " + Form.f(floorWorldCoordinate(worldX)) + "   Z " + Form.f(floorWorldCoordinate(worldZ)));
    }

    private static String formatScale(double blocksPerPixel) {
        if (blocksPerPixel >= 1D) {
            return Form.f(blocksPerPixel, 1);
        }
        return "1/" + String.format(Locale.ROOT, "%.0f", 1D / blocksPerPixel);
    }

    private boolean ensureEngine() {
        Engine activeEngine = engine;
        if (activeEngine != null && !activeEngine.isClosed() && activeEngine.getComplex() != null) {
            return true;
        }
        try {
            Engine reacquired = GuiHost.findActiveEngine();
            if (reacquired == null || reacquired.isClosed() || reacquired.getComplex() == null) {
                return false;
            }
            engine = reacquired;
            renderer = new IrisRenderer(reacquired);
            overlay = new VisionOverlay(reacquired, openerId);
            contentRevision++;
            captureHeightRange();
            return true;
        } catch (Throwable error) {
            Iris.debug("Vision engine reacquisition failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
            return false;
        }
    }

    private void captureHeightRange() {
        heightMaximum = Math.max(1D, engine.getHeight());
        IrisDimension dimension = engine.getDimension();
        fluidHeight = dimension == null
                ? 0D
                : Math.max(0D, Math.min(heightMaximum, dimension.getFluidHeight()));
    }

    private void refreshMarkers() {
        if (closed || overlay == null) {
            return;
        }
        try {
            List<GuiMarker> nextPlayers = overlay.lastKnownPlayers();
            players = nextPlayers == null ? List.of() : List.copyOf(nextPlayers);
            if (entitiesVisible) {
                overlay.requestEntities(next -> EventQueue.invokeLater(() -> {
                    if (!closed && entitiesVisible) {
                        entities = next == null ? List.of() : List.copyOf(next);
                        repaint();
                    }
                }));
            } else if (!entities.isEmpty()) {
                entities = List.of();
            }
            if (follow && !players.isEmpty()) {
                GuiMarker player = players.get(0);
                if (Math.abs(centerX - player.worldX()) > 0.5D || Math.abs(centerZ - player.worldZ()) > 0.5D) {
                    centerOn(player.worldX(), player.worldZ());
                }
            }
            if (active) {
                repaint();
            }
        } catch (Throwable error) {
            Iris.debug("Vision marker refresh failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private void requestHover() {
        Point point = cursor;
        VisionRenderController.Frame frame = renderFrame;
        Engine probeEngine = engine;
        if (closed || !active || point == null || frame == null || probeEngine == null || probeEngine.getComplex() == null) {
            return;
        }
        double worldX = screenToWorldX(point.x);
        double worldZ = screenToWorldZ(point.y);
        controller.submitProbe(
                frame,
                () -> sampleHover(probeEngine, worldX, worldZ),
                info -> {
                    hoverInfo = info;
                    repaint();
                }
        );
    }

    private static HoverInfo sampleHover(Engine engine, double worldX, double worldZ) {
        IrisBiome biome = engine.getComplex().getBaseBiomeStream().get(worldX, worldZ);
        IrisRegion region = engine.getComplex().getRegionStream().get(worldX, worldZ);
        return new HoverInfo(
                worldX,
                worldZ,
                biome.getName(),
                region.getName(),
                biome.getLoadKey(),
                String.valueOf(biome.getLoadFile())
        );
    }

    private void stopFollowing() {
        if (follow) {
            follow = false;
            listener.onTogglesChanged();
        }
    }

    private void notifyUser(String message) {
        notifications.put(message, System.currentTimeMillis() + NOTIFICATION_MILLIS);
        repaint();
    }

    private void expireNotifications() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        Iterator<Map.Entry<String, Long>> iterator = notifications.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() <= now) {
                iterator.remove();
                changed = true;
            }
        }
        if (changed) {
            repaint();
        }
    }

    private void changeZoom(double factor, Point anchor) {
        if (!Double.isFinite(factor) || factor <= 0D) {
            return;
        }
        Point effectiveAnchor = anchor == null ? new Point(getWidth() / 2, getHeight() / 2) : anchor;
        VisionViewport next = new VisionViewport(centerX, centerZ, blocksPerPixel).zoomAt(
                effectiveAnchor.x,
                effectiveAnchor.y,
                getWidth(),
                getHeight(),
                factor,
                VisionRenderController.MINIMUM_VIEW_BLOCKS_PER_PIXEL,
                VisionRenderController.MAXIMUM_BLOCKS_PER_PIXEL
        );
        if (next.blocksPerPixel() == blocksPerPixel) {
            return;
        }
        centerX = next.centerX();
        centerZ = next.centerZ();
        blocksPerPixel = next.blocksPerPixel();
        stopFollowing();
        hoverInfo = null;
        requestRender();
    }

    private double screenToWorldX(double screenX) {
        return centerX + (screenX - getWidth() * 0.5D) * blocksPerPixel;
    }

    private double screenToWorldZ(double screenY) {
        return centerZ + (screenY - getHeight() * 0.5D) * blocksPerPixel;
    }

    private double worldToScreenX(double worldX) {
        return getWidth() * 0.5D + (worldX - centerX) / blocksPerPixel;
    }

    private double worldToScreenZ(double worldZ) {
        return getHeight() * 0.5D + (worldZ - centerZ) / blocksPerPixel;
    }

    private void openInEditor(Point point) {
        VisionOverlay activeOverlay = overlay;
        if (activeOverlay == null || point == null) {
            return;
        }
        double worldX = screenToWorldX(point.x);
        double worldZ = screenToWorldZ(point.y);
        RenderType type = currentType;
        controller.submitProbe(
                renderFrame,
                () -> activeOverlay.openInEditor(worldX, worldZ, type),
                opened -> opened.ifPresent(fileName -> notifyUser("Opened " + fileName))
        );
    }

    private void teleportTo(Point point) {
        if (overlay == null || point == null) {
            notifyUser("No player in this world");
            return;
        }
        int worldX = floorWorldCoordinate(screenToWorldX(point.x));
        int worldZ = floorWorldCoordinate(screenToWorldZ(point.y));
        overlay.teleport(worldX, worldZ);
        notifyUser("Teleporting to " + worldX + ", " + worldZ);
    }

    static int floorWorldCoordinate(double coordinate) {
        return (int) StrictMath.floor(coordinate);
    }

    interface Listener {
        void onTogglesChanged();

        void onShowPalettesAt(double worldX, double worldZ);
    }

    private record HoverInfo(
            double worldX,
            double worldZ,
            String biomeName,
            String regionName,
            String biomeKey,
            String biomeFile
    ) {
    }
}
