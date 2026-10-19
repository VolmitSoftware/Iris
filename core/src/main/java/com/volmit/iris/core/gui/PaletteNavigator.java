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

import com.volmit.iris.core.gui.PaletteCatalog.BiomeEntry;
import com.volmit.iris.core.gui.PaletteCatalog.BiomeGroup;
import com.volmit.iris.core.gui.PaletteCatalog.RegionEntry;
import com.volmit.iris.core.gui.PlayerLocationTracker.PlayerLocation;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;


final class PaletteNavigator extends JPanel {
    private static final long serialVersionUID = -6402313718430917284L;
    private static final String HERE_CARD = "here";
    private static final String OVERVIEW_CARD = "overview";
    private static final String DETAIL_COLOR = StudioTheme.SECONDARY_TEXT_HTML;
    private static final int MESSAGE_WRAP_WIDTH_PIXELS = 190;

    private final Listener listener;
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);
    private final JLabel regionLabel = new JLabel();
    private final DefaultListModel<HereRow> hereRows = new DefaultListModel<>();
    private final JList<HereRow> hereList = new JList<>(hereRows);
    private final JTextField searchField = new JTextField();
    private final DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    private final JTree overviewTree = new JTree(treeModel);

    private PaletteCatalog catalog;
    private PlayerLocation location;
    private BiomePalette selectedPalette;
    private boolean updatingSelection;
    private boolean untracked;

    PaletteNavigator(Listener listener) {
        super(new BorderLayout());
        this.listener = listener;
        cardPanel.add(buildHereCard(), HERE_CARD);
        cardPanel.add(buildOverviewCard(), OVERVIEW_CARD);
        add(buildTabBar(), BorderLayout.NORTH);
        add(cardPanel, BorderLayout.CENTER);
        refreshHere();
    }

    void showCatalog(PaletteCatalog nextCatalog) {
        catalog = nextCatalog;
        rebuildOverview();
        refreshHere();
    }

    void showLocation(PlayerLocation nextLocation) {
        location = nextLocation;
        refreshHere();
    }

    // Without a player there is nowhere to call "here", so the Here tab explains how to get one instead.
    void showUntracked() {
        untracked = true;
        refreshHere();
    }

    void markSelected(BiomePalette palette) {
        selectedPalette = palette;
        updatingSelection = true;
        try {
            selectInHereList();
            selectInTree();
        } finally {
            updatingSelection = false;
        }
    }

    private JPanel buildTabBar() {
        JPanel bar = new JPanel(new GridLayout(1, 2, 0, 0));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, StudioTheme.SEPARATOR));
        ButtonGroup group = new ButtonGroup();
        JToggleButton here = createTab("Here", HERE_CARD, group);
        JToggleButton overview = createTab("Overview", OVERVIEW_CARD, group);
        bar.add(here);
        bar.add(overview);
        here.doClick();
        return bar;
    }

    private JToggleButton createTab(String text, String card, ButtonGroup group) {
        JToggleButton tab = StudioToolbar.tab(text);
        tab.addActionListener(event -> cards.show(cardPanel, card));
        group.add(tab);
        return tab;
    }

    private JPanel buildHereCard() {
        JPanel card = new JPanel(new BorderLayout());
        JPanel location = new JPanel(new GridLayout(0, 1, 0, StudioTheme.scale(3)));
        location.setBorder(StudioTheme.emptyBorder(9, 12, 5, 12));
        location.add(regionLabel);

        hereList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        hereList.setCellRenderer(new HereRowRenderer());
        hereList.addListSelectionListener(event -> {
            HereRow row = hereList.getSelectedValue();
            if (!event.getValueIsAdjusting() && !updatingSelection && row != null && row.biomeKey() != null) {
                choose(row.biomeKey(), row.palette());
            }
        });
        card.add(location, BorderLayout.NORTH);
        card.add(StudioToolbar.scroll(hereList), BorderLayout.CENTER);
        return card;
    }

    private JPanel buildOverviewCard() {
        JPanel card = new JPanel(new BorderLayout());
        StudioToolbar.makeSearchField(searchField, "Search biomes or blocks...");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                rebuildOverview();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                rebuildOverview();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                rebuildOverview();
            }
        });

        overviewTree.setRootVisible(false);
        overviewTree.setShowsRootHandles(true);
        overviewTree.setRowHeight(StudioTheme.scale(24));
        overviewTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        overviewTree.setCellRenderer(new OverviewRenderer());
        overviewTree.addTreeSelectionListener(event -> {
            if (!updatingSelection && nodeAt(event.getPath()) instanceof OverviewNode node && node.biomeKey() != null) {
                choose(node.biomeKey(), node.palette());
            }
        });
        card.add(StudioToolbar.padded(searchField, 8, 8, 8, 8), BorderLayout.NORTH);
        card.add(StudioToolbar.scroll(overviewTree), BorderLayout.CENTER);
        return card;
    }

    // Choosing a biome rather than one of its palettes opens its first palette.
    private void choose(String biomeKey, BiomePalette palette) {
        BiomeEntry biome = catalog == null ? null : catalog.biome(biomeKey);
        BiomePalette chosen = palette != null ? palette : biome == null ? null : biome.firstPalette();
        if (chosen != null) {
            listener.onPaletteChosen(chosen);
        }
    }

    private void refreshHere() {
        updatingSelection = true;
        try {
            hereRows.clear();
            if (untracked) {
                showHereMessage("Run /iris std pal in-game to see the palettes where you stand.");
                return;
            }
            if (location == null || catalog == null) {
                showHereMessage("Finding where you are...");
                return;
            }
            switch (location.status()) {
                case OFFLINE -> showHereMessage("You are offline.");
                case IN_OTHER_WORLD -> showHereMessage("You left the world this window shows.");
                case IN_REGION -> showHereRows(location);
            }
            selectInHereList();
        } finally {
            updatingSelection = false;
        }
    }

    private void showHereMessage(String message) {
        regionLabel.setText("<html><body style='width:" + StudioTheme.scale(MESSAGE_WRAP_WIDTH_PIXELS) + "px'>" + message + "</body></html>");
    }

    private void showHereRows(PlayerLocation here) {
        RegionEntry region = catalog.region(here.regionKey());
        regionLabel.setText(labeled("Region", region == null ? here.regionKey() : region.name(), here.regionKey()));
        addBiomePalettes("Surface biome here", here.surfaceBiomeKey());
        if (here.isBelowSurfaceBiome()) {
            addBiomePalettes("Underground biome at your height", here.biomeAtPositionKey());
        }
        if (region == null) {
            return;
        }
        hereRows.addElement(HereRow.header("Biomes in this region"));
        for (String biomeKey : region.allBiomeKeys()) {
            addRegionBiome(biomeKey, 0, new HashSet<>(), here);
        }
    }

    private void addBiomePalettes(String title, String biomeKey) {
        BiomeEntry biome = catalog.biome(biomeKey);
        if (biome == null) {
            return;
        }
        hereRows.addElement(HereRow.header(title));
        hereRows.addElement(HereRow.biome(biomeText(biome, childOfText(biome)), biomeKey, 0));
        addPaletteRows(biome, 0);
    }

    private void addPaletteRows(BiomeEntry biome, int nesting) {
        for (PaletteCatalog.PaletteSummary summary : biome.palettes()) {
            hereRows.addElement(HereRow.palette(summary.text(), summary.palette(), nesting));
        }
    }

    // Children sit under the biome they grow inside of, so a child biome is found where it actually appears.
    private void addRegionBiome(String biomeKey, int nesting, Set<String> ancestors, PlayerLocation here) {
        BiomeEntry biome = catalog.biome(biomeKey);
        if (biome == null || !ancestors.add(biomeKey)) {
            return;
        }
        boolean standingHere = biomeKey.equals(here.surfaceBiomeKey()) || biomeKey.equals(here.biomeAtPositionKey());
        hereRows.addElement(HereRow.biome(biomeText(biome, standingHere ? "you are here" : null), biomeKey, nesting));
        addPaletteRows(biome, nesting);
        for (String childKey : biome.childKeys()) {
            addRegionBiome(childKey, nesting + 1, new HashSet<>(ancestors), here);
        }
    }

    private String childOfText(BiomeEntry biome) {
        if (biome.parentKeys().isEmpty()) {
            return null;
        }
        List<String> parentNames = biome.parentKeys().stream()
                .map(key -> catalog.biome(key) == null ? key : catalog.biome(key).name())
                .toList();
        return "child of " + String.join(", ", parentNames);
    }

    private void rebuildOverview() {
        if (catalog == null) {
            return;
        }
        String filter = searchField.getText().trim().toLowerCase(Locale.ROOT);
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        for (RegionEntry region : catalog.regions()) {
            boolean regionMatches = !filter.isEmpty() && region.name().toLowerCase(Locale.ROOT).contains(filter);
            DefaultMutableTreeNode regionNode = new DefaultMutableTreeNode(new OverviewNode(region.name(), region.key(), null, null));
            for (BiomeGroup group : region.groups()) {
                DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode();
                for (String biomeKey : group.biomeKeys()) {
                    addBiomeNode(groupNode, biomeKey, regionMatches ? "" : filter, new HashSet<>());
                }
                if (groupNode.getChildCount() > 0) {
                    groupNode.setUserObject(new OverviewNode(group.label(), groupNode.getChildCount() + " biomes", null, null));
                    regionNode.add(groupNode);
                }
            }
            if (regionNode.getChildCount() > 0) {
                root.add(regionNode);
            }
        }
        DefaultMutableTreeNode outside = new DefaultMutableTreeNode();
        for (String biomeKey : catalog.biomesOutsideRegions()) {
            addBiomeNode(outside, biomeKey, filter, new HashSet<>());
        }
        if (outside.getChildCount() > 0) {
            outside.setUserObject(new OverviewNode("Not in any region", outside.getChildCount() + " biomes", null, null));
            root.add(outside);
        }
        updatingSelection = true;
        try {
            treeModel.setRoot(root);
            if (!filter.isEmpty()) {
                expandDownToBiomes(root);
            }
            selectInTree();
        } finally {
            updatingSelection = false;
        }
    }

    private void addBiomeNode(DefaultMutableTreeNode parent, String biomeKey, String filter, Set<String> ancestors) {
        BiomeEntry biome = catalog.biome(biomeKey);
        if (biome == null || !ancestors.add(biomeKey)) {
            return;
        }
        DefaultMutableTreeNode biomeNode = new DefaultMutableTreeNode(new OverviewNode(biome.name(), biomeKey, biomeKey, null));
        for (PaletteCatalog.PaletteSummary summary : biome.palettes()) {
            biomeNode.add(new DefaultMutableTreeNode(new OverviewNode(summary.text(), null, biomeKey, summary.palette())));
        }
        for (String childKey : biome.childKeys()) {
            addBiomeNode(biomeNode, childKey, filter, new HashSet<>(ancestors));
        }
        boolean hasMatchingChild = biomeNode.getChildCount() > biome.palettes().size();
        if (filter.isEmpty() || biome.matches(filter) || hasMatchingChild) {
            parent.add(biomeNode);
        }
    }

    private void expandDownToBiomes(DefaultMutableTreeNode root) {
        Enumeration<?> nodes = root.breadthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
            if (node.getUserObject() instanceof OverviewNode overview && overview.biomeKey() != null) {
                overviewTree.expandPath(new TreePath(((DefaultMutableTreeNode) node.getParent()).getPath()));
            }
        }
    }

    private void selectInTree() {
        DefaultMutableTreeNode match = findLayerNode((DefaultMutableTreeNode) treeModel.getRoot());
        if (match == null) {
            overviewTree.clearSelection();
            return;
        }
        TreePath path = new TreePath(match.getPath());
        overviewTree.setSelectionPath(path);
        overviewTree.makeVisible(path);
        Rectangle row = overviewTree.getPathBounds(path);
        if (row != null) {
            overviewTree.scrollRectToVisible(new Rectangle(0, row.y, 1, row.height));
        }
    }

    private DefaultMutableTreeNode findLayerNode(DefaultMutableTreeNode root) {
        Enumeration<?> nodes = root.depthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
            if (node.getUserObject() instanceof OverviewNode overview
                    && overview.palette() != null
                    && overview.palette().equals(selectedPalette)) {
                return node;
            }
        }
        return null;
    }

    private void selectInHereList() {
        for (int index = 0; index < hereRows.size(); index++) {
            HereRow row = hereRows.get(index);
            if (row.palette() != null && row.palette().equals(selectedPalette)) {
                hereList.setSelectedIndex(index);
                return;
            }
        }
        hereList.clearSelection();
    }

    private static Object nodeAt(TreePath path) {
        return path == null ? null : ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
    }

    private static String biomeText(BiomeEntry biome, String note) {
        String noteText = note == null ? "" : " · " + escape(note);
        return "<html>" + escape(biome.name()) + noteText + " <font color='" + DETAIL_COLOR + "'>" + escape(biome.key())
                + "</font></html>";
    }

    private static String labeled(String title, String name, String key) {
        return "<html><font color='" + DETAIL_COLOR + "'>" + title + "</font>&nbsp; " + escape(name)
                + " <font color='" + DETAIL_COLOR + "'>" + escape(key) + "</font></html>";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    interface Listener {
        void onPaletteChosen(BiomePalette palette);
    }

    /**
     * @param nesting how many parent biomes deep the row's biome sits
     */
    private record HereRow(String text, String biomeKey, BiomePalette palette, int nesting, boolean header) {
        private static HereRow header(String text) {
            return new HereRow(text, null, null, 0, true);
        }

        private static HereRow biome(String text, String biomeKey, int nesting) {
            return new HereRow(text, biomeKey, null, nesting, false);
        }

        private static HereRow palette(String text, BiomePalette palette, int nesting) {
            return new HereRow(text, palette.biomeKey(), palette, nesting, false);
        }
    }

    private record OverviewNode(String label, String detail, String biomeKey, BiomePalette palette) {
        private String html() {
            if (detail == null) {
                return escape(label);
            }
            return "<html>" + escape(label) + " <font color='" + DETAIL_COLOR + "'>" + escape(detail) + "</font></html>";
        }
    }

    private static final class HereRowRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 2785341117408251320L;
        private static final int HEADER_INDENT = 11;
        private static final int BIOME_INDENT = 20;
        private static final int PALETTE_INDENT = 34;
        private static final int CHILD_INDENT = 16;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
            HereRow row = (HereRow) value;
            boolean selectable = row.biomeKey() != null;
            super.getListCellRendererComponent(list, row.header() ? row.text().toUpperCase(Locale.ROOT) : row.text(), index,
                    selected && selectable, false);
            setFont(row.header() ? StudioTheme.sectionFont() : StudioTheme.font());
            if (row.header()) {
                setForeground(StudioTheme.MUTED_TEXT);
            }
            int indent = row.header() ? HEADER_INDENT
                    : (row.palette() != null ? PALETTE_INDENT : BIOME_INDENT) + row.nesting() * CHILD_INDENT;
            setBorder(StudioTheme.emptyBorder(row.header() ? 11 : 4, indent, 4, 10));
            return this;
        }
    }

    private static final class OverviewRenderer extends DefaultTreeCellRenderer {
        private static final long serialVersionUID = -1950176034017718227L;

        private OverviewRenderer() {
            setLeafIcon(null);
            setOpenIcon(null);
            setClosedIcon(null);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
                                                      boolean leaf, int row, boolean focus) {
            Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
            String text = userObject instanceof OverviewNode node ? node.html() : "";
            super.getTreeCellRendererComponent(tree, text, selected, expanded, leaf, row, false);
            setFont(userObject instanceof OverviewNode node && node.biomeKey() == null ? StudioTheme.boldFont() : StudioTheme.font());
            return this;
        }
    }
}
