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

import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.iris.studio.map.components.MapStyle;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * The studio map: one window showing the world as tiles in several views, with the palette, object, noise and image map
 * explorers as its Palettes, Objects, Noise and Image maps views.
 */
public final class MapWindow implements MapCanvas.Listener {
    private static final String MAP_CARD = "map";
    private static final String PALETTES_CARD = "palettes";
    private static final String NOISE_CARD = "noise";
    private static final String WORLD_CARD = "world";
    private static final String IMAGE_MAPS_CARD = "image maps";
    private static final int SIDEBAR_WIDTH = 270;
    private static final int SIDEBAR_MINIMUM_WIDTH = 200;
    private static final int CONTENT_MINIMUM_WIDTH = 320;
    private static final double PALETTE_RECENTER_DISTANCE_BLOCKS = 1D;
    private static final double WORLD_RECENTER_DISTANCE_BLOCKS = 1D;
    private static final int VIEW_LIST_SCROLL_INSETS = 4;
    private static final int EXPLORER_DIVIDER_HEIGHT = 7;

    private final JFrame frame;
    private final PaletteExplorerStart start;
    private final NoiseSelection noiseSelection;
    private final MapCanvas canvas;
    private final MapViewList viewList;
    private final JLabel viewTitle = new JLabel();
    private final JPanel content = new JPanel(new CardLayout());
    private final JPanel statusBars = new JPanel(new CardLayout());
    private final JPanel sidebar = new JPanel(new BorderLayout());
    private final IslandPanel sidebarIsland = new IslandPanel(sidebar);
    private final JPanel inspectorSlot = new JPanel(new BorderLayout());
    private final JComponent viewListScroll;
    private final JSplitPane explorerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
    private final JSplitPane pickInspectorSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
    private final List<JComponent> mapControls = new ArrayList<>();
    private final List<JComponent> explorerControls = new ArrayList<>();
    private final List<JComponent> noiseControls = new ArrayList<>();
    private final List<JComponent> worldControls = new ArrayList<>();
    private final List<JComponent> imageMapControls = new ArrayList<>();
    private final JToolBar toolbar = StudioToolbar.create();
    private final JComponent resetViewButton = StudioToolbar.button("Reset view", "Go back to the starting position and zoom (R)",
            this::resetExplorerView);
    private final JToggleButton gridToggle = StudioToolbar.toggle("Grid", "Show chunk and region borders (G)");
    private final JToggleButton entitiesToggle = StudioToolbar.toggle("Entities", "Show mobs and other entities (E)");
    private final JToggleButton followToggle = StudioToolbar.toggle("Follow", "Keep your player in the middle (F)");
    private final JToggleButton shadingToggle = StudioToolbar.toggle("Shading", "Shade hills and valleys on every view (S)");
    private final JToggleButton bordersToggle = StudioToolbar.toggle("Borders", "Outline the regions on every view (B)");
    private final JToggleButton waterToggle = StudioToolbar.toggle("Water", "Overlay water on biome picks (W)");
    private final ResourceUsage resourceUsage = new ResourceUsage();
    private final MapLegendPanel legendPanel;
    private final RegionExplorer regionExplorer;
    private final BiomeExplorer biomeExplorer;

    private NoiseExplorerGUI paletteExplorer;
    private final Map<MapView, ObjectExplorer> objectExplorers = new EnumMap<>(MapView.class);
    private NoiseExplorerGUI noiseExplorer;
    private JComponent noiseSidebar;
    private WorldExplorer worldExplorer;
    private ImageMapExplorer imageMapExplorer;
    private double worldCenterX;
    private double worldCenterZ;
    private MapView currentView;
    private double paletteCenterX;
    private double paletteCenterZ;

