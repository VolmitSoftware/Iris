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

import com.formdev.flatlaf.FlatClientProperties;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.iris.generation.noise.IrisShapedGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.volmlib.util.function.NoiseProvider;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.stream.ProceduralStream;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.AlphaComposite;
import java.awt.Composite;
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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleUnaryOperator;
import java.util.function.LongFunction;

public final class NoiseExplorerGUI extends JPanel {
    private static final long serialVersionUID = 2094606939770332040L;
    private static final Color GRID = new Color(255, 255, 255, 12);
    private static final Color CROSSHAIR = new Color(255, 255, 255, 125);
    private static final int SIDEBAR_WIDTH = 270;
    private static final int LEGEND_WIDTH = 190;
    private static final int REFINEMENT_DELAY_MILLIS = 180;
    private static final long DEFAULT_SEED = 12345L;
    private static final double TEXTURE_PIXELS_MAX_BLOCKS_PER_PIXEL = 0.25D;
    private static final int FULL_DETAIL_SAMPLE_STEP = 1;
    private static final double PALETTE_START_BLOCKS_PER_PIXEL = 1D / 16D;
    private static final long COARSE_SAMPLE_BUDGET = 4_096L;
    private static final double REFINEMENT_TARGET_MILLIS = 250D;
    private static final double PALETTE_REFINEMENT_TARGET_MILLIS = 2_000D;
    private static final double INTERACTIVE_FRAME_TARGET_MILLIS = 33D;
    private static final int RESIZE_SETTLE_MILLIS = 45;
    private static final double WHEEL_ZOOM_EXPONENT = 0.08D;
    private static final int MOST_TERRAIN_WORKERS = 8;
    private static final String OVERLAY_NOISE_ID = "dimension:overlay";
    private static final String TERRAIN_CAMERA_HINT = "Drag to orbit · Right-drag to pan · Scroll to zoom · R resets";
    private static final NoiseCategory[] CATEGORY_ORDER = {
            NoiseCategory.CUSTOM,
            NoiseCategory.PACK_GENERATORS,
            NoiseCategory.PACK_PALETTES,
            NoiseCategory.SIMPLEX,
            NoiseCategory.PERLIN,
            NoiseCategory.CELLULAR,
            NoiseCategory.IRIS,
            NoiseCategory.CLOVER,
            NoiseCategory.HEXAGON,
            NoiseCategory.VASCULAR,
            NoiseCategory.GLOBE,
            NoiseCategory.CUBIC,
            NoiseCategory.FRACTAL,
            NoiseCategory.STATIC,
            NoiseCategory.NOWHERE,
            NoiseCategory.SIERPINSKI,
            NoiseCategory.UTILITY,
            NoiseCategory.OTHER
    };

