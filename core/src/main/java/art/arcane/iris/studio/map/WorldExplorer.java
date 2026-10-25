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

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.studio.map.components.MapLabels;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.stream.ProvisionalSampling;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.stream.ProceduralStream;

import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JToggleButton;
import javax.swing.Timer;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleBinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The map's 3D world view: the terrain the engine plans, without caves or objects, coloured by biome, region or how
 * HYBRID terrain picked each biome. The legend counts what the view shows and the status bar names what is under the
 * pointer.
 */
final class WorldExplorer {
    private static final int MOST_WORKERS = 12;
    private static final String CAMERA_HINT = "Drag to orbit · Right-drag to pan · Scroll to zoom · R resets";
    private static final int POINTER_SETTLE_MILLIS = 60;
    private static final int CENSUS_SETTLE_MILLIS = 250;
    private static final int FIRST_RETRY_MILLIS = 500;
    private static final int MAX_RETRY_MILLIS = 8_000;
    // Enough for each share to be within about a percent.
    private static final int MOST_CENSUS_SPOTS = 2_500;
    // Heights sampled around where the view opens, to tell how low and high its ground goes.
    private static final int HEIGHT_PROBES_ACROSS = 9;
    private static final double HEIGHT_PROBE_REACH_BLOCKS = 2_048D;
    private static final int FLOOR_MARGIN_BLOCKS = 32;
    private static final int TOP_MARGIN_BLOCKS = 96;

