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

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.function.IntConsumer;

/**
 * A small bar floating at the bottom of the 3D view that steps through the variants of a procedural object. It hides
 * itself for anything with a single variant.
 */
final class ObjectVariantPicker extends JPanel {
    private static final long serialVersionUID = -2716301839488217455L;
    private static final int BOTTOM_MARGIN = 14;
    private static final int LABEL_WIDTH = 110;
    private static final String SHORTCUT_HINT = " · [ and ]";

    private final JLabel shownVariant = new JLabel(" ", SwingConstants.CENTER);
    private final IntConsumer onVariantChosen;
    private int variantCount;
    private int shownIndex;
    private int coveredLeftWidth;

    /**
     * @param onVariantChosen told the index of the variant to show whenever the user steps to another one
     */
    ObjectVariantPicker(IntConsumer onVariantChosen) {
        super(new BorderLayout());
        this.onVariantChosen = onVariantChosen;
        setOpaque(false);
        setBorder(StudioTheme.emptyBorder(3, 4, 3, 4));
        shownVariant.setFont(StudioTheme.smallFont());
        shownVariant.setForeground(StudioTheme.TEXT);
        shownVariant.setPreferredSize(new Dimension(StudioTheme.scale(LABEL_WIDTH), shownVariant.getPreferredSize().height));
        add(StudioToolbar.button("‹", "Previous variant" + SHORTCUT_HINT, () -> stepBy(-1)), BorderLayout.WEST);
        add(shownVariant, BorderLayout.CENTER);
        add(StudioToolbar.button("›", "Next variant" + SHORTCUT_HINT, () -> stepBy(1)), BorderLayout.EAST);
        showVariants(0);
    }

    /**
     * The view with this bar floating over its bottom edge, centred on the part the left island leaves uncovered.
     */
    JComponent floatingOver(JComponent view) {
        ObjectVariantPicker picker = this;
        JPanel stack = new JPanel(null) {
            private static final long serialVersionUID = 1609842937565014832L;

            // The bar overlaps the view, so the view may not repaint without the bar on top of it.
            @Override
            public boolean isOptimizedDrawingEnabled() {
                return false;
            }

            @Override
            public void doLayout() {
                view.setBounds(0, 0, getWidth(), getHeight());
                Dimension size = picker.getPreferredSize();
                int centerX = coveredLeftWidth + (getWidth() - coveredLeftWidth) / 2;
                picker.setBounds(centerX - size.width / 2, getHeight() - size.height - StudioTheme.scale(BOTTOM_MARGIN), size.width, size.height);
            }
        };
        stack.add(picker);
        stack.add(view);
        return stack;
    }

    void setCoveredLeftWidth(int width) {
        coveredLeftWidth = width;
        if (getParent() != null) {
            getParent().revalidate();
        }
    }

    /**
     * Starts again at the first of {@code count} variants without telling anyone, since the first is what loads anyway.
     */
    void showVariants(int count) {
        variantCount = count;
        shownIndex = 0;
        showLabel();
        setVisible(count > 1);
    }

    void stepBy(int step) {
        if (variantCount < 2) {
            return;
        }
        shownIndex = Math.floorMod(shownIndex + step, variantCount);
        showLabel();
        onVariantChosen.accept(shownIndex);
    }

    private void showLabel() {
        shownVariant.setText("Variant " + (shownIndex + 1) + " of " + variantCount);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double arc = StudioTheme.scale(StudioTheme.OVERLAY_ARC);
            RoundRectangle2D shape = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1D, getHeight() - 1D, arc, arc);
            canvas.setColor(StudioTheme.OVERLAY_BACKGROUND);
            canvas.fill(shape);
            canvas.setColor(StudioTheme.OVERLAY_BORDER);
            canvas.draw(shape);
        } finally {
            canvas.dispose();
        }
    }
}
