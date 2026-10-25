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
 * A slim vertical slider floating over the right edge of a 3D view, halfway down, with a short label above it. It
 * stays faint so it hides little of the view, until the mouse comes near or it is told to stand out.
 */
final class FloatingSlider extends JPanel {
    private static final long serialVersionUID = 4473820211915526120L;
    private static final int PANEL_WIDTH = 30;
    private static final int MAX_HEIGHT = 320;
    private static final double HEIGHT_SHARE_OF_VIEW = 0.5D;
    private static final int EDGE_MARGIN = 8;
    private static final float FAINT_OPACITY = 0.28F;
    private static final String BLANK_LABEL = " ";

    private final JSlider slider = new JSlider(SwingConstants.VERTICAL, 0, 1, 1);
    private final JLabel label = new JLabel(BLANK_LABEL, SwingConstants.CENTER);
    private boolean movingQuietly;
    private boolean hovered;
    private boolean standingOut;

    /**
     * @param onMoved told the slider's value whenever the user moves it
     */
    FloatingSlider(String tooltip, IntConsumer onMoved) {
        super(new BorderLayout(0, StudioTheme.scale(4)));
        setOpaque(false);
        setToolTipText(tooltip);
        label.setFont(StudioTheme.smallFont());
        label.setForeground(StudioTheme.SECONDARY_TEXT);
        slider.setOpaque(false);
        slider.setFocusable(false);
        slider.setToolTipText(tooltip);
        slider.addChangeListener(event -> {
            if (!movingQuietly) {
                onMoved.accept(slider.getValue());
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
        add(label, BorderLayout.NORTH);
        add(slider, BorderLayout.CENTER);
    }

    /**
     * The view with this slider floating over its right edge, halfway down.
     */
    JComponent floatingOver(JComponent view) {
        FloatingSlider floating = this;
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
                floating.setBounds(getWidth() - width - StudioTheme.scale(EDGE_MARGIN), (getHeight() - height) / 2, width, height);
            }
        };
        stack.add(floating);
        stack.add(view);
        return stack;
    }

    /**
     * Shows a range and value the user didn't choose, without telling anyone the slider moved.
     */
    void showQuietly(int minimum, int maximum, int value) {
        movingQuietly = true;
        try {
            slider.getModel().setRangeProperties(value, 0, minimum, maximum, false);
        } finally {
            movingQuietly = false;
        }
    }

    // Moves the slider as if the user had.
    void moveBy(int steps) {
        slider.setValue(slider.getValue() + steps);
    }

    int value() {
        return slider.getValue();
    }

    int maximum() {
        return slider.getMaximum();
    }

    void showLabel(String text) {
        label.setText(text.isEmpty() ? BLANK_LABEL : text);
        repaint();
    }

    void setStandingOut(boolean nowStandingOut) {
        if (standingOut != nowStandingOut) {
            standingOut = nowStandingOut;
            repaint();
        }
    }

    @Override
    protected void paintChildren(Graphics graphics) {
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            if (!hovered && !standingOut) {
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
}