    private final Supplier<Engine> engineSource;
    private final OrbitViewport viewport = new OrbitViewport("Iris World Terrain Viewer", null);
    private final int workerCount = Math.min(MOST_WORKERS, Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
    private final ThreadPoolExecutor renderWorkers = NoiseExplorerGUI.createWorkers("Iris World Terrain Worker", workerCount);
    private final ThreadPoolExecutor samplers = NoiseExplorerGUI.createWorkers("Iris World Terrain Sampler", workerCount);
    // Counting what is in view takes a while, so the pointer is looked up on a thread of its own.
    private final ExecutorService lookups = singleThread("Iris World Lookup");
    private final ExecutorService counter = singleThread("Iris World Census");
    private final NoiseTerrainView terrainView;
    private final StudioStatusBar statusBar = new StudioStatusBar();
    private final MapLegendPanel legend = MapLegendPanel.listingOnly();
    private final Map<WorldColoring, JToggleButton> coloringButtons = new EnumMap<>(WorldColoring.class);
    private final AtomicLong pointerRevision = new AtomicLong();
    private final AtomicLong censusRevision = new AtomicLong();
    private final AtomicLong samplingRevision = new AtomicLong();
    private final Timer pointerTimer = singleShot(POINTER_SETTLE_MILLIS, this::lookUpPointer);
    private final Timer censusTimer = singleShot(CENSUS_SETTLE_MILLIS, this::countInView);
    private final Timer measurementRetryTimer = singleShot(FIRST_RETRY_MILLIS, this::retryMeasurement);
    private WorldColoring coloring = WorldColoring.BIOMES;
    private Engine sampledEngine;
    // How low and high the sampled world's ground goes; the colouring is swapped in for each sampling.
    private TerrainSampling measured;
    private WorldSampleRequest measurementRequest;
    private int measurementRetryMillis = FIRST_RETRY_MILLIS;
    private Point pointer;
    private String pointedAt;
    private boolean visible;
    private boolean closed;

    WorldExplorer(Supplier<Engine> engineSource) {
        this.engineSource = engineSource;
        this.terrainView = new NoiseTerrainView(viewport, renderWorkers, samplers, workerCount);
        terrainView.onProgress(this::viewProgressed);
        MapHost.interruptOnPluginShutdown(renderWorkers);
        MapHost.interruptOnPluginShutdown(samplers);
        MapHost.interruptOnPluginShutdown(lookups);
        MapHost.interruptOnPluginShutdown(counter);
        followPointer();
        refreshStatus();
    }

    JComponent view() {
        return viewport;
    }

    StudioStatusBar statusBar() {
        return statusBar;
    }

    MapLegendPanel legend() {
        return legend;
    }

    List<JComponent> controls() {
        ButtonGroup group = new ButtonGroup();
        for (WorldColoring choice : WorldColoring.values()) {
            JToggleButton button = StudioToolbar.toggle(choice.label(), choice.description());
            button.setSelected(choice == coloring);
            button.addActionListener(event -> colorBy(choice));
            group.add(button);
            coloringButtons.put(choice, button);
        }
        return List.copyOf(coloringButtons.values());
    }

    /**
     * Shows the world around this point, keeping the camera when the view already shows the same world there.
     */
    void show(double centerX, double centerZ, boolean recenter) {
        if (closed) {
            return;
        }
        visible = true;
        if (recenter || sampledEngine == null || sampledEngine != engineSource.get()) {
            terrainView.centerOn(centerX, centerZ);
            sampleWorld(centerX, centerZ);
        } else {
            terrainView.resume();
            if (measurementRequest != null) {
                measurementRetryTimer.restart();
            }
        }
        viewport.requestFocusInWindow();
    }

    void hide() {
        visible = false;
        pointerTimer.stop();
        censusTimer.stop();
        measurementRetryTimer.stop();
        terrainView.hide();
    }

    /**
     * Samples the world anew, for a pack that changed on disk.
     */
    void refresh(double centerX, double centerZ) {
        if (closed) {
            return;
        }
        if (visible) {
            sampleWorld(centerX, centerZ);
        } else {
            invalidateSampling();
        }
    }

    void resetCamera() {
        viewport.resetCamera();
    }

    void setCoveredLeftWidth(int width) {
        viewport.setCoveredLeftWidth(width);
    }

    void close() {
        if (closed) {
            return;
        }
        closed = true;
        visible = false;
        invalidateSampling();
        terrainView.close();
        lookups.shutdownNow();
        counter.shutdownNow();
        renderWorkers.shutdownNow();
        samplers.shutdownNow();
        viewport.close();
    }

    private void colorBy(WorldColoring nextColoring) {
        if (closed || nextColoring == coloring) {
            return;
        }
        coloring = nextColoring;
        legend.show(coloring.label() + " in view", List.of(), null);
        if (sampledEngine != null) {
            terrainView.show(samplingOf(sampledEngine, measured));
        }
        refreshStatus();
        viewport.requestFocusInWindow();
    }

    // How low and high the ground goes is measured off the Swing thread, as it samples the terrain itself.
    private void sampleWorld(double centerX, double centerZ) {
        if (closed) {
            return;
        }
        invalidateSampling();
        Engine engine = engineSource.get();
        if (engine == null || engine.isClosed() || engine.isClosing()) {
            viewport.showMessage("No active Iris engine");
            return;
        }
        WorldSampleRequest request = new WorldSampleRequest(samplingRevision.get(), engine, centerX, centerZ);
        measurementRequest = request;
        if (!viewport.isShowingScene()) {
            viewport.showLoading("Sampling the world");
        }
        sampleMeasurement(request);
    }

    private void invalidateSampling() {
        samplingRevision.incrementAndGet();
        pointerRevision.incrementAndGet();
        censusRevision.incrementAndGet();
        measurementRequest = null;
        measurementRetryMillis = FIRST_RETRY_MILLIS;
        measurementRetryTimer.stop();
        pointerTimer.stop();
        censusTimer.stop();
        sampledEngine = null;
        measured = null;
        terrainView.invalidate();
    }

    private void retryMeasurement() {
        WorldSampleRequest request = measurementRequest;
        if (!closed && visible && request != null) {
            sampleMeasurement(request);
        }
    }

    private void sampleMeasurement(WorldSampleRequest request) {
        lookups.execute(() -> {
            if (request.revision() != samplingRevision.get()) {
                return;
            }
            long revision = ProvisionalSampling.revision();
            TerrainSampling sampling = measuredSampling(request.engine(), request.centerX(), request.centerZ());
            boolean provisional = revision != ProvisionalSampling.revision();
            EventQueue.invokeLater(() -> showSampled(request, sampling, provisional));
        });
    }

    private void showSampled(WorldSampleRequest request, TerrainSampling sampling, boolean provisional) {
        Engine engine = request.engine();
        if (closed || request != measurementRequest || request.revision() != samplingRevision.get()
                || engine != engineSource.get() || engine.isClosed() || engine.isClosing()) {
            return;
        }
        boolean changed = measured == null || sampledEngine != engine || measured.floorY() != sampling.floorY()
                || measured.topY() != sampling.topY() || measured.waterLevel() != sampling.waterLevel();
        sampledEngine = engine;
        measured = sampling;
        if (changed) {
            terrainView.show(samplingOf(engine, sampling));
            if (!visible) {
                terrainView.hide();
            }
        }
        if (provisional && visible) {
            measurementRetryTimer.setInitialDelay(measurementRetryMillis);
            measurementRetryTimer.restart();
            measurementRetryMillis = Math.min(MAX_RETRY_MILLIS, measurementRetryMillis * 2);
        } else if (!provisional) {
            measurementRequest = null;
            measurementRetryTimer.stop();
        }
        refreshStatus();
    }

    private TerrainSampling measuredSampling(Engine engine, double centerX, double centerZ) {
        ProceduralStream<Double> heights = engine.getComplex().getHeightStream();
        double fluidHeight = engine.getComplex().getFluidHeight();
        double lowest = fluidHeight;
        double highest = fluidHeight;
        double spacing = 2D * HEIGHT_PROBE_REACH_BLOCKS / (HEIGHT_PROBES_ACROSS - 1);
        for (int row = 0; row < HEIGHT_PROBES_ACROSS; row++) {
            for (int column = 0; column < HEIGHT_PROBES_ACROSS; column++) {
                double height = heights.getDouble(centerX - HEIGHT_PROBE_REACH_BLOCKS + column * spacing,
                        centerZ - HEIGHT_PROBE_REACH_BLOCKS + row * spacing);
                lowest = Math.min(lowest, height);
                highest = Math.max(highest, height);
            }
        }
        int floorY = Math.max(0, (int) Math.floor(lowest) - FLOOR_MARGIN_BLOCKS);
        int topY = Math.min(engine.getHeight(), (int) Math.ceil(highest) + TOP_MARGIN_BLOCKS);
        return new TerrainSampling(new WorldColumns(engine, coloring), floorY, topY, fluidHeight);
    }

    private TerrainSampling samplingOf(Engine engine, TerrainSampling measured) {
        return new TerrainSampling(new WorldColumns(engine, coloring), measured.floorY(), measured.topY(), measured.waterLevel());
    }

    private void viewProgressed() {
        if (closed || !visible) {
            return;
        }
        refreshStatus();
        censusTimer.restart();
    }

    private void followPointer() {
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                if (closed || !visible) {
                    return;
                }
                pointer = event.getPoint();
                pointerTimer.restart();
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                mouseMoved(event);
            }

            @Override
            public void mouseExited(MouseEvent event) {
                pointer = null;
                pointerRevision.incrementAndGet();
                pointedAt = null;
                refreshStatus();
            }
        };
        viewport.addMouseListener(mouse);
        viewport.addMouseMotionListener(mouse);
    }

    private void lookUpPointer() {
        Point at = pointer;
        Engine engine = sampledEngine;
        GroundSight sight = terrainView.groundSight();
        if (closed || !visible || at == null || engine == null || sight == null) {
            return;
        }
        long revision = pointerRevision.incrementAndGet();
        lookups.execute(() -> {
            String description = describeGroundAt(engine, sight, at);
            EventQueue.invokeLater(() -> {
                if (!closed && visible && revision == pointerRevision.get()) {
                    pointedAt = description;
                    refreshStatus();
                }
            });
        });
    }

    private static String describeGroundAt(Engine engine, GroundSight sight, Point at) {
        try {
            Optional<Vec3> ground = sight.groundAt(at.x, at.y, surfaceOf(engine));
            if (ground.isEmpty()) {
                return null;
            }
            Vec3 spot = ground.get();
            double terrainHeight = engine.getComplex().getHeightStream().getDouble(spot.x(), spot.z());
            return subjectsAt(engine, spot.x(), spot.z()) + "   X " + Form.f((int) Math.floor(spot.x()))
                    + "   Y " + Form.f(engine.getMinHeight() + (int) Math.floor(terrainHeight))
                    + "   Z " + Form.f((int) Math.floor(spot.z()));
        } catch (RuntimeException error) {
            IrisLogging.reportError(error);
            return null;
        }
    }

    private static String subjectsAt(Engine engine, double x, double z) {
        List<String> names = new ArrayList<>();
        for (WorldColoring each : WorldColoring.values()) {
            WorldColoring.WorldSubject subject = each.subjectAt(engine, x, z);
            if (subject != null) {
                names.add(subject.name());
            }
        }
        return String.join(" · ", names);
    }

    // Water hides the ground under it, so a line of sight stops on its surface.
    private static DoubleBinaryOperator surfaceOf(Engine engine) {
        ProceduralStream<Double> heights = engine.getComplex().getHeightStream();
        double fluidHeight = engine.getComplex().getFluidHeight();
        return (x, z) -> Math.max(heights.getDouble(x, z), fluidHeight);
    }

    private void countInView() {
        Engine engine = sampledEngine;
        GroundSight sight = terrainView.groundSight();
        if (closed || !visible || engine == null || sight == null) {
            return;
        }
        WorldColoring counted = coloring;
        long revision = censusRevision.incrementAndGet();
        long worldRevision = samplingRevision.get();
        counter.execute(() -> {
            if (revision != censusRevision.get()) {
                return;
            }
            List<MapCensus.Share> shares = countSafely(engine, sight, counted);
            EventQueue.invokeLater(() -> {
                if (worldRevision == samplingRevision.get()) {
                    showCensus(counted, shares);
                }
            });
        });
    }

    // A count of a view the camera has since left still beats an empty legend, as long as it counts what the ground shows.
    private void showCensus(WorldColoring counted, List<MapCensus.Share> shares) {
        if (!closed && counted == coloring) {
            legend.show(counted.label() + " in view", shares, null);
        }
    }

    private static List<MapCensus.Share> countSafely(Engine engine, GroundSight sight, WorldColoring coloring) {
        try {
            return count(engine, sight, coloring);
        } catch (RuntimeException error) {
            IrisLogging.reportError(error);
            return List.of();
        }
    }

    private static List<MapCensus.Share> count(Engine engine, GroundSight sight, WorldColoring coloring) {
        List<Vec3> spots = sight.groundInView(MOST_CENSUS_SPOTS, surfaceOf(engine));
        Map<WorldColoring.WorldSubject, Long> counts = spots.stream()
                .map(spot -> coloring.subjectAt(engine, spot.x(), spot.z()))
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        List<MapCensus.Share> shares = new ArrayList<>(counts.size());
        short id = 0;
        for (Map.Entry<WorldColoring.WorldSubject, Long> entry : counts.entrySet()) {
            WorldColoring.WorldSubject subject = entry.getKey();
            MapLabels.Label label = new MapLabels.Label(id++, subject.kind(), subject.key(), subject.name(), subject);
            shares.add(new MapCensus.Share(label, subject.color(), entry.getValue() / (double) total));
        }
        shares.sort(Comparator.comparingDouble(MapCensus.Share::fraction).reversed().thenComparing(share -> share.label().name()));
        return shares;
    }

    private void refreshStatus() {
        statusBar.showSummary(summary());
        int pending = terrainView.pendingTiles();
        statusBar.showActivity(pending == 0 ? "" : "Sampling " + pending + (pending == 1 ? " tile" : " tiles"));
        statusBar.showPosition(pointedAt == null ? CAMERA_HINT : pointedAt);
    }

    private String summary() {
        int detailStep = terrainView.detailStep();
        if (detailStep == 0) {
            return coloring.label() + " · sampling";
        }
        return coloring.label() + " · " + (detailStep == 1 ? "full detail" : "1 column per " + detailStep + " blocks");
    }

    private static ExecutorService singleThread(String name) {
        return Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, name);
            thread.setDaemon(true);
            return thread;
        });
    }

    private static Timer singleShot(int delayMillis, Runnable action) {
        Timer timer = new Timer(delayMillis, event -> action.run());
        timer.setRepeats(false);
        return timer;
    }

    private record WorldSampleRequest(long revision, Engine engine, double centerX, double centerZ) {
    }
}
