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
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;

/**
 * A slim slider floating on the right of the 3D view that hides an object's top layers, to look inside buildings
 * floor by floor. Most objects never need it, so it stays faint until the mouse comes near or a cut is active.
 */
final class ObjectLayerCut extends JPanel {
    private static final long serialVersionUID = 4473820211915526120L;
    private static final int PANEL_WIDTH = 30;
    private static final int MAX_HEIGHT = 320;
    private static final double HEIGHT_SHARE_OF_VIEW = 0.5D;
    private static final int EDGE_MARGIN = 8;
    private static final float FAINT_OPACITY = 0.28F;

    private final JSlider slider = new JSlider(SwingConstants.VERTICAL, 1, 1, 1);
    private final JLabel shownLayers = new JLabel(" ", SwingConstants.CENTER);
    private final IntConsumer onLayersChanged;
    private boolean resetting;
    private boolean hovered;

    /**
     * @param onLayersChanged told how many layers from the bottom stay visible whenever the user moves the slider
     */
    ObjectLayerCut(IntConsumer onLayersChanged) {
        super(new BorderLayout(0, StudioTheme.scale(4)));
        this.onLayersChanged = onLayersChanged;
        setOpaque(false);
        setToolTipText("Hide the top layers to look inside · Page Up / Page Down");
        shownLayers.setFont(StudioTheme.smallFont());
        shownLayers.setForeground(StudioTheme.SECONDARY_TEXT);
        slider.setOpaque(false);
        slider.setFocusable(false);
        slider.setToolTipText(getToolTipText());
        slider.addChangeListener(event -> {
            showLayerLabel();
            if (!resetting) {
                onLayersChanged.accept(slider.getValue());
            }
        });
        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                setHovered(true);
            }

            @Override
            public void mouseExited(MouseEvent event) {
                setHovered(slider.getValueIsAdjusting());
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                setHovered(slider.contains(event.getPoint()));
            }
        };
        slider.addMouseListener(hover);
        addMouseListener(hover);
        add(shownLayers, BorderLayout.NORTH);
        add(slider, BorderLayout.CENTER);
        showHeight(1);
    }

    /**
     * The view with this slider floating over its right edge, halfway down.
     */
    JComponent floatingOver(JComponent view) {
        ObjectLayerCut cut = this;
        JPanel stack = new JPanel(null) {
            private static final long serialVersionUID = -1950381405658102347L;

            // The slider overlaps the view, so the view may not repaint without the slider on top of it.
            @Override
            public boolean isOptimizedDrawingEnabled() {
                return false;
            }

            @Override
            public void doLayout() {
                view.setBounds(0, 0, getWidth(), getHeight());
                int width = StudioTheme.scale(PANEL_WIDTH);
                int height = Math.min(StudioTheme.scale(MAX_HEIGHT), (int) (getHeight() * HEIGHT_SHARE_OF_VIEW));
                cut.setBounds(getWidth() - width - StudioTheme.scale(EDGE_MARGIN), (getHeight() - height) / 2, width, height);
            }
        };
        stack.add(cut);
        stack.add(view);
        return stack;
    }

    /**
     * Shows every layer of a newly chosen object without telling anyone, since nothing is hidden yet.
     */
    void showHeight(int layers) {
        resetting = true;
        try {
            slider.setMaximum(Math.max(1, layers));
            slider.setValue(slider.getMaximum());
        } finally {
            resetting = false;
        }
        showLayerLabel();
        setVisible(layers > 1);
    }

    void stepBy(int layers) {
        if (isVisible()) {
            slider.setValue(slider.getValue() + layers);
        }
    }

    boolean isCutting() {
        return slider.getValue() < slider.getMaximum();
    }

    int shownLayerCount() {
        return slider.getValue();
    }

    int layerCount() {
        return slider.getMaximum();
    }

    @Override
    protected void paintChildren(Graphics graphics) {
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            if (!hovered && !isCutting()) {
                canvas.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, FAINT_OPACITY));
            }
            super.paintChildren(canvas);
        } finally {
            canvas.dispose();
        }
    }

    private void setHovered(boolean nowHovered) {
        if (hovered != nowHovered) {
            hovered = nowHovered;
            repaint();
        }
    }

    private void showLayerLabel() {
        shownLayers.setText(isCutting() ? Integer.toString(slider.getValue()) : " ");
        repaint();
    }
}
