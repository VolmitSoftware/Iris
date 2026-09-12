/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.studio.view;

import art.arcane.iris.localization.DesktopUiMessages;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.studio.render.IrisRenderer;
import art.arcane.iris.studio.render.RenderType;
import art.arcane.iris.generation.hydrology.HydrologyCandidateKind;
import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageKey;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.Timer;
import javax.swing.event.MouseInputListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class VisionGUI extends JPanel implements MouseWheelListener, KeyListener, MouseMotionListener, MouseInputListener {
    private static final long serialVersionUID = 2094606939770332040L;

    private static final Color BACKGROUND = new Color(15, 17, 22);
    private static final Color TOOLBAR_BACKGROUND = new Color(23, 25, 31);
    private static final Color CONTROL_BACKGROUND = new Color(31, 34, 42);
    private static final Color CONTROL_SELECTED = new Color(50, 64, 91);
    private static final Color CARD_BACKGROUND = new Color(25, 28, 35, 232);
    private static final Color CARD_BORDER = new Color(69, 74, 88, 210);
    private static final Color TEXT_PRIMARY = new Color(229, 231, 237);
    private static final Color TEXT_SECONDARY = new Color(155, 161, 174);
    private static final Color ACCENT = new Color(96, 146, 244);
    private static final Color PLAYER_COLOR = new Color(81, 201, 128);
    private static final Color MOB_COLOR = new Color(227, 94, 98);
    private static final Color STATUS_BACKGROUND = new Color(21, 23, 29, 246);
    private static final Color GRID_MINOR = new Color(255, 255, 255, 18);
    private static final Color GRID_MAJOR = new Color(255, 255, 255, 38);
    private static final Color TILE_EMPTY_A = new Color(24, 27, 34);
    private static final Color TILE_EMPTY_B = new Color(27, 30, 38);
    private static final Font STATUS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final Font BODY_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private static final Font BODY_BOLD_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private static final Font TOOLBAR_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private static final Font KEY_FONT = new Font(Font.MONOSPACED, Font.BOLD, 12);
    private static final int STATUS_HEIGHT = 28;
    private static final int PROGRESS_HEIGHT = 3;
    private static final int CARD_RADIUS = 10;
    private static final int CARD_PADDING = 12;
    private static final double DEFAULT_BLOCKS_PER_PIXEL = 4D;
    private static final double WHEEL_ZOOM_EXPONENT = 0.08D;
    private static final double KEYBOARD_ZOOM_FACTOR = 1.189207115002721D;

    private final JFrame hostFrame;
    private final UUID openerId;
    private final VisionRenderController controller;
    private final Runnable hotloadHook;
    private final Timer resizeTimer;
    private final Timer dragRenderTimer;
    private final Timer zoomRenderTimer;
    private final Timer hoverTimer;
    private final Timer markerTimer;
    private final Timer notificationTimer;
    private final Map<String, Long> notifications;

    private Engine engine;
    private IrisRenderer renderer;
    private GuiOverlay overlay;
    private VisionRenderController.Frame renderFrame;
    private JComboBox<RenderType> modeSelector;
    private JToggleButton gridToggle;
    private JToggleButton entitiesToggle;
    private JToggleButton followToggle;
    private RenderType currentType;
    private List<GuiMarker> players;
    private List<GuiMarker> entities;
    private HoverInfo hoverInfo;
    private Point cursor;
    private double centerX;
    private double centerZ;
    private double dragX;
    private double dragY;
    private double heightMaximum;
    private double fluidHeight;
    private long contentRevision;
    private long previousPaintStartedNanos;
    private double blocksPerPixel;
    private int paintCadenceFps;
    private boolean grid;
    private boolean entitiesVisible;
    private boolean follow;
    private boolean help;
    private boolean debug;
    private boolean detailedHover;
    private boolean controlsUpdating;
    private boolean closed;

    private VisionGUI(JFrame hostFrame, Engine engine, UUID openerId) {
        this.hostFrame = Objects.requireNonNull(hostFrame, "hostFrame");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.openerId = openerId;
        this.renderer = new IrisRenderer(engine);
        this.overlay = GuiHost.get().overlayFor(engine, openerId);
        this.controller = new VisionRenderController(this::repaint);
        this.hotloadHook = () -> EventQueue.invokeLater(this::refreshContent);
        this.notifications = new LinkedHashMap<>();
        this.players = List.of();
        this.entities = List.of();
        this.entitiesVisible = false;
        this.currentType = RenderType.BIOME;
        this.blocksPerPixel = DEFAULT_BLOCKS_PER_PIXEL;
        this.contentRevision = 1L;
        captureHeightRange();

        setBackground(BACKGROUND);
        setFocusable(true);
        addMouseWheelListener(this);
        addMouseMotionListener(this);
        addMouseListener(this);
        hostFrame.addKeyListener(this);

        this.resizeTimer = singleShotTimer(90, this::requestRender);
        this.dragRenderTimer = singleShotTimer(35, this::requestRender);
        this.zoomRenderTimer = singleShotTimer(110, this::requestRender);
        this.hoverTimer = singleShotTimer(90, this::requestHover);
        this.markerTimer = new Timer(650, event -> refreshMarkers());
        this.markerTimer.setCoalesce(true);
        this.notificationTimer = new Timer(200, event -> expireNotifications());
        this.notificationTimer.setCoalesce(true);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                resizeTimer.restart();
                repaint();
            }
        });
        hostFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                close();
            }

            @Override
            public void windowClosed(WindowEvent event) {
                close();
            }
        });
        GuiHost.get().registerHotloadHook(hotloadHook);
        markerTimer.start();
        notificationTimer.start();
    }

    public static void launch(Engine engine, UUID openerId) {
        EventQueue.invokeLater(() -> createAndShowGUI(engine, openerId));
    }

    private static void createAndShowGUI(Engine engine, UUID openerId) {
        JFrame frame = new JFrame(IrisLanguage.plain(DesktopUiMessages.VISION_TITLE));
        GuiHost.prepareFrame(frame);
        VisionGUI vision = new VisionGUI(frame, engine, openerId);
        frame.getContentPane().setBackground(BACKGROUND);
        frame.setLayout(new BorderLayout());
        frame.add(buildToolbar(vision), BorderLayout.NORTH);
        frame.add(vision, BorderLayout.CENTER);
        frame.setSize(1440, 820);
        frame.setMinimumSize(new Dimension(720, 500));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        EventQueue.invokeLater(() -> {
            vision.requestFocusInWindow();
            vision.refreshMarkers();
            vision.requestRender();
        });
    }

    private static JPanel buildToolbar(VisionGUI vision) {
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBackground(TOOLBAR_BACKGROUND);
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, CARD_BORDER));

        JPanel leading = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 5));
        leading.setOpaque(false);
        JLabel viewLabel = new JLabel(IrisLanguage.plain(DesktopUiMessages.VISION_VIEW));
        viewLabel.setFont(BODY_BOLD_FONT);
        viewLabel.setForeground(TEXT_SECONDARY);
        leading.add(viewLabel);

        JComboBox<RenderType> selector = new JComboBox<>(RenderType.values());
        selector.setSelectedItem(vision.currentType);
        selector.setFont(TOOLBAR_FONT);
        selector.setForeground(TEXT_PRIMARY);
        selector.setBackground(CONTROL_BACKGROUND);
        selector.setFocusable(false);
        selector.setRenderer(new ModeCellRenderer());
        selector.addActionListener(event -> {
            if (!vision.controlsUpdating && selector.getSelectedItem() instanceof RenderType type) {
                vision.setRenderType(type);
            }
        });
        vision.modeSelector = selector;
        leading.add(selector);

        JButton refresh = new JButton(IrisLanguage.plain(DesktopUiMessages.VISION_HELP_REFRESH));
        styleButton(refresh);
        refresh.addActionListener(event -> vision.refreshContent());
        leading.add(refresh);
        toolbar.add(leading, BorderLayout.WEST);

        JPanel trailing = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        trailing.setOpaque(false);
        vision.gridToggle = createToggle(IrisLanguage.plain(DesktopUiMessages.VISION_GRID), vision.grid);
        vision.gridToggle.addActionListener(event -> vision.toggleGrid());
        trailing.add(vision.gridToggle);
        vision.entitiesToggle = createToggle(
                IrisLanguage.plain(DesktopUiMessages.VISION_ENTITIES),
                vision.entitiesVisible
        );
        vision.entitiesToggle.addActionListener(event -> vision.toggleEntities());
        trailing.add(vision.entitiesToggle);
        vision.followToggle = createToggle(IrisLanguage.plain(DesktopUiMessages.VISION_FOLLOW), vision.follow);
        vision.followToggle.addActionListener(event -> vision.toggleFollow());
        trailing.add(vision.followToggle);
        toolbar.add(trailing, BorderLayout.EAST);
        return toolbar;
    }

    private static void styleButton(JButton button) {
        button.setFont(TOOLBAR_FONT);
        button.setForeground(TEXT_PRIMARY);
        button.setBackground(CONTROL_BACKGROUND);
        button.setFocusable(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BORDER),
                BorderFactory.createEmptyBorder(4, 9, 4, 9)
        ));
    }

    private static JToggleButton createToggle(String text, boolean selected) {
        JToggleButton toggle = new JToggleButton(text, selected);
        toggle.setFont(TOOLBAR_FONT);
        toggle.setForeground(selected ? TEXT_PRIMARY : TEXT_SECONDARY);
        toggle.setBackground(selected ? CONTROL_SELECTED : CONTROL_BACKGROUND);
        toggle.setFocusable(false);
        toggle.setOpaque(true);
        toggle.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(selected ? ACCENT : CARD_BORDER),
                BorderFactory.createEmptyBorder(4, 9, 4, 9)
        ));
        toggle.addChangeListener(event -> updateToggleStyle(toggle));
        return toggle;
    }

    private static void updateToggleStyle(JToggleButton toggle) {
        toggle.setForeground(toggle.isSelected() ? TEXT_PRIMARY : TEXT_SECONDARY);
        toggle.setBackground(toggle.isSelected() ? CONTROL_SELECTED : CONTROL_BACKGROUND);
        toggle.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(toggle.isSelected() ? ACCENT : CARD_BORDER),
                BorderFactory.createEmptyBorder(4, 9, 4, 9)
        ));
    }

    private static Timer singleShotTimer(int delay, Runnable task) {
        Timer timer = new Timer(delay, event -> task.run());
        timer.setRepeats(false);
        timer.setCoalesce(true);
        return timer;
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
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            renderTiles(canvas);
            if (grid) {
                renderGrid(canvas);
            }
            renderMarkers(canvas);
            renderLegend(canvas);
            if (help) {
                renderHelp(canvas);
            } else if (debug) {
                renderDebug(canvas);
            }
            renderHover(canvas);
            renderNotifications(canvas);
            renderStatus(canvas);
        } catch (Throwable error) {
            IrisLogging.debug("Vision paint failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
        } finally {
            canvas.dispose();
        }
    }

    private void renderTiles(Graphics2D canvas) {
        VisionRenderController.Frame frame = renderFrame;
        if (frame == null) {
            canvas.setColor(BACKGROUND);
            canvas.fillRect(0, 0, getWidth(), getHeight());
            return;
        }

        canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        double currentBlocksPerPixel = blocksPerPixel();
        double frameTileSpan = VisionRenderController.TILE_PIXELS * frame.spec().blocksPerPixel();
        for (VisionRenderController.VisibleTile tile : frame.tiles()) {
            double worldX = tile.tileX() * frameTileSpan;
            double worldZ = tile.tileZ() * frameTileSpan;
            int screenX = (int) Math.round(getWidth() * 0.5D + (worldX - centerX) / currentBlocksPerPixel);
            int screenY = (int) Math.round(getHeight() * 0.5D + (worldZ - centerZ) / currentBlocksPerPixel);
            int screenEndX = (int) Math.round(
                    getWidth() * 0.5D + (worldX + frameTileSpan - centerX) / currentBlocksPerPixel);
            int screenEndY = (int) Math.round(
                    getHeight() * 0.5D + (worldZ + frameTileSpan - centerZ) / currentBlocksPerPixel);
            int tileWidth = Math.max(1, screenEndX - screenX);
            int tileHeight = Math.max(1, screenEndY - screenY);
            canvas.setColor(((tile.tileX() ^ tile.tileZ()) & 1L) == 0L ? TILE_EMPTY_A : TILE_EMPTY_B);
            canvas.fillRect(screenX, screenY, tileWidth, tileHeight);
            BufferedImage image = controller.image(frame, tile);
            if (image != null) {
                canvas.drawImage(image, screenX, screenY, tileWidth, tileHeight, null);
            }
        }
    }

    private void renderGrid(Graphics2D canvas) {
        double blocksPerPixel = blocksPerPixel();
        double chunkPixels = 16D / blocksPerPixel;
        if (chunkPixels >= 8D) {
            canvas.setColor(GRID_MINOR);
            drawWorldGrid(canvas, 16D, chunkPixels);
        }
        canvas.setColor(GRID_MAJOR);
        drawWorldGrid(canvas, VisionRenderController.TILE_PIXELS * blocksPerPixel, VisionRenderController.TILE_PIXELS);
    }

    private void drawWorldGrid(Graphics2D canvas, double blockSpacing, double pixelSpacing) {
        double leftWorld = screenToWorldX(0D);
        double topWorld = screenToWorldZ(0D);
        double firstX = Math.floor(leftWorld / blockSpacing) * blockSpacing;
        double firstZ = Math.floor(topWorld / blockSpacing) * blockSpacing;
        double firstScreenX = worldToScreenX(firstX);
        double firstScreenZ = worldToScreenZ(firstZ);
        for (double screen = firstScreenX; screen <= getWidth(); screen += pixelSpacing) {
            int screenX = (int) Math.round(screen);
            canvas.drawLine(screenX, 0, screenX, getHeight() - STATUS_HEIGHT);
        }
        for (double screen = firstScreenZ; screen <= getHeight(); screen += pixelSpacing) {
            int screenY = (int) Math.round(screen);
            canvas.drawLine(0, screenY, getWidth(), screenY);
        }
    }

    private void renderMarkers(Graphics2D canvas) {
        if (entitiesVisible) {
            for (GuiMarker marker : entities) {
                int screenX = (int) Math.round(worldToScreenX(marker.worldX()));
                int screenY = (int) Math.round(worldToScreenZ(marker.worldZ()));
                canvas.setColor(MOB_COLOR);
                canvas.fillRect(screenX - 2, screenY - 2, 5, 5);
            }
        }

        for (GuiMarker marker : players) {
            int screenX = (int) Math.round(worldToScreenX(marker.worldX()));
            int screenY = (int) Math.round(worldToScreenZ(marker.worldZ()));
            canvas.setColor(new Color(81, 201, 128, 48));
            canvas.fillOval(screenX - 12, screenY - 12, 24, 24);
            canvas.setColor(PLAYER_COLOR);
            canvas.fillOval(screenX - 5, screenY - 5, 10, 10);
            canvas.setFont(BODY_FONT);
            canvas.setColor(TEXT_PRIMARY);
            int labelWidth = canvas.getFontMetrics().stringWidth(marker.label());
            canvas.drawString(marker.label(), screenX - labelWidth / 2, screenY - 14);
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
        lines.add(IrisLanguage.plain(
                DesktopUiMessages.VISION_ENTITY_POSITION,
                MessageArgument.trusted("x", (int) nearest.worldX()),
                MessageArgument.trusted("y", (int) nearest.worldY()),
                MessageArgument.trusted("z", (int) nearest.worldZ())
        ));
        if (nearest.maxHealth() > 0D) {
            lines.add(IrisLanguage.plain(
                    DesktopUiMessages.VISION_ENTITY_HEALTH,
                    MessageArgument.trusted("health", Form.f(nearest.health(), 1)),
                    MessageArgument.trusted("maximum", Form.f(nearest.maxHealth(), 1))
            ));
        }
        drawCard(canvas, getWidth() - CARD_PADDING, CARD_PADDING, 1D, 0D, lines);
    }

    private void renderLegend(Graphics2D canvas) {
        if (currentType == RenderType.RIVER) {
            renderRiverLegend(canvas);
        } else if (currentType == RenderType.HEIGHT) {
            renderHeightLegend(canvas);
        }
    }

    private void renderRiverLegend(Graphics2D canvas) {
        HydrologyFeatureType[] types = HydrologyFeatureType.values();
        HydrologyCandidateKind[] candidateKinds = HydrologyCandidateKind.values();
        int lineHeight = 18;
        int width = 188;
        int height = (types.length + candidateKinds.length + 1) * lineHeight + CARD_PADDING * 2;
        int x = getWidth() - width - CARD_PADDING;
        int y = getHeight() - STATUS_HEIGHT - PROGRESS_HEIGHT - height - CARD_PADDING;
        drawCardBackground(canvas, x, y, width, height);
        canvas.setFont(BODY_FONT);
        int headwaterY = y + CARD_PADDING;
        canvas.setColor(new Color(IrisRenderer.headwaterColor()));
        canvas.fillRoundRect(x + CARD_PADDING, headwaterY + 2, 12, 12, 4, 4);
        canvas.setColor(new Color(IrisRenderer.headwaterDirectionColor()));
        canvas.drawLine(x + CARD_PADDING + 3, headwaterY + 8, x + CARD_PADDING + 9, headwaterY + 8);
        canvas.drawLine(x + CARD_PADDING + 9, headwaterY + 8, x + CARD_PADDING + 6, headwaterY + 5);
        canvas.drawLine(x + CARD_PADDING + 9, headwaterY + 8, x + CARD_PADDING + 6, headwaterY + 11);
        canvas.setColor(TEXT_SECONDARY);
        canvas.drawString("headwater / source flow", x + CARD_PADDING + 20, headwaterY + 13);
        for (int index = 0; index < types.length; index++) {
            HydrologyFeatureType type = types[index];
            int rowY = y + CARD_PADDING + (index + 1) * lineHeight;
            canvas.setColor(new Color(IrisRenderer.hydrologyFeatureColor(type)));
            canvas.fillRoundRect(x + CARD_PADDING, rowY + 2, 12, 12, 4, 4);
            canvas.setColor(TEXT_SECONDARY);
            String label = type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
            canvas.drawString(label, x + CARD_PADDING + 20, rowY + 13);
        }
        for (int index = 0; index < candidateKinds.length; index++) {
            HydrologyCandidateKind kind = candidateKinds[index];
            int rowY = y + CARD_PADDING + (types.length + index + 1) * lineHeight;
            canvas.setColor(new Color(IrisRenderer.hydrologyDiagnosticColor(kind)));
            canvas.fillRoundRect(x + CARD_PADDING, rowY + 2, 12, 12, 4, 4);
            canvas.setColor(TEXT_SECONDARY);
            String label = "projected " + kind.name().toLowerCase(Locale.ROOT).replace('_', ' ');
            canvas.drawString(label, x + CARD_PADDING + 20, rowY + 13);
        }
    }

    private void renderHeightLegend(Graphics2D canvas) {
        int width = 244;
        int height = 54;
        int x = getWidth() - width - CARD_PADDING;
        int y = getHeight() - STATUS_HEIGHT - PROGRESS_HEIGHT - height - CARD_PADDING;
        drawCardBackground(canvas, x, y, width, height);
        int gradientX = x + CARD_PADDING;
        int gradientY = y + CARD_PADDING;
        int gradientWidth = width - CARD_PADDING * 2;
        for (int pixel = 0; pixel < gradientWidth; pixel++) {
            double sampleHeight = heightMaximum * pixel / Math.max(1D, gradientWidth - 1D);
            canvas.setColor(new Color(IrisRenderer.heightColor(sampleHeight, heightMaximum, fluidHeight)));
            canvas.drawLine(gradientX + pixel, gradientY, gradientX + pixel, gradientY + 14);
        }
        canvas.setColor(CARD_BORDER);
        canvas.drawRect(gradientX, gradientY, gradientWidth, 14);
        canvas.setFont(STATUS_FONT);
        canvas.setColor(TEXT_SECONDARY);
        canvas.drawString("0", gradientX, gradientY + 32);
        String fluid = Integer.toString((int) Math.round(fluidHeight));
        int fluidX = gradientX + (int) Math.round(gradientWidth * fluidHeight / Math.max(1D, heightMaximum));
        canvas.drawString(fluid, Math.max(gradientX, fluidX - canvas.getFontMetrics().stringWidth(fluid) / 2), gradientY + 32);
        String maximum = Integer.toString((int) Math.round(heightMaximum));
        canvas.drawString(maximum, gradientX + gradientWidth - canvas.getFontMetrics().stringWidth(maximum), gradientY + 32);
    }

    private void renderStatus(Graphics2D canvas) {
        VisionRenderController.Frame frame = renderFrame;
        VisionRenderController.Progress progress = controller.progress(frame);
        int statusY = getHeight() - STATUS_HEIGHT;
        int progressY = statusY - PROGRESS_HEIGHT;
        canvas.setColor(CONTROL_BACKGROUND);
        canvas.fillRect(0, progressY, getWidth(), PROGRESS_HEIGHT);
        canvas.setColor(ACCENT);
        canvas.fillRect(0, progressY, (int) Math.round(getWidth() * progress.completion()), PROGRESS_HEIGHT);
        canvas.setColor(STATUS_BACKGROUND);
        canvas.fillRect(0, statusY, getWidth(), STATUS_HEIGHT);
        canvas.setColor(CARD_BORDER);
        canvas.drawLine(0, statusY, getWidth(), statusY);
        canvas.setFont(STATUS_FONT);
        canvas.setColor(TEXT_SECONDARY);

        String left = IrisLanguage.plain(
                DesktopUiMessages.VISION_STATUS_LEFT,
                MessageArgument.trusted("mode", modeName(currentType)),
                MessageArgument.trusted("bpp", Form.f(blocksPerPixel(), 1)),
                MessageArgument.trusted("width", Form.f((int) (blocksPerPixel() * getWidth()))),
                MessageArgument.trusted("height", Form.f((int) (blocksPerPixel() * Math.max(0, getHeight() - STATUS_HEIGHT))))
        );
        canvas.drawString(left, 9, statusY + 19);

        String right = IrisLanguage.plain(
                DesktopUiMessages.VISION_STATUS_RIGHT,
                MessageArgument.trusted("x", Form.f((int) centerX)),
                MessageArgument.trusted("z", Form.f((int) centerZ)),
                MessageArgument.trusted("fps", paintCadenceFps)
        );
        int rightWidth = canvas.getFontMetrics().stringWidth(right);
        canvas.drawString(right, getWidth() - rightWidth - 9, statusY + 19);

        String middle = progress.ready() + "/" + progress.total() + " exact  "
                + progress.active() + "+" + progress.queued();
        int middleWidth = canvas.getFontMetrics().stringWidth(middle);
        if (middleWidth + canvas.getFontMetrics().stringWidth(left) + rightWidth + 54 < getWidth()) {
            canvas.drawString(middle, (getWidth() - middleWidth) / 2, statusY + 19);
        }
    }

    private void renderHover(Graphics2D canvas) {
        HoverInfo info = hoverInfo;
        if (info == null) {
            return;
        }
        ArrayList<String> lines = new ArrayList<>();
        lines.add(info.biomeName());
        lines.add(info.regionName());
        lines.add(IrisLanguage.plain(
                DesktopUiMessages.VISION_BLOCK_POSITION,
                MessageArgument.trusted("x", (int) info.worldX()),
                MessageArgument.trusted("z", (int) info.worldZ())
        ));
        if (detailedHover) {
            lines.add(IrisLanguage.plain(
                    DesktopUiMessages.VISION_CHUNK_POSITION,
                    MessageArgument.trusted("x", (int) info.worldX() >> 4),
                    MessageArgument.trusted("z", (int) info.worldZ() >> 4)
            ));
            lines.add(IrisLanguage.plain(
                    DesktopUiMessages.VISION_REGION_POSITION,
                    MessageArgument.trusted("x", (int) info.worldX() >> 9),
                    MessageArgument.trusted("z", (int) info.worldZ() >> 9)
            ));
            lines.add(IrisLanguage.plain(
                    DesktopUiMessages.VISION_BIOME_KEY,
                    MessageArgument.untrusted("key", info.biomeKey())
            ));
            lines.add(IrisLanguage.plain(
                    DesktopUiMessages.VISION_BIOME_FILE,
                    MessageArgument.untrusted("file", info.biomeFile())
            ));
        }
        int screenX = (int) Math.round(worldToScreenX(info.worldX())) + 16;
        int screenY = (int) Math.round(worldToScreenZ(info.worldZ()));
        drawCard(canvas, screenX, screenY, 0D, 0D, lines);
    }

    private void renderHelp(Graphics2D canvas) {
        List<String> keys = List.of("/", "R", "F", "+/-", "\\", "M", "G", "Shift", "Ctrl+Click", "Alt+Click");
        List<String> descriptions = List.of(
                IrisLanguage.plain(DesktopUiMessages.VISION_HELP_TOGGLE),
                IrisLanguage.plain(DesktopUiMessages.VISION_HELP_REFRESH),
                IrisLanguage.plain(DesktopUiMessages.VISION_HELP_FOLLOW),
                IrisLanguage.plain(DesktopUiMessages.VISION_HELP_ZOOM),
                IrisLanguage.plain(DesktopUiMessages.VISION_HELP_RESET_ZOOM),
                IrisLanguage.plain(DesktopUiMessages.VISION_HELP_CYCLE_MODE),
                IrisLanguage.plain(DesktopUiMessages.VISION_HELP_GRID),
                IrisLanguage.plain(DesktopUiMessages.VISION_HELP_BIOME),
                IrisLanguage.plain(DesktopUiMessages.VISION_HELP_TELEPORT),
                IrisLanguage.plain(DesktopUiMessages.VISION_HELP_EDITOR)
        );
        int keyWidth = 0;
        canvas.setFont(KEY_FONT);
        for (String key : keys) {
            keyWidth = Math.max(keyWidth, canvas.getFontMetrics().stringWidth(key));
        }
        int lineHeight = 20;
        int width = keyWidth + 206;
        int height = keys.size() * lineHeight + CARD_PADDING * 2;
        drawCardBackground(canvas, CARD_PADDING, CARD_PADDING, width, height);
        for (int index = 0; index < keys.size(); index++) {
            int y = CARD_PADDING * 2 + 14 + index * lineHeight;
            canvas.setFont(KEY_FONT);
            canvas.setColor(ACCENT);
            canvas.drawString(keys.get(index), CARD_PADDING * 2, y);
            canvas.setFont(BODY_FONT);
            canvas.setColor(TEXT_SECONDARY);
            canvas.drawString(descriptions.get(index), CARD_PADDING * 2 + keyWidth + 14, y);
        }
    }

    private void renderDebug(Graphics2D canvas) {
        VisionRenderController.Progress progress = controller.progress(renderFrame);
        ArrayList<String> lines = new ArrayList<>();
        lines.add(IrisLanguage.plain(
                DesktopUiMessages.VISION_TILES,
                MessageArgument.trusted("ready", progress.ready()),
                MessageArgument.trusted("total", progress.total())
        ));
        lines.add(IrisLanguage.plain(
                DesktopUiMessages.VISION_WORKERS,
                MessageArgument.trusted("active", progress.active()),
                MessageArgument.trusted("queued", progress.queued())
        ));
        lines.add(IrisLanguage.plain(
                DesktopUiMessages.VISION_CENTER,
                MessageArgument.trusted("x", Form.f((int) centerX)),
                MessageArgument.trusted("z", Form.f((int) centerZ))
        ));
        drawCard(canvas, CARD_PADDING, getHeight() - STATUS_HEIGHT - PROGRESS_HEIGHT - CARD_PADDING, 0D, 1D, lines);
    }

    private void renderNotifications(Graphics2D canvas) {
        if (notifications.isEmpty()) {
            return;
        }
        String text = String.join("  ·  ", notifications.keySet());
        canvas.setFont(BODY_BOLD_FONT);
        int textWidth = canvas.getFontMetrics().stringWidth(text);
        int width = textWidth + CARD_PADDING * 2;
        int height = 36;
        int x = (getWidth() - width) / 2;
        int y = getHeight() - STATUS_HEIGHT - PROGRESS_HEIGHT - height - 14;
        drawCardBackground(canvas, x, y, width, height);
        canvas.setColor(TEXT_PRIMARY);
        canvas.drawString(text, x + CARD_PADDING, y + 23);
    }

    private void drawCard(Graphics2D canvas, int anchorX, int anchorY, double pushX, double pushY, List<String> lines) {
        canvas.setFont(BODY_FONT);
        int lineHeight = canvas.getFontMetrics().getHeight();
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, canvas.getFontMetrics().stringWidth(line));
        }
        width += CARD_PADDING * 2;
        int height = lines.size() * lineHeight + CARD_PADDING * 2 - 2;
        int x = (int) Math.round(anchorX - width * pushX);
        int y = (int) Math.round(anchorY - height * pushY);
        x = Math.max(CARD_PADDING, Math.min(getWidth() - width - CARD_PADDING, x));
        y = Math.max(CARD_PADDING, Math.min(getHeight() - STATUS_HEIGHT - height - CARD_PADDING, y));
        drawCardBackground(canvas, x, y, width, height);
        for (int index = 0; index < lines.size(); index++) {
            canvas.setFont(index == 0 ? BODY_BOLD_FONT : BODY_FONT);
            canvas.setColor(index == 0 ? TEXT_PRIMARY : TEXT_SECONDARY);
            canvas.drawString(lines.get(index), x + CARD_PADDING, y + CARD_PADDING + lineHeight - 3 + index * lineHeight);
        }
    }

    private void drawCardBackground(Graphics2D canvas, int x, int y, int width, int height) {
        RoundRectangle2D card = new RoundRectangle2D.Double(x, y, width, height, CARD_RADIUS, CARD_RADIUS);
        canvas.setColor(CARD_BACKGROUND);
        canvas.fill(card);
        canvas.setColor(CARD_BORDER);
        canvas.draw(card);
    }

    private void requestRender() {
        if (closed || getWidth() < 1 || getHeight() < 1 || !ensureEngine()) {
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
    }

    private boolean ensureEngine() {
        Engine activeEngine = engine;
        if (activeEngine != null
                && !activeEngine.isClosed()
                && !activeEngine.isClosing()
                && activeEngine.getComplex() != null) {
            return true;
        }
        try {
            Engine reacquired = GuiHost.get().findActiveEngine();
            if (reacquired == null || reacquired.isClosed() || reacquired.isClosing() || reacquired.getComplex() == null) {
                return false;
            }
            engine = reacquired;
            renderer = new IrisRenderer(reacquired);
            overlay = GuiHost.get().overlayFor(reacquired, openerId);
            contentRevision++;
            captureHeightRange();
            return true;
        } catch (Throwable error) {
            IrisLogging.debug("Vision engine reacquisition failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
            return false;
        }
    }

    private void refreshContent() {
        if (closed) {
            return;
        }
        if (!ensureEngine()) {
            notifyUser("No active Iris engine");
            return;
        }
        renderer = new IrisRenderer(engine);
        overlay = GuiHost.get().overlayFor(engine, openerId);
        contentRevision++;
        captureHeightRange();
        requestRender();
        notifyUser(IrisLanguage.plain(DesktopUiMessages.VISION_REFRESHING));
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
            List<GuiMarker> nextPlayers = overlay.players();
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
                    centerX = player.worldX();
                    centerZ = player.worldZ();
                    requestRender();
                }
            }
            repaint();
        } catch (Throwable error) {
            IrisLogging.debug("Vision marker refresh failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private void requestHover() {
        Point point = cursor;
        VisionRenderController.Frame frame = renderFrame;
        Engine probeEngine = engine;
        if (closed || point == null || frame == null || probeEngine == null || probeEngine.getComplex() == null) {
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

    private void setRenderType(RenderType type) {
        if (type == null || type == currentType) {
            return;
        }
        currentType = type;
        syncControls();
        requestRender();
        notifyUser(modeName(type));
    }

    private void toggleGrid() {
        grid = !grid;
        syncControls();
        repaint();
        notifyUser(IrisLanguage.plain(grid ? DesktopUiMessages.VISION_GRID_ENABLED : DesktopUiMessages.VISION_GRID_DISABLED));
    }

    private void toggleEntities() {
        entitiesVisible = !entitiesVisible;
        if (entitiesVisible) {
            refreshMarkers();
        } else {
            entities = List.of();
            repaint();
        }
        syncControls();
        notifyUser(IrisLanguage.plain(
                entitiesVisible
                        ? DesktopUiMessages.VISION_ENTITIES_ENABLED
                        : DesktopUiMessages.VISION_ENTITIES_DISABLED
        ));
    }

    private void toggleFollow() {
        follow = !follow;
        if (follow && players.isEmpty()) {
            follow = false;
            notifyUser(IrisLanguage.plain(DesktopUiMessages.VISION_NO_PLAYER));
        } else if (follow) {
            GuiMarker player = players.get(0);
            centerX = player.worldX();
            centerZ = player.worldZ();
            requestRender();
            notifyUser(IrisLanguage.plain(
                    DesktopUiMessages.VISION_FOLLOWING,
                    MessageArgument.untrusted("player", player.label())
            ));
        } else {
            notifyUser(IrisLanguage.plain(DesktopUiMessages.VISION_FOLLOW_DISABLED));
        }
        syncControls();
    }

    private void syncControls() {
        controlsUpdating = true;
        try {
            if (modeSelector != null) {
                modeSelector.setSelectedItem(currentType);
            }
            if (gridToggle != null) {
                gridToggle.setSelected(grid);
            }
            if (entitiesToggle != null) {
                entitiesToggle.setSelected(entitiesVisible);
            }
            if (followToggle != null) {
                followToggle.setSelected(follow);
            }
        } finally {
            controlsUpdating = false;
        }
    }

    private void notifyUser(String message) {
        notifications.put(message, System.currentTimeMillis() + 2500L);
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
                VisionRenderController.MINIMUM_BLOCKS_PER_PIXEL,
                VisionRenderController.MAXIMUM_BLOCKS_PER_PIXEL
        );
        if (next.blocksPerPixel() == blocksPerPixel) {
            return;
        }
        centerX = next.centerX();
        centerZ = next.centerZ();
        blocksPerPixel = next.blocksPerPixel();
        follow = false;
        hoverInfo = null;
        syncControls();
        zoomRenderTimer.restart();
        repaint();
    }

    private double blocksPerPixel() {
        return blocksPerPixel;
    }

    private double screenToWorldX(double screenX) {
        return centerX + (screenX - getWidth() * 0.5D) * blocksPerPixel();
    }

    private double screenToWorldZ(double screenY) {
        return centerZ + (screenY - getHeight() * 0.5D) * blocksPerPixel();
    }

    private double worldToScreenX(double worldX) {
        return getWidth() * 0.5D + (worldX - centerX) / blocksPerPixel();
    }

    private double worldToScreenZ(double worldZ) {
        return getHeight() * 0.5D + (worldZ - centerZ) / blocksPerPixel();
    }

    private void openAtCursor() {
        GuiOverlay activeOverlay = overlay;
        Point point = cursor;
        VisionRenderController.Frame frame = renderFrame;
        if (activeOverlay == null || point == null || frame == null) {
            return;
        }
        double worldX = screenToWorldX(point.x);
        double worldZ = screenToWorldZ(point.y);
        RenderType type = currentType;
        controller.submitProbe(
                frame,
                () -> activeOverlay.openInEditor(worldX, worldZ, type),
                opened -> {
                    if (opened != null) {
                        notifyUser(IrisLanguage.plain(
                                DesktopUiMessages.VISION_OPENED,
                                MessageArgument.untrusted("target", opened)
                        ));
                    }
                }
        );
    }

    private void teleportToCursor() {
        Point point = cursor;
        if (overlay == null || point == null) {
            notifyUser(IrisLanguage.plain(DesktopUiMessages.VISION_NO_PLAYER));
            return;
        }
        int worldX = floorWorldCoordinate(screenToWorldX(point.x));
        int worldZ = floorWorldCoordinate(screenToWorldZ(point.y));
        overlay.teleport(worldX, worldZ);
        notifyUser(IrisLanguage.plain(
                DesktopUiMessages.VISION_TELEPORTING,
                MessageArgument.trusted("x", worldX),
                MessageArgument.trusted("z", worldZ)
        ));
    }

    private void close() {
        if (closed) {
            return;
        }
        closed = true;
        resizeTimer.stop();
        dragRenderTimer.stop();
        zoomRenderTimer.stop();
        hoverTimer.stop();
        markerTimer.stop();
        notificationTimer.stop();
        GuiHost.get().unregisterHotloadHook(hotloadHook);
        hostFrame.removeKeyListener(this);
        controller.close();
    }

    static int floorWorldCoordinate(double coordinate) {
        return (int) StrictMath.floor(coordinate);
    }

    private static String modeName(RenderType type) {
        return IrisLanguage.plain(modeKey(type));
    }

    static MessageKey modeKey(RenderType type) {
        return switch (type) {
            case BIOME -> DesktopUiMessages.VISION_MODE_BIOME;
            case BIOME_LAND -> DesktopUiMessages.VISION_MODE_BIOME_LAND;
            case BIOME_SEA -> DesktopUiMessages.VISION_MODE_BIOME_SEA;
            case REGION -> DesktopUiMessages.VISION_MODE_REGION;
            case CAVE_LAND -> DesktopUiMessages.VISION_MODE_CAVE_LAND;
            case RIVER -> DesktopUiMessages.VISION_MODE_RIVER;
            case HEIGHT -> DesktopUiMessages.VISION_MODE_HEIGHT;
            case OBJECT_LOAD -> DesktopUiMessages.VISION_MODE_OBJECT_LOAD;
            case DECORATOR_LOAD -> DesktopUiMessages.VISION_MODE_DECORATOR_LOAD;
            case CONTINENT -> DesktopUiMessages.VISION_MODE_CONTINENT;
            case LAYER_LOAD -> DesktopUiMessages.VISION_MODE_LAYER_LOAD;
        };
    }

    @Override
    public void mouseMoved(MouseEvent event) {
        cursor = event.getPoint();
        hoverTimer.restart();
    }

    @Override
    public void mouseDragged(MouseEvent event) {
        cursor = event.getPoint();
        double deltaX = dragX - event.getX();
        double deltaY = dragY - event.getY();
        centerX += deltaX * blocksPerPixel();
        centerZ += deltaY * blocksPerPixel();
        dragX = event.getX();
        dragY = event.getY();
        follow = false;
        hoverInfo = null;
        syncControls();
        dragRenderTimer.restart();
        repaint();
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent event) {
        if (!event.isControlDown()) {
            cursor = event.getPoint();
            double factor = Math.pow(2D, event.getPreciseWheelRotation() * WHEEL_ZOOM_EXPONENT);
            changeZoom(factor, cursor);
            event.consume();
        }
    }

    @Override
    public void mouseClicked(MouseEvent event) {
        cursor = event.getPoint();
        if (event.isControlDown()) {
            teleportToCursor();
        } else if (event.isAltDown()) {
            openAtCursor();
        }
    }

    @Override
    public void mousePressed(MouseEvent event) {
        requestFocusInWindow();
        cursor = event.getPoint();
        dragX = event.getX();
        dragY = event.getY();
    }

    @Override
    public void mouseReleased(MouseEvent event) {
        if (dragRenderTimer.isRunning()) {
            dragRenderTimer.stop();
            requestRender();
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
        repaint();
    }

    @Override
    public void keyPressed(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.VK_SHIFT) {
            detailedHover = true;
            repaint();
        } else if (event.getKeyCode() == KeyEvent.VK_SEMICOLON) {
            debug = true;
            repaint();
        }
    }

    @Override
    public void keyReleased(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.VK_SHIFT) {
            detailedHover = false;
            repaint();
            return;
        }
        if (keyCode == KeyEvent.VK_SEMICOLON) {
            debug = false;
            repaint();
            return;
        }
        if (keyCode == KeyEvent.VK_SLASH) {
            help = !help;
            repaint();
            return;
        }
        if (keyCode == KeyEvent.VK_R) {
            refreshContent();
            return;
        }
        if (keyCode == KeyEvent.VK_F) {
            toggleFollow();
            return;
        }
        if (keyCode == KeyEvent.VK_G) {
            toggleGrid();
            return;
        }
        if (keyCode == KeyEvent.VK_EQUALS || keyCode == KeyEvent.VK_ADD) {
            changeZoom(1D / KEYBOARD_ZOOM_FACTOR, cursor);
            return;
        }
        if (keyCode == KeyEvent.VK_MINUS || keyCode == KeyEvent.VK_SUBTRACT) {
            changeZoom(KEYBOARD_ZOOM_FACTOR, cursor);
            return;
        }
        if (keyCode == KeyEvent.VK_BACK_SLASH) {
            changeZoom(DEFAULT_BLOCKS_PER_PIXEL / blocksPerPixel, null);
            notifyUser(IrisLanguage.plain(DesktopUiMessages.VISION_ZOOM_RESET));
            return;
        }
        if (keyCode == KeyEvent.VK_M) {
            RenderType[] types = RenderType.values();
            setRenderType(types[(currentType.ordinal() + 1) % types.length]);
            return;
        }

        int modeIndex = modeIndex(keyCode);
        RenderType[] types = RenderType.values();
        if (modeIndex >= 0 && modeIndex < types.length) {
            setRenderType(types[modeIndex]);
        }
    }

    private static int modeIndex(int keyCode) {
        if (keyCode >= KeyEvent.VK_1 && keyCode <= KeyEvent.VK_9) {
            return keyCode - KeyEvent.VK_1;
        }
        if (keyCode == KeyEvent.VK_0) {
            return 9;
        }
        if (keyCode == KeyEvent.VK_BACK_QUOTE) {
            return 10;
        }
        return -1;
    }

    @Override
    public void keyTyped(KeyEvent event) {
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

    private static final class ModeCellRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = -3387015482484702284L;

        @Override
        public Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean selected,
                boolean focus
        ) {
            Component component = super.getListCellRendererComponent(list, value, index, selected, focus);
            if (component instanceof JLabel label && value instanceof RenderType type) {
                label.setText(modeName(type));
                label.setFont(TOOLBAR_FONT);
                label.setForeground(selected ? TEXT_PRIMARY : TEXT_SECONDARY);
                label.setBackground(selected ? CONTROL_SELECTED : CONTROL_BACKGROUND);
                label.setBorder(BorderFactory.createEmptyBorder(3, 7, 3, 7));
            }
            return component;
        }
    }
}