    private MapWindow(PaletteExplorerStart start, NoiseSelection noiseSelection) {
        this.start = start;
        this.noiseSelection = noiseSelection;
        this.frame = MapHost.createFrame("Iris Map");
        this.canvas = new MapCanvas(start.engine(), start.worldId(), start.playerId(), this);
        this.viewList = new MapViewList(this::showView);
        this.viewListScroll = StudioToolbar.scroll(viewList);
        this.legendPanel = new MapLegendPanel(share -> canvas.toggleHighlight(share.label()));
        this.regionExplorer = new RegionExplorer(canvas::engine, canvas::previewRegion, canvas::showPicksAs);
        this.biomeExplorer = new BiomeExplorer(canvas::engine, canvas::showPicksAs);
        canvas.centerOn(start.centerX(), start.centerZ());
        buildFrame();
    }

    public static void openMap(PaletteExplorerStart start) {
        openLater(start, MapView.BIOMES);
    }

    public static void openPalettes(PaletteExplorerStart start) {
        openLater(start, MapView.PALETTES);
    }

    public static void openNoise(PaletteExplorerStart start) {
        openLater(start, MapView.NOISE);
    }

    public static void openNoise(PaletteExplorerStart start, NoiseSelection selection) {
        if (!MapHost.isAvailable()) {
            return;
        }
        EventQueue.invokeLater(() -> open(start, MapView.NOISE, selection));
    }

    public static void openImageMaps(PaletteExplorerStart start) {
        openLater(start, MapView.IMAGE_MAPS);
    }

    private static void openLater(PaletteExplorerStart start, MapView initialView) {
        if (!MapHost.isAvailable()) {
            return;
        }
        EventQueue.invokeLater(() -> open(start, initialView, null));
    }

    private static void open(PaletteExplorerStart start, MapView initialView, NoiseSelection selection) {
        if (!MapHost.isAvailable()) {
            return;
        }
        StudioTheme.install();
        MapWindow window = new MapWindow(start, selection);
        window.frame.setVisible(true);
        window.canvas.start();
        window.resourceUsage.start();
        window.showView(initialView);
    }

