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

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Every object in the pack as a folder tree, with a search that matches every word typed anywhere in an object's path.
 */
final class ObjectBrowser extends JPanel {
    private static final long serialVersionUID = 6795434561028735214L;
    private static final int ROW_HEIGHT = 24;

    private final Consumer<String> onObjectChosen;
    private final JTextField searchField = new JTextField();
    private final DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    private final JTree tree = new JTree(treeModel);
    private final JLabel countLabel = new JLabel("Loading objects...");
    private List<String> objectKeys = List.of();
    private String chosenKey;
    private boolean updatingSelection;

    ObjectBrowser(Consumer<String> onObjectChosen) {
        super(new BorderLayout());
        this.onObjectChosen = onObjectChosen;
        StudioToolbar.makeSearchField(searchField, "Search objects...");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                rebuildTree();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                rebuildTree();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                rebuildTree();
            }
        });
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(StudioTheme.scale(ROW_HEIGHT));
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new EntryRenderer());
        ToolTipManager.sharedInstance().registerComponent(tree);
        tree.addTreeSelectionListener(event -> {
            if (!updatingSelection && nodeAt(event.getPath()) instanceof Entry entry && entry.isObject()) {
                chosenKey = entry.key();
                onObjectChosen.accept(entry.key());
            }
        });
        countLabel.setFont(StudioTheme.smallFont());
        countLabel.setForeground(StudioTheme.SECONDARY_TEXT);
        add(StudioToolbar.padded(searchField, 8, 8, 8, 8), BorderLayout.NORTH);
        add(StudioToolbar.scroll(tree), BorderLayout.CENTER);
        add(StudioToolbar.padded(countLabel, 6, 12, 8, 12), BorderLayout.SOUTH);
    }

    void showObjects(List<String> keys) {
        objectKeys = keys.stream().sorted(Comparator.comparing(key -> key.toLowerCase(Locale.ROOT))).toList();
        countLabel.setText(objectKeys.size() == 1 ? "1 object" : objectKeys.size() + " objects");
        rebuildTree();
    }

    void showError(String text) {
        countLabel.setText(text);
    }

    String firstObject() {
        return objectKeys.isEmpty() ? null : objectKeys.get(0);
    }

    private void rebuildTree() {
        String[] words = searchField.getText().trim().toLowerCase(Locale.ROOT).split("\\s+");
        boolean searching = !searchField.getText().isBlank();
        DefaultMutableTreeNode root = searching ? searchResults(words) : folderTree();
        treeModel.setRoot(root);
        if (searching) {
            expandAll();
        }
        selectChosen();
    }

    private DefaultMutableTreeNode searchResults(String[] words) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        for (String key : objectKeys) {
            if (matchesEveryWord(key, words)) {
                root.add(new DefaultMutableTreeNode(Entry.object(key, key)));
            }
        }
        if (root.getChildCount() == 0) {
            root.add(new DefaultMutableTreeNode(Entry.note("No matching objects")));
        }
        return root;
    }

    private static boolean matchesEveryWord(String key, String[] words) {
        String lowerKey = key.toLowerCase(Locale.ROOT);
        for (String word : words) {
            if (!lowerKey.contains(word)) {
                return false;
            }
        }
        return true;
    }

    // Folders come before the objects beside them, like a file browser.
    private DefaultMutableTreeNode folderTree() {
        Folder top = new Folder();
        for (String key : objectKeys) {
            Folder folder = top;
            String[] parts = key.split("/");
            for (int index = 0; index < parts.length - 1; index++) {
                folder = folder.subfolders.computeIfAbsent(parts[index], name -> new Folder());
            }
            folder.objects.add(key);
        }
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        addFolderContents(root, top, "");
        return root;
    }

    private static void addFolderContents(DefaultMutableTreeNode node, Folder folder, String path) {
        for (Map.Entry<String, Folder> subfolder : folder.subfolders.entrySet()) {
            String subfolderPath = path + subfolder.getKey() + "/";
            DefaultMutableTreeNode child = new DefaultMutableTreeNode(Entry.folder(subfolder.getKey(), subfolderPath));
            addFolderContents(child, subfolder.getValue(), subfolderPath);
            node.add(child);
        }
        for (String key : folder.objects) {
            node.add(new DefaultMutableTreeNode(Entry.object(key.substring(key.lastIndexOf('/') + 1), key)));
        }
    }

    void select(String key) {
        chosenKey = key;
        selectChosen();
    }

    private void selectChosen() {
        DefaultMutableTreeNode node = chosenKey == null ? null : findObjectNode((DefaultMutableTreeNode) treeModel.getRoot());
        updatingSelection = true;
        try {
            if (node == null) {
                tree.clearSelection();
                return;
            }
            TreePath path = new TreePath(node.getPath());
            tree.setSelectionPath(path);
            tree.scrollPathToVisible(path);
        } finally {
            updatingSelection = false;
        }
    }

    private DefaultMutableTreeNode findObjectNode(DefaultMutableTreeNode node) {
        if (node.getUserObject() instanceof Entry entry && entry.isObject() && entry.key().equals(chosenKey)) {
            return node;
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            DefaultMutableTreeNode found = findObjectNode((DefaultMutableTreeNode) node.getChildAt(index));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void expandAll() {
        for (int row = 0; row < tree.getRowCount(); row++) {
            tree.expandRow(row);
        }
    }

    private static Object nodeAt(TreePath path) {
        return path == null ? null : ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
    }

    private static final class Folder {
        private final Map<String, Folder> subfolders = new LinkedHashMap<>();
        private final List<String> objects = new ArrayList<>();
    }

    /**
     * A row of the tree: an object with its key, a folder with its path, or a note without either.
     */
    private record Entry(String label, String key, String folderPath) {
        static Entry object(String label, String key) {
            return new Entry(label, key, null);
        }

        static Entry folder(String label, String path) {
            return new Entry(label, null, path);
        }

        static Entry note(String text) {
            return new Entry(text, null, null);
        }

        boolean isObject() {
            return key != null;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class EntryRenderer extends DefaultTreeCellRenderer {
        private static final long serialVersionUID = -4316652201793418740L;

        private EntryRenderer() {
            setLeafIcon(null);
            setOpenIcon(null);
            setClosedIcon(null);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
                                                      boolean leaf, int row, boolean focus) {
            Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
            super.getTreeCellRendererComponent(tree, userObject == null ? "" : userObject.toString(), selected, expanded, leaf, row, false);
            boolean isNote = userObject instanceof Entry entry && !entry.isObject() && entry.folderPath() == null;
            setFont(StudioTheme.font());
            setForeground(isNote ? StudioTheme.MUTED_TEXT : StudioTheme.TEXT);
            setToolTipText(userObject instanceof Entry entry ? entry.isObject() ? entry.key() : entry.folderPath() : null);
            return this;
        }
    }
}