    private final ExplorerMode mode;
    private final PaletteExplorerStart paletteStart;
    private final NoiseViewport initialViewport;
    private volatile Engine activeEngine;
    private final LongFunction<NoiseProvider> customFactory;
    private final String customName;
    private final List<SourceItem> fixedSources;
    private final ThreadPoolExecutor sourceExecutor;
    private final ThreadPoolExecutor catalogExecutor;
    private final NoiseRenderCoordinator renderCoordinator;
    private final Timer refinementTimer;
    private final Timer resizeTimer;
    private final FrameCrossfade crossfade;
    private final AtomicLong sourceLoadRevision = new AtomicLong();
    private final AtomicLong catalogLoadRevision = new AtomicLong();
    private final Runnable hotloadHook = this::handleHotload;
    private final DefaultListModel<ListItem> sourceModel = new DefaultListModel<>();
    private final JList<ListItem> sourceList = new JList<>(sourceModel);
    private final JTextField searchField = new JTextField();
    private final JTextField seedField = new JTextField(9);
    private final JLabel selectedLabel = new JLabel("No source selected");
    private final JLabel catalogLabel = new JLabel("Pack catalog waiting");
    private final JComboBox<NoisePalette> palettePicker = new JComboBox<>(NoisePalette.values());
    private final StudioStatusBar statusBar = new StudioStatusBar();
    private final PaletteInspectorPanel paletteInspector = new PaletteInspectorPanel(new PaletteInspectorListener());
    private final PaletteNavigator paletteNavigator;
    private final OrbitViewport terrainViewport = new OrbitViewport("Iris Noise Terrain Viewer", null);
    private final int terrainWorkerCount = Math.min(MOST_TERRAIN_WORKERS, Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
    private final ThreadPoolExecutor terrainWorkers;
    private final ThreadPoolExecutor terrainSamplers;
    private final NoiseTerrainView terrainView;
    private final Map<NoiseDisplay, JToggleButton> displayButtons = new EnumMap<>(NoiseDisplay.class);
    private final TerrainHeightSlider heightSlider = new TerrainHeightSlider(heightBlocks -> requestTerrain());
    private final JComponent terrainPane = heightSlider.floatingOver(terrainViewport);
    private PlayerLocationTracker locationTracker;
    private volatile BlockTextures blockTextures;

    private List<SourceItem> packSources = List.of();
    private NoiseViewport viewport;
    private NoisePalette palette;
    private SourceItem selectedSource;
    private NoiseProvider sampler;
    private BlockRange samplerBlockRange;
    private NoiseDisplay display = NoiseDisplay.FLAT;
    private PaletteLayerPreview palettePreview;
    private int paletteDepth;
    private NoiseRenderCoordinator.Result renderResult;
    private NoiseRenderCoordinator.Result lastTimedFrame;
    private NoiseRenderCoordinator.Request interactiveFrame;
    private boolean interactiveFramePending;
    private Point pointer;
    private Point dragPoint;
    private long seed;
    private long viewRevision;
    private int lastSourceRow = -1;
    private boolean catalogLoading;
    private boolean sourceLoading;
    private boolean rendering;
    private boolean disposed;
    private boolean awaitingInitialPackSource;
    private String requestedPaletteBiome;
    private String errorMessage;

    private NoiseExplorerGUI(ExplorerMode mode, Engine engine, String customGeneratorKey, IrisGenerator fallbackGenerator,
                             String customName, long initialSeed, PaletteExplorerStart paletteStart) {
        this.mode = mode;
        this.paletteStart = paletteStart;
        this.paletteNavigator = new PaletteNavigator(this::selectPalette);
        this.awaitingInitialPackSource = mode == ExplorerMode.PALETTES;
        this.requestedPaletteBiome = paletteStart == null ? null : paletteStart.biomeKey();
        this.initialViewport = paletteStart == null
                ? new NoiseViewport(0D, 0D, 1D)
                : new NoiseViewport(paletteStart.centerX(), paletteStart.centerZ(), PALETTE_START_BLOCKS_PER_PIXEL);
        this.viewport = initialViewport;
        this.activeEngine = engine;
        this.customFactory = fallbackGenerator == null
                ? null
                : sourceSeed -> createGeneratorSampler(customGeneratorKey, fallbackGenerator, sourceSeed);
        this.customName = customName;
        this.seed = initialSeed;
        this.seedField.setText(Long.toString(initialSeed));
        this.palette = IrisSettings.get().getGui().colorMode && mode == ExplorerMode.NOISE ? NoisePalette.TERRAIN : NoisePalette.GRAYSCALE;
        this.sourceExecutor = createExecutor("Iris Noise Source Loader");
        this.catalogExecutor = createExecutor("Iris Noise Catalog Loader");
        this.renderCoordinator = new NoiseRenderCoordinator(new RenderListener());
        this.terrainWorkers = createWorkers("Iris Noise Terrain Worker", terrainWorkerCount);
        this.terrainSamplers = createWorkers("Iris Noise Terrain Sampler", terrainWorkerCount);
        this.terrainView = new NoiseTerrainView(terrainViewport, terrainWorkers, terrainSamplers, terrainWorkerCount);
        this.terrainView.onProgress(this::refreshStatus);
        MapHost.interruptOnPluginShutdown(sourceExecutor);
        MapHost.interruptOnPluginShutdown(catalogExecutor);
        MapHost.interruptOnPluginShutdown(terrainWorkers);
        MapHost.interruptOnPluginShutdown(terrainSamplers);
        this.refinementTimer = new Timer(REFINEMENT_DELAY_MILLIS, event -> requestRefinement());
        this.refinementTimer.setRepeats(false);
        this.resizeTimer = new Timer(RESIZE_SETTLE_MILLIS, event -> requestPreview());
        this.resizeTimer.setRepeats(false);
        this.crossfade = new FrameCrossfade(this::repaint);
        this.fixedSources = buildFixedSources();
        setBackground(StudioTheme.EDITOR_BACKGROUND);
        setFocusable(true);
        setLayout(new BorderLayout());
        terrainPane.setVisible(false);
        add(terrainPane, BorderLayout.CENTER);
        installInputHandlers();
        catalogLabel.setFont(StudioTheme.smallFont());
        catalogLabel.setForeground(StudioTheme.SECONDARY_TEXT);
        statusBar.showActivity(mode == ExplorerMode.PALETTES ? "Opening palettes..." : "Opening noise explorer...");
        MapHost.registerHotloadHook(hotloadHook);
    }

    public static void launchGeneratorKey(Engine engine, String generatorKey, IrisGenerator fallbackGenerator, long initialSeed) {
        if (!MapHost.isAvailable()) {
            return;
        }
        Engine sourceEngine = engine != null ? engine : MapHost.findActiveEngine();
        String displayName = generatorKey == null || generatorKey.isBlank() ? "Custom Generator" : generatorKey;
        EventQueue.invokeLater(() -> {
            StudioTheme.install();
            NoiseExplorerGUI explorer = new NoiseExplorerGUI(
                    ExplorerMode.NOISE,
                    sourceEngine,
                    generatorKey,
                    fallbackGenerator,
                    displayName,
                    initialSeed,
                    null
            );
            buildNoiseFrame("Noise Explorer: " + displayName, explorer);
        });
    }

    // The palette explorer has no window of its own: the map window shows it as its Palettes view.
    static NoiseExplorerGUI createPaletteExplorer(PaletteExplorerStart start) {
        return new NoiseExplorerGUI(ExplorerMode.PALETTES, start.engine(), null, null, null, DEFAULT_SEED, start);
    }

    // The map window shows the noise explorer as its Noise view.
    static NoiseExplorerGUI createNoiseExplorer(Engine engine) {
        return new NoiseExplorerGUI(ExplorerMode.NOISE, engine, null, null, null, DEFAULT_SEED, null);
    }

    private static void buildNoiseFrame(String title, NoiseExplorerGUI explorer) {
        JFrame frame = MapHost.createFrame(title);
        frame.setLayout(new BorderLayout());
        frame.add(explorer.buildNoiseToolbar(), BorderLayout.NORTH);
        JComponent sidebar = explorer.noiseSidebar();
        sidebar.setPreferredSize(StudioTheme.scaledSize(SIDEBAR_WIDTH, 0));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, StudioTheme.SEPARATOR));
        frame.add(sidebar, BorderLayout.WEST);
        frame.add(explorer, BorderLayout.CENTER);
        frame.add(explorer.statusBar, BorderLayout.SOUTH);
        frame.getRootPane().getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('R'), "reset-view");
        frame.getRootPane().getActionMap().put("reset-view", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                explorer.resetViewport();
            }
        });
        frame.setSize(MapHost.fitToScreen(StudioTheme.scaledSize(1440, 860)));
        frame.setMinimumSize(StudioTheme.scaledSize(760, 500));
        frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                explorer.close();
            }

            @Override
            public void windowClosed(WindowEvent event) {
                explorer.close();
            }
        });
        frame.setVisible(true);
        explorer.openViewer();
    }

    private static ThreadPoolExecutor createExecutor(String name) {
        AtomicInteger threadId = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, name + " " + threadId.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1,
                1,
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                threadFactory,
                new ThreadPoolExecutor.DiscardOldestPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    static ThreadPoolExecutor createWorkers(String name, int count) {
        AtomicInteger threadId = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, name + " " + threadId.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        };
        ThreadPoolExecutor workers = new ThreadPoolExecutor(count, count, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), threadFactory);
        workers.allowCoreThreadTimeOut(true);
        return workers;
    }

    private JToolBar buildNoiseToolbar() {
        JToolBar toolbar = StudioToolbar.create();
        noiseControls().forEach(toolbar::add);
        toolbar.add(Box.createHorizontalGlue());
        toolbar.add(StudioToolbar.button("Reset view", "Go back to the starting position and zoom (R)", this::resetViewport));
        return toolbar;
    }

    List<JComponent> noiseControls() {
        selectedLabel.setFont(StudioTheme.boldFont());
        selectedLabel.setBorder(StudioTheme.emptyBorder(0, 4, 0, 10));
        seedField.setMaximumSize(seedField.getPreferredSize());
        seedField.addActionListener(event -> applySeed());
        palettePicker.setSelectedItem(palette);
        palettePicker.setMaximumSize(palettePicker.getPreferredSize());
        palettePicker.addActionListener(event -> {
            NoisePalette nextPalette = (NoisePalette) palettePicker.getSelectedItem();
            if (nextPalette != null && nextPalette != palette) {
                palette = nextPalette;
                requestPreview();
            }
        });
        List<JComponent> controls = new ArrayList<>(List.of(
                selectedLabel,
                new JToolBar.Separator(),
                StudioToolbar.label("Seed"),
                seedField,
                StudioToolbar.button("Apply", "Use this seed", this::applySeed),
                new JToolBar.Separator(),
                StudioToolbar.label("Colours"),
                palettePicker,
                new JToolBar.Separator()
        ));
        controls.addAll(displayButtons());
        refreshTerrainControls();
        return controls;
    }

    private List<JToggleButton> displayButtons() {
        ButtonGroup group = new ButtonGroup();
        for (NoiseDisplay choice : NoiseDisplay.values()) {
            JToggleButton button = StudioToolbar.toggle(choice.label(), choice.description());
            button.setSelected(choice == display);
            button.addActionListener(event -> showAs(choice));
            group.add(button);
            displayButtons.put(choice, button);
        }
        return List.copyOf(displayButtons.values());
    }

    // Noise already measured in blocks keeps its own heights, so only the 3D views of other noise can be raised.
    private void refreshTerrainControls() {
        heightSlider.setUsable(samplerBlockRange == null);
    }

    JComponent noiseSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        StudioToolbar.makeSearchField(searchField, "Search noise...");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                populateSourceList();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                populateSourceList();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                populateSourceList();
            }
        });
        // Down or Enter in the search box jumps to the results, so the list can be searched without the mouse.
        searchField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "focus-results");
        searchField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "focus-results");
        searchField.getActionMap().put("focus-results", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                focusFirstSource();
            }
        });

        sourceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sourceList.setCellRenderer(new SourceCellRenderer());
        sourceList.addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) {
                return;
            }
            if (skipHeaderRow()) {
                return;
            }
            ListItem item = sourceList.getSelectedValue();
            lastSourceRow = sourceList.getSelectedIndex();
            if (item != null && item.source() != null) {
                selectSource(item.source());
            }
        });

        sidebar.add(StudioToolbar.padded(searchField, 8, 8, 8, 8), BorderLayout.NORTH);
        sidebar.add(StudioToolbar.scroll(sourceList), BorderLayout.CENTER);
        sidebar.add(StudioToolbar.padded(catalogLabel, 6, 12, 8, 12), BorderLayout.SOUTH);
        populateSourceList();
        return sidebar;
    }

    JComponent paletteSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.add(paletteNavigator, BorderLayout.CENTER);
        sidebar.add(StudioToolbar.padded(catalogLabel, 6, 12, 8, 12), BorderLayout.SOUTH);
        return sidebar;
    }

    PaletteInspectorPanel paletteInspector() {
        return paletteInspector;
    }

    StudioStatusBar statusBar() {
        return statusBar;
    }

    double centerX() {
        return viewport.centerX();
    }

    double centerZ() {
        return viewport.centerZ();
    }

    void centerOn(double worldX, double worldZ) {
        viewport = new NoiseViewport(worldX, worldZ, viewport.blocksPerPixel());
        requestPreview();
    }

    // Jumps to a spot and opens the palette of the biome there, once the catalog has loaded if it hasn't yet.
    void showPalettesAt(double worldX, double worldZ, String biomeKey) {
        centerOn(worldX, worldZ);
        if (biomeKey == null) {
            return;
        }
        requestedPaletteBiome = biomeKey;
        SourceItem firstPalette = firstPaletteOf(biomeKey, packSources);
        if (firstPalette != null && firstPalette != selectedSource) {
            selectSource(firstPalette);
        }
    }

    // Header rows can't be chosen, so moving onto one with the arrow keys carries on to the next real source.
    private boolean skipHeaderRow() {
        int row = sourceList.getSelectedIndex();
        if (row < 0 || sourceModel.get(row).source() != null) {
            return false;
        }
        int step = row >= lastSourceRow ? 1 : -1;
        int next = nextSourceRow(row, step);
        if (next < 0) {
            next = nextSourceRow(row, -step);
        }
        if (next < 0) {
            sourceList.clearSelection();
        } else {
            sourceList.setSelectedIndex(next);
            sourceList.ensureIndexIsVisible(next);
        }
        return true;
    }

    private int nextSourceRow(int from, int step) {
        for (int row = from + step; row >= 0 && row < sourceModel.size(); row += step) {
            if (sourceModel.get(row).source() != null) {
                return row;
            }
        }
        return -1;
    }

    private void focusFirstSource() {
        int row = sourceList.getSelectedIndex() >= 0 ? sourceList.getSelectedIndex() : nextSourceRow(-1, 1);
        if (row < 0) {
            return;
        }
        sourceList.setSelectedIndex(row);
        sourceList.ensureIndexIsVisible(row);
        sourceList.requestFocusInWindow();
    }

    void openViewer() {
        loadCatalog();
        if (mode == ExplorerMode.PALETTES) {
            loadBlockTextures();
            if (paletteStart != null && paletteStart.playerId() != null) {
                locationTracker = PlayerLocationTracker.follow(() -> activeEngine,
                        MapHost.overlayFor(activeEngine, paletteStart.playerId()), paletteNavigator::showLocation);
            } else {
                paletteNavigator.showUntracked();
            }
        }
        SourceItem initial = findInitialSource();
        if (initial != null && !selectSourceInList(initial.id())) {
            selectSource(initial);
        }
    }

    private SourceItem findInitialSource() {
        if (customFactory != null) {
            for (SourceItem source : fixedSources) {
                if (source.category() == NoiseCategory.CUSTOM) {
                    return source;
                }
            }
        }
        for (SourceItem source : fixedSources) {
            if (source.id().equals("style:SIMPLEX")) {
                return source;
            }
        }
        return fixedSources.isEmpty() ? null : fixedSources.get(0);
    }

    private List<SourceItem> buildFixedSources() {
        if (mode == ExplorerMode.PALETTES) {
            return List.of();
        }
        List<SourceItem> sources = new ArrayList<>();
        if (customFactory != null && customName != null) {
            sources.add(new SourceItem(
                    "custom:" + customName,
                    customName,
                    customName,
                    NoiseCategory.CUSTOM,
                    true,
                    (sourceSeed, depth) -> LoadedSource.of(customFactory.apply(sourceSeed))
            ));
        }
        for (NoiseStyle style : NoiseStyle.values()) {
            sources.add(new SourceItem(
                    "style:" + style.name(),
                    formatName(style.name()),
                    style.name(),
                    categorize(style),
                    true,
                    (sourceSeed, depth) -> {
                        CNG noise = style.create(new RNG(sourceSeed));
                        return LoadedSource.of((x, z) -> noise.noise(x, z));
                    }
            ));
        }
        return List.copyOf(sources);
    }

    private void loadCatalog() {
        if (disposed) {
            return;
        }
        long revision = catalogLoadRevision.incrementAndGet();
        catalogLoading = true;
        catalogLabel.setForeground(StudioTheme.SECONDARY_TEXT);
        catalogLabel.setText("Loading pack catalog...");
        submitLatest(catalogExecutor, () -> {
            try {
                Engine engine = resolveEngine();
                List<SourceItem> loadedSources = loadPackSources(engine);
                PaletteCatalog paletteCatalog = mode == ExplorerMode.PALETTES && engine != null ? PaletteCatalog.load(engine) : null;
                EventQueue.invokeLater(() -> applyCatalog(revision, loadedSources, paletteCatalog, null));
            } catch (Throwable error) {
                IrisLogging.reportError(error);
                EventQueue.invokeLater(() -> applyCatalog(revision, List.of(), null, error));
            }
        });
    }

    private Engine resolveEngine() {
        Engine engine = activeEngine;
        return engine != null && !engine.isClosed() ? engine : null;
    }

    private NoiseProvider createGeneratorSampler(String generatorKey, IrisGenerator fallback, long sourceSeed) {
        IrisGenerator generator = fallback;
        Engine engine = resolveEngine();
        if (engine != null && generatorKey != null && !generatorKey.isBlank()) {
            IrisGenerator refreshedGenerator = engine.getData().getGeneratorLoader().load(generatorKey);
            if (refreshedGenerator != null) {
                generator = refreshedGenerator;
            }
        }
        long mixedSeed = new RNG(sourceSeed).nextParallelRNG(3245).lmax();
        IrisGenerator selectedGenerator = generator;
        return (x, z) -> selectedGenerator.getHeight(x, z, mixedSeed);
    }

    private List<SourceItem> loadPackSources(Engine engine) {
        if (engine == null) {
            return List.of();
        }
        return switch (mode) {
            case NOISE -> loadPackGeneratorSources(engine);
            case PALETTES -> loadPackPaletteSources(engine);
        };
    }

    private static List<SourceItem> loadPackGeneratorSources(Engine engine) {
        List<SourceItem> sources = new ArrayList<>();
        SourceItem overlayNoise = overlayNoiseSource(engine);
        if (overlayNoise != null) {
            sources.add(overlayNoise);
        }
        IrisData data = engine.getData();
        String[] keys = data.getGeneratorLoader().getPossibleKeys();
        if (keys == null || keys.length == 0) {
            return List.copyOf(sources);
        }
        Arrays.sort(keys);
        for (String key : keys) {
            sources.add(new SourceItem(
                    "pack:" + key,
                    formatName(key),
                    key,
                    NoiseCategory.PACK_GENERATORS,
                    true,
                    (sourceSeed, depth) -> {
                        IrisGenerator generator = data.getGeneratorLoader().load(key);
                        if (generator == null) {
                            throw new IllegalStateException("Pack generator not found: " + key);
                        }
                        long mixedSeed = new RNG(sourceSeed).nextParallelRNG(3245).lmax();
                        return LoadedSource.of((x, z) -> generator.getHeight(x, z, mixedSeed));
                    }
            ));
        }
        return List.copyOf(sources);
    }

    /**
     * The dimension's overlay noise, summed the way the world adds it: heights in blocks above the fluid, with the
     * world's own seed. In hybrid terrain it is the base terrain itself.
     */
    private static SourceItem overlayNoiseSource(Engine engine) {
        List<IrisShapedGeneratorStyle> overlay = engine.getDimension().getOverlayNoise();
        if (overlay.isEmpty()) {
            return null;
        }
        BlockRange range = new BlockRange(
                overlay.stream().mapToInt(IrisShapedGeneratorStyle::getMin).sum(),
                overlay.stream().mapToInt(IrisShapedGeneratorStyle::getMax).sum());
        return new SourceItem(OVERLAY_NOISE_ID, "Overlay noise", "overlay noise", NoiseCategory.PACK_GENERATORS, false,
                (sourceSeed, depth) -> {
                    ProceduralStream<Double> stream = engine.getComplex().getOverlayStream();
                    return new LoadedSource(stream::get, null, range);
                });
    }

    // Palette layers always use the world's terrain seed: they share their cached noise with the running world.
    private List<SourceItem> loadPackPaletteSources(Engine engine) {
        IrisData data = engine.getData();
        String[] keys = data.getBiomeLoader().getPossibleKeys();
        if (keys == null || keys.length == 0) {
            return List.of();
        }
        Arrays.sort(keys);
        List<SourceItem> sources = new ArrayList<>();
        for (String key : keys) {
            IrisBiome biome = data.getBiomeLoader().load(key);
            if (biome == null) {
                continue;
            }
            for (PaletteCatalog.PaletteSummary summary : PaletteCatalog.summarizePalettes(key, biome)) {
                BiomePalette palette = summary.palette();
                sources.add(new SourceItem(
                        palette.id(),
                        summary.text(),
                        key,
                        NoiseCategory.PACK_PALETTES,
                        false,
                        (sourceSeed, depth) -> {
                            PaletteLayerPreview preview = PaletteLayerPreview.load(engine, palette, depth, blockTextures);
                            return new LoadedSource(preview::sample, preview, null);
                        }
                ));
            }
        }
        return List.copyOf(sources);
    }

    private static SourceItem firstPaletteOf(String biomeKey, List<SourceItem> sources) {
        return sources.stream().filter(source -> BiomePalette.belongsTo(source.id(), biomeKey)).findFirst().orElse(null);
    }

    private void selectPalette(BiomePalette palette) {
        SourceItem source = findSourceById(palette.id(), packSources);
        if (source != null && source != selectedSource) {
            selectSource(source);
        }
    }

    private static BiomePalette paletteOf(SourceItem source) {
        return BiomePalette.fromId(source.id());
    }

    private void loadBlockTextures() {
        statusBar.showActivity(MinecraftClientJar.isCached() ? "Loading block textures..." : "Downloading block textures...");
        BlockTextures.shared().whenComplete((textures, error) -> EventQueue.invokeLater(() -> applyBlockTextures(textures, error)));
    }

    private void applyBlockTextures(BlockTextures textures, Throwable error) {
        if (disposed) {
            return;
        }
        if (error != null) {
            Throwable reason = error.getCause() == null ? error : error.getCause();
            statusBar.showError("Block textures unavailable: " + readableError(reason));
            return;
        }
        blockTextures = textures;
        statusBar.showActivity("");
        // The first layer may still be loading without textures, so it has to be reloaded even before it shows.
        if (selectedSource != null) {
            loadSelectedSource();
        }
    }

    private void applyCatalog(long revision, List<SourceItem> sources, PaletteCatalog paletteCatalog, Throwable error) {
        if (disposed || revision != catalogLoadRevision.get()) {
            return;
        }
        catalogLoading = false;
        packSources = sources;
        if (paletteCatalog != null) {
            paletteNavigator.showCatalog(paletteCatalog);
        }
        if (error != null) {
            catalogLabel.setForeground(StudioTheme.ERROR);
            catalogLabel.setText("Pack catalog unavailable");
        } else {
            catalogLabel.setForeground(StudioTheme.SECONDARY_TEXT);
            catalogLabel.setText(catalogSummary(sources));
        }
        boolean reloadSelectedPack = false;
        if (error == null
                && selectedSource != null
                && selectedSource.category().isFromPack()) {
            SourceItem replacement = findSourceById(selectedSource.id(), sources);
            if (replacement == null) {
                failRemovedPackSource(selectedSource);
            } else {
                selectedSource = replacement;
                reloadSelectedPack = true;
            }
        }
        populateSourceList();
        if (reloadSelectedPack) {
            loadSelectedSource();
        }
        if (error == null && awaitingInitialPackSource && !sources.isEmpty()) {
            awaitingInitialPackSource = false;
            selectInitialPackSource(sources);
        }
    }

    private void selectInitialPackSource(List<SourceItem> sources) {
        SourceItem requested = requestedPaletteBiome == null ? null : firstPaletteOf(requestedPaletteBiome, sources);
        SourceItem initial = requested == null ? sources.get(0) : requested;
        if (!selectSourceInList(initial.id())) {
            selectSource(initial);
        }
    }

    private String catalogSummary(List<SourceItem> sources) {
        if (mode == ExplorerMode.NOISE) {
            return sources.isEmpty() ? "No active pack generators" : sources.size() + " pack generators";
        }
        if (resolveEngine() == null) {
            return "Open a studio world to see its palettes";
        }
        long biomes = sources.stream().map(SourceItem::rawName).distinct().count();
        return sources.size() + " palettes in " + biomes + " biomes";
    }

    private void failRemovedPackSource(SourceItem removedSource) {
        sourceLoadRevision.incrementAndGet();
        refinementTimer.stop();
        viewRevision++;
        renderCoordinator.cancel(viewRevision);
        selectedSource = null;
        clearSampler();
        renderResult = null;
        crossfade.clear();
        sourceLoading = false;
        rendering = false;
        errorMessage = "Pack source removed during hotload: " + removedSource.text();
        selectedLabel.setText("No source selected");
        sourceList.clearSelection();
        refreshStatus();
        repaint();
    }

    private void populateSourceList() {
        if (!EventQueue.isDispatchThread()) {
            EventQueue.invokeLater(this::populateSourceList);
            return;
        }
        if (mode == ExplorerMode.PALETTES) {
            return;
        }
        String selectedId = selectedSource == null ? null : selectedSource.id();
        String filter = searchField.getText().trim().toLowerCase(Locale.ROOT);
        sourceModel.clear();
        for (NoiseCategory category : CATEGORY_ORDER) {
            List<SourceItem> matching = matchingSources(category, filter);
            if (matching.isEmpty()) {
                continue;
            }
            sourceModel.addElement(new ListItem(categoryLabel(category), null));
            for (SourceItem source : matching) {
                sourceModel.addElement(new ListItem(source.text(), source));
            }
        }
        if (selectedId != null) {
            selectSourceInList(selectedId);
        }
    }

    private List<SourceItem> matchingSources(NoiseCategory category, String filter) {
        List<SourceItem> matching = new ArrayList<>();
        addMatchingSources(matching, fixedSources, category, filter);
        addMatchingSources(matching, packSources, category, filter);
        return matching;
    }

    private static void addMatchingSources(List<SourceItem> destination, List<SourceItem> sources,
                                           NoiseCategory category, String filter) {
        for (SourceItem source : sources) {
            if (source.category() != category) {
                continue;
            }
            String searchable = (source.text() + " " + source.rawName()).toLowerCase(Locale.ROOT);
            if (filter.isEmpty() || searchable.contains(filter)) {
                destination.add(source);
            }
        }
    }

    private boolean selectSourceInList(String sourceId) {
        for (int index = 0; index < sourceModel.size(); index++) {
            ListItem item = sourceModel.get(index);
            if (item.source() != null && item.source().id().equals(sourceId)) {
                sourceList.setSelectedIndex(index);
                sourceList.ensureIndexIsVisible(index);
                return true;
            }
        }
        return false;
    }

    private void selectSource(SourceItem source) {
        if (disposed || source == null || (source == selectedSource && (sampler != null || sourceLoading))) {
            return;
        }
        selectedSource = source;
        paletteDepth = 0;
        selectedLabel.setText(source.category() == NoiseCategory.PACK_PALETTES ? source.rawName() : source.text());
        if (source.category() == NoiseCategory.PACK_PALETTES) {
            paletteNavigator.markSelected(paletteOf(source));
        }
        loadSelectedSource();
    }

    private void loadSelectedSource() {
        SourceItem source = selectedSource;
        if (disposed || source == null) {
            return;
        }
        long revision = sourceLoadRevision.incrementAndGet();
        long requestedSeed = seed;
        int requestedDepth = paletteDepth;
        refinementTimer.stop();
        viewRevision++;
        renderCoordinator.cancel(viewRevision);
        sampler = null;
        crossfade.hold(renderResult);
        renderResult = null;
        sourceLoading = true;
        rendering = false;
        errorMessage = null;
        if (display.showsTerrain()) {
            terrainView.hide();
            terrainViewport.showLoading("Loading " + source.text());
        }
        refreshStatus();
        repaint();
        submitLatest(sourceExecutor, () -> {
            try {
                LoadedSource loaded = source.factory().create(requestedSeed, requestedDepth);
                if (loaded == null || loaded.sampler() == null) {
                    throw new IllegalStateException("Noise source returned no sampler: " + source.rawName());
                }
                EventQueue.invokeLater(() -> applyLoadedSource(revision, source, loaded));
            } catch (Throwable error) {
                IrisLogging.reportError(error);
                EventQueue.invokeLater(() -> applySourceError(revision, source, error));
            }
        });
    }

    private void applyLoadedSource(long revision, SourceItem source, LoadedSource loaded) {
        if (disposed || revision != sourceLoadRevision.get() || selectedSource != source) {
            return;
        }
        sampler = loaded.sampler();
        samplerBlockRange = loaded.blockRange();
        palettePreview = loaded.palettePreview();
        refreshTerrainControls();
        showPaletteInspector();
        sourceLoading = false;
        errorMessage = null;
        renderResult = null;
        refreshStatus();
        requestPreview();
    }

    private void applySourceError(long revision, SourceItem source, Throwable error) {
        if (disposed || revision != sourceLoadRevision.get() || selectedSource != source) {
            return;
        }
        clearSampler();
        crossfade.clear();
        sourceLoading = false;
        rendering = false;
        errorMessage = readableError(error);
        if (display.showsTerrain()) {
            terrainViewport.showMessage("Could not load " + source.text());
        }
        refreshStatus();
        repaint();
    }

    private void clearSampler() {
        sampler = null;
        samplerBlockRange = null;
        palettePreview = null;
        refreshTerrainControls();
        showPaletteInspector();
    }

    private void showPaletteInspector() {
        if (palettePreview == null) {
            paletteInspector.hideInspector();
        } else {
            paletteInspector.showPreview(palettePreview);
        }
        if (paletteInspector.getParent() != null) {
            paletteInspector.getParent().revalidate();
        }
    }

    private NoiseColorScale activeColorScale() {
        return activeColorScale(FULL_DETAIL_SAMPLE_STEP);
    }

    // A coarse frame's sample stands for a whole square of pixels, so texture pixels would show magnified there.
    private NoiseColorScale activeColorScale(int sampleStep) {
        PaletteLayerPreview preview = palettePreview;
        if (preview == null) {
            BlockRange blockRange = samplerBlockRange;
            return blockRange == null ? palette : new BlockHeightColorScale(palette, blockRange.lowest(), blockRange.highest());
        }
        boolean showTexturePixels = viewport.blocksPerPixel() * sampleStep <= TEXTURE_PIXELS_MAX_BLOCKS_PER_PIXEL;
        return new PaletteLayerScale(paletteInspector.selectedView(), preview, palette, showTexturePixels);
    }

    private void applySeed() {
        String rawSeed = seedField.getText().trim();
        try {
            long nextSeed = Long.parseLong(rawSeed);
            seedField.putClientProperty(FlatClientProperties.OUTLINE, null);
            if (nextSeed == seed) {
                return;
            }
            seed = nextSeed;
            if (selectedSource != null && selectedSource.usesSeed()) {
                loadSelectedSource();
            }
        } catch (NumberFormatException error) {
            seedField.putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_ERROR);
            statusBar.showError("Seed must be a whole number");
        }
    }

    private void requestPreview() {
        if (display.showsTerrain()) {
            requestTerrain();
            return;
        }
        if (disposed || sampler == null || getWidth() < 1 || renderHeight() < 1) {
            repaint();
            return;
        }
        refinementTimer.stop();
        viewRevision++;
        int sampleStep = NoiseRenderCoordinator.sampleStepForBudget(
                getWidth(),
                renderHeight(),
                interactiveSampleBudget()
        );
        interactiveFrame = requestRender(sampleStep);
    }

    private void showAs(NoiseDisplay nextDisplay) {
        if (nextDisplay == display) {
            return;
        }
        display = nextDisplay;
        displayButtons.get(nextDisplay).setSelected(true);
        terrainPane.setVisible(nextDisplay.showsTerrain());
        refreshTerrainControls();
        revalidate();
        if (!nextDisplay.showsTerrain()) {
            terrainView.hide();
            requestFocusInWindow();
            requestPreview();
            refreshStatus();
            return;
        }
        // Turns around the middle of the flat view, so switching to 3D shows the same stretch of noise.
        terrainView.centerOn(viewport.centerX(), viewport.centerZ());
        requestTerrain();
        terrainViewport.requestFocusInWindow();
        refreshStatus();
    }

    private void requestTerrain() {
        NoiseProvider terrainNoise = sampler;
        if (disposed || !display.showsTerrain() || terrainNoise == null) {
            return;
        }
        double waterLevel = samplerBlockRange == null ? Double.NaN : 0D;
        TerrainSampling sampling = TerrainSampling.ofNoise(terrainNoise, terrainHeights(), activeColorScale(), waterLevel);
        if (!terrainViewport.isShowingScene()) {
            terrainViewport.showLoading("Sampling " + (selectedSource == null ? "noise" : selectedSource.text()));
        }
        terrainView.show(sampling);
    }

    // Noise measured in blocks is its own height; any other noise is stretched from its colour range up to the chosen height.
    private DoubleUnaryOperator terrainHeights() {
        if (samplerBlockRange != null) {
            return value -> value;
        }
        NoiseColorScale colorScale = activeColorScale();
        double lowest = colorScale.minimum();
        double span = colorScale.maximum() - lowest;
        double heightBlocks = heightSlider.blocks();
        return value -> (value - lowest) / span * heightBlocks;
    }

    /**
     * Keeps the 3D views clear of a panel floating over the explorer's left side.
     */
    void setCoveredLeftWidth(int width) {
        terrainViewport.setCoveredLeftWidth(width);
    }

    // Draws one frame at a time while the view moves, so each frame finishes and the next one starts from where the view is by then.
    private void requestInteractiveFrame() {
        repaint();
        if (isInteractiveFrameInFlight()) {
            interactiveFramePending = true;
            return;
        }
        requestPreview();
    }

    private boolean isInteractiveFrameInFlight() {
        return interactiveFrame != null && interactiveFrame.revision() == viewRevision;
    }

    // The last frame's speed predicts this one, so cheap views draw sharp straight away instead of starting blocky. It
    // outlives a switch to another source, which samples about as fast.
    private long interactiveSampleBudget() {
        NoiseRenderCoordinator.Result lastResult = lastTimedFrame;
        if (lastResult == null) {
            return COARSE_SAMPLE_BUDGET;
        }
        return timedSampleBudget(lastResult, INTERACTIVE_FRAME_TARGET_MILLIS);
    }

    private void requestRefinement() {
        if (disposed || sampler == null || getWidth() < 1 || renderHeight() < 1) {
            return;
        }
        NoiseRenderCoordinator.Result currentResult = renderResult;
        if (currentResult == null || currentResult.request().revision() != viewRevision) {
            return;
        }
        long sampleBudget = refinedSampleBudget(currentResult);
        int sampleStep = NoiseRenderCoordinator.nextRefinementStep(
                getWidth(),
                renderHeight(),
                currentResult.request().sampleStep(),
                sampleBudget
        );
        if (sampleStep >= currentResult.request().sampleStep()) {
            return;
        }
        requestRender(sampleStep);
    }

    // Blocks and their textures are slower to sample, so palettes get longer refinement passes than plain noise.
    private long refinedSampleBudget(NoiseRenderCoordinator.Result currentResult) {
        double targetMilliseconds = mode == ExplorerMode.PALETTES ? PALETTE_REFINEMENT_TARGET_MILLIS : REFINEMENT_TARGET_MILLIS;
        return timedSampleBudget(currentResult, targetMilliseconds);
    }

    private long timedSampleBudget(NoiseRenderCoordinator.Result lastResult, double targetMilliseconds) {
        BufferedImage lastImage = lastResult.image();
        long renderedPixels = (long) lastImage.getWidth() * lastImage.getHeight();
        if (lastResult.milliseconds() <= 0D) {
            return maximumSampleBudget();
        }
        return NoiseRenderCoordinator.timeBoundSampleBudget(
                renderedPixels,
                lastResult.milliseconds(),
                targetMilliseconds,
                COARSE_SAMPLE_BUDGET,
                maximumSampleBudget()
        );
    }

    private long maximumSampleBudget() {
        return Math.max(COARSE_SAMPLE_BUDGET, (long) getWidth() * renderHeight());
    }

    private NoiseRenderCoordinator.Request requestRender(int sampleStep) {
        NoiseRenderCoordinator.Request request = new NoiseRenderCoordinator.Request(
                viewRevision,
                sampler,
                viewport,
                activeColorScale(sampleStep),
                getWidth(),
                renderHeight(),
                sampleStep
        );
        renderCoordinator.request(request);
        return request;
    }

    private void installInputHandlers() {
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                requestFocusInWindow();
                dragPoint = event.getPoint();
                pointer = event.getPoint();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                boolean wasDragging = dragPoint != null;
                dragPoint = null;
                if (wasDragging) {
                    requestInteractiveFrame();
                }
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                Point nextPoint = event.getPoint();
                pointer = nextPoint;
                if (dragPoint != null) {
                    double deltaX = nextPoint.getX() - dragPoint.getX();
                    double deltaZ = nextPoint.getY() - dragPoint.getY();
                    viewport = viewport.panPixels(deltaX, deltaZ);
                    dragPoint = nextPoint;
                    requestInteractiveFrame();
                }
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                pointer = event.getPoint();
                refreshPointerStatus();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                pointer = null;
                refreshPointerStatus();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent event) {
                pointer = event.getPoint();
                double factor = Math.pow(2D, event.getPreciseWheelRotation() * WHEEL_ZOOM_EXPONENT);
                viewport = viewport.zoomAt(
                        event.getX(),
                        Math.min(event.getY(), renderHeight()),
                        getWidth(),
                        renderHeight(),
                        factor
                );
                requestInteractiveFrame();
                event.consume();
            }
        };
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
        addMouseWheelListener(mouseHandler);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                resizeTimer.restart();
            }
        });
    }

    // A hotload replaces the engine; the explorer stays on the world it was opened for.
    private Engine engineAfterHotload() {
        return MapHost.findEngine(paletteStart == null ? null : paletteStart.worldId());
    }

    void resetViewport() {
        if (display.showsTerrain()) {
            terrainViewport.resetCamera();
            return;
        }
        viewport = initialViewport;
        requestPreview();
    }

    private void handleHotload() {
        Engine refreshedEngine = engineAfterHotload();
        EventQueue.invokeLater(() -> {
            if (disposed) {
                return;
            }
            if (refreshedEngine != null && !refreshedEngine.isClosed()) {
                activeEngine = refreshedEngine;
            }
            loadCatalog();
            if (selectedSource != null && selectedSource.category() == NoiseCategory.CUSTOM) {
                loadSelectedSource();
            }
        });
    }

    void close() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (locationTracker != null) {
            locationTracker.stop();
        }
        refinementTimer.stop();
        resizeTimer.stop();
        crossfade.clear();
        MapHost.unregisterHotloadHook(hotloadHook);
        sourceLoadRevision.incrementAndGet();
        catalogLoadRevision.incrementAndGet();
        sourceExecutor.shutdownNow();
        catalogExecutor.shutdownNow();
        renderCoordinator.close();
        terrainView.close();
        terrainWorkers.shutdownNow();
        terrainSamplers.shutdownNow();
        terrainViewport.close();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            canvas.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            int renderHeight = renderHeight();
            drawEmptyState(canvas, getWidth(), renderHeight);
            if (renderResult != null) {
                drawLatestFrame(canvas, renderResult, getWidth(), renderHeight);
            }
            drawOutgoingFrame(canvas, getWidth(), renderHeight);
            drawCrosshair(canvas, getWidth(), renderHeight);
            drawLegend(canvas, getWidth());
        } finally {
            canvas.dispose();
        }
    }

    // Returns true when the view moved while the frame rendered, after starting the frame for where it is now.
    private boolean finishInteractiveFrame(NoiseRenderCoordinator.Request finishedRequest) {
        if (finishedRequest != interactiveFrame) {
            return false;
        }
        interactiveFrame = null;
        if (!interactiveFramePending) {
            return false;
        }
        interactiveFramePending = false;
        requestPreview();
        return true;
    }

    private void drawOutgoingFrame(Graphics2D canvas, int width, int renderHeight) {
        NoiseRenderCoordinator.Result outgoing = crossfade.outgoing();
        if (outgoing == null) {
            return;
        }
        Composite opaque = canvas.getComposite();
        canvas.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, crossfade.outgoingOpacity()));
        drawLatestFrame(canvas, outgoing, width, renderHeight);
        canvas.setComposite(opaque);
    }

    // Moves and scales the last finished frame to the current view, so the map follows the mouse before the next frame lands.
    private void drawLatestFrame(Graphics2D canvas, NoiseRenderCoordinator.Result frame, int width, int renderHeight) {
        NoiseRenderCoordinator.Request request = frame.request();
        BufferedImage image = frame.image();
        NoiseViewport frameViewport = request.viewport();
        double frameScreenWidth = image.getWidth() * (double) request.sampleStep();
        double frameScreenHeight = image.getHeight() * (double) request.sampleStep();
        int left = (int) Math.round(viewport.screenX(frameViewport.worldX(0D, request.width()), width));
        int top = (int) Math.round(viewport.screenZ(frameViewport.worldZ(0D, request.height()), renderHeight));
        int right = (int) Math.round(viewport.screenX(frameViewport.worldX(frameScreenWidth, request.width()), width));
        int bottom = (int) Math.round(viewport.screenZ(frameViewport.worldZ(frameScreenHeight, request.height()), renderHeight));
        Object interpolation = request.sampleStep() == 1 || mode == ExplorerMode.PALETTES
                ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                : RenderingHints.VALUE_INTERPOLATION_BILINEAR;
        canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        canvas.drawImage(image, left, top, right - left, bottom - top, null);
    }

    private static void drawEmptyState(Graphics2D canvas, int width, int height) {
        canvas.setColor(StudioTheme.EDITOR_BACKGROUND);
        canvas.fillRect(0, 0, width, height);
        canvas.setColor(GRID);
        for (int x = 0; x < width; x += 48) {
            canvas.drawLine(x, 0, x, height);
        }
        for (int y = 0; y < height; y += 48) {
            canvas.drawLine(0, y, width, y);
        }
    }

    private static void drawCrosshair(Graphics2D canvas, int width, int height) {
        int centerX = width / 2;
        int centerZ = height / 2;
        canvas.setColor(CROSSHAIR);
        canvas.setStroke(new BasicStroke(1F));
        int armLength = StudioTheme.scale(8);
        canvas.drawLine(centerX - armLength, centerZ, centerX + armLength, centerZ);
        canvas.drawLine(centerX, centerZ - armLength, centerX, centerZ + armLength);
    }

    private void drawLegend(Graphics2D canvas, int width) {
        NoiseColorScale colorScale = activeColorScale();
        canvas.setFont(StudioTheme.smallFont());
        String minimum = formatNumber(colorScale.minimum());
        String maximum = formatNumber(colorScale.maximum());
        String label = colorScale.label();
        int padding = StudioTheme.scale(9);
        int labelGap = StudioTheme.scale(12);
        int gradientHeight = StudioTheme.scale(8);
        int textWidth = canvas.getFontMetrics().stringWidth(minimum) + canvas.getFontMetrics().stringWidth(label)
                + canvas.getFontMetrics().stringWidth(maximum) + labelGap * 2;
        int legendWidth = Math.max(StudioTheme.scale(LEGEND_WIDTH), textWidth + padding * 2);
        int legendHeight = padding * 2 + gradientHeight + StudioTheme.scale(4) + canvas.getFontMetrics().getHeight();
        int legendX = Math.max(StudioTheme.scale(10), width - legendWidth - StudioTheme.scale(12));
        int legendY = StudioTheme.scale(12);
        canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        StudioOverlay.drawCard(canvas, legendX, legendY, legendWidth, legendHeight);
        int gradientX = legendX + padding;
        int gradientY = legendY + padding;
        int gradientWidth = legendWidth - padding * 2;
        for (int x = 0; x < gradientWidth; x++) {
            double normalized = x / (double) Math.max(1, gradientWidth - 1);
            canvas.setColor(colorScale.displayColorNormalized(normalized));
            canvas.drawLine(gradientX + x, gradientY, gradientX + x, gradientY + gradientHeight);
        }
        int baseline = gradientY + gradientHeight + StudioTheme.scale(4) + canvas.getFontMetrics().getAscent();
        canvas.setColor(StudioTheme.TEXT);
        canvas.drawString(minimum, gradientX, baseline);
        canvas.drawString(maximum, gradientX + gradientWidth - canvas.getFontMetrics().stringWidth(maximum), baseline);
        int labelWidth = canvas.getFontMetrics().stringWidth(label);
        canvas.drawString(label, legendX + (legendWidth - labelWidth) / 2, baseline);
    }

    private void refreshStatus() {
        if (errorMessage != null) {
            statusBar.showError("Error: " + errorMessage);
        } else {
            statusBar.showSummary(primaryStatus());
        }
        refreshPointerStatus();
    }

    // Only the status bar changes when the pointer moves, so moving the mouse never redraws the map.
    private void refreshPointerStatus() {
        if (display.showsTerrain()) {
            statusBar.showPosition(TERRAIN_CAMERA_HINT);
            statusBar.showActivity(describeTerrain());
            return;
        }
        int renderHeight = renderHeight();
        Point currentPointer = pointer;
        double screenX = currentPointer == null ? getWidth() / 2D : currentPointer.getX();
        double screenZ = currentPointer == null ? renderHeight / 2D : Math.min(currentPointer.getY(), renderHeight);
        double worldX = viewport.worldX(screenX, getWidth());
        double worldZ = viewport.worldZ(screenZ, renderHeight);
        statusBar.showPosition(String.format(Locale.ROOT, "X %.1f  Z %.1f   %s blocks/px", worldX, worldZ,
                formatScale(viewport.blocksPerPixel())));
        statusBar.showActivity(pointerDetail(worldX, worldZ));
    }

    private String pointerDetail(double worldX, double worldZ) {
        if (catalogLoading && renderResult == null) {
            return "Loading catalog...";
        }
        NoiseRenderCoordinator.Result result = renderResult;
        if (result == null) {
            return "";
        }
        PaletteLayerPreview preview = palettePreview;
        if (preview != null) {
            return describePaletteAt(preview, worldX, worldZ);
        }
        long clipped = result.underflow() + result.overflow();
        return "range " + formatNumber(result.minimum()) + " to " + formatNumber(result.maximum())
                + "   clipped " + clipped + "   invalid " + result.invalid();
    }

    private String describeTerrain() {
        int pending = terrainView.pendingTiles();
        return pending == 0 ? "" : "Sampling " + pending + (pending == 1 ? " tile" : " tiles");
    }

    private String primaryStatus() {
        String sourceName = selectedSource == null ? "No source" : sourceTitle(selectedSource);
        if (sourceLoading) {
            return sourceName + " · loading";
        }
        if (display.showsTerrain()) {
            return terrainStatus(sourceName);
        }
        if (rendering && renderResult == null) {
            return sourceName + " · rendering";
        }
        if (renderResult == null) {
            return sourceName;
        }
        NoiseRenderCoordinator.Result result = renderResult;
        String quality = result.request().sampleStep() == 1
                ? "full detail"
                : "1 sample per " + result.request().sampleStep() + " px";
        String seedText = selectedSource != null && selectedSource.usesSeed() ? " · seed " + seed : "";
        return sourceName + seedText + " · " + quality + " · " + String.format(Locale.ROOT, "%.0f ms", result.milliseconds());
    }

    private String terrainStatus(String sourceName) {
        int detailStep = terrainView.detailStep();
        if (detailStep == 0) {
            return sourceName + " · sampling";
        }
        String seedText = selectedSource != null && selectedSource.usesSeed() ? " · seed " + seed : "";
        String detail = detailStep == 1 ? "full detail" : "1 column per " + detailStep + " blocks";
        return sourceName + seedText + " · " + detail;
    }

    private static String sourceTitle(SourceItem source) {
        return source.category() == NoiseCategory.PACK_PALETTES
                ? source.rawName() + " · " + paletteOf(source).lowercaseLabel()
                : source.text();
    }

    private static String describePaletteAt(PaletteLayerPreview preview, double worldX, double worldZ) {
        double noise = preview.sample(worldX, worldZ);
        if (Double.isNaN(noise)) {
            return preview.palette().kind() == BiomePalette.Kind.DECORATOR
                    ? "The decorator places nothing here"
                    : "The layer doesn't reach this depth here";
        }
        return preview.entryForNoise(noise).block() + "   noise " + formatNumber(noise);
    }

    private int renderHeight() {
        return Math.max(1, getHeight());
    }

    private static String formatScale(double blocksPerPixel) {
        if (blocksPerPixel >= 1D) {
            return String.format(Locale.ROOT, "%.2f", blocksPerPixel);
        }
        return "1/" + String.format(Locale.ROOT, "%.0f", 1D / blocksPerPixel);
    }

    private static String formatNumber(double value) {
        if (!Double.isFinite(value)) {
            return "n/a";
        }
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String readableError(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message;
    }

    private static SourceItem findSourceById(String id, List<SourceItem> sources) {
        for (SourceItem source : sources) {
            if (source.id().equals(id)) {
                return source;
            }
        }
        return null;
    }

    private static void submitLatest(ThreadPoolExecutor executor, Runnable task) {
        if (executor.isShutdown()) {
            return;
        }
        executor.getQueue().clear();
        executor.execute(task);
    }

    private static NoiseCategory categorize(NoiseStyle style) {
        String name = style.name();
        if (name.startsWith("STATIC")) {
            return NoiseCategory.STATIC;
        }
        if (name.startsWith("IRIS")) {
            return NoiseCategory.IRIS;
        }
        if (name.startsWith("CLOVER")) {
            return NoiseCategory.CLOVER;
        }
        if (name.startsWith("VASCULAR")) {
            return NoiseCategory.VASCULAR;
        }
        if (name.equals("FLAT")) {
            return NoiseCategory.UTILITY;
        }
        if (name.startsWith("CELLULAR")) {
            return NoiseCategory.CELLULAR;
        }
        if (name.startsWith("HEX") || name.equals("HEXAGON")) {
            return NoiseCategory.HEXAGON;
        }
        if (name.startsWith("SIERPINSKI")) {
            return NoiseCategory.SIERPINSKI;
        }
        if (name.startsWith("NOWHERE")) {
            return NoiseCategory.NOWHERE;
        }
        if (name.startsWith("GLOB")) {
            return NoiseCategory.GLOBE;
        }
        if (name.startsWith("PERLIN")) {
            return NoiseCategory.PERLIN;
        }
        if (name.startsWith("CUBIC") || (name.startsWith("FRACTAL") && name.contains("CUBIC"))) {
            return NoiseCategory.CUBIC;
        }
        if (name.contains("SIMPLEX") && !name.startsWith("FRACTAL")) {
            return NoiseCategory.SIMPLEX;
        }
        if (name.startsWith("FRACTAL")) {
            return NoiseCategory.FRACTAL;
        }
        return NoiseCategory.OTHER;
    }

    private static String categoryLabel(NoiseCategory category) {
        return switch (category) {
            case CUSTOM -> "Custom";
            case PACK_GENERATORS -> "Pack Generators";
            case PACK_PALETTES -> "Pack Palettes";
            case SIMPLEX -> "Simplex";
            case PERLIN -> "Perlin";
            case CELLULAR -> "Cellular";
            case IRIS -> "Iris";
            case CLOVER -> "Clover";
            case HEXAGON -> "Hexagon";
            case VASCULAR -> "Vascular";
            case GLOBE -> "Globe";
            case CUBIC -> "Cubic";
            case FRACTAL -> "Fractal";
            case STATIC -> "Static";
            case NOWHERE -> "Nowhere";
            case SIERPINSKI -> "Sierpinski";
            case UTILITY -> "Utility";
            case OTHER -> "Other";
        };
    }

    private static String formatName(String rawName) {
        if (rawName == null || rawName.isEmpty()) {
            return "Unnamed";
        }
        String lower = rawName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    @FunctionalInterface
    private interface SourceFactory {
        LoadedSource create(long seed, int paletteDepth);
    }

    /**
     * @param blockRange the heights in blocks the noise spans, or null when its values aren't heights
     */
    private record LoadedSource(NoiseProvider sampler, PaletteLayerPreview palettePreview, BlockRange blockRange) {
        private static LoadedSource of(NoiseProvider sampler) {
            return new LoadedSource(sampler, null, null);
        }
    }

    private record BlockRange(double lowest, double highest) {
    }

    private record SourceItem(String id, String text, String rawName, NoiseCategory category, boolean usesSeed,
                              SourceFactory factory) {
    }

    private record ListItem(String text, SourceItem source) {
    }

    private final class PaletteInspectorListener implements PaletteInspectorPanel.Listener {
        @Override
        public void onViewChanged(PaletteLayerScale.View view) {
            requestPreview();
        }

        @Override
        public void onDepthChanged(int depth) {
            if (depth == paletteDepth) {
                return;
            }
            paletteDepth = depth;
            loadSelectedSource();
        }
    }

    private final class RenderListener implements NoiseRenderCoordinator.Listener {
        @Override
        public void onRenderStarted(NoiseRenderCoordinator.Request request) {
            EventQueue.invokeLater(() -> {
                if (!disposed && request.revision() == viewRevision) {
                    rendering = true;
                    refreshStatus();
                }
            });
        }

        @Override
        public void onRenderCompleted(NoiseRenderCoordinator.Result result) {
            EventQueue.invokeLater(() -> {
                if (disposed || result.request().revision() != viewRevision) {
                    return;
                }
                rendering = false;
                errorMessage = null;
                renderResult = result;
                lastTimedFrame = result;
                crossfade.reveal();
                repaint();
                refreshStatus();
                if (finishInteractiveFrame(result.request())) {
                    return;
                }
                if (result.request().sampleStep() <= 1) {
                    return;
                }
                long nextBudget = refinedSampleBudget(result);
                int nextStep = NoiseRenderCoordinator.nextRefinementStep(
                        result.request().width(),
                        result.request().height(),
                        result.request().sampleStep(),
                        nextBudget
                );
                if (nextStep < result.request().sampleStep()) {
                    refinementTimer.restart();
                }
            });
        }

        @Override
        public void onRenderFailed(NoiseRenderCoordinator.Request request, Throwable error) {
            IrisLogging.reportError(error);
            EventQueue.invokeLater(() -> {
                if (disposed || request.revision() != viewRevision) {
                    return;
                }
                if (request == interactiveFrame) {
                    interactiveFrame = null;
                    interactiveFramePending = false;
                }
                rendering = false;
                errorMessage = readableError(error);
                repaint();
                refreshStatus();
            });
        }
    }

    private static final class SourceCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                       boolean selected, boolean focus) {
            ListItem item = (ListItem) value;
            boolean header = item.source() == null;
            super.getListCellRendererComponent(list, header ? item.text().toUpperCase(Locale.ROOT) : item.text(), index,
                    selected && !header, false);
            if (header) {
                setFont(StudioTheme.sectionFont());
                setForeground(StudioTheme.MUTED_TEXT);
                setBorder(StudioTheme.emptyBorder(12, 12, 4, 12));
            } else {
                setFont(StudioTheme.font());
                setBorder(StudioTheme.emptyBorder(3, 20, 3, 12));
            }
            setHorizontalAlignment(SwingConstants.LEFT);
            return this;
        }
    }

    private enum ExplorerMode {
        NOISE,
        PALETTES
    }

    private enum NoiseDisplay {
        FLAT("2D", "The noise from above, coloured by value"),
        TERRAIN("3D", "The noise as terrain built from blocks, the way Minecraft would place it");

        private final String label;
        private final String description;

        NoiseDisplay(String label, String description) {
            this.label = label;
            this.description = description;
        }

        private String label() {
            return label;
        }

        private String description() {
            return description;
        }

        private boolean showsTerrain() {
            return this == TERRAIN;
        }
    }

    private enum NoiseCategory {
        CUSTOM,
        PACK_GENERATORS,
        PACK_PALETTES,
        SIMPLEX,
        PERLIN,
        CELLULAR,
        IRIS,
        CLOVER,
        HEXAGON,
        VASCULAR,
        GLOBE,
        CUBIC,
        FRACTAL,
        STATIC,
        NOWHERE,
        SIERPINSKI,
        UTILITY,
        OTHER;

        private boolean isFromPack() {
            return this == PACK_GENERATORS || this == PACK_PALETTES;
        }
    }
}
