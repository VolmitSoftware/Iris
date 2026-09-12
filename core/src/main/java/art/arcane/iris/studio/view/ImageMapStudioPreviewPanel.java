package art.arcane.iris.studio.view;

import art.arcane.iris.localization.DesktopUiMessages;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.generation.image.CompiledIrisImageMap;
import art.arcane.iris.generation.image.IrisImageMapRuntime;
import art.arcane.iris.generation.image.IrisImageMapMaskSampler;
import art.arcane.iris.generation.image.IrisImageMap;
import art.arcane.iris.generation.image.IrisImageMapApplication;
import art.arcane.iris.generation.image.IrisImageMapType;
import art.arcane.iris.world.IrisWorldBoundary;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.localization.MessageArgument;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingWorker;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.DoubleBinaryOperator;

final class ImageMapStudioPreviewPanel extends JPanel implements AutoCloseable {
    private static final Color BACKGROUND = new Color(12, 15, 22);
    private static final Color PANEL_BACKGROUND = new Color(20, 24, 33);
    private static final Color BORDER = new Color(48, 55, 72);
    private static final Color TEXT = new Color(230, 234, 242);
    private static final Color SECONDARY = new Color(151, 160, 178);

    private final SourceCanvas sourceCanvas = new SourceCanvas();
    private final WorldCanvas worldCanvas = new WorldCanvas();
    private final JLabel statusLabel = new JLabel(" ");

