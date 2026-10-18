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
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
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
import java.util.Locale;
import java.util.Set;

import static com.volmit.iris.core.gui.NoiseExplorerGUI.ACCENT;
import static com.volmit.iris.core.gui.NoiseExplorerGUI.BORDER;
import static com.volmit.iris.core.gui.NoiseExplorerGUI.FIELD_BACKGROUND;
import static com.volmit.iris.core.gui.NoiseExplorerGUI.HEADER_FONT;
import static com.volmit.iris.core.gui.NoiseExplorerGUI.ITEM_FONT;
import static com.volmit.iris.core.gui.NoiseExplorerGUI.PANEL_BACKGROUND;
import static com.volmit.iris.core.gui.NoiseExplorerGUI.PRIMARY_TEXT;
import static com.volmit.iris.core.gui.NoiseExplorerGUI.SECONDARY_TEXT;
import static com.volmit.iris.core.gui.NoiseExplorerGUI.SIDEBAR_BACKGROUND;

final class PaletteNavigator extends JPanel {
    private static final long serialVersionUID = -6402313718430917284L;
    private static final String HERE_CARD = "here";
    private static final String OVERVIEW_CARD = "overview";
    private static final String DETAIL_COLOR = "#8890A0";

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
    private String selectedBiome;
    private int selectedLayer;
    private boolean updatingSelection;

