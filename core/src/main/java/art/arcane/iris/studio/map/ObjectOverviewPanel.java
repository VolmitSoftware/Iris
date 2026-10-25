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

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The chosen object's size and the blocks it is made of, most used first, like the overview in Procedural Objects.
 * Clicking a block makes it see-through in the 3D view, to look past walls into the object.
 */
final class ObjectOverviewPanel extends JPanel {
    private static final long serialVersionUID = -8811407613645718290L;
    private static final int PANEL_WIDTH = 280;
    private static final int ROW_HEIGHT = 26;
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 8;
    private static final int ROW_PADDING = 10;
    private static final int SHARE_WIDTH = 92;
    private static final int SHARE_BAR_HEIGHT = 4;
    private static final float SEE_THROUGH_ICON_OPACITY = 0.3F;
    private static final String SEE_THROUGH_HINT = "Click a block to see through it";

    private final JLabel title = new JLabel(" ");
    private final JLabel path = new JLabel(" ");
    private final JLabel size = statValue();
    private final JLabel blockCount = statValue();
    private final JLabel blockTypes = statValue();
    private final JLabel filled = statValue();
    private final BakeMeter bakeMeter = new BakeMeter();
    private final JTextField filterField = new JTextField();
    private final DefaultListModel<ObjectSummary.BlockUse> rows = new DefaultListModel<>();
    private final JList<ObjectSummary.BlockUse> list = new JList<>(rows) {
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }
    };
    private final JLabel seeThroughHint = new JLabel(SEE_THROUGH_HINT);
    private final JButton showAllSolid = new JButton("Show all solid");
    private final Set<String> seeThroughTypes = new LinkedHashSet<>();
    private final Consumer<Set<String>> onSeeThroughChanged;
    private ObjectSummary summary;

    /**
     * @param onSeeThroughChanged told which block types to draw see-through whenever the user changes them
     * @param showsBakeTime       whether the objects are baked, so the time their bake takes is shown
     */
    ObjectOverviewPanel(Consumer<Set<String>> onSeeThroughChanged, boolean showsBakeTime) {
        super(new BorderLayout());
        this.onSeeThroughChanged = onSeeThroughChanged;
        setPreferredSize(StudioTheme.scaledSize(PANEL_WIDTH, 0));
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, StudioTheme.SEPARATOR));
        setBackground(StudioTheme.PANEL_BACKGROUND);
        add(buildHeader(showsBakeTime), BorderLayout.NORTH);
        add(buildBlockList(), BorderLayout.CENTER);
    }

    void show(ObjectSummary nextSummary) {
        summary = nextSummary;
        title.setText(nextSummary.name());
        path.setText(nextSummary.key());
        path.setToolTipText(nextSummary.key());
        size.setText(nextSummary.sizeX() + " × " + nextSummary.sizeY() + " × " + nextSummary.sizeZ());
        blockCount.setText(formatCount(nextSummary.blockCount()));
        blockTypes.setText(formatCount(nextSummary.blockUses().size()));
        filled.setText(String.format(Locale.ROOT, "%.1f%%", nextSummary.filledFraction() * 100D));
        seeThroughTypes.clear();
        showSeeThroughState();
        refilter();
    }

    void showTimingBake() {
        bakeMeter.showTiming();
    }

    void showBakeTiming(BakeTiming timing) {
        bakeMeter.show(timing);
    }

    void clear() {
        summary = null;
        title.setText(" ");
        path.setText(" ");
        for (JLabel value : List.of(size, blockCount, blockTypes, filled)) {
            value.setText("—");
        }
        bakeMeter.clear();
        rows.clear();
        seeThroughTypes.clear();
        showSeeThroughState();
    }

    private JPanel buildHeader(boolean showsBakeTime) {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.setBorder(StudioTheme.emptyBorder(12, 16, 8, 16));
        title.setFont(StudioTheme.titleFont());
        path.setFont(StudioTheme.smallFont());
        path.setForeground(StudioTheme.SECONDARY_TEXT);
        header.add(leftAligned(title));
        header.add(leftAligned(path));

        JPanel stats = new JPanel(new GridLayout(2, 2, StudioTheme.scale(12), StudioTheme.scale(8)));
        stats.setOpaque(false);
        stats.setBorder(StudioTheme.emptyBorder(12, 0, 4, 0));
        stats.add(stat("Size · X × Y × Z", size));
        stats.add(stat("Blocks", blockCount));
        stats.add(stat("Block types", blockTypes));
        stats.add(stat("Filled volume", filled));
        filled.setToolTipText("How much of the object's bounding box its blocks fill");
        header.add(leftAligned(stats));
        if (showsBakeTime) {
            JPanel bakeTime = stat("Bake time", bakeMeter);
            bakeTime.setBorder(StudioTheme.emptyBorder(4, 0, 4, 0));
            header.add(leftAligned(bakeTime));
        }
        clear();
        return header;
    }

    private JPanel buildBlockList() {
        JPanel blocks = new JPanel(new BorderLayout());
        blocks.setOpaque(false);
        StudioToolbar.makeSearchField(filterField, "Find a block...");
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                refilter();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                refilter();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                refilter();
            }
        });
        list.setFocusable(false);
        list.setFixedCellHeight(StudioTheme.scale(ROW_HEIGHT));
        list.setCellRenderer(new BlockUseRenderer());
        list.setBackground(StudioTheme.PANEL_BACKGROUND);
        list.setBorder(StudioTheme.emptyBorder(0, 6, 4, 6));
        list.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                int row = list.locationToIndex(event.getPoint());
                if (SwingUtilities.isLeftMouseButton(event) && row >= 0 && list.getCellBounds(row, row).contains(event.getPoint())) {
                    toggleSeeThrough(rows.get(row).blockType());
                }
            }
        });
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);
        top.add(leftAligned(StudioToolbar.padded(filterField, 4, 12, 4, 12)));
        top.add(leftAligned(StudioToolbar.padded(seeThroughRow(), 0, 14, 6, 12)));
        blocks.add(top, BorderLayout.NORTH);
        blocks.add(StudioToolbar.scroll(list), BorderLayout.CENTER);
        return blocks;
    }

    private JPanel seeThroughRow() {
        seeThroughHint.setFont(StudioTheme.smallFont());
        seeThroughHint.setForeground(StudioTheme.MUTED_TEXT);
        showAllSolid.setFont(StudioTheme.smallFont());
        showAllSolid.setForeground(StudioTheme.LINK);
        showAllSolid.setBorderPainted(false);
        showAllSolid.setContentAreaFilled(false);
        showAllSolid.setFocusable(false);
        showAllSolid.setMargin(new Insets(0, 0, 0, 0));
        showAllSolid.setBorder(BorderFactory.createEmptyBorder());
        showAllSolid.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        showAllSolid.addActionListener(event -> {
            seeThroughTypes.clear();
            seeThroughChanged();
        });
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.add(seeThroughHint, BorderLayout.CENTER);
        row.add(showAllSolid, BorderLayout.EAST);
        showSeeThroughState();
        return row;
    }

    private void toggleSeeThrough(String blockType) {
        if (!seeThroughTypes.remove(blockType)) {
            seeThroughTypes.add(blockType);
        }
        seeThroughChanged();
    }

    private void seeThroughChanged() {
        showSeeThroughState();
        list.repaint();
        onSeeThroughChanged.accept(Set.copyOf(seeThroughTypes));
    }

    private void showSeeThroughState() {
        int count = seeThroughTypes.size();
        seeThroughHint.setText(count == 0 ? SEE_THROUGH_HINT : count == 1 ? "1 block see-through" : count + " blocks see-through");
        showAllSolid.setVisible(count > 0);
    }

    private void refilter() {
        rows.clear();
        if (summary == null) {
            return;
        }
        String filter = filterField.getText().trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        for (ObjectSummary.BlockUse use : summary.blockUses()) {
            if (use.blockType().contains(filter)) {
                rows.addElement(use);
            }
        }
    }

    private static JPanel stat(String label, JComponent value) {
        JPanel stat = new JPanel(new BorderLayout());
        stat.setOpaque(false);
        JLabel name = new JLabel(label);
        name.setFont(StudioTheme.smallFont());
        name.setForeground(StudioTheme.MUTED_TEXT);
        stat.add(name, BorderLayout.NORTH);
        stat.add(value, BorderLayout.CENTER);
        return stat;
    }

    private static JLabel statValue() {
        JLabel value = new JLabel("—");
        value.setFont(StudioTheme.boldFont());
        return value;
    }

    private static Component leftAligned(JPanel panel) {
        panel.setAlignmentX(LEFT_ALIGNMENT);
        return panel;
    }

    private static Component leftAligned(JLabel label) {
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private static String formatCount(int count) {
        return String.format(Locale.ROOT, "%,d", count);
    }

    private final class BlockUseRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 2785337045812613941L;
        private transient BufferedImage icon;
        private boolean seeThrough;
        private double share;
        private String percent = "";

        @Override
        public Component getListCellRendererComponent(JList<?> owner, Object value, int index, boolean selected, boolean focused) {
            ObjectSummary.BlockUse use = (ObjectSummary.BlockUse) value;
            super.getListCellRendererComponent(owner, use.blockType(), index, false, false);
            seeThrough = seeThroughTypes.contains(use.blockType());
            setFont(StudioTheme.font());
            setForeground(seeThrough ? StudioTheme.MUTED_TEXT : StudioTheme.TEXT);
            setBorder(StudioTheme.emptyBorder(0, ROW_PADDING + ICON_SIZE + ICON_GAP, 0, ROW_PADDING + SHARE_WIDTH));
            setToolTipText(use.blockType() + " · " + formatCount(use.count()) + " blocks · click to "
                    + (seeThrough ? "make solid again" : "see through it"));
            icon = use.icon();
            share = summary == null || summary.blockCount() == 0 ? 0D : use.count() / (double) summary.blockCount();
            percent = share > 0D && share < 0.001D ? "<0.1%" : String.format(Locale.ROOT, "%.1f%%", share * 100D);
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D canvas = (Graphics2D) graphics.create();
            try {
                int iconSize = StudioTheme.scale(ICON_SIZE);
                int iconX = StudioTheme.scale(ROW_PADDING);
                int iconY = (getHeight() - iconSize) / 2;
                if (icon != null) {
                    canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    Composite solid = canvas.getComposite();
                    if (seeThrough) {
                        canvas.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, SEE_THROUGH_ICON_OPACITY));
                    }
                    canvas.drawImage(icon, iconX, iconY, iconSize, iconSize, null);
                    canvas.setComposite(solid);
                } else {
                    canvas.setColor(StudioTheme.CONTROL_BORDER);
                    canvas.drawRect(iconX, iconY, iconSize - 1, iconSize - 1);
                }
                drawShare(canvas);
            } finally {
                canvas.dispose();
            }
        }

        private void drawShare(Graphics2D canvas) {
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            canvas.setFont(StudioTheme.smallFont());
            int padding = StudioTheme.scale(ROW_PADDING);
            int percentWidth = canvas.getFontMetrics().stringWidth("100.0%");
            int barWidth = StudioTheme.scale(SHARE_WIDTH) - percentWidth - StudioTheme.scale(6);
            int barX = getWidth() - padding - StudioTheme.scale(SHARE_WIDTH);
            int barHeight = StudioTheme.scale(SHARE_BAR_HEIGHT);
            int barY = (getHeight() - barHeight) / 2;
            canvas.setColor(StudioTheme.HOVER_BACKGROUND);
            canvas.fillRoundRect(barX, barY, barWidth, barHeight, barHeight, barHeight);
            canvas.setColor(seeThrough ? StudioTheme.CONTROL_BORDER : StudioTheme.ACCENT);
            canvas.fillRoundRect(barX, barY, Math.max(barHeight, (int) Math.round(barWidth * share)), barHeight, barHeight, barHeight);
            canvas.setColor(StudioTheme.SECONDARY_TEXT);
            int textWidth = canvas.getFontMetrics().stringWidth(percent);
            int baseline = (getHeight() + canvas.getFontMetrics().getAscent() - canvas.getFontMetrics().getDescent()) / 2;
            canvas.drawString(percent, getWidth() - padding - textWidth, baseline);
        }
    }
}
