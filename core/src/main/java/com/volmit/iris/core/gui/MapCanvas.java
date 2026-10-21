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
import com.volmit.iris.core.gui.components.MapLabels;
import com.volmit.iris.core.gui.components.MapLayer;
import com.volmit.iris.core.gui.components.MapStyle;
import com.volmit.iris.core.gui.components.PickDisplay;
import com.volmit.iris.core.gui.components.RenderType;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisBiome;
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
    private static final Color TILE_EMPTY_A = new ThemeColor(0xF0F1F3, 0x232427);
    private static final Color TILE_EMPTY_B = new ThemeColor(0xEBECEF, 0x26272B);
    private static final int CARD_PADDING = 12;
    private static final int NOTIFICATION_MILLIS = 2_500;
    private static final double DEFAULT_BLOCKS_PER_PIXEL = 4D;
    private static final double WHEEL_ZOOM_EXPONENT = 0.08D;
    private static final double KEYBOARD_ZOOM_FACTOR = 1.189207115002721D;
    private static final int LEGEND_REFRESH_MILLIS = 200;

    private final UUID worldId;
    private final UUID openerId;
    private final Listener listener;
    private final VisionRenderController controller;
    private final StudioStatusBar statusBar = new StudioStatusBar();
    private final Runnable hotloadHook;
    private final Timer hoverTimer;
    private final Timer markerTimer;
    private final Timer notificationTimer;
    private final Timer legendTimer;
    private final Map<String, Long> notifications = new LinkedHashMap<>();

    private Engine engine;
    private IrisRenderer renderer;
    private VisionOverlay overlay;
    private VisionRenderController.Frame renderFrame;
    private BufferedImage tileLayer;
    private boolean tileLayerStale = true;
    private MapStyle style = MapStyle.PLAIN;
    private RenderType currentType = RenderType.BIOME;
    private String previewRegionKey;
    private PickDisplay pickDisplay = PickDisplay.PICKS;
    private String viewLabel = MapView.BIOMES.label();
    private List<GuiMarker> players = List.of();
    private List<GuiMarker> entities = List.of();
    private QuickHover quickHover;
    private MapHoverProbe.Details hoverDetails;
    private Point cursor;
    private Point dragOrigin;
    private double centerX;
    private double centerZ;
    private long contentRevision = 1L;
    private long previousPaintStartedNanos;
    private double blocksPerPixel = DEFAULT_BLOCKS_PER_PIXEL;
    private int paintCadenceFps;
    private boolean grid;
    private boolean entitiesVisible;
    private boolean follow;
    private boolean help;
    private int coveredLeftWidth;
    private boolean debug;
    private boolean detailedHover;
    private boolean active = true;
    private boolean closed;

    MapCanvas(Engine engine, UUID worldId, UUID openerId, Listener listener) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.worldId = Objects.requireNonNull(worldId, "worldId");
        this.openerId = openerId;
        this.listener = Objects.requireNonNull(listener, "listener");
        this.renderer = new IrisRenderer(engine);
        this.overlay = new VisionOverlay(engine, openerId);
        this.controller = new VisionRenderController(this::onTilesChanged);
        this.hotloadHook = () -> EventQueue.invokeLater(this::refreshContent);

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
        this.legendTimer = singleShotTimer(LEGEND_REFRESH_MILLIS, this::publishLegend);
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

    MapStyle style() {
        return style;
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
        RenderType nextType = view == MapView.REGIONS && previewRegionKey != null ? RenderType.REGION_PREVIEW : view.renderType();
        if (nextType == currentType) {
            return;
        }
        currentType = nextType;
        if (currentType != RenderType.HEIGHT) {
            style = style.withHeightRange(null);
        }
        requestRender();
    }

    /**
     * Shows the given region's biomes everywhere in the region view, as if every spot belonged to it; null shows the
     * regions themselves again.
     */
    void previewRegion(String regionKey) {
        if (Objects.equals(regionKey, previewRegionKey)) {
            return;
        }
        previewRegionKey = regionKey;
        if (currentType.isRegionView()) {
            currentType = regionKey == null ? RenderType.REGION : RenderType.REGION_PREVIEW;
            requestRender();
        }
    }

    /**
     * Draws the region view's picks, the noise that picks them, or both.
     */
    void showPicksAs(PickDisplay display) {
        if (display == pickDisplay) {
            return;
        }
        pickDisplay = display;
        if (currentType.isRegionView()) {
            requestRender();
        }
    }

    MapCensus.ViewArea viewArea() {
        return new MapCensus.ViewArea(screenToWorldX(0D), screenToWorldZ(0D), screenToWorldX(getWidth()),
                screenToWorldZ(getHeight()));
    }

    private MapLayer currentLayer() {
        return switch (currentType) {
            case REGION -> MapLayer.regions(pickDisplay);
            case REGION_PREVIEW -> MapLayer.regionPreview(previewRegionKey, pickDisplay);
            default -> MapLayer.of(currentType);
        };
    }

    String legendTitle() {
        return currentType == RenderType.REGION ? "Regions in view" : "Biomes in view";
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
        requestRender();
        notifyUser("Refreshing");
        listener.onTogglesChanged();
        listener.onContentRefreshed();
    }

    void toggleShading() {
        applyStyle(style.withShading(!style.shading()));
        notifyUser(style.shading() ? "Terrain shading on" : "Terrain shading off");
        listener.onTogglesChanged();
    }

    void toggleRegionBorders() {
        applyStyle(style.withRegionBorders(!style.regionBorders()));
        notifyUser(style.regionBorders() ? "Region borders on" : "Region borders off");
        listener.onTogglesChanged();
    }

    /**
     * Highlights the biome or region, or clears the highlight when it is the one already highlighted.
     */
    void toggleHighlight(MapLabels.Label label) {
        if (label == null) {
            return;
        }
        MapStyle.Highlight current = style.highlight();
        if (current != null && current.kind() == label.kind() && current.key().equals(label.key())) {
            clearHighlight();
            return;
        }
        applyStyle(style.withHighlight(new MapStyle.Highlight(label.kind(), label.key(), label.name())));
        notifyUser("Highlighting " + label.name() + " · Esc clears");
    }

    void highlightUnderCursor() {
        VisionRenderController.TilePixel pixel = cursor == null ? null : tilePixelAt(cursor);
        if (pixel == null) {
            notifyUser("Point at the map to highlight what is there");
            return;
        }
        toggleHighlight(pixel.subject());
    }

    /**
     * Esc steps back one layer of emphasis: the highlight first, then the help.
     */
    void dismissEmphasis() {
        if (style.highlight() != null) {
            clearHighlight();
        } else if (help) {
            toggleHelp();
        }
    }

    private void clearHighlight() {
        applyStyle(style.withHighlight(null));
        notifyUser("Highlight cleared");
    }

    private void applyStyle(MapStyle next) {
        boolean heightsWereNeeded = needsHeights();
        style = next;
        if (needsHeights() != heightsWereNeeded) {
            requestRender();
        } else {
            tileLayerStale = true;
            repaint();
        }
        scheduleLegendUpdate();
    }

    private boolean needsHeights() {
        return style.shading() || currentType == RenderType.HEIGHT;
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

    /**
     * Keeps the help, debug and notification cards clear of a panel floating over the map's left side.
     */
    void setCoveredLeftWidth(int width) {
        if (width == coveredLeftWidth) {
            return;
        }
        coveredLeftWidth = width;
        repaint();
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
        legendTimer.stop();
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
                showQuickHover();
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
                clearHover();
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
                clearHover();
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
        addHighlightItems(menu, tilePixelAt(point));
        menu.add(menuItem("Show palettes here", () -> showPalettesAt(point)));
        menu.add(menuItem("Teleport here", () -> teleportTo(point)));
        menu.add(menuItem(currentType == RenderType.REGION ? "Open region file" : "Open biome file", () -> openInEditor(point)));
        menu.addSeparator();
        menu.add(menuItem("Copy coordinates (" + blockX + ", " + blockZ + ")", () -> copyCoordinates(blockX, blockZ)));
        menu.show(this, point.x, point.y);
    }

    private void addHighlightItems(JPopupMenu menu, VisionRenderController.TilePixel pixel) {
        MapLabels.Label subject = pixel == null ? null : pixel.subject();
        MapLabels.Label region = pixel == null ? null : pixel.region();
        if (subject != null) {
            menu.add(menuItem(highlightText(subject), () -> toggleHighlight(subject)));
        }
        if (region != null && subject != null && subject.kind() != MapLabels.Kind.REGION) {
            menu.add(menuItem(highlightText(region), () -> toggleHighlight(region)));
        }
        if (style.highlight() != null) {
            menu.add(menuItem("Clear highlight", this::clearHighlight));
        }
        if (menu.getComponentCount() > 0) {
            menu.addSeparator();
        }
    }

    private String highlightText(MapLabels.Label label) {
        MapStyle.Highlight current = style.highlight();
        boolean highlighted = current != null && current.kind() == label.kind() && current.key().equals(label.key());
        String kind = label.kind() == MapLabels.Kind.REGION ? "region " : "";
        return (highlighted ? "Stop highlighting " : "Highlight ") + kind + label.name();
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

    // The display server gets one finished picture per paint: handing it every tile separately made the map lag.
    private void renderTiles(Graphics2D canvas) {
        canvas.drawImage(currentTileLayer(), 0, 0, null);
    }

    // The theme changed: the cached layer holds the old theme's background.
    @Override
    public void updateUI() {
        super.updateUI();
        tileLayerStale = true;
    }

    private BufferedImage currentTileLayer() {
        int width = Math.max(1, getWidth());
        int height = Math.max(1, getHeight());
        if (tileLayer == null || tileLayer.getWidth() != width || tileLayer.getHeight() != height) {
            tileLayer = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            tileLayer.setAccelerationPriority(0F);
            tileLayerStale = true;
        }
        if (tileLayerStale) {
            Graphics2D layer = tileLayer.createGraphics();
            try {
                composeTiles(layer);
            } finally {
                layer.dispose();
            }
            tileLayerStale = false;
        }
        return tileLayer;
    }

    private void composeTiles(Graphics2D canvas) {
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
            if (tile.tile() != null) {
                canvas.drawImage(tile.tile().image(style), screenX, screenY, tileWidth, tileHeight, null);
                continue;
            }
            canvas.setColor(((tile.tileX() ^ tile.tileZ()) & 1L) == 0L ? TILE_EMPTY_A : TILE_EMPTY_B);
            canvas.fillRect(screenX, screenY, tileWidth, tileHeight);
            drawFallback(canvas, controller.fallback(frame, tile), screenX, screenY, tileWidth, tileHeight);
        }
    }

    private void drawFallback(Graphics2D canvas, VisionRenderController.Fallback fallback, int screenX, int screenY,
                                     int tileWidth, int tileHeight) {
        if (fallback == null) {
            return;
        }
        for (VisionRenderController.FallbackPiece piece : fallback.pieces()) {
            int left = screenX + (int) Math.round(piece.offsetX() * tileWidth);
            int top = screenY + (int) Math.round(piece.offsetZ() * tileHeight);
            int right = screenX + (int) Math.round((piece.offsetX() + piece.size()) * tileWidth);
            int bottom = screenY + (int) Math.round((piece.offsetZ() + piece.size()) * tileHeight);
            canvas.drawImage(piece.tile().image(style), left, top, right, bottom,
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
                fillDot(canvas, screenX, screenY, 3);
            }
        }

        canvas.setFont(StudioTheme.smallFont());
        for (GuiMarker marker : players) {
            int screenX = (int) Math.round(worldToScreenX(marker.worldX()));
            int screenY = (int) Math.round(worldToScreenZ(marker.worldZ()));
            canvas.setColor(PLAYER_HALO);
            fillDot(canvas, screenX, screenY, 12);
            canvas.setColor(PLAYER_COLOR);
            fillDot(canvas, screenX, screenY, 5);
            canvas.setColor(StudioTheme.TEXT);
            int labelWidth = canvas.getFontMetrics().stringWidth(marker.label());
            canvas.drawString(marker.label(), screenX - labelWidth / 2, screenY - StudioTheme.scale(15));
        }
        if (entitiesVisible && detailedHover) {
            renderNearestEntity(canvas);
        }
    }

    private static void fillDot(Graphics2D canvas, int centerX, int centerY, int logicalRadius) {
        int radius = StudioTheme.scale(logicalRadius);
        canvas.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
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
        int padding = StudioTheme.scale(CARD_PADDING);
        drawCard(canvas, getWidth() - padding, padding, 1D, 0D, lines);
    }

    private void renderLegend(Graphics2D canvas) {
        if (currentType != RenderType.HEIGHT) {
            return;
        }
        MapStyle.HeightRange range = currentHeightRange();
        canvas.setFont(StudioTheme.smallFont());
        int padding = StudioTheme.scale(CARD_PADDING);
        int gradientHeight = StudioTheme.scale(14);
        int labelGap = StudioTheme.scale(4);
        int width = StudioTheme.scale(244);
        int height = padding * 2 + gradientHeight + labelGap + canvas.getFontMetrics().getHeight();
        int x = getWidth() - width - padding;
        int y = getHeight() - height - padding;
        StudioOverlay.drawCard(canvas, x, y, width, height);
        int gradientX = x + padding;
        int gradientY = y + padding;
        int gradientWidth = width - padding * 2;
        double span = Math.max(1D, range.highest() - range.lowest());
        for (int pixel = 0; pixel < gradientWidth; pixel++) {
            double sampleHeight = range.lowest() + span * pixel / Math.max(1D, gradientWidth - 1D);
            canvas.setColor(new Color(IrisRenderer.heightColor(sampleHeight, range)));
            canvas.drawLine(gradientX + pixel, gradientY, gradientX + pixel, gradientY + gradientHeight);
        }
        canvas.setColor(StudioTheme.SECONDARY_TEXT);
        int labelBaseline = gradientY + gradientHeight + labelGap + canvas.getFontMetrics().getAscent();
        String minimum = Integer.toString((int) Math.round(range.lowest()));
        String sea = "sea " + Math.round(range.seaLevel());
        String maximum = Integer.toString((int) Math.round(range.highest()));
        int minimumRight = gradientX + canvas.getFontMetrics().stringWidth(minimum);
        int maximumX = gradientX + gradientWidth - canvas.getFontMetrics().stringWidth(maximum);
        int seaWidth = canvas.getFontMetrics().stringWidth(sea);
        int seaX = gradientX + (int) Math.round(gradientWidth * (range.seaLevel() - range.lowest()) / span) - seaWidth / 2;
        canvas.drawString(minimum, gradientX, labelBaseline);
        if (seaX > minimumRight + labelGap && seaX + seaWidth < maximumX - labelGap) {
            canvas.drawString(sea, seaX, labelBaseline);
        }
        canvas.drawString(maximum, maximumX, labelBaseline);
    }

    private void renderHover(Graphics2D canvas) {
        QuickHover quick = quickHover;
        if (quick == null) {
            return;
        }
        MapHoverProbe.Details details = hoverDetails;
        List<String> lines = MapHoverCard.lines(quick.subject(), quick.region(), details, currentType, detailedHover,
                quick.worldX(), quick.worldZ());
        int screenX = (int) Math.round(worldToScreenX(quick.worldX())) + StudioTheme.scale(16);
        int screenY = (int) Math.round(worldToScreenZ(quick.worldZ()));
        drawCard(canvas, screenX, screenY, 0D, 0D, lines);
    }

    private void renderHelp(Graphics2D canvas) {
        List<String> keys = List.of("Drag", "Wheel / + -", "\\", "Double-click", "Right-click", "Ctrl+Click", "Alt+Click",
                "Shift", "H", "Esc", "S", "B", "R", "F", "G", "E", "M", "1 to 0, P", "/");
        List<String> descriptions = List.of(
                "Move the map",
                "Zoom in and out",
                "Reset zoom",
                "Show the palettes of that spot",
                "More actions for that spot",
                "Teleport there",
                "Open the biome file",
                "More details while hovering",
                "Highlight what is under the cursor",
                "Clear the highlight",
                "Terrain shading",
                "Region borders",
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
        int padding = StudioTheme.scale(CARD_PADDING);
        int columnGap = StudioTheme.scale(18);
        int lineHeight = canvas.getFontMetrics().getHeight() + StudioTheme.scale(4);
        int width = keyWidth + columnGap + descriptionWidth + padding * 2;
        int height = keys.size() * lineHeight + padding * 2;
        int left = coveredLeftWidth + padding;
        StudioOverlay.drawCard(canvas, left, padding, width, height);
        for (int index = 0; index < keys.size(); index++) {
            int y = padding * 2 + canvas.getFontMetrics().getAscent() + index * lineHeight;
            canvas.setFont(StudioTheme.boldFont());
            canvas.setColor(StudioTheme.LINK);
            canvas.drawString(keys.get(index), left + padding, y);
            canvas.setFont(StudioTheme.font());
            canvas.setColor(StudioTheme.SECONDARY_TEXT);
            canvas.drawString(descriptions.get(index), left + padding + keyWidth + columnGap, y);
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
        int padding = StudioTheme.scale(CARD_PADDING);
        drawCard(canvas, coveredLeftWidth + padding, getHeight() - padding, 0D, 1D, lines);
    }

    private void renderNotifications(Graphics2D canvas) {
        if (notifications.isEmpty()) {
            return;
        }
        String text = String.join("  ·  ", notifications.keySet());
        canvas.setFont(StudioTheme.font());
        int textWidth = canvas.getFontMetrics().stringWidth(text);
        int padding = StudioTheme.scale(CARD_PADDING);
        int width = textWidth + padding * 2;
        int height = canvas.getFontMetrics().getHeight() + padding;
        int x = coveredLeftWidth + (getWidth() - coveredLeftWidth - width) / 2;
        int y = getHeight() - height - StudioTheme.scale(16);
        StudioOverlay.drawCard(canvas, x, y, width, height);
        canvas.setColor(StudioTheme.TEXT);
        canvas.drawString(text, x + padding, y + padding / 2 + canvas.getFontMetrics().getAscent());
    }

    private void drawCard(Graphics2D canvas, int anchorX, int anchorY, double pushX, double pushY, List<String> lines) {
        canvas.setFont(StudioTheme.font());
        int lineHeight = canvas.getFontMetrics().getHeight();
        int width = 0;
        for (int index = 0; index < lines.size(); index++) {
            canvas.setFont(index == 0 ? StudioTheme.boldFont() : StudioTheme.font());
            width = Math.max(width, canvas.getFontMetrics().stringWidth(lines.get(index)));
        }
        int padding = StudioTheme.scale(CARD_PADDING);
        width += padding * 2;
        int height = lines.size() * lineHeight + padding * 2;
        int x = (int) Math.round(anchorX - width * pushX);
        int y = (int) Math.round(anchorY - height * pushY);
        x = Math.max(padding, Math.min(getWidth() - width - padding, x));
        y = Math.max(padding, Math.min(getHeight() - height - padding, y));
        StudioOverlay.drawCard(canvas, x, y, width, height);
        for (int index = 0; index < lines.size(); index++) {
            boolean title = index == 0;
            canvas.setFont(title ? StudioTheme.boldFont() : StudioTheme.font());
            canvas.setColor(title ? StudioTheme.TEXT : StudioTheme.SECONDARY_TEXT);
            canvas.drawString(lines.get(index), x + padding, y + padding + canvas.getFontMetrics().getAscent() + index * lineHeight);
        }
    }

    private void onTilesChanged() {
        tileLayerStale = true;
        repaint();
        refreshStatus();
        scheduleLegendUpdate();
        if (quickHover != null && quickHover.subject() == null) {
            showQuickHover();
        }
    }

    // At most one count per interval however fast tiles arrive, so the legend stays live without costing frames.
    private void scheduleLegendUpdate() {
        if (!legendTimer.isRunning()) {
            legendTimer.start();
        }
    }

    private void publishLegend() {
        if (closed || !active) {
            return;
        }
        IrisRenderer legendRenderer = renderer;
        RenderType type = currentType;
        MapCensus.ViewArea area = viewArea();
        List<MapCensus.Share> shares = MapCensus.count(renderFrame, area, label -> legendRenderer.legendColor(label, type));
        listener.onLegendChanged(legendTitle(), shares, style.highlight());
        if (type == RenderType.HEIGHT) {
            fitHeightColorsToView(area);
        }
    }

    // The height colours stretch over the heights on screen, so plains and mountains don't share one shade of green.
    private void fitHeightColorsToView(MapCensus.ViewArea area) {
        MapStyle.HeightRange measured = MapCensus.heightRange(renderFrame, area, renderer.defaultHeightRange().seaLevel());
        if (measured != null && !measured.equals(style.heightRange())) {
            applyStyle(style.withHeightRange(measured));
        }
    }

    private MapStyle.HeightRange currentHeightRange() {
        return style.heightRange() == null ? renderer.defaultHeightRange() : style.heightRange();
    }

    private void requestRender() {
        tileLayerStale = true;
        if (closed || !active || getWidth() < 1 || getHeight() < 1 || !ensureEngine()) {
            repaint();
            return;
        }
        clearHover();
        VisionRenderController.RenderSpec spec = new VisionRenderController.RenderSpec(
                renderer,
                currentLayer(),
                contentRevision,
                centerX,
                centerZ,
                blocksPerPixel,
                getWidth(),
                getHeight(),
                needsHeights()
        );
        renderFrame = controller.request(spec);
        repaint();
        refreshStatus();
        scheduleLegendUpdate();
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
            Engine reacquired = GuiHost.findEngine(worldId);
            if (reacquired == null || reacquired.isClosed() || reacquired.getComplex() == null) {
                return false;
            }
            engine = reacquired;
            renderer = new IrisRenderer(reacquired);
            overlay = new VisionOverlay(reacquired, openerId);
            contentRevision++;
            return true;
        } catch (Throwable error) {
            Iris.debug("Vision engine reacquisition failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
            return false;
        }
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
                () -> MapHoverProbe.sample(probeEngine, worldX, worldZ, previewedRegion(), currentType.isRegionView()),
                details -> {
                    QuickHover quick = quickHover;
                    if (quick != null && quick.worldX() == details.worldX() && quick.worldZ() == details.worldZ()) {
                        hoverDetails = details;
                        repaint();
                    }
                }
        );
    }

    private String previewedRegion() {
        return currentType == RenderType.REGION_PREVIEW ? previewRegionKey : null;
    }

    // The drawn tiles already know the biome and region under the cursor, so the card follows the mouse without waiting.
    private void showQuickHover() {
        Point point = cursor;
        if (point == null || dragOrigin != null) {
            return;
        }
        double worldX = screenToWorldX(point.x);
        double worldZ = screenToWorldZ(point.y);
        VisionRenderController.TilePixel pixel = tilePixelAt(point);
        quickHover = new QuickHover(worldX, worldZ, pixel == null ? null : pixel.subject(), pixel == null ? null : pixel.region());
        repaint();
    }

    private void clearHover() {
        quickHover = null;
        hoverDetails = null;
    }

    private VisionRenderController.TilePixel tilePixelAt(Point point) {
        VisionRenderController.Frame frame = renderFrame;
        return frame == null ? null : frame.pixelAt(screenToWorldX(point.x), screenToWorldZ(point.y));
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
        clearHover();
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
        MapLayer layer = currentLayer();
        controller.submitProbe(
                renderFrame,
                () -> activeOverlay.openInEditor(worldX, worldZ, layer),
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

        void onContentRefreshed();

        void onShowPalettesAt(double worldX, double worldZ);

        void onLegendChanged(String title, List<MapCensus.Share> shares, MapStyle.Highlight highlight);
    }

    private record QuickHover(double worldX, double worldZ, MapLabels.Label subject, MapLabels.Label region) {
    }
}