    ImageMapStudioPreviewPanel() {
        super(new BorderLayout());
        setBackground(BACKGROUND);
        JPanel source = wrap(IrisLanguage.plain(DesktopUiMessages.IMAGEMAP_SOURCE), sourceCanvas);
        JPanel interpreted = wrap(IrisLanguage.plain(DesktopUiMessages.IMAGEMAP_INTERPRETED), worldCanvas);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, source, interpreted);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setDividerSize(5);
        split.setResizeWeight(0.42D);
        split.setBackground(BACKGROUND);
        add(split, BorderLayout.CENTER);
        statusLabel.setForeground(SECONDARY);
        statusLabel.setBackground(PANEL_BACKGROUND);
        statusLabel.setOpaque(true);
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
                BorderFactory.createEmptyBorder(5, 9, 5, 9)
        ));
        add(statusLabel, BorderLayout.SOUTH);
        worldCanvas.setHoverConsumer(statusLabel::setText);
    }

    void setPreview(
            BufferedImage source,
            CompiledIrisImageMap compiled,
            IrisImageMapMaskSampler maskSampler,
            IrisWorldBoundary boundary,
            IrisImageMapApplication application,
            int minimumWorldHeight,
            DoubleBinaryOperator proceduralHeightSampler
    ) {
        sourceCanvas.setSource(source);
        worldCanvas.setPreview(
                compiled, maskSampler, boundary, application, minimumWorldHeight, proceduralHeightSampler
        );
    }

    void setSource(BufferedImage source) {
        sourceCanvas.setSource(source);
        worldCanvas.clear();
    }

    void setDiagnosticConsumer(Consumer<String> diagnosticConsumer) {
        worldCanvas.setDiagnosticConsumer(diagnosticConsumer);
    }

    void setOverlays(boolean chunks, boolean regions, boolean boundary, boolean coverage) {
        worldCanvas.setOverlays(chunks, regions, boundary, coverage);
    }

    BufferedImage renderInterpretedSnapshot(int width, int height) {
        return worldCanvas.renderSnapshot(width, height).image();
    }

    BufferedImage renderSourceSnapshot(int width, int height) {
        sourceCanvas.setSize(width, height);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            sourceCanvas.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    @Override
    public void close() {
        worldCanvas.close();
    }

    private static JPanel wrap(String title, JPanel canvas) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(PANEL_BACKGROUND);
        panel.setBorder(BorderFactory.createLineBorder(BORDER));
        JLabel label = new JLabel(title);
        label.setForeground(TEXT);
        label.setBackground(PANEL_BACKGROUND);
        label.setOpaque(true);
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                BorderFactory.createEmptyBorder(6, 9, 6, 9)
        ));
        panel.add(label, BorderLayout.NORTH);
        panel.add(canvas, BorderLayout.CENTER);
        return panel;
    }

    private static final class SourceCanvas extends JPanel {
        private BufferedImage source;

        private SourceCanvas() {
            setBackground(BACKGROUND);
            setPreferredSize(new Dimension(420, 520));
        }

        private void setSource(BufferedImage source) {
            this.source = source;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (source == null) {
                drawEmpty(graphics, IrisLanguage.plain(DesktopUiMessages.IMAGEMAP_NO_SOURCE));
                return;
            }
            Graphics2D canvas = (Graphics2D) graphics.create();
            try {
                canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                int padding = 16;
                double scale = Math.min(
                        (getWidth() - (padding * 2D)) / source.getWidth(),
                        (getHeight() - (padding * 2D)) / source.getHeight()
                );
                scale = Math.max(0.0001D, scale);
                int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
                int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
                int x = (getWidth() - width) / 2;
                int y = (getHeight() - height) / 2;
                canvas.drawImage(source, x, y, width, height, null);
                canvas.setColor(new Color(255, 255, 255, 64));
                canvas.drawRect(x, y, width - 1, height - 1);
                if (scale >= 8D && source.getWidth() <= 256 && source.getHeight() <= 256) {
                    canvas.setColor(new Color(255, 255, 255, 25));
                    for (int sourceX = 1; sourceX < source.getWidth(); sourceX++) {
                        int lineX = x + (int) Math.round(sourceX * scale);
                        canvas.drawLine(lineX, y, lineX, y + height);
                    }
                    for (int sourceZ = 1; sourceZ < source.getHeight(); sourceZ++) {
                        int lineY = y + (int) Math.round(sourceZ * scale);
                        canvas.drawLine(x, lineY, x + width, lineY);
                    }
                }
            } finally {
                canvas.dispose();
            }
        }
    }

    private static final class WorldCanvas extends JPanel implements AutoCloseable {
        private final AtomicLong revision = new AtomicLong();
        private CompiledIrisImageMap compiled;
        private IrisImageMapMaskSampler maskSampler = IrisImageMapMaskSampler.empty();
        private IrisWorldBoundary boundary;
        private IrisImageMapApplication application = IrisImageMapApplication.CUSTOM;
        private int minimumWorldHeight;
        private DoubleBinaryOperator proceduralHeightSampler;
        private BufferedImage rendered;
        private SwingWorker<RenderResult, Void> worker;
        private Consumer<String> diagnosticConsumer = ignored -> {
        };
        private Consumer<String> hoverConsumer = ignored -> {
        };
        private double centerX;
        private double centerZ;
        private double blocksPerScreenPixel = 1D;
        private boolean showChunks = true;
        private boolean showRegions = true;
        private boolean showBoundary = true;
        private boolean showCoverage = true;
        private Point dragOrigin;
        private double dragCenterX;
        private double dragCenterZ;

        private WorldCanvas() {
            setBackground(BACKGROUND);
            setPreferredSize(new Dimension(620, 520));
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    dragOrigin = event.getPoint();
                    dragCenterX = centerX;
                    dragCenterZ = centerZ;
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    if (dragOrigin == null) {
                        return;
                    }
                    centerX = dragCenterX - ((event.getX() - dragOrigin.x) * blocksPerScreenPixel);
                    centerZ = dragCenterZ - ((event.getY() - dragOrigin.y) * blocksPerScreenPixel);
                    requestRender();
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    dragOrigin = null;
                }

                @Override
                public void mouseMoved(MouseEvent event) {
                    publishHover(event.getX(), event.getY());
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent event) {
                    double beforeX = screenToWorldX(event.getX());
                    double beforeZ = screenToWorldZ(event.getY());
                    double factor = Math.pow(1.12D, event.getPreciseWheelRotation());
                    blocksPerScreenPixel = Math.max(0.01D, Math.min(1_000_000D, blocksPerScreenPixel * factor));
                    centerX += beforeX - screenToWorldX(event.getX());
                    centerZ += beforeZ - screenToWorldZ(event.getY());
                    requestRender();
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            addMouseWheelListener(mouse);
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent event) {
                    requestRender();
                }
            });
        }

        private void setPreview(
                CompiledIrisImageMap compiled,
                IrisImageMapMaskSampler maskSampler,
                IrisWorldBoundary boundary,
                IrisImageMapApplication application,
                int minimumWorldHeight,
                DoubleBinaryOperator proceduralHeightSampler
        ) {
            this.compiled = compiled;
            this.maskSampler = maskSampler == null ? IrisImageMapMaskSampler.empty() : maskSampler;
            this.boundary = boundary;
            this.application = application == null ? IrisImageMapApplication.CUSTOM : application;
            this.minimumWorldHeight = minimumWorldHeight;
            this.proceduralHeightSampler = proceduralHeightSampler;
            IrisImageMap definition = compiled.getDefinition();
            centerX = definition.getOrigin().getX();
            centerZ = definition.getOrigin().getZ();
            fitSource();
            requestRender();
        }

        private void clear() {
            revision.incrementAndGet();
            if (worker != null) {
                worker.cancel(true);
                worker = null;
            }
            compiled = null;
            rendered = null;
            repaint();
        }

        private void setDiagnosticConsumer(Consumer<String> diagnosticConsumer) {
            this.diagnosticConsumer = diagnosticConsumer == null ? ignored -> {
            } : diagnosticConsumer;
        }

        private void setHoverConsumer(Consumer<String> hoverConsumer) {
            this.hoverConsumer = hoverConsumer == null ? ignored -> {
            } : hoverConsumer;
        }

        private void setOverlays(boolean chunks, boolean regions, boolean boundary, boolean coverage) {
            showChunks = chunks;
            showRegions = regions;
            showBoundary = boundary;
            showCoverage = coverage;
            requestRender();
        }

        private void fitSource() {
            if (compiled == null) {
                return;
            }
            Point2D.Double[] corners = ImageMapStudioModel.sourceWorldCorners(
                    compiled.getDefinition(), compiled.getSourceWidth(), compiled.getSourceHeight()
            );
            double minimumX = Double.POSITIVE_INFINITY;
            double maximumX = Double.NEGATIVE_INFINITY;
            double minimumZ = Double.POSITIVE_INFINITY;
            double maximumZ = Double.NEGATIVE_INFINITY;
            for (Point2D.Double corner : corners) {
                minimumX = Math.min(minimumX, corner.x);
                maximumX = Math.max(maximumX, corner.x);
                minimumZ = Math.min(minimumZ, corner.y);
                maximumZ = Math.max(maximumZ, corner.y);
            }
            centerX = (minimumX + maximumX) / 2D;
            centerZ = (minimumZ + maximumZ) / 2D;
            int width = Math.max(320, getWidth());
            int height = Math.max(320, getHeight());
            blocksPerScreenPixel = Math.max(
                    (maximumX - minimumX) / (width * 0.82D),
                    (maximumZ - minimumZ) / (height * 0.82D)
            );
            blocksPerScreenPixel = Math.max(0.01D, blocksPerScreenPixel);
        }

        private void requestRender() {
            if (compiled == null || getWidth() <= 0 || getHeight() <= 0) {
                repaint();
                return;
            }
            long currentRevision = revision.incrementAndGet();
            if (worker != null) {
                worker.cancel(true);
            }
            int width = Math.max(1, getWidth());
            int height = Math.max(1, getHeight());
            worker = new SwingWorker<>() {
                @Override
                protected RenderResult doInBackground() {
                    return renderSnapshot(width, height);
                }

                @Override
                protected void done() {
                    if (isCancelled() || currentRevision != revision.get()) {
                        return;
                    }
                    try {
                        RenderResult result = get();
                        rendered = result.image();
                        if (result.errors() > 0) {
                            diagnosticConsumer.accept(result.errors() + " preview sample(s) failed; invalid pixels are magenta.");
                        }
                        repaint();
                    } catch (CancellationException ignored) {
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    } catch (ExecutionException exception) {
                        IrisLogging.reportError(exception.getCause());
                        diagnosticConsumer.accept("Preview render failed: " + exception.getCause().getMessage());
                    }
                }
            };
            worker.execute();
        }

        private RenderResult renderSnapshot(int width, int height) {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            if (compiled == null) {
                return new RenderResult(image, 0);
            }
            int errors = 0;
            IrisImageMapType type = compiled.getType();
            IrisImageMap definition = compiled.getDefinition();
            for (int screenZ = 0; screenZ < height; screenZ++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                double worldZ = centerZ + ((screenZ - (height / 2D)) * blocksPerScreenPixel);
                for (int screenX = 0; screenX < width; screenX++) {
                    double worldX = centerX + ((screenX - (width / 2D)) * blocksPerScreenPixel);
                    int color;
                    try {
                        double maskWeight = maskSampler.sample(worldX, worldZ);
                        color = switch (type) {
                            case GRAYSCALE_HEIGHT, RGB_HEIGHT -> ImageMapStudioModel.heightColor(
                                    interpretedHeight(worldX, worldZ, maskWeight),
                                    definition.getMinimumHeight(),
                                    definition.getMaximumHeight()
                            );
                            case COLOR_MAP -> categoricalApplication()
                                    && !IrisImageMapRuntime.selectCategorical(maskWeight)
                                    ? BACKGROUND.getRGB()
                                    : ImageMapStudioModel.targetColor(compiled.sampleTarget(worldX, worldZ));
                            case BINARY_MASK, GRAYSCALE_MASK, ALPHA_MASK -> grayscale(
                                    compiled.sampleNormalized(worldX, worldZ)
                            );
                        };
                    } catch (RuntimeException exception) {
                        color = 0xFFFF3DA8;
                        errors++;
                    }
                    if (showCoverage && !compiled.containsWorld(worldX, worldZ)
                            && ((screenX + screenZ) & 7) < 3) {
                        color = blend(color, 0xFF090B10, 0.48D);
                    }
                    image.setRGB(screenX, screenZ, color);
                }
            }
            return new RenderResult(image, errors);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (compiled == null) {
                drawEmpty(graphics, IrisLanguage.plain(DesktopUiMessages.IMAGEMAP_NO_PREVIEW));
                return;
            }
            Graphics2D canvas = (Graphics2D) graphics.create();
            try {
                canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (rendered != null) {
                    canvas.drawImage(rendered, 0, 0, getWidth(), getHeight(), null);
                }
                drawWorldGrid(canvas, 16D, new Color(255, 255, 255, 28), showChunks);
                drawWorldGrid(canvas, 512D, new Color(99, 161, 255, 96), showRegions);
                if (showCoverage) {
                    drawCoverage(canvas);
                }
                if (showBoundary && boundary != null) {
                    drawBoundary(canvas);
                }
                drawOrigin(canvas);
            } finally {
                canvas.dispose();
            }
        }

        private void drawWorldGrid(Graphics2D canvas, double spacing, Color color, boolean enabled) {
            if (!enabled || spacing / blocksPerScreenPixel < 7D) {
                return;
            }
            canvas.setColor(color);
            double minimumX = screenToWorldX(0);
            double maximumX = screenToWorldX(getWidth());
            double minimumZ = screenToWorldZ(0);
            double maximumZ = screenToWorldZ(getHeight());
            double firstX = Math.floor(minimumX / spacing) * spacing;
            double firstZ = Math.floor(minimumZ / spacing) * spacing;
            for (double worldX = firstX; worldX <= maximumX; worldX += spacing) {
                int screenX = worldToScreenX(worldX);
                canvas.drawLine(screenX, 0, screenX, getHeight());
            }
            for (double worldZ = firstZ; worldZ <= maximumZ; worldZ += spacing) {
                int screenZ = worldToScreenZ(worldZ);
                canvas.drawLine(0, screenZ, getWidth(), screenZ);
            }
        }

        private void drawCoverage(Graphics2D canvas) {
            Point2D.Double[] corners = ImageMapStudioModel.sourceWorldCorners(
                    compiled.getDefinition(), compiled.getSourceWidth(), compiled.getSourceHeight()
            );
            Polygon polygon = new Polygon();
            for (Point2D.Double corner : corners) {
                polygon.addPoint(worldToScreenX(corner.x), worldToScreenZ(corner.y));
            }
            canvas.setStroke(new BasicStroke(2F));
            canvas.setColor(new Color(99, 161, 255, 205));
            canvas.drawPolygon(polygon);
        }

        private void drawBoundary(Graphics2D canvas) {
            int minimumX = worldToScreenX(boundary.minimumX());
            int maximumX = worldToScreenX(boundary.maximumX());
            int minimumZ = worldToScreenZ(boundary.minimumZ());
            int maximumZ = worldToScreenZ(boundary.maximumZ());
            int x = Math.min(minimumX, maximumX);
            int z = Math.min(minimumZ, maximumZ);
            int width = Math.abs(maximumX - minimumX);
            int height = Math.abs(maximumZ - minimumZ);
            canvas.setColor(new Color(255, 188, 82, 220));
            canvas.setStroke(new BasicStroke(2F));
            canvas.drawRect(x, z, width, height);
        }

        private void drawOrigin(Graphics2D canvas) {
            IrisImageMap definition = compiled.getDefinition();
            int x = worldToScreenX(definition.getOrigin().getX());
            int z = worldToScreenZ(definition.getOrigin().getZ());
            canvas.setColor(new Color(255, 255, 255, 190));
            canvas.drawLine(x - 7, z, x + 7, z);
            canvas.drawLine(x, z - 7, x, z + 7);
        }

        private void publishHover(int screenX, int screenZ) {
            if (compiled == null) {
                return;
            }
            double worldX = screenToWorldX(screenX);
            double worldZ = screenToWorldZ(screenZ);
            String value;
            try {
                double maskWeight = maskSampler.sample(worldX, worldZ);
                value = switch (compiled.getType()) {
                    case GRAYSCALE_HEIGHT, RGB_HEIGHT -> String.format(
                            "Y %.3f", interpretedHeight(worldX, worldZ, maskWeight)
                    );
                    case COLOR_MAP -> categoricalApplication()
                            && !IrisImageMapRuntime.selectCategorical(maskWeight)
                            ? "procedural"
                            : String.valueOf(compiled.sampleTarget(worldX, worldZ));
                    case BINARY_MASK, GRAYSCALE_MASK, ALPHA_MASK -> String.format(
                            "%.4f", compiled.sampleNormalized(worldX, worldZ)
                    );
                };
                if (!maskSampler.isEmpty()) {
                    value += String.format("  |  mask %.4f", maskWeight);
                }
            } catch (RuntimeException exception) {
                value = exception.getMessage();
            }
            hoverConsumer.accept(IrisLanguage.plain(
                    DesktopUiMessages.IMAGEMAP_PREVIEW_STATUS,
                    MessageArgument.untrusted("x", String.format("%.2f", worldX)),
                    MessageArgument.untrusted("z", String.format("%.2f", worldZ)),
                    MessageArgument.untrusted("value", value),
                    MessageArgument.untrusted("scale", String.format("%.3f", blocksPerScreenPixel))
            ));
        }

        private double interpretedHeight(double worldX, double worldZ, double maskWeight) {
            double mappedHeight = compiled.sampleHeight(worldX, worldZ);
            if (application != IrisImageMapApplication.TERRAIN_HEIGHT || maskSampler.isEmpty()) {
                return mappedHeight;
            }
            IrisImageMap definition = compiled.getDefinition();
            double proceduralLocalHeight = proceduralHeightSampler == null
                    ? definition.getMinimumHeight() - minimumWorldHeight
                    : proceduralHeightSampler.applyAsDouble(worldX, worldZ);
            double mappedLocalHeight = mappedHeight - minimumWorldHeight;
            return minimumWorldHeight + IrisImageMapRuntime.blendTerrainHeight(
                    mappedLocalHeight, proceduralLocalHeight, maskWeight
            );
        }

        private boolean categoricalApplication() {
            return application == IrisImageMapApplication.BIOME
                    || application == IrisImageMapApplication.REGION
                    || application == IrisImageMapApplication.SURFACE_BLOCK;
        }

        private int worldToScreenX(double worldX) {
            return (int) Math.round((getWidth() / 2D) + ((worldX - centerX) / blocksPerScreenPixel));
        }

        private int worldToScreenZ(double worldZ) {
            return (int) Math.round((getHeight() / 2D) + ((worldZ - centerZ) / blocksPerScreenPixel));
        }

        private double screenToWorldX(double screenX) {
            return centerX + ((screenX - (getWidth() / 2D)) * blocksPerScreenPixel);
        }

        private double screenToWorldZ(double screenZ) {
            return centerZ + ((screenZ - (getHeight() / 2D)) * blocksPerScreenPixel);
        }

        @Override
        public void close() {
            revision.incrementAndGet();
            if (worker != null) {
                worker.cancel(true);
            }
        }
    }

    private static void drawEmpty(Graphics graphics, String text) {
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            canvas.setColor(SECONDARY);
            canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int width = canvas.getFontMetrics().stringWidth(text);
            int x = Math.max(8, (graphics.getClipBounds() == null ? 0 : graphics.getClipBounds().width - width) / 2);
            int y = Math.max(20, graphics.getClipBounds() == null ? 20 : graphics.getClipBounds().height / 2);
            canvas.drawString(text, x, y);
        } finally {
            canvas.dispose();
        }
    }

    private static int grayscale(double value) {
        int level = (int) Math.round(Math.max(0D, Math.min(1D, value)) * 255D);
        return 0xFF000000 | (level << 16) | (level << 8) | level;
    }

    private static int blend(int first, int second, double secondWeight) {
        double firstWeight = 1D - secondWeight;
        int red = (int) ((((first >>> 16) & 0xFF) * firstWeight) + (((second >>> 16) & 0xFF) * secondWeight));
        int green = (int) ((((first >>> 8) & 0xFF) * firstWeight) + (((second >>> 8) & 0xFF) * secondWeight));
        int blue = (int) (((first & 0xFF) * firstWeight) + ((second & 0xFF) * secondWeight));
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private record RenderResult(BufferedImage image, int errors) {
    }
}
