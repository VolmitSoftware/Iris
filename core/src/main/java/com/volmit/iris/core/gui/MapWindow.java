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

import com.volmit.iris.core.gui.components.MapStyle;

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
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * The studio map: one window showing the world as tiles in several views, with the palette explorer as its Palettes view.
 */
public final class MapWindow implements MapCanvas.Listener {
    private static final String MAP_CARD = "map";
    private static final String PALETTES_CARD = "palettes";
    private static final int SIDEBAR_WIDTH = 270;
    private static final double PALETTE_RECENTER_DISTANCE_BLOCKS = 1D;
    private static final double VIEW_LIST_SHARE_WITH_PALETTES = 0.34D;

    private final JFrame frame;
    private final PaletteExplorerStart start;
    private final MapCanvas canvas;
    private final MapViewList viewList;
    private final JLabel viewTitle = new JLabel();
    private final JPanel content = new JPanel(new CardLayout());
    private final JPanel statusBars = new JPanel(new CardLayout());
    private final JPanel sidebar = new JPanel(new BorderLayout());
    private final JPanel inspectorSlot = new JPanel(new BorderLayout());
    private final JComponent viewListScroll;
    private final List<JComponent> mapControls = new ArrayList<>();
    private final List<JComponent> paletteControls = new ArrayList<>();
    private final JToggleButton gridToggle = StudioToolbar.toggle("Grid", "Show chunk and region borders (G)");
    private final JToggleButton entitiesToggle = StudioToolbar.toggle("Entities", "Show mobs and other entities (E)");
    private final JToggleButton followToggle = StudioToolbar.toggle("Follow", "Keep your player in the middle (F)");
    private final JToggleButton shadingToggle = StudioToolbar.toggle("Shading", "Shade hills and valleys on every view (S)");
    private final JToggleButton bordersToggle = StudioToolbar.toggle("Borders", "Outline the regions on every view (B)");
    private final MapLegendPanel legendPanel;

    private NoiseExplorerGUI paletteExplorer;
    private MapView currentView;
    private double paletteCenterX;
    private double paletteCenterZ;

    private MapWindow(PaletteExplorerStart start) {
        this.start = start;
        this.frame = GuiHost.createFrame("Iris Map");
        this.canvas = new MapCanvas(start.engine(), start.worldId(), start.playerId(), this);
        this.viewList = new MapViewList(this::showView);
        this.viewListScroll = StudioToolbar.scroll(viewList);
        this.legendPanel = new MapLegendPanel(share -> canvas.toggleHighlight(share.label()));
        canvas.centerOn(start.centerX(), start.centerZ());
        buildFrame();
    }

    public static void openMap(PaletteExplorerStart start) {
        EventQueue.invokeLater(() -> open(start, MapView.BIOMES));
    }

    public static void openPalettes(PaletteExplorerStart start) {
        EventQueue.invokeLater(() -> open(start, MapView.PALETTES));
    }

    private static void open(PaletteExplorerStart start, MapView initialView) {
        StudioTheme.install();
        MapWindow window = new MapWindow(start);
        window.frame.setVisible(true);
        window.canvas.start();
        window.showView(initialView);
    }

    private void buildFrame() {
        content.add(canvas, MAP_CARD);
        statusBars.add(canvas.statusBar(), MAP_CARD);
        sidebar.setPreferredSize(StudioTheme.scaledSize(SIDEBAR_WIDTH, 0));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, StudioTheme.SEPARATOR));
        sidebar.add(viewListScroll, BorderLayout.CENTER);

        frame.setLayout(new BorderLayout());
        frame.add(buildToolbar(), BorderLayout.NORTH);
        frame.add(sidebar, BorderLayout.WEST);
        frame.add(content, BorderLayout.CENTER);
        frame.add(inspectorSlot, BorderLayout.EAST);
        frame.add(statusBars, BorderLayout.SOUTH);
        installShortcuts(frame.getRootPane());
        frame.setSize(GuiHost.fitToScreen(StudioTheme.scaledSize(1440, 880)));
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
        JToolBar toolbar = StudioToolbar.create();
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
        gridToggle.addActionListener(event -> canvas.toggleGrid());
        entitiesToggle.addActionListener(event -> canvas.toggleEntities());
        followToggle.addActionListener(event -> canvas.toggleFollow());
        shadingToggle.addActionListener(event -> canvas.toggleShading());
        bordersToggle.addActionListener(event -> canvas.toggleRegionBorders());
        mapControls.forEach(toolbar::add);

        paletteControls.add(StudioToolbar.button("Reset view", "Go back to the starting position and zoom (R)", this::resetPaletteView));
        paletteControls.forEach(toolbar::add);

        toolbar.add(Box.createHorizontalGlue());
        JComponent help = StudioToolbar.button("Shortcuts", "Show every mouse and key shortcut (/)", canvas::toggleHelp);
        mapControls.add(help);
        toolbar.add(help);
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
        if (view.isTileMap()) {
            showMap(previousView, view);
        } else {
            showPalettes(previousView);
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
        setControlsVisible(mapControls, true);
        setControlsVisible(paletteControls, false);
        useSidebar(viewListScroll);
        inspectorSlot.removeAll();
        inspectorSlot.add(legendPanel, BorderLayout.CENTER);
        showCard(MAP_CARD);
        canvas.requestFocusInWindow();
    }

    private void showPalettes(MapView previousView) {
        boolean firstOpen = paletteExplorer == null;
        if (firstOpen) {
            createPaletteExplorer(previousView == null);
        } else {
            followMapIntoPalettes();
        }
        canvas.setActive(false);
        setControlsVisible(mapControls, false);
        setControlsVisible(paletteControls, true);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, viewListScroll, paletteExplorer.paletteSidebar());
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setResizeWeight(0D);
        split.setDividerLocation(viewListHeightBesidePalettes());
        useSidebar(split);
        inspectorSlot.removeAll();
        inspectorSlot.add(paletteExplorer.paletteInspector(), BorderLayout.CENTER);
        showCard(PALETTES_CARD);
        EventQueue.invokeLater(() -> viewList.select(currentView));
        if (firstOpen) {
            paletteExplorer.openViewer();
        }
    }

    // The palettes need the room more than the view list, which scrolls once it is squeezed.
    private int viewListHeightBesidePalettes() {
        int fullHeight = viewList.getPreferredSize().height + 4;
        if (sidebar.getHeight() < 1) {
            return fullHeight;
        }
        return Math.min(fullHeight, (int) (sidebar.getHeight() * VIEW_LIST_SHARE_WITH_PALETTES));
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
    }

    @Override
    public void onLegendChanged(String title, List<MapCensus.Share> shares, MapStyle.Highlight highlight) {
        legendPanel.show(title, shares, highlight);
    }

    private void resetPaletteView() {
        if (paletteExplorer != null) {
            paletteExplorer.resetViewport();
        }
    }

    private void useSidebar(JComponent component) {
        sidebar.removeAll();
        sidebar.add(component, BorderLayout.CENTER);
    }

    private void showCard(String card) {
        ((CardLayout) content.getLayout()).show(content, card);
        ((CardLayout) statusBars.getLayout()).show(statusBars, card);
    }

    private static void setControlsVisible(List<JComponent> controls, boolean visible) {
        controls.forEach(control -> control.setVisible(visible));
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
            resetPaletteView();
        }
    }

    private void close() {
        canvas.close();
        if (paletteExplorer != null) {
            paletteExplorer.close();
        }
    }
}