    private void buildFrame() {
        styleExplorerSplit();
        stylePickInspectorSplit();
        content.add(canvas, MAP_CARD);
        statusBars.add(canvas.statusBar(), MAP_CARD);
        sidebar.add(viewListScroll, BorderLayout.CENTER);

        frame.setLayout(new BorderLayout());
        frame.add(buildToolbar(), BorderLayout.NORTH);
        frame.add(new IslandOverlay(sidebarIsland, content, StudioTheme.scale(SIDEBAR_WIDTH),
                StudioTheme.scale(SIDEBAR_MINIMUM_WIDTH), StudioTheme.scale(CONTENT_MINIMUM_WIDTH)), BorderLayout.CENTER);
        frame.add(inspectorSlot, BorderLayout.EAST);
        frame.add(statusBars, BorderLayout.SOUTH);
        installShortcuts(frame.getRootPane());
        keepClearOfIsland(canvas::setCoveredLeftWidth);
        frame.setSize(MapHost.fitToScreen(StudioTheme.scaledSize(1440, 880)));
        frame.setMinimumSize(StudioTheme.scaledSize(820, 520));
        frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                close();
            }
        });
    }

    private JToolBar buildToolbar() {
        viewTitle.setFont(StudioTheme.titleFont());
        viewTitle.setBorder(StudioTheme.emptyBorder(0, 6, 0, 14));
        toolbar.add(viewTitle);

        mapControls.add(StudioToolbar.button("Refresh", "Redraw the map with the latest pack files (R)", canvas::refreshContent));
        mapControls.add(StudioToolbar.button("Reset zoom", "Go back to the default zoom (\\)", canvas::resetZoom));
        mapControls.add(gridToggle);
        mapControls.add(entitiesToggle);
        mapControls.add(followToggle);
        mapControls.add(toolbarSeparator());
        mapControls.add(shadingToggle);
        mapControls.add(bordersToggle);
        mapControls.add(waterToggle);
        gridToggle.addActionListener(event -> canvas.toggleGrid());
        entitiesToggle.addActionListener(event -> canvas.toggleEntities());
        followToggle.addActionListener(event -> canvas.toggleFollow());
        shadingToggle.addActionListener(event -> canvas.toggleShading());
        bordersToggle.addActionListener(event -> canvas.toggleRegionBorders());
        waterToggle.addActionListener(event -> canvas.toggleWaterOverlay());
        mapControls.forEach(toolbar::add);

        explorerControls.add(resetViewButton);
        explorerControls.forEach(toolbar::add);

        toolbar.add(Box.createHorizontalGlue());
        toolbar.add(resourceUsage);
        toolbar.add(StudioToolbar.themeButton());
        JComponent help = StudioToolbar.button("Shortcuts", "Show every mouse and key shortcut (/)", canvas::toggleHelp);
        mapControls.add(help);
        toolbar.add(help);
        onTogglesChanged();
        return toolbar;
    }

    private static JComponent toolbarSeparator() {
        JToolBar.Separator separator = new JToolBar.Separator();
        separator.setOrientation(SwingConstants.VERTICAL);
        return separator;
    }

    private void showView(MapView view) {
        if (view == null || view == currentView) {
            return;
        }
        MapView previousView = currentView;
        currentView = view;
        viewList.select(view);
        viewTitle.setText(view.label());
        viewTitle.setToolTipText(view.description());
        if (previousView == MapView.WORLD_3D) {
            worldExplorer.hide();
        }
        if (view.isTileMap()) {
            showMap(previousView, view);
        } else if (view == MapView.WORLD_3D) {
            showWorld();
        } else if (view == MapView.PALETTES) {
            showPalettes(previousView);
        } else if (view == MapView.OBJECTS || view == MapView.PROCEDURAL) {
            showObjectExplorer(view);
        } else if (view == MapView.IMAGE_MAPS) {
            showImageMaps();
        } else {
            showNoise();
        }
        frame.getRootPane().revalidate();
        frame.getRootPane().repaint();
    }

    private void showMap(MapView previousView, MapView view) {
        if (previousView == MapView.PALETTES && paletteExplorer != null) {
            canvas.centerOn(paletteExplorer.centerX(), paletteExplorer.centerZ());
        }
        canvas.showView(view);
        canvas.setActive(true);
        showControls(mapControls);
        waterToggle.setVisible(view == MapView.BIOMES);
        if (view == MapView.REGIONS) {
            showRegionExplorer(previousView);
        } else if (view == MapView.BIOMES) {
            showBiomeExplorer();
        } else {
            useSidebar(viewListScroll);
            useInspector(legendPanel);
        }
        showCard(MAP_CARD);
        canvas.requestFocusInWindow();
    }

    // The region inspector sits above the legend, which then lists the chosen region's biomes on the map.
    private void showRegionExplorer(MapView previousView) {
        if (previousView != MapView.REGIONS) {
            regionExplorer.loadRegions();
        }
        useSidebar(viewListAbove(regionExplorer.navigator()));
        showPickInspector(regionExplorer.inspector());
        EventQueue.invokeLater(() -> viewList.select(currentView));
        regionExplorer.measure(canvas.viewArea());
    }

    private void showBiomeExplorer() {
        useSidebar(viewListScroll);
        showPickInspector(biomeExplorer.inspector());
        biomeExplorer.measure(canvas.viewArea());
    }

    // The region and biome views share how the map draws its picks, so their switches show the same choice.
    private void showPickInspector(PickInspectorPanel inspector) {
        inspector.selectDisplay(canvas.pickDisplay());
        pickInspectorSplit.setTopComponent(StudioTheme.matchTheme(inspector));
        pickInspectorSplit.setBottomComponent(StudioTheme.matchTheme(legendPanel));
        useInspector(pickInspectorSplit);
    }

    private void stylePickInspectorSplit() {
        pickInspectorSplit.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, StudioTheme.SEPARATOR));
        pickInspectorSplit.setResizeWeight(0.7D);
        pickInspectorSplit.setBackground(StudioTheme.PANEL_BACKGROUND);
        pickInspectorSplit.setDividerSize(StudioTheme.scale(EXPLORER_DIVIDER_HEIGHT));
        pickInspectorSplit.putClientProperty("FlatLaf.style", "style: grip");
    }

    private void showPalettes(MapView previousView) {
        boolean firstOpen = paletteExplorer == null;
        if (firstOpen) {
            createPaletteExplorer(previousView == null);
        } else {
            followMapIntoPalettes();
        }
        canvas.setActive(false);
        showControls(explorerControls);
        useSidebar(viewListAbove(paletteExplorer.paletteSidebar()));
        useInspector(paletteExplorer.paletteInspector());
        showCard(PALETTES_CARD);
        EventQueue.invokeLater(() -> viewList.select(currentView));
        if (firstOpen) {
            paletteExplorer.openViewer();
        }
    }

    private void showObjectExplorer(MapView view) {
        boolean firstOpen = !objectExplorers.containsKey(view);
        ObjectExplorer explorer = objectExplorers.computeIfAbsent(view, this::createObjectExplorer);
        canvas.setActive(false);
        showControls(explorerControls);
        useSidebar(viewListAbove(explorer.browser()));
        useInspector(explorer.overview());
        showCard(view.name());
        EventQueue.invokeLater(() -> viewList.select(currentView));
        if (firstOpen) {
            explorer.open();
        }
    }

    // The 3D world keeps its camera unless the map was moved since; then it turns around the map's middle.
    private void showWorld() {
        boolean firstOpen = worldExplorer == null;
        if (firstOpen) {
            createWorldExplorer();
        }
        boolean mapMoved = Math.hypot(canvas.centerX() - worldCenterX, canvas.centerZ() - worldCenterZ) >= WORLD_RECENTER_DISTANCE_BLOCKS;
        worldCenterX = canvas.centerX();
        worldCenterZ = canvas.centerZ();
        canvas.setActive(false);
        showControls(worldControls, explorerControls);
        useSidebar(viewListScroll);
        useInspector(worldExplorer.legend());
        showCard(WORLD_CARD);
        worldExplorer.show(worldCenterX, worldCenterZ, firstOpen || mapMoved);
    }

    private void createWorldExplorer() {
        worldExplorer = new WorldExplorer(canvas::engine);
        keepClearOfIsland(worldExplorer::setCoveredLeftWidth);
        worldControls.addAll(worldExplorer.controls());
        worldControls.forEach(control -> toolbar.add(control, toolbar.getComponentIndex(resetViewButton)));
        content.add(worldExplorer.view(), WORLD_CARD);
        statusBars.add(worldExplorer.statusBar(), WORLD_CARD);
    }

    private void keepClearOfIsland(IntConsumer setCoveredLeftWidth) {
        setCoveredLeftWidth.accept(sidebarIsland.getWidth());
        sidebarIsland.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                setCoveredLeftWidth.accept(sidebarIsland.getWidth());
            }
        });
    }

    private ObjectExplorer createObjectExplorer(MapView view) {
        ObjectSource source = view == MapView.PROCEDURAL ? new ProceduralObjectSource() : new PackObjectSource();
        ObjectExplorer explorer = new ObjectExplorer(start.engine(), start.worldId(), source);
        keepClearOfIsland(explorer::setCoveredLeftWidth);
        content.add(explorer.view(), view.name());
        statusBars.add(explorer.statusBar(), view.name());
        return explorer;
    }

    private void showNoise() {
        boolean firstOpen = noiseExplorer == null;
        if (firstOpen) {
            createNoiseExplorer();
        }
        canvas.setActive(false);
        showControls(noiseControls, explorerControls);
        useSidebar(viewListAbove(noiseSidebar));
        inspectorSlot.removeAll();
        showCard(NOISE_CARD);
        EventQueue.invokeLater(() -> viewList.select(currentView));
        if (firstOpen) {
            noiseExplorer.openViewer();
        }
    }

    private void showImageMaps() {
        boolean firstOpen = imageMapExplorer == null;
        if (firstOpen) {
            createImageMapExplorer();
        }
        canvas.setActive(false);
        showControls(imageMapControls);
        useSidebar(viewListScroll);
        inspectorSlot.removeAll();
        showCard(IMAGE_MAPS_CARD);
        if (firstOpen) {
            imageMapExplorer.open();
        }
    }

    private void createImageMapExplorer() {
        imageMapExplorer = new ImageMapExplorer(start.engine());
        keepClearOfIsland(imageMapExplorer::setCoveredLeftWidth);
        imageMapControls.addAll(imageMapExplorer.controls());
        imageMapControls.forEach(control -> toolbar.add(control, toolbar.getComponentIndex(resetViewButton)));
        content.add(imageMapExplorer.view(), IMAGE_MAPS_CARD);
        statusBars.add(imageMapExplorer.statusBar(), IMAGE_MAPS_CARD);
    }

    // Both explorers share one split, so the divider stays where it was dragged when switching between them.
    private JSplitPane viewListAbove(JComponent explorerSidebar) {
        boolean firstUse = explorerSplit.getBottomComponent() == null;
        explorerSplit.setTopComponent(viewListScroll);
        explorerSplit.setBottomComponent(StudioTheme.matchTheme(explorerSidebar));
        if (firstUse) {
            EventQueue.invokeLater(this::fitViewListAboveExplorer);
        }
        return explorerSplit;
    }

    private void styleExplorerSplit() {
        explorerSplit.setBorder(BorderFactory.createEmptyBorder());
        explorerSplit.setResizeWeight(0D);
        explorerSplit.setBackground(StudioTheme.PANEL_BACKGROUND);
        explorerSplit.setDividerSize(StudioTheme.scale(EXPLORER_DIVIDER_HEIGHT));
        explorerSplit.putClientProperty("FlatLaf.style", "style: grip");
    }

    // Every view stays in sight, so the divider starts below the last one and can only be dragged further down. Measured
    // once the sidebar is laid out, since the list's rows are taller before they know their width.
    private void fitViewListAboveExplorer() {
        int fullHeight = viewList.getPreferredSize().height + VIEW_LIST_SCROLL_INSETS;
        viewListScroll.setMinimumSize(new Dimension(0, fullHeight));
        explorerSplit.setDividerLocation(fullHeight);
    }

    private void createNoiseExplorer() {
        noiseExplorer = noiseSelection == null
                ? NoiseExplorerGUI.createNoiseExplorer(start)
                : NoiseExplorerGUI.createGeneratorExplorer(start, noiseSelection);
        keepClearOfIsland(noiseExplorer::setCoveredLeftWidth);
        noiseSidebar = noiseExplorer.noiseSidebar();
        noiseControls.addAll(noiseExplorer.noiseControls());
        noiseControls.forEach(control -> toolbar.add(control, toolbar.getComponentIndex(resetViewButton)));
        content.add(noiseExplorer, NOISE_CARD);
        statusBars.add(noiseExplorer.statusBar(), NOISE_CARD);
    }

    private void createPaletteExplorer(boolean openedOnPalettes) {
        PaletteExplorerStart explorerStart = openedOnPalettes
                ? start
                : new PaletteExplorerStart(start.engine(), start.worldId(), start.playerId(), null, canvas.centerX(), canvas.centerZ());
        paletteExplorer = NoiseExplorerGUI.createPaletteExplorer(explorerStart);
        content.add(paletteExplorer, PALETTES_CARD);
        statusBars.add(paletteExplorer.statusBar(), PALETTES_CARD);
        rememberPaletteCenter(explorerStart.centerX(), explorerStart.centerZ());
        if (!openedOnPalettes) {
            showSurfaceBiomePalettes(explorerStart.centerX(), explorerStart.centerZ());
        }
    }

    // Coming back from the map only moves the palettes when the map was moved, so a chosen layer survives a quick look.
    private void followMapIntoPalettes() {
        double movedX = canvas.centerX() - paletteCenterX;
        double movedZ = canvas.centerZ() - paletteCenterZ;
        if (Math.hypot(movedX, movedZ) < PALETTE_RECENTER_DISTANCE_BLOCKS) {
            return;
        }
        paletteExplorer.centerOn(canvas.centerX(), canvas.centerZ());
        rememberPaletteCenter(canvas.centerX(), canvas.centerZ());
        showSurfaceBiomePalettes(canvas.centerX(), canvas.centerZ());
    }

    @Override
    public void onShowPalettesAt(double worldX, double worldZ) {
        canvas.centerOn(worldX, worldZ);
        showView(MapView.PALETTES);
    }

    private void showSurfaceBiomePalettes(double worldX, double worldZ) {
        canvas.findSurfaceBiome(worldX, worldZ, biomeKey -> {
            if (paletteExplorer != null) {
                paletteExplorer.showPalettesAt(worldX, worldZ, biomeKey);
            }
        });
    }

    private void rememberPaletteCenter(double worldX, double worldZ) {
        paletteCenterX = worldX;
        paletteCenterZ = worldZ;
    }

    @Override
    public void onTogglesChanged() {
        gridToggle.setSelected(canvas.isGridVisible());
        entitiesToggle.setSelected(canvas.areEntitiesVisible());
        followToggle.setSelected(canvas.isFollowing());
        shadingToggle.setSelected(canvas.style().shading());
        bordersToggle.setSelected(canvas.style().regionBorders());
        waterToggle.setSelected(canvas.isWaterOverlayEnabled());
    }

    @Override
    public void onLegendChanged(String title, List<MapCensus.Share> shares, MapStyle.Highlight highlight) {
        legendPanel.show(title, shares, highlight);
        if (currentView == MapView.REGIONS) {
            regionExplorer.measure(canvas.viewArea());
        } else if (currentView == MapView.BIOMES) {
            biomeExplorer.measure(canvas.viewArea());
        }
    }

    @Override
    public void onContentRefreshed() {
        regionExplorer.refresh();
        biomeExplorer.refresh();
        if (worldExplorer != null) {
            worldExplorer.refresh(worldCenterX, worldCenterZ);
        }
    }

    // Only called for the view on screen, whose explorer exists by then.
    private void resetExplorerView() {
        switch (currentView) {
            case WORLD_3D -> worldExplorer.resetCamera();
            case OBJECTS, PROCEDURAL -> objectExplorers.get(currentView).resetCamera();
            case NOISE -> noiseExplorer.resetViewport();
            case PALETTES -> paletteExplorer.resetViewport();
            default -> {
            }
        }
    }

    private void useSidebar(JComponent component) {
        sidebar.removeAll();
        sidebar.add(StudioTheme.matchTheme(component), BorderLayout.CENTER);
    }

    private void useInspector(JComponent component) {
        inspectorSlot.removeAll();
        inspectorSlot.add(StudioTheme.matchTheme(component), BorderLayout.CENTER);
    }

    private void showCard(String card) {
        ((CardLayout) content.getLayout()).show(content, card);
        ((CardLayout) statusBars.getLayout()).show(statusBars, card);
    }

    @SafeVarargs
    private void showControls(List<JComponent>... visibleControls) {
        for (List<JComponent> controls : List.of(mapControls, explorerControls, noiseControls, worldControls, imageMapControls)) {
            controls.forEach(control -> control.setVisible(false));
        }
        for (List<JComponent> controls : visibleControls) {
            controls.forEach(control -> control.setVisible(true));
        }
    }

    private void installShortcuts(JRootPane rootPane) {
        for (MapView view : MapView.values()) {
            bind(rootPane, KeyStroke.getKeyStroke(view.shortcutKey(), 0), "view-" + view.name(), () -> showView(view));
        }
        bind(rootPane, KeyStroke.getKeyStroke(KeyEvent.VK_M, 0), "next-view", this::showNextView);
        bind(rootPane, KeyStroke.getKeyStroke(KeyEvent.VK_R, 0), "refresh", this::refreshOrReset);
        bindMap(rootPane, KeyEvent.VK_F, 0, "follow", canvas::toggleFollow);
        bindMap(rootPane, KeyEvent.VK_G, 0, "grid", canvas::toggleGrid);
        bindMap(rootPane, KeyEvent.VK_E, 0, "entities", canvas::toggleEntities);
        bindMap(rootPane, KeyEvent.VK_F3, 0, "debug", canvas::toggleDebug);
        bindMap(rootPane, KeyEvent.VK_H, 0, "highlight", canvas::highlightUnderCursor);
        bindMap(rootPane, KeyEvent.VK_ESCAPE, 0, "dismiss", canvas::dismissEmphasis);
        bindMap(rootPane, KeyEvent.VK_S, 0, "shading", canvas::toggleShading);
        bindMap(rootPane, KeyEvent.VK_B, 0, "borders", canvas::toggleRegionBorders);
        bindMap(rootPane, KeyEvent.VK_W, 0, "water", canvas::toggleWaterOverlay);
        bindMap(rootPane, KeyEvent.VK_EQUALS, 0, "zoom-in", canvas::zoomIn);
        bindMap(rootPane, KeyEvent.VK_EQUALS, KeyEvent.SHIFT_DOWN_MASK, "zoom-in-plus", canvas::zoomIn);
        bindMap(rootPane, KeyEvent.VK_ADD, 0, "zoom-in-keypad", canvas::zoomIn);
        bindMap(rootPane, KeyEvent.VK_MINUS, 0, "zoom-out", canvas::zoomOut);
        bindMap(rootPane, KeyEvent.VK_SUBTRACT, 0, "zoom-out-keypad", canvas::zoomOut);
        bindMap(rootPane, KeyEvent.VK_BACK_SLASH, 0, "reset-zoom", canvas::resetZoom);
        bindMap(rootPane, KeyEvent.VK_SLASH, 0, "help", canvas::toggleHelp);
        bindMap(rootPane, KeyEvent.VK_SLASH, KeyEvent.SHIFT_DOWN_MASK, "help-question", canvas::toggleHelp);
        bind(rootPane, KeyStroke.getKeyStroke(KeyEvent.VK_SHIFT, KeyEvent.SHIFT_DOWN_MASK, false), "details-on",
                () -> canvas.setDetailedHover(true));
        bind(rootPane, KeyStroke.getKeyStroke(KeyEvent.VK_SHIFT, 0, true), "details-off", () -> canvas.setDetailedHover(false));
    }

    private void bindMap(JRootPane rootPane, int keyCode, int modifiers, String name, Runnable action) {
        bind(rootPane, KeyStroke.getKeyStroke(keyCode, modifiers), name, () -> {
            if (currentView != null && currentView.isTileMap()) {
                action.run();
            }
        });
    }

    // Keys typed into a search box belong to that box, not to the window's shortcuts.
    private static void bind(JRootPane rootPane, KeyStroke keyStroke, String name, Runnable action) {
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(keyStroke, name);
        rootPane.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (!(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof JTextComponent)) {
                    action.run();
                }
            }
        });
    }

    private void showNextView() {
        MapView[] views = MapView.values();
        showView(views[(currentView.ordinal() + 1) % views.length]);
    }

    private void refreshOrReset() {
        if (currentView.isTileMap()) {
            canvas.refreshContent();
        } else {
            resetExplorerView();
        }
    }

    private void close() {
        resourceUsage.stop();
        canvas.close();
        regionExplorer.close();
        biomeExplorer.close();
        if (paletteExplorer != null) {
            paletteExplorer.close();
        }
        objectExplorers.values().forEach(ObjectExplorer::close);
        if (noiseExplorer != null) {
            noiseExplorer.close();
        }
        if (worldExplorer != null) {
            worldExplorer.close();
        }
        if (imageMapExplorer != null) {
            imageMapExplorer.close();
        }
    }

    public record NoiseSelection(String generatorKey, IrisGenerator generator, long seed) {
    }
}
