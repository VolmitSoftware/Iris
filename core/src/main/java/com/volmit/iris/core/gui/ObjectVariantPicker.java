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

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
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
import java.util.OptionalLong;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

/**
 * A small bar floating at the bottom of the object view that steps through the variants of a procedural object and
 * the seeds they are baked from, and switches between one variant in 3D and all of them side by side. It hides itself
 * for anything with a single variant and no seed.
 */
final class ObjectVariantPicker extends JPanel {
    private static final long serialVersionUID = -2716301839488217455L;
    private static final int BOTTOM_MARGIN = 14;
    private static final int VARIANT_LABEL_WIDTH = 110;
    private static final int SECTION_GAP = 10;
    private static final String VARIANT_SHORTCUT_HINT = " · [ and ]";
    private static final String COMPARE_SHORTCUT_HINT = " · G";

    private final JLabel shownVariant = label(VARIANT_LABEL_WIDTH);
    private final JComponent variantStepper;
    private final ObjectSeedChooser seedChooser;
    private final JButton compareButton;
    private final IntConsumer onVariantChosen;
    private int variantCount;
    private int shownIndex;
    private boolean seeded;
    private int coveredLeftWidth;

    /**
     * @param onVariantChosen told the index of the variant to show whenever the user steps to another one
     * @param onSeedChosen    told the seed to bake the variants from whenever the user chooses another one
     * @param onCompareToggled told when the user switches between one variant and all of them
     */
    ObjectVariantPicker(IntConsumer onVariantChosen, LongConsumer onSeedChosen, Runnable onCompareToggled) {
        this.onVariantChosen = onVariantChosen;
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setOpaque(false);
        setBorder(StudioTheme.emptyBorder(3, 4, 3, 4));
        variantStepper = stepper(shownVariant,
                StudioToolbar.button("‹", "Previous variant" + VARIANT_SHORTCUT_HINT, () -> stepBy(-1)),
                StudioToolbar.button("›", "Next variant" + VARIANT_SHORTCUT_HINT, () -> stepBy(1)));
        seedChooser = new ObjectSeedChooser(onSeedChosen);
        compareButton = StudioToolbar.button("", "", onCompareToggled);
        add(variantStepper);
        add(Box.createHorizontalStrut(StudioTheme.scale(SECTION_GAP)));
        add(seedChooser);
        add(Box.createHorizontalStrut(StudioTheme.scale(SECTION_GAP)));
        add(compareButton);
        showComparing(false);
        showVariants(0, 0);
    }

    private static JLabel label(int width) {
        JLabel label = new JLabel(" ", SwingConstants.CENTER);
        label.setFont(StudioTheme.smallFont());
        label.setForeground(StudioTheme.TEXT);
        label.setPreferredSize(new Dimension(StudioTheme.scale(width), label.getPreferredSize().height));
        return label;
    }

    private static JComponent stepper(JLabel shown, JButton previous, JButton next) {
        JPanel stepper = new JPanel(new BorderLayout());
        stepper.setOpaque(false);
        stepper.add(previous, BorderLayout.WEST);
        stepper.add(shown, BorderLayout.CENTER);
        stepper.add(next, BorderLayout.EAST);
        return stepper;
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
        relayout();
    }

    /**
     * Shows variant {@code index} of {@code count} without telling anyone, since the caller already shows it.
     */
    void showVariants(int count, int index) {
        variantCount = count;
        shownIndex = index;
        shownVariant.setText("Variant " + (index + 1) + " of " + count);
        variantStepper.setVisible(count > 1);
        compareButton.setVisible(count > 1);
        showWhenUseful();
    }

    /**
     * Shows the seed the variants are baked from; empty for objects not generated from a seed.
     */
    void showSeed(OptionalLong shown, OptionalLong pack) {
        seeded = shown.isPresent();
        seedChooser.setVisible(seeded);
        shown.ifPresent(seed -> seedChooser.showSeed(seed, pack));
        showWhenUseful();
    }

    void forgetTriedSeeds() {
        seedChooser.forgetTriedSeeds();
    }

    void showComparing(boolean comparing) {
        compareButton.setText(comparing ? "3D view" : "All variants");
        compareButton.setToolTipText((comparing ? "Show the chosen variant in 3D" : "Show every variant side by side") + COMPARE_SHORTCUT_HINT);
        relayout();
    }

    void stepBy(int step) {
        if (variantCount < 2) {
            return;
        }
        shownIndex = Math.floorMod(shownIndex + step, variantCount);
        showVariants(variantCount, shownIndex);
        onVariantChosen.accept(shownIndex);
    }

    private void showWhenUseful() {
        setVisible(variantCount > 1 || seeded);
        relayout();
    }

    private void relayout() {
        if (getParent() != null) {
            getParent().revalidate();
            getParent().repaint();
        }
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
