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

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Every variant of a procedural object side by side, drawn at one scale so their sizes compare. Clicking one opens it
 * in the 3D view. While new thumbnails are drawn, each card keeps its old one faded until its own arrives; cards
 * still waiting after a moment play the loading animation, one after another like a wave.
 */
final class ObjectVariantGallery extends JPanel {
    private static final long serialVersionUID = 6153024487310558262L;
    private static final int THUMBNAIL_SIZE = 220;
    private static final int CARD_PADDING = 6;
    private static final int CARD_GAP = 12;
    private static final int EDGE_MARGIN = 16;
    // The variant bar floats over the bottom edge, so the last row scrolls clear of it.
    private static final int VARIANT_BAR_CLEARANCE = 64;
    private static final float OUTDATED_THUMBNAIL_OPACITY = 0.35F;
    private static final int THUMBNAIL_SIZE_PER_LOADING_BLOCK = 14;
    private static final int LOADING_WAVE_STEP_MILLIS = 90;

    private final JLabel title = new JLabel(" ");
    private final CardGrid grid = new CardGrid();
    private final IntConsumer onVariantOpened;
    private final Runnable onThemeChanged;
    private final List<VariantCard> cards = new ArrayList<>();
    private final Timer loadingTicker = new Timer(LoadingAnimation.FRAME_MILLIS, event -> repaintWaitingCards());
    private int coveredLeftWidth;
    private int selectedIndex;
    private long waitingSinceMillis;