    PaletteNavigator(Listener listener) {
        super(new BorderLayout());
        this.listener = listener;
        setBackground(SIDEBAR_BACKGROUND);
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

    void markSelected(String biomeKey, int layer) {
        selectedBiome = biomeKey;
        selectedLayer = layer;
        updatingSelection = true;
        try {
            selectInHereList();
            selectInTree();
        } finally {
            updatingSelection = false;
        }
    }

    private JPanel buildTabBar() {
        JPanel bar = new JPanel(new GridLayout(1, 2, 6, 0));
        bar.setBackground(SIDEBAR_BACKGROUND);
        bar.setBorder(BorderFactory.createEmptyBorder(0, 12, 8, 12));
        ButtonGroup group = new ButtonGroup();
        JToggleButton here = createTab("Here", HERE_CARD, group);
        JToggleButton overview = createTab("Overview", OVERVIEW_CARD, group);
        bar.add(here);
        bar.add(overview);
        here.doClick();
        return bar;
    }

    private JToggleButton createTab(String text, String card, ButtonGroup group) {
        JToggleButton tab = ExplorerStyle.toggleButton(text);
        tab.addActionListener(event -> cards.show(cardPanel, card));
        group.add(tab);
        return tab;
    }

    private JPanel buildHereCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(SIDEBAR_BACKGROUND);
        JPanel location = new JPanel(new GridLayout(0, 1, 0, 3));
        location.setBackground(PANEL_BACKGROUND);
        location.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, BORDER),
                BorderFactory.createEmptyBorder(9, 12, 9, 12)
        ));
        regionLabel.setForeground(PRIMARY_TEXT);
        regionLabel.setFont(ITEM_FONT);
        location.add(regionLabel);

        hereList.setBackground(SIDEBAR_BACKGROUND);
        hereList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        hereList.setCellRenderer(new HereRowRenderer());
        hereList.addListSelectionListener(event -> {
            HereRow row = hereList.getSelectedValue();
            if (!event.getValueIsAdjusting() && !updatingSelection && row != null && row.biomeKey() != null) {
                chooseBiome(row.biomeKey(), Math.max(0, row.layer()));
            }
        });
        card.add(location, BorderLayout.NORTH);
        card.add(scroll(hereList), BorderLayout.CENTER);
        return card;
    }

    private JPanel buildOverviewCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(SIDEBAR_BACKGROUND);
        searchField.setBackground(FIELD_BACKGROUND);
        searchField.setForeground(PRIMARY_TEXT);
        searchField.setCaretColor(PRIMARY_TEXT);
        searchField.setFont(ITEM_FONT);
        searchField.putClientProperty("JTextField.placeholderText", "Search biomes or blocks...");
        searchField.setToolTipText("Search biomes or blocks");
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, BORDER),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
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
        overviewTree.setBackground(SIDEBAR_BACKGROUND);
        overviewTree.setRowHeight(22);
        overviewTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        overviewTree.setCellRenderer(new OverviewRenderer());
        overviewTree.addTreeSelectionListener(event -> {
            if (!updatingSelection && nodeAt(event.getPath()) instanceof OverviewNode node && node.biomeKey() != null) {
                chooseBiome(node.biomeKey(), Math.max(0, node.layer()));
            }
        });
        card.add(searchField, BorderLayout.NORTH);
        card.add(scroll(overviewTree), BorderLayout.CENTER);
        return card;
    }

    private static JScrollPane scroll(Component content) {
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getViewport().setBackground(SIDEBAR_BACKGROUND);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        return scrollPane;
    }

    private void chooseBiome(String biomeKey, int preferredLayer) {
        BiomeEntry biome = catalog == null ? null : catalog.biome(biomeKey);
        if (biome == null || biome.layerSummaries().isEmpty()) {
            return;
        }
        listener.onLayerChosen(biomeKey, Math.min(preferredLayer, biome.layerSummaries().size() - 1));
    }

    private void refreshHere() {
        updatingSelection = true;
        try {
            hereRows.clear();
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
        regionLabel.setText(message);
    }

    private void showHereRows(PlayerLocation here) {
        RegionEntry region = catalog.region(here.regionKey());
        regionLabel.setText(labeled("Region", region == null ? here.regionKey() : region.name(), here.regionKey()));
        addBiomeLayers("Surface biome here", here.surfaceBiomeKey());
        if (here.isBelowSurfaceBiome()) {
            addBiomeLayers("Underground biome at your height", here.biomeAtPositionKey());
        }
        if (region == null) {
            return;
        }
        hereRows.addElement(HereRow.header("Other biomes in this region"));
        for (String otherKey : region.allBiomeKeys()) {
            BiomeEntry other = catalog.biome(otherKey);
            boolean shownAbove = otherKey.equals(here.surfaceBiomeKey()) || otherKey.equals(here.biomeAtPositionKey());
            if (!shownAbove && other != null) {
                hereRows.addElement(new HereRow(biomeText(other), otherKey, -1, false));
            }
        }
    }

    private void addBiomeLayers(String title, String biomeKey) {
        BiomeEntry biome = catalog.biome(biomeKey);
        if (biome == null) {
            return;
        }
        hereRows.addElement(HereRow.header(title));
        hereRows.addElement(new HereRow(biomeText(biome), biomeKey, -1, false));
        for (int layer = 0; layer < biome.layerSummaries().size(); layer++) {
            hereRows.addElement(new HereRow(layerText(layer, biome), biomeKey, layer, false));
        }
    }

    private void rebuildOverview() {
        if (catalog == null) {
            return;
        }
        String filter = searchField.getText().trim().toLowerCase(Locale.ROOT);
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        for (RegionEntry region : catalog.regions()) {
            boolean regionMatches = !filter.isEmpty() && region.name().toLowerCase(Locale.ROOT).contains(filter);
            DefaultMutableTreeNode regionNode = new DefaultMutableTreeNode(new OverviewNode(region.name(), region.key(), null, -1));
            for (BiomeGroup group : region.groups()) {
                DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode();
                for (String biomeKey : group.biomeKeys()) {
                    addBiomeNode(groupNode, biomeKey, regionMatches ? "" : filter, new HashSet<>());
                }
                if (groupNode.getChildCount() > 0) {
                    groupNode.setUserObject(new OverviewNode(group.label(), groupNode.getChildCount() + " biomes", null, -1));
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
            outside.setUserObject(new OverviewNode("Not in any region", outside.getChildCount() + " biomes", null, -1));
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
        DefaultMutableTreeNode biomeNode = new DefaultMutableTreeNode(new OverviewNode(biome.name(), biomeKey, biomeKey, -1));
        for (int layer = 0; layer < biome.layerSummaries().size(); layer++) {
            biomeNode.add(new DefaultMutableTreeNode(new OverviewNode(layerText(layer, biome), null, biomeKey, layer)));
        }
        for (String childKey : biome.childKeys()) {
            addBiomeNode(biomeNode, childKey, filter, new HashSet<>(ancestors));
        }
        boolean hasMatchingChild = biomeNode.getChildCount() > biome.layerSummaries().size();
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
                    && overview.layer() == selectedLayer
                    && overview.biomeKey() != null
                    && overview.biomeKey().equals(selectedBiome)) {
                return node;
            }
        }
        return null;
    }

    private void selectInHereList() {
        for (int index = 0; index < hereRows.size(); index++) {
            HereRow row = hereRows.get(index);
            if (row.layer() == selectedLayer && row.biomeKey() != null && row.biomeKey().equals(selectedBiome)) {
                hereList.setSelectedIndex(index);
                return;
            }
        }
        hereList.clearSelection();
    }

    private static Object nodeAt(TreePath path) {
        return path == null ? null : ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
    }

    private static String layerText(int layer, BiomeEntry biome) {
        return "Layer " + layer + " · " + biome.layerSummaries().get(layer);
    }

    private static String biomeText(BiomeEntry biome) {
        return "<html>" + escape(biome.name()) + " <font color='" + DETAIL_COLOR + "'>" + escape(biome.key()) + "</font></html>";
    }

    private static String labeled(String title, String name, String key) {
        return "<html><font color='" + DETAIL_COLOR + "'>" + title + "</font>&nbsp; " + escape(name)
                + " <font color='" + DETAIL_COLOR + "'>" + escape(key) + "</font></html>";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    interface Listener {
        void onLayerChosen(String biomeKey, int layer);
    }

    private record HereRow(String text, String biomeKey, int layer, boolean header) {
        private static HereRow header(String text) {
            return new HereRow(text, null, -1, true);
        }
    }

    private record OverviewNode(String label, String detail, String biomeKey, int layer) {
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
        private static final int LAYER_INDENT = 34;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
            HereRow row = (HereRow) value;
            boolean selectable = row.biomeKey() != null;
            super.getListCellRendererComponent(list, row.text(), index, selected && selectable, false);
            setOpaque(true);
            setFont(row.header() ? HEADER_FONT : ITEM_FONT);
            setForeground(row.header() ? ACCENT : selected ? PRIMARY_TEXT : SECONDARY_TEXT);
            setBackground(selected && selectable ? FIELD_BACKGROUND.brighter() : SIDEBAR_BACKGROUND);
            int indent = row.header() ? HEADER_INDENT : row.layer() >= 0 ? LAYER_INDENT : BIOME_INDENT;
            setBorder(BorderFactory.createEmptyBorder(row.header() ? 11 : 4, indent, 4, 10));
            return this;
        }
    }

    private static final class OverviewRenderer extends DefaultTreeCellRenderer {
        private static final long serialVersionUID = -1950176034017718227L;

        private OverviewRenderer() {
            setLeafIcon(null);
            setOpenIcon(null);
            setClosedIcon(null);
            setBackgroundNonSelectionColor(SIDEBAR_BACKGROUND);
            setBackgroundSelectionColor(FIELD_BACKGROUND.brighter());
            setBorderSelectionColor(null);
            setTextNonSelectionColor(SECONDARY_TEXT);
            setTextSelectionColor(PRIMARY_TEXT);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
                                                      boolean leaf, int row, boolean focus) {
            Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
            String text = userObject instanceof OverviewNode node ? node.html() : "";
            super.getTreeCellRendererComponent(tree, text, selected, expanded, leaf, row, false);
            setFont(userObject instanceof OverviewNode node && node.biomeKey() == null ? HEADER_FONT : ITEM_FONT);
            return this;
        }
    }
}
