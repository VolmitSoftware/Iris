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

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.localization.DesktopUiMessages;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.PreservationRegistry;
import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.function.NoiseProvider;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.math.RNG;

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
    private static final Color SIDEBAR_BACKGROUND = new Color(17, 20, 29);
    private static final Color PANEL_BACKGROUND = new Color(23, 27, 38);
    private static final Color FIELD_BACKGROUND = new Color(29, 34, 47);
    private static final Color SELECTED_BACKGROUND = new Color(42, 55, 78);
    private static final Color PRIMARY_TEXT = new Color(230, 234, 242);
    private static final Color SECONDARY_TEXT = new Color(151, 160, 178);
    private static final Color ACCENT = new Color(99, 161, 255);
    private static final Color BORDER = new Color(48, 55, 72);
    private static final Color ERROR = new Color(255, 116, 116);
    private static final Color GRID = new Color(255, 255, 255, 15);
    private static final Color CROSSHAIR = new Color(255, 255, 255, 125);
    private static final Color LEGEND_BACKGROUND = new Color(10, 12, 18, 215);
    private static final Font MONOSPACED_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final Font HEADER_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 11);
    private static final Font ITEM_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private static final Font LEGEND_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
    private static final int SIDEBAR_WIDTH = 270;
    private static final int STATUS_HEIGHT = 50;
    private static final int REFINEMENT_DELAY_MILLIS = 180;
    private static final long DEFAULT_SEED = 12345L;
    private static final long COARSE_SAMPLE_BUDGET = 4_096L;
    private static final long MAX_REFINED_SAMPLE_BUDGET = 48_000L;
    private static final double REFINEMENT_TARGET_MILLIS = 250D;
    private static final double WHEEL_ZOOM_EXPONENT = 0.08D;
    private static final NoiseCategory[] CATEGORY_ORDER = {
            NoiseCategory.CUSTOM,
            NoiseCategory.PACK_GENERATORS,
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

    private volatile Engine activeEngine;
    private final LongFunction<NoiseProvider> customFactory;
    private final String customName;
    private final List<SourceItem> fixedSources;
    private final ThreadPoolExecutor sourceExecutor;
    private final ThreadPoolExecutor catalogExecutor;
    private final NoiseRenderCoordinator renderCoordinator;
    private final Timer refinementTimer;
    private final Timer interactionTimer;
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

    private List<SourceItem> packSources = List.of();
    private NoiseViewport viewport = new NoiseViewport(0D, 0D, 1D);
    private NoisePalette palette;
    private SourceItem selectedSource;
    private NoiseProvider sampler;
    private NoiseRenderCoordinator.Result renderResult;
    private BufferedImage renderedImage;
    private Point pointer;
    private Point dragPoint;
    private long seed;
    private long viewRevision;
    private boolean catalogLoading;
    private boolean sourceLoading;
    private boolean rendering;
    private boolean disposed;
    private String errorMessage;

    public NoiseExplorerGUI() {
        this(GuiHost.get().findActiveEngine(), null, null, null, DEFAULT_SEED, null, null);
    }

    private NoiseExplorerGUI(Engine engine, String customGeneratorKey, IrisGenerator fallbackGenerator,
                             String customName, long initialSeed) {
        this(engine, customGeneratorKey, fallbackGenerator, customName, initialSeed, null, null);
    }

    NoiseExplorerGUI(LongFunction<NoiseProvider> customFactory, String customName, long initialSeed) {
        this(null, null, null, customName, initialSeed, customFactory, NoisePalette.TERRAIN);
    }

    private NoiseExplorerGUI(Engine engine, String customGeneratorKey, IrisGenerator fallbackGenerator,
                             String customName, long initialSeed, LongFunction<NoiseProvider> injectedFactory,
                             NoisePalette initialPalette) {
        this.activeEngine = engine;
        this.customFactory = injectedFactory != null
                ? injectedFactory
                : fallbackGenerator == null
                ? null
                : sourceSeed -> createGeneratorSampler(customGeneratorKey, fallbackGenerator, sourceSeed);
        this.customName = customName;
        this.seed = initialSeed;
        this.seedField.setText(Long.toString(initialSeed));
        this.palette = initialPalette != null
                ? initialPalette
                : IrisSettings.get().getGui().colorMode
                ? NoisePalette.TERRAIN
                : NoisePalette.GRAYSCALE;
        this.sourceExecutor = createExecutor("Iris Noise Source Loader");
        this.catalogExecutor = createExecutor("Iris Noise Catalog Loader");
        this.renderCoordinator = new NoiseRenderCoordinator(new RenderListener());
        registerExecutor(sourceExecutor);
        registerExecutor(catalogExecutor);
        this.refinementTimer = new Timer(REFINEMENT_DELAY_MILLIS, event -> requestRefinement());
        this.refinementTimer.setRepeats(false);
        this.interactionTimer = new Timer(45, event -> requestPreview());
        this.interactionTimer.setRepeats(false);
        this.fixedSources = buildFixedSources();
        setBackground(BACKGROUND);
        setFocusable(true);
        installInputHandlers();
        GuiHost.get().registerHotloadHook(hotloadHook);
    }

    public static void launch() {
        Engine engine = GuiHost.get().findActiveEngine();
        EventQueue.invokeLater(() -> {
            NoiseExplorerGUI explorer = new NoiseExplorerGUI(engine, null, null, null, DEFAULT_SEED, null, null);
            buildFrame(IrisLanguage.plain(DesktopUiMessages.NOISE_TITLE), explorer);
        });
    }

    public static void launchGeneratorKey(String generatorKey, IrisGenerator fallbackGenerator, long initialSeed) {
        Engine engine = GuiHost.get().findActiveEngine();
        String displayName = generatorKey == null || generatorKey.isBlank() ? "Custom Generator" : generatorKey;
        EventQueue.invokeLater(() -> {
            NoiseExplorerGUI explorer = new NoiseExplorerGUI(
                    engine,
                    generatorKey,
                    fallbackGenerator,
                    displayName,
                    initialSeed
            );
            String title = IrisLanguage.plain(
                    DesktopUiMessages.NOISE_TITLE_GENERATOR,
                    MessageArgument.untrusted("generator", displayName)
            );
            buildFrame(title, explorer);
        });
    }

    private static void buildFrame(String title, NoiseExplorerGUI explorer) {
        JFrame frame = new JFrame(title);
        GuiHost.prepareFrame(frame);
        frame.getContentPane().setBackground(BACKGROUND);
        frame.setLayout(new BorderLayout());
        frame.add(explorer.buildSidebar(), BorderLayout.WEST);
        frame.add(explorer, BorderLayout.CENTER);
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

    private static void registerExecutor(ThreadPoolExecutor executor) {
        PreservationRegistry preservation = IrisServices.getOrNull(PreservationRegistry.class);
        if (preservation != null) {
            preservation.register(executor);
        }
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
        searchField.putClientProperty("JTextField.placeholderText", IrisLanguage.plain(DesktopUiMessages.NOISE_SEARCH));
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
        north.add(searchField, BorderLayout.SOUTH);

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
        sidebar.add(scrollPane, BorderLayout.CENTER);
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

        JButton resetView = createButton("Reset view");
        resetView.addActionListener(event -> resetViewport());
        controls.add(resetView);
        return controls;
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
        List<SourceItem> sources = new ArrayList<>();
        if (customFactory != null && customName != null) {
            sources.add(new SourceItem(
                    "custom:" + customName,
                    customName,
                    customName,
                    NoiseCategory.CUSTOM,
                    true,
                    customFactory::apply
            ));
        }
        for (NoiseStyle style : NoiseStyle.values()) {
            sources.add(new SourceItem(
                    "style:" + style.name(),
                    formatName(style.name()),
                    style.name(),
                    categorize(style),
                    true,
                    sourceSeed -> {
                        CNG noise = style.create(new RNG(sourceSeed));
                        return noise::noiseFast2D;
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
                EventQueue.invokeLater(() -> applyCatalog(revision, loadedSources, null));
            } catch (Throwable error) {
                IrisLogging.reportError(error);
                EventQueue.invokeLater(() -> applyCatalog(revision, List.of(), error));
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

    private static List<SourceItem> loadPackSources(Engine engine) {
        if (engine == null) {
            return List.of();
        }
        IrisData data = engine.getData();
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
                    sourceSeed -> {
                        IrisGenerator generator = data.getGeneratorLoader().load(key);
                        if (generator == null) {
                            throw new IllegalStateException("Pack generator not found: " + key);
                        }
                        long mixedSeed = new RNG(sourceSeed).nextParallelRNG(3245).lmax();
                        return (x, z) -> generator.getHeight(x, z, mixedSeed);
                    }
            ));
        }
        return List.copyOf(sources);
    }

    private void applyCatalog(long revision, List<SourceItem> sources, Throwable error) {
        if (disposed || revision != catalogLoadRevision.get()) {
            return;
        }
        catalogLoading = false;
        packSources = sources;
        if (error != null) {
            catalogLabel.setForeground(ERROR);
            catalogLabel.setText("Pack catalog unavailable");
        } else if (sources.isEmpty()) {
            catalogLabel.setForeground(SECONDARY_TEXT);
            catalogLabel.setText("No active pack generators");
        } else {
            catalogLabel.setForeground(SECONDARY_TEXT);
            catalogLabel.setText(sources.size() + " pack generators");
        }
        boolean reloadSelectedPack = false;
        if (error == null
                && selectedSource != null
                && selectedSource.category() == NoiseCategory.PACK_GENERATORS) {
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
    }

    private void failRemovedPackSource(SourceItem removedSource) {
        sourceLoadRevision.incrementAndGet();
        refinementTimer.stop();
        viewRevision++;
        renderCoordinator.cancel(viewRevision);
        selectedSource = null;
        sampler = null;
        renderedImage = null;
        renderResult = null;
        sourceLoading = false;
        rendering = false;
        errorMessage = "Pack generator removed during hotload: " + removedSource.rawName();
        selectedLabel.setText("No source selected");
        progressLabel.setForeground(ERROR);
        progressLabel.setText("Selected pack generator was removed");
        sourceList.clearSelection();
        repaint();
    }

    private void populateSourceList() {
        if (!EventQueue.isDispatchThread()) {
            EventQueue.invokeLater(this::populateSourceList);
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
        selectedLabel.setText(source.text());
        loadSelectedSource();
    }

    private void loadSelectedSource() {
        SourceItem source = selectedSource;
        if (disposed || source == null) {
            return;
        }
        long revision = sourceLoadRevision.incrementAndGet();
        long requestedSeed = seed;
        refinementTimer.stop();
        viewRevision++;
        renderCoordinator.cancel(viewRevision);
        sampler = null;
        renderedImage = null;
        renderResult = null;
        sourceLoading = true;
        rendering = false;
        errorMessage = null;
        progressLabel.setForeground(SECONDARY_TEXT);
        progressLabel.setText("Loading source...");
        repaint();
        submitLatest(sourceExecutor, () -> {
            try {
                NoiseProvider loadedSampler = source.factory().create(requestedSeed);
                if (loadedSampler == null) {
                    throw new IllegalStateException("Noise source returned no sampler: " + source.rawName());
                }
                EventQueue.invokeLater(() -> applyLoadedSource(revision, source, loadedSampler));
            } catch (Throwable error) {
                IrisLogging.reportError(error);
                EventQueue.invokeLater(() -> applySourceError(revision, source, error));
            }
        });
    }

    private void applyLoadedSource(long revision, SourceItem source, NoiseProvider loadedSampler) {
        if (disposed || revision != sourceLoadRevision.get() || selectedSource != source) {
            return;
        }
        sampler = loadedSampler;
        sourceLoading = false;
        errorMessage = null;
        renderedImage = null;
        renderResult = null;
        progressLabel.setForeground(SECONDARY_TEXT);
        progressLabel.setText(source.usesSeed() ? "Ready · seed " + seed : "Ready");
        requestPreview();
    }

    private void applySourceError(long revision, SourceItem source, Throwable error) {
        if (disposed || revision != sourceLoadRevision.get() || selectedSource != source) {
            return;
        }
        sampler = null;
        sourceLoading = false;
        rendering = false;
        errorMessage = readableError(error);
        progressLabel.setForeground(ERROR);
        progressLabel.setText("Source failed to load");
        repaint();
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
                COARSE_SAMPLE_BUDGET
        );
        requestRender(sampleStep);
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

    private static long refinedSampleBudget(NoiseRenderCoordinator.Result currentResult) {
        return NoiseRenderCoordinator.timeBoundSampleBudget(
                currentResult.samples(),
                currentResult.milliseconds(),
                REFINEMENT_TARGET_MILLIS,
                COARSE_SAMPLE_BUDGET,
                MAX_REFINED_SAMPLE_BUDGET
        );
    }

    private void requestRender(int sampleStep) {
        NoiseRenderCoordinator.Request request = new NoiseRenderCoordinator.Request(
                viewRevision,
                sampler,
                viewport,
                palette,
                getWidth(),
                renderHeight(),
                sampleStep
        );
        renderCoordinator.request(request);
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
                    interactionTimer.stop();
                    requestPreview();
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
                    interactionTimer.restart();
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
                interactionTimer.restart();
                event.consume();
            }
        };
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
        addMouseWheelListener(mouseHandler);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                interactionTimer.restart();
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
        viewport = new NoiseViewport(0D, 0D, 1D);
        requestPreview();
    }

    private void handleHotload() {
        Engine refreshedEngine = GuiHost.get().findActiveEngine();
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
        refinementTimer.stop();
        interactionTimer.stop();
        GuiHost.get().unregisterHotloadHook(hotloadHook);
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
            if (renderedImage == null) {
                drawEmptyState(canvas, getWidth(), renderHeight);
            } else {
                Object interpolation = renderedImage.getWidth() == getWidth()
                        ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                        : RenderingHints.VALUE_INTERPOLATION_BILINEAR;
                canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
                canvas.drawImage(renderedImage, 0, 0, getWidth(), renderHeight, null);
            }
            drawCrosshair(canvas, getWidth(), renderHeight);
            drawLegend(canvas, getWidth());
            drawStatus(canvas, getWidth(), renderHeight);
        } finally {
            canvas.dispose();
        }
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
            canvas.setColor(palette.displayColorNormalized(normalized));
            canvas.drawLine(gradientX + x, gradientY, gradientX + x, gradientY + 8);
        }
        canvas.setFont(LEGEND_FONT);
        canvas.setColor(PRIMARY_TEXT);
        canvas.drawString(formatNumber(palette.minimum()), gradientX, legendY + 31);
        String maximum = formatNumber(palette.maximum());
        int maximumWidth = canvas.getFontMetrics().stringWidth(maximum);
        canvas.drawString(maximum, gradientX + gradientWidth - maximumWidth, legendY + 31);
        String label = palette.label();
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
        NoiseRenderCoordinator.Result result = renderResult;
        long clipped = result.underflow() + result.overflow();
        return coordinates + "  ·  range " + formatNumber(result.minimum()) + ".." + formatNumber(result.maximum())
                + "  ·  clipped " + clipped + "  invalid " + result.invalid();
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
        return IrisLanguage.plain(switch (category) {
            case CUSTOM -> DesktopUiMessages.NOISE_CATEGORY_CUSTOM;
            case PACK_GENERATORS -> DesktopUiMessages.NOISE_CATEGORY_PACK_GENERATORS;
            case SIMPLEX -> DesktopUiMessages.NOISE_CATEGORY_SIMPLEX;
            case PERLIN -> DesktopUiMessages.NOISE_CATEGORY_PERLIN;
            case CELLULAR -> DesktopUiMessages.NOISE_CATEGORY_CELLULAR;
            case IRIS -> DesktopUiMessages.NOISE_CATEGORY_IRIS;
            case CLOVER -> DesktopUiMessages.NOISE_CATEGORY_CLOVER;
            case HEXAGON -> DesktopUiMessages.NOISE_CATEGORY_HEXAGON;
            case VASCULAR -> DesktopUiMessages.NOISE_CATEGORY_VASCULAR;
            case GLOBE -> DesktopUiMessages.NOISE_CATEGORY_GLOBE;
            case CUBIC -> DesktopUiMessages.NOISE_CATEGORY_CUBIC;
            case FRACTAL -> DesktopUiMessages.NOISE_CATEGORY_FRACTAL;
            case STATIC -> DesktopUiMessages.NOISE_CATEGORY_STATIC;
            case NOWHERE -> DesktopUiMessages.NOISE_CATEGORY_NOWHERE;
            case SIERPINSKI -> DesktopUiMessages.NOISE_CATEGORY_SIERPINSKI;
            case UTILITY -> DesktopUiMessages.NOISE_CATEGORY_UTILITY;
            case OTHER -> DesktopUiMessages.NOISE_CATEGORY_OTHER;
        });
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
        NoiseProvider create(long seed);
    }

    private record SourceItem(String id, String text, String rawName, NoiseCategory category, boolean usesSeed,
                              SourceFactory factory) {
    }

    private record ListItem(String text, SourceItem source) {
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
                renderedImage = result.image();
                repaint();
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

    private enum NoiseCategory {
        CUSTOM,
        PACK_GENERATORS,
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
        OTHER
    }
}