    /**
     * @param onVariantOpened told the index of a variant the user clicks
     * @param onThemeChanged  told when the theme changes, since thumbnails are drawn on the theme's background
     */
    ObjectVariantGallery(IntConsumer onVariantOpened, Runnable onThemeChanged) {
        super(new BorderLayout(0, StudioTheme.scale(EDGE_MARGIN)));
        this.onVariantOpened = onVariantOpened;
        this.onThemeChanged = onThemeChanged;
        setOpaque(true);
        setBackground(StudioTheme.EDITOR_BACKGROUND);
        setFocusable(true);
        title.setFont(StudioTheme.sectionFont());
        title.setForeground(StudioTheme.TEXT);
        JScrollPane scroll = StudioToolbar.scroll(grid);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        add(title, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        keepClearOfOverlays();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (onThemeChanged != null) {
            onThemeChanged.run();
        }
    }

    void bindKey(int keyCode, String name, Runnable action) {
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(keyCode, 0), name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
    }

    void setCoveredLeftWidth(int width) {
        coveredLeftWidth = width;
        keepClearOfOverlays();
        revalidate();
    }

    int thumbnailSize() {
        return StudioTheme.scale(THUMBNAIL_SIZE);
    }

    /**
     * Cards for {@code count} variants, waiting for their thumbnails. The cards already shown stay, with their old
     * thumbnails faded, so a reroll never blanks the view.
     */
    void showVariants(String heading, int count, int selected) {
        title.setText(heading);
        if (count != cards.size()) {
            replaceCards(count);
        }
        startWaiting();
        select(selected);
    }

    /** Fades every thumbnail while something new loads. */
    void showLoading() {
        startWaiting();
    }

    void showThumbnail(int index, BufferedImage thumbnail) {
        if (index < cards.size()) {
            cards.get(index).showThumbnail(thumbnail);
        }
        if (cards.stream().noneMatch(VariantCard::isWaiting)) {
            loadingTicker.stop();
        }
    }

    private void replaceCards(int count) {
        cards.clear();
        grid.removeAll();
        for (int index = 0; index < count; index++) {
            VariantCard card = new VariantCard(index);
            cards.add(card);
            grid.add(card);
        }
        grid.revalidate();
        grid.repaint();
    }

    private void startWaiting() {
        if (!loadingTicker.isRunning()) {
            waitingSinceMillis = System.currentTimeMillis();
            loadingTicker.start();
        }
        cards.forEach(VariantCard::markOutdated);
    }

    private void repaintWaitingCards() {
        if (LoadingAnimation.isDue(waitingSinceMillis)) {
            cards.stream().filter(VariantCard::isWaiting).forEach(VariantCard::repaint);
        }
    }

    void select(int index) {
        selectedIndex = index;
        cards.forEach(VariantCard::repaint);
        if (index < cards.size()) {
            VariantCard card = cards.get(index);
            grid.scrollRectToVisible(card.getBounds());
        }
    }

    void showMessage(String text) {
        title.setText(text);
        loadingTicker.stop();
        cards.clear();
        grid.removeAll();
        grid.revalidate();
        grid.repaint();
    }

    private void keepClearOfOverlays() {
        int margin = StudioTheme.scale(EDGE_MARGIN);
        setBorder(BorderFactory.createEmptyBorder(margin, coveredLeftWidth + margin, StudioTheme.scale(VARIANT_BAR_CLEARANCE), margin));
    }

    /**
     * Cards in as many columns as fit the width, wrapping onto more rows.
     */
    private final class CardGrid extends JPanel implements Scrollable {
        private static final long serialVersionUID = -3618705187215931408L;

        CardGrid() {
            super(null);
            setOpaque(false);
        }

        @Override
        public void doLayout() {
            Dimension card = cardSize();
            int gap = StudioTheme.scale(CARD_GAP);
            int columns = columns();
            for (int index = 0; index < getComponentCount(); index++) {
                int column = index % columns;
                int row = index / columns;
                getComponent(index).setBounds(column * (card.width + gap), row * (card.height + gap), card.width, card.height);
            }
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension card = cardSize();
            int gap = StudioTheme.scale(CARD_GAP);
            int rows = (getComponentCount() + columns() - 1) / columns();
            return new Dimension(card.width, Math.max(0, rows * (card.height + gap) - gap));
        }

        private int columns() {
            int gap = StudioTheme.scale(CARD_GAP);
            return Math.max(1, (getWidth() + gap) / (cardSize().width + gap));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return cardSize().height / 4;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return visibleRect.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private Dimension cardSize() {
        int padding = StudioTheme.scale(CARD_PADDING);
        int labelHeight = getFontMetrics(StudioTheme.smallFont()).getHeight();
        return new Dimension(thumbnailSize() + 2 * padding, thumbnailSize() + labelHeight + 3 * padding);
    }

    private final class VariantCard extends JComponent {
        private static final long serialVersionUID = 2296135546207834180L;

        private final int index;
        private BufferedImage thumbnail;
        private boolean thumbnailOutdated;
        private boolean hovered;

        VariantCard(int index) {
            this.index = index;
            setToolTipText("Open variant " + (index + 1) + " in 3D");
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    onVariantOpened.accept(VariantCard.this.index);
                }

                @Override
                public void mouseEntered(MouseEvent event) {
                    setHovered(true);
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    setHovered(false);
                }
            });
        }

        void showThumbnail(BufferedImage image) {
            thumbnail = image;
            thumbnailOutdated = false;
            repaint();
        }

        void markOutdated() {
            thumbnailOutdated = true;
            repaint();
        }

        boolean isWaiting() {
            return thumbnail == null || thumbnailOutdated;
        }

        private void setHovered(boolean nowHovered) {
            hovered = nowHovered;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D canvas = (Graphics2D) graphics.create();
            try {
                canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                double arc = StudioTheme.scale(StudioTheme.OVERLAY_ARC);
                canvas.setColor(index == selectedIndex ? StudioTheme.SELECTION : hovered ? StudioTheme.HOVER_BACKGROUND : StudioTheme.PANEL_BACKGROUND);
                canvas.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), arc, arc));
                int padding = StudioTheme.scale(CARD_PADDING);
                int size = thumbnailSize();
                canvas.setFont(StudioTheme.smallFont());
                FontMetrics metrics = canvas.getFontMetrics();
                drawThumbnail(canvas, padding, size);
                canvas.setColor(StudioTheme.TEXT);
                drawCentered(canvas, "Variant " + (index + 1), padding * 2 + size + metrics.getAscent());
            } finally {
                canvas.dispose();
            }
        }

        private void drawThumbnail(Graphics2D canvas, int padding, int size) {
            if (thumbnail != null) {
                Graphics2D faded = (Graphics2D) canvas.create();
                if (thumbnailOutdated) {
                    faded.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, OUTDATED_THUMBNAIL_OPACITY));
                }
                faded.drawImage(thumbnail, padding, padding, size, size, null);
                faded.dispose();
            }
            if (isWaiting() && LoadingAnimation.isDue(waitingSinceMillis)) {
                long shownMillis = System.currentTimeMillis() - waitingSinceMillis - LoadingAnimation.SHOW_AFTER_MILLIS;
                LoadingAnimation.paint(canvas, padding + size / 2D, padding + size / 2D, size / (double) THUMBNAIL_SIZE_PER_LOADING_BLOCK,
                        shownMillis - (long) index * LOADING_WAVE_STEP_MILLIS);
            }
        }

        private void drawCentered(Graphics2D canvas, String text, int baseline) {
            canvas.drawString(text, (getWidth() - canvas.getFontMetrics().stringWidth(text)) / 2, baseline);
        }
    }
}
