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
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisGenerator;
import com.volmit.iris.engine.object.NoiseStyle;
import com.volmit.iris.util.function.NoiseProvider;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.CNG;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
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
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongFunction;

public final class NoiseExplorerGUI extends JPanel {
    private static final long serialVersionUID = 2094606939770332040L;
    private static final Color BACKGROUND = new Color(12, 15, 22);
    static final Color SIDEBAR_BACKGROUND = new Color(17, 20, 29);
    static final Color PANEL_BACKGROUND = new Color(23, 27, 38);
    static final Color FIELD_BACKGROUND = new Color(29, 34, 47);
    private static final Color SELECTED_BACKGROUND = new Color(42, 55, 78);
    static final Color PRIMARY_TEXT = new Color(230, 234, 242);
    static final Color SECONDARY_TEXT = new Color(151, 160, 178);
    static final Color ACCENT = new Color(99, 161, 255);
    static final Color BORDER = new Color(48, 55, 72);
    static final Color ERROR = new Color(255, 116, 116);
    private static final Color GRID = new Color(255, 255, 255, 15);
    private static final Color CROSSHAIR = new Color(255, 255, 255, 125);
    private static final Color LEGEND_BACKGROUND = new Color(10, 12, 18, 215);
    private static final Font MONOSPACED_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    static final Font HEADER_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 11);
    static final Font ITEM_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    static final Font LEGEND_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
    private static final int SIDEBAR_WIDTH = 270;
    private static final int STATUS_HEIGHT = 50;
    private static final int REFINEMENT_DELAY_MILLIS = 180;
    private static final long DEFAULT_SEED = 12345L;
    private static final double TEXTURE_PIXELS_MAX_BLOCKS_PER_PIXEL = 0.25D;
    private static final double PALETTE_START_BLOCKS_PER_PIXEL = 1D / 16D;
    private static final long COARSE_SAMPLE_BUDGET = 4_096L;
    private static final long MAX_REFINED_SAMPLE_BUDGET = 48_000L;
    private static final double REFINEMENT_TARGET_MILLIS = 250D;
    private static final double PALETTE_REFINEMENT_TARGET_MILLIS = 2_000D;
    private static final double INTERACTIVE_FRAME_TARGET_MILLIS = 33D;
    private static final int RESIZE_SETTLE_MILLIS = 45;
    private static final double WHEEL_ZOOM_EXPONENT = 0.08D;
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
    private final AtomicLong sourceLoadRevision = new AtomicLong();
    private final AtomicLong catalogLoadRevision = new AtomicLong();
    private final Runnable hotloadHook = this::handleHotload;
    private final DefaultListModel<ListItem> sourceModel = new DefaultListModel<>();
    private final JList<ListItem> sourceList = new JList<>(sourceModel);
    private final JTextField searchField = new JTextField();
    private final JTextField seedField = new JTextField();
    private final JLabel selectedLabel = new JLabel("No source selected");
    private final JLabel progressLabel = new JLabel("Opening noise explorer...");
    private final JLabel catalogLabel = new JLabel("Pack catalog waiting");
    private final JComboBox<NoisePalette> palettePicker = new JComboBox<>(NoisePalette.values());
    private final PaletteInspectorPanel paletteInspector = new PaletteInspectorPanel(new PaletteInspectorListener());
    private final PaletteNavigator paletteNavigator;
    private PlayerLocationTracker locationTracker;
    private volatile BlockTextures blockTextures;

    private List<SourceItem> packSources = List.of();
    private NoiseViewport viewport;
    private NoisePalette palette;
    private SourceItem selectedSource;
    private NoiseProvider sampler;
    private PaletteLayerPreview palettePreview;
    private int paletteDepth;
    private NoiseRenderCoordinator.Result renderResult;
    private NoiseRenderCoordinator.Request interactiveFrame;
    private boolean interactiveFramePending;
    private Point pointer;
    private Point dragPoint;
    private long seed;
    private long viewRevision;
    private boolean catalogLoading;
    private boolean sourceLoading;
    private boolean rendering;
    private boolean disposed;
    private boolean awaitingInitialPackSource;
    private String errorMessage;

    private NoiseExplorerGUI(ExplorerMode mode, Engine engine, String customGeneratorKey, IrisGenerator fallbackGenerator,
                             String customName, long initialSeed, PaletteExplorerStart paletteStart) {
        this.mode = mode;
        this.paletteStart = paletteStart;
        this.paletteNavigator = new PaletteNavigator(this::selectPaletteLayer);
        this.awaitingInitialPackSource = mode == ExplorerMode.PALETTES;
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
        GuiHost.interruptOnPluginShutdown(sourceExecutor);
        GuiHost.interruptOnPluginShutdown(catalogExecutor);
        this.refinementTimer = new Timer(REFINEMENT_DELAY_MILLIS, event -> requestRefinement());
        this.refinementTimer.setRepeats(false);
        this.resizeTimer = new Timer(RESIZE_SETTLE_MILLIS, event -> requestPreview());
        this.resizeTimer.setRepeats(false);
        this.fixedSources = buildFixedSources();
        setBackground(BACKGROUND);
        setFocusable(true);
        installInputHandlers();
        GuiHost.registerHotloadHook(hotloadHook);
    }

    public static void launch() {
        Engine engine = GuiHost.findActiveEngine();
        EventQueue.invokeLater(() -> {
            NoiseExplorerGUI explorer = new NoiseExplorerGUI(ExplorerMode.NOISE, engine, null, null, null, DEFAULT_SEED, null);
            buildFrame("Noise Explorer", explorer);
        });
    }

    public static void launchPalettes(PaletteExplorerStart start) {
        EventQueue.invokeLater(() -> {
            NoiseExplorerGUI explorer = new NoiseExplorerGUI(ExplorerMode.PALETTES, start.engine(), null, null, null, DEFAULT_SEED, start);
            buildFrame("Palette Explorer", explorer);
        });
    }

    public static void launchGeneratorKey(String generatorKey, IrisGenerator fallbackGenerator, long initialSeed) {
        Engine engine = GuiHost.findActiveEngine();
        String displayName = generatorKey == null || generatorKey.isBlank() ? "Custom Generator" : generatorKey;
        EventQueue.invokeLater(() -> {
            NoiseExplorerGUI explorer = new NoiseExplorerGUI(
                    ExplorerMode.NOISE,
                    engine,
                    generatorKey,
                    fallbackGenerator,
                    displayName,
                    initialSeed,
                    null
            );
            buildFrame("Noise Explorer: " + displayName, explorer);
        });
    }

    private static void buildFrame(String title, NoiseExplorerGUI explorer) {
        JFrame frame = GuiHost.createFrame(title);
        frame.getContentPane().setBackground(BACKGROUND);
        frame.setLayout(new BorderLayout());
        frame.add(explorer.buildSidebar(), BorderLayout.WEST);
        frame.add(explorer, BorderLayout.CENTER);
        frame.add(explorer.paletteInspector, BorderLayout.EAST);
        frame.setSize(1440, 820);
        frame.setMinimumSize(new Dimension(760, 500));
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

    JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(SIDEBAR_WIDTH, 0));
        sidebar.setBackground(SIDEBAR_BACKGROUND);
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER));

        JPanel heading = new JPanel(new BorderLayout());
        heading.setBackground(SIDEBAR_BACKGROUND);
        heading.setBorder(BorderFactory.createEmptyBorder(12, 12, 10, 12));
        selectedLabel.setForeground(PRIMARY_TEXT);
        selectedLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        progressLabel.setForeground(SECONDARY_TEXT);
        progressLabel.setFont(ITEM_FONT);
        heading.add(selectedLabel, BorderLayout.NORTH);
        heading.add(progressLabel, BorderLayout.SOUTH);

        configureTextField(searchField);
        searchField.putClientProperty("JTextField.placeholderText", "Search...");
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, BORDER),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
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

        JPanel north = new JPanel(new BorderLayout());
        north.setBackground(SIDEBAR_BACKGROUND);
        north.add(heading, BorderLayout.NORTH);
        if (mode == ExplorerMode.NOISE) {
            north.add(searchField, BorderLayout.SOUTH);
        }

        sourceList.setBackground(SIDEBAR_BACKGROUND);
        sourceList.setForeground(SECONDARY_TEXT);
        sourceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sourceList.setCellRenderer(new SourceCellRenderer());
        sourceList.addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) {
                return;
            }
            ListItem item = sourceList.getSelectedValue();
            if (item != null && item.source() != null) {
                selectSource(item.source());
            }
        });
        JScrollPane scrollPane = new JScrollPane(sourceList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);

        JPanel controls = buildControls();
        sidebar.add(north, BorderLayout.NORTH);
        sidebar.add(mode == ExplorerMode.PALETTES ? paletteNavigator : scrollPane, BorderLayout.CENTER);
        sidebar.add(controls, BorderLayout.SOUTH);
        populateSourceList();
        return sidebar;
    }

    private JPanel buildControls() {
        JPanel controls = new JPanel(new GridLayout(0, 1, 0, 7));
        controls.setBackground(PANEL_BACKGROUND);
        controls.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
                BorderFactory.createEmptyBorder(10, 12, 12, 12)
        ));

        catalogLabel.setForeground(SECONDARY_TEXT);
        catalogLabel.setFont(ITEM_FONT);
        controls.add(catalogLabel);

        if (mode == ExplorerMode.NOISE) {
            addSeedControls(controls);
            addPaletteControls(controls);
        }

        JButton resetView = createButton("Reset view");
        resetView.addActionListener(event -> resetViewport());
        controls.add(resetView);
        return controls;
    }

    private void addPaletteControls(JPanel controls) {
        JLabel paletteTitle = new JLabel("Palette");
        paletteTitle.setForeground(SECONDARY_TEXT);
        paletteTitle.setFont(HEADER_FONT);
        controls.add(paletteTitle);
        palettePicker.setSelectedItem(palette);
        palettePicker.setBackground(FIELD_BACKGROUND);
        palettePicker.setForeground(PRIMARY_TEXT);
        palettePicker.addActionListener(event -> {
            NoisePalette nextPalette = (NoisePalette) palettePicker.getSelectedItem();
            if (nextPalette != null && nextPalette != palette) {
                palette = nextPalette;
                requestPreview();
            }
        });
        controls.add(palettePicker);
    }

    private void addSeedControls(JPanel controls) {
        JLabel seedTitle = new JLabel("Seed");
        seedTitle.setForeground(SECONDARY_TEXT);
        seedTitle.setFont(HEADER_FONT);
        controls.add(seedTitle);
        JPanel seedRow = new JPanel(new BorderLayout(7, 0));
        seedRow.setOpaque(false);
        configureTextField(seedField);
        JButton applySeed = createButton("Apply");
        applySeed.addActionListener(event -> applySeed());
        seedField.addActionListener(event -> applySeed());
        seedRow.add(seedField, BorderLayout.CENTER);
        seedRow.add(applySeed, BorderLayout.EAST);
        controls.add(seedRow);
    }

    private static JButton createButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(FIELD_BACKGROUND);
        button.setForeground(PRIMARY_TEXT);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(5, 9, 5, 9)
        ));
        return button;
    }

    private static void configureTextField(JTextField field) {
        field.setBackground(FIELD_BACKGROUND);
        field.setForeground(PRIMARY_TEXT);
        field.setCaretColor(PRIMARY_TEXT);
        field.setFont(ITEM_FONT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
    }

    void openViewer() {
        loadCatalog();
        if (mode == ExplorerMode.PALETTES) {
            loadBlockTextures();
            if (paletteStart != null) {
                locationTracker = PlayerLocationTracker.follow(paletteStart.playerId(), paletteStart.worldId(), paletteNavigator::showLocation);
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
        catalogLabel.setForeground(SECONDARY_TEXT);
        catalogLabel.setText("Loading pack catalog...");
        submitLatest(catalogExecutor, () -> {
            try {
                Engine engine = resolveEngine();
                List<SourceItem> loadedSources = loadPackSources(engine);
                PaletteCatalog paletteCatalog = mode == ExplorerMode.PALETTES && engine != null ? PaletteCatalog.load(engine) : null;
                EventQueue.invokeLater(() -> applyCatalog(revision, loadedSources, paletteCatalog, null));
            } catch (Throwable error) {
                Iris.reportError(error);
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
            case NOISE -> loadPackGeneratorSources(engine.getData());
            case PALETTES -> loadPackPaletteSources(engine);
        };
    }

    private static List<SourceItem> loadPackGeneratorSources(IrisData data) {
        String[] keys = data.getGeneratorLoader().getPossibleKeys();
        if (keys == null || keys.length == 0) {
            return List.of();
        }
        Arrays.sort(keys);
        List<SourceItem> sources = new ArrayList<>(keys.length);
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
            for (int layer = 0; layer < biome.getLayers().size(); layer++) {
                int selectedLayer = layer;
                sources.add(new SourceItem(
                        paletteSourceId(key, layer),
                        "Layer " + layer + " · " + PaletteCatalog.summarizeBlocks(biome.getLayers().get(layer)),
                        key,
                        NoiseCategory.PACK_PALETTES,
                        false,
                        (sourceSeed, depth) -> {
                            PaletteLayerPreview preview = PaletteLayerPreview.load(engine, key, selectedLayer, depth, blockTextures);
                            return new LoadedSource(preview::sample, preview);
                        }
                ));
            }
        }
        return List.copyOf(sources);
    }

    private static String paletteSourceId(String biomeKey, int layer) {
        return "palette:" + biomeKey + "#" + layer;
    }

    private void selectPaletteLayer(String biomeKey, int layer) {
        SourceItem source = findSourceById(paletteSourceId(biomeKey, layer), packSources);
        if (source != null && source != selectedSource) {
            selectSource(source);
        }
    }

    private static int paletteLayerOf(SourceItem source) {
        return Integer.parseInt(source.id().substring(source.id().lastIndexOf('#') + 1));
    }

    private void loadBlockTextures() {
        paletteInspector.showTextureStatus(MinecraftClientJar.isCached()
                ? "Loading block textures..."
                : "Downloading block textures from Mojang. This happens once and is about 40 MB.");
        BlockTextures.shared().whenComplete((textures, error) -> EventQueue.invokeLater(() -> applyBlockTextures(textures, error)));
    }

    private void applyBlockTextures(BlockTextures textures, Throwable error) {
        if (disposed) {
            return;
        }
        if (error != null) {
            Throwable reason = error.getCause() == null ? error : error.getCause();
            paletteInspector.showTextureStatus("Block textures unavailable (" + readableError(reason) + "). Showing map colours instead.");
            return;
        }
        blockTextures = textures;
        paletteInspector.showTextureStatus(null);
        if (palettePreview != null) {
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
            catalogLabel.setForeground(ERROR);
            catalogLabel.setText("Pack catalog unavailable");
        } else {
            catalogLabel.setForeground(SECONDARY_TEXT);
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
        String initialSourceId = paletteStart == null || paletteStart.biomeKey() == null ? null : paletteSourceId(paletteStart.biomeKey(), 0);
        SourceItem requested = initialSourceId == null ? null : findSourceById(initialSourceId, sources);
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
        return sources.size() + " palette layers in " + biomes + " biomes";
    }

    private void failRemovedPackSource(SourceItem removedSource) {
        sourceLoadRevision.incrementAndGet();
        refinementTimer.stop();
        viewRevision++;
        renderCoordinator.cancel(viewRevision);
        selectedSource = null;
        clearSampler();
        renderResult = null;
        sourceLoading = false;
        rendering = false;
        errorMessage = "Pack source removed during hotload: " + removedSource.text();
        selectedLabel.setText("No source selected");
        progressLabel.setForeground(ERROR);
        progressLabel.setText("Selected pack source was removed");
        sourceList.clearSelection();
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
            paletteNavigator.markSelected(source.rawName(), paletteLayerOf(source));
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
        renderResult = null;
        sourceLoading = true;
        rendering = false;
        errorMessage = null;
        progressLabel.setForeground(SECONDARY_TEXT);
        progressLabel.setText("Loading source...");
        repaint();
        submitLatest(sourceExecutor, () -> {
            try {
                LoadedSource loaded = source.factory().create(requestedSeed, requestedDepth);
                if (loaded == null || loaded.sampler() == null) {
                    throw new IllegalStateException("Noise source returned no sampler: " + source.rawName());
                }
                EventQueue.invokeLater(() -> applyLoadedSource(revision, source, loaded));
            } catch (Throwable error) {
                Iris.reportError(error);
                EventQueue.invokeLater(() -> applySourceError(revision, source, error));
            }
        });
    }

    private void applyLoadedSource(long revision, SourceItem source, LoadedSource loaded) {
        if (disposed || revision != sourceLoadRevision.get() || selectedSource != source) {
            return;
        }
        sampler = loaded.sampler();
        palettePreview = loaded.palettePreview();
        showPaletteInspector();
        sourceLoading = false;
        errorMessage = null;
        renderResult = null;
        progressLabel.setForeground(SECONDARY_TEXT);
        progressLabel.setText(source.usesSeed() ? "Ready · seed " + seed : "Ready");
        requestPreview();
    }

    private void applySourceError(long revision, SourceItem source, Throwable error) {
        if (disposed || revision != sourceLoadRevision.get() || selectedSource != source) {
            return;
        }
        clearSampler();
        sourceLoading = false;
        rendering = false;
        errorMessage = readableError(error);
        progressLabel.setForeground(ERROR);
        progressLabel.setText("Source failed to load");
        repaint();
    }

    private void clearSampler() {
        sampler = null;
        palettePreview = null;
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
        PaletteLayerPreview preview = palettePreview;
        if (preview == null) {
            return palette;
        }
        boolean showTexturePixels = viewport.blocksPerPixel() <= TEXTURE_PIXELS_MAX_BLOCKS_PER_PIXEL;
        return new PaletteLayerScale(paletteInspector.selectedView(), preview, palette, showTexturePixels);
    }

    private void applySeed() {
        String rawSeed = seedField.getText().trim();
        try {
            long nextSeed = Long.parseLong(rawSeed);
            seedField.setForeground(PRIMARY_TEXT);
            if (nextSeed == seed) {
                return;
            }
            seed = nextSeed;
            if (selectedSource != null && selectedSource.usesSeed()) {
                loadSelectedSource();
            }
        } catch (NumberFormatException error) {
            seedField.setForeground(ERROR);
            progressLabel.setForeground(ERROR);
            progressLabel.setText("Seed must be a whole number");
        }
    }

    private void requestPreview() {
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

    // The last frame's speed predicts this one, so cheap views draw sharp straight away instead of starting blocky.
    private long interactiveSampleBudget() {
        NoiseRenderCoordinator.Result lastResult = renderResult;
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

    // Smooth noise looks fine upscaled, but blocks and their textures only read correctly at full resolution.
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
        if (mode == ExplorerMode.PALETTES) {
            return Math.max(COARSE_SAMPLE_BUDGET, (long) getWidth() * renderHeight());
        }
        return MAX_REFINED_SAMPLE_BUDGET;
    }

    private NoiseRenderCoordinator.Request requestRender(int sampleStep) {
        NoiseRenderCoordinator.Request request = new NoiseRenderCoordinator.Request(
                viewRevision,
                sampler,
                viewport,
                activeColorScale(),
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
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                pointer = null;
                repaint();
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
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('R'), "reset-view");
        getActionMap().put("reset-view", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                resetViewport();
            }
        });
    }

    private void resetViewport() {
        viewport = initialViewport;
        requestPreview();
    }

    private void handleHotload() {
        Engine refreshedEngine = paletteStart == null ? GuiHost.findActiveEngine() : GuiHost.findEngine(paletteStart.worldId());
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
        GuiHost.unregisterHotloadHook(hotloadHook);
        sourceLoadRevision.incrementAndGet();
        catalogLoadRevision.incrementAndGet();
        sourceExecutor.shutdownNow();
        catalogExecutor.shutdownNow();
        renderCoordinator.close();
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
            drawCrosshair(canvas, getWidth(), renderHeight);
            drawLegend(canvas, getWidth());
            drawStatus(canvas, getWidth(), renderHeight);
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
        canvas.setColor(BACKGROUND);
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
        canvas.drawLine(centerX - 8, centerZ, centerX + 8, centerZ);
        canvas.drawLine(centerX, centerZ - 8, centerX, centerZ + 8);
    }

    private void drawLegend(Graphics2D canvas, int width) {
        NoiseColorScale colorScale = activeColorScale();
        int legendWidth = 190;
        int legendHeight = 40;
        int legendX = Math.max(10, width - legendWidth - 12);
        int legendY = 12;
        canvas.setColor(LEGEND_BACKGROUND);
        canvas.fillRoundRect(legendX, legendY, legendWidth, legendHeight, 10, 10);
        int gradientX = legendX + 9;
        int gradientY = legendY + 8;
        int gradientWidth = legendWidth - 18;
        for (int x = 0; x < gradientWidth; x++) {
            double normalized = x / (double) Math.max(1, gradientWidth - 1);
            canvas.setColor(colorScale.displayColorNormalized(normalized));
            canvas.drawLine(gradientX + x, gradientY, gradientX + x, gradientY + 8);
        }
        canvas.setFont(LEGEND_FONT);
        canvas.setColor(PRIMARY_TEXT);
        canvas.drawString(formatNumber(colorScale.minimum()), gradientX, legendY + 31);
        String maximum = formatNumber(colorScale.maximum());
        int maximumWidth = canvas.getFontMetrics().stringWidth(maximum);
        canvas.drawString(maximum, gradientX + gradientWidth - maximumWidth, legendY + 31);
        String label = colorScale.label();
        int labelWidth = canvas.getFontMetrics().stringWidth(label);
        canvas.drawString(label, legendX + ((legendWidth - labelWidth) / 2), legendY + 31);
    }

    private void drawStatus(Graphics2D canvas, int width, int renderHeight) {
        canvas.setColor(PANEL_BACKGROUND);
        canvas.fillRect(0, renderHeight, width, STATUS_HEIGHT);
        canvas.setColor(BORDER);
        canvas.drawLine(0, renderHeight, width, renderHeight);
        canvas.setFont(MONOSPACED_FONT);
        canvas.setColor(errorMessage == null ? PRIMARY_TEXT : ERROR);
        canvas.drawString(primaryStatus(), 10, renderHeight + 19);
        canvas.setColor(SECONDARY_TEXT);
        canvas.drawString(secondaryStatus(width, renderHeight), 10, renderHeight + 38);
    }

    private String primaryStatus() {
        if (errorMessage != null) {
            return "Error: " + errorMessage;
        }
        String sourceName = selectedSource == null ? "No source" : selectedSource.text();
        if (sourceLoading) {
            return sourceName + " · loading source";
        }
        if (rendering) {
            return sourceName + " · rendering preview";
        }
        if (renderResult == null) {
            return sourceName + " · waiting for preview";
        }
        NoiseRenderCoordinator.Result result = renderResult;
        String quality = result.request().sampleStep() == 1
                ? "full"
                : "sample 1:" + result.request().sampleStep();
        return sourceName + " · " + quality + " · " + formatNumber(result.centerValue())
                + " center · " + String.format(Locale.ROOT, "%.1f ms", result.milliseconds());
    }

    private String secondaryStatus(int width, int renderHeight) {
        Point currentPointer = pointer;
        double screenX = currentPointer == null ? width / 2D : currentPointer.getX();
        double screenZ = currentPointer == null ? renderHeight / 2D : Math.min(currentPointer.getY(), renderHeight);
        double worldX = viewport.worldX(screenX, width);
        double worldZ = viewport.worldZ(screenZ, renderHeight);
        String coordinates = String.format(
                Locale.ROOT,
                "X %.2f  Z %.2f  ·  %.5f blocks/px",
                worldX,
                worldZ,
                viewport.blocksPerPixel()
        );
        if (renderResult == null) {
            return coordinates + (catalogLoading ? "  ·  loading catalog" : "");
        }
        PaletteLayerPreview preview = palettePreview;
        if (preview != null) {
            return coordinates + "  ·  " + describePaletteAt(preview, worldX, worldZ);
        }
        NoiseRenderCoordinator.Result result = renderResult;
        long clipped = result.underflow() + result.overflow();
        return coordinates + "  ·  range " + formatNumber(result.minimum()) + ".." + formatNumber(result.maximum())
                + "  ·  clipped " + clipped + "  invalid " + result.invalid();
    }

    private static String describePaletteAt(PaletteLayerPreview preview, double worldX, double worldZ) {
        double noise = preview.sample(worldX, worldZ);
        if (Double.isNaN(noise)) {
            return "layer doesn't reach this depth here";
        }
        return preview.entryForNoise(noise).block() + "  ·  noise " + formatNumber(noise);
    }

    private int renderHeight() {
        return Math.max(1, getHeight() - STATUS_HEIGHT);
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

    private record LoadedSource(NoiseProvider sampler, PaletteLayerPreview palettePreview) {
        private static LoadedSource of(NoiseProvider sampler) {
            return new LoadedSource(sampler, null);
        }
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
                    repaint();
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
                repaint();
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
            Iris.reportError(error);
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
            });
        }
    }

    private static final class SourceCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                       boolean selected, boolean focus) {
            ListItem item = (ListItem) value;
            boolean header = item.source() == null;
            super.getListCellRendererComponent(list, item.text(), index, selected && !header, false);
            setOpaque(true);
            if (header) {
                setFont(HEADER_FONT);
                setForeground(ACCENT);
                setBackground(SIDEBAR_BACKGROUND);
                setBorder(BorderFactory.createEmptyBorder(11, 11, 4, 11));
            } else {
                setFont(ITEM_FONT);
                setForeground(selected ? Color.WHITE : SECONDARY_TEXT);
                setBackground(selected ? SELECTED_BACKGROUND : SIDEBAR_BACKGROUND);
                setBorder(BorderFactory.createEmptyBorder(4, 20, 4, 10));
            }
            setHorizontalAlignment(SwingConstants.LEFT);
            return this;
        }
    }

    private enum ExplorerMode {
        NOISE,
        PALETTES
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
