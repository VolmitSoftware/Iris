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
import com.volmit.iris.core.gui.components.RenderType;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Locale;

/**
 * Keeps the picture of the map from just before a hotload, and shows it next to the new one: either split by a
 * draggable divider, or with everything that stayed the same faded out.
 */
final class HotloadComparison {
    private static final double DEFAULT_SWIPE_FRACTION = 0.5D;
    private static final int DIVIDER_GRAB_DISTANCE = 8;
    private static final int DIVIDER_HANDLE_RADIUS = 9;
    private static final int UNCHANGED_FADE_TARGET = 0x1E1F22;
    private static final double UNCHANGED_FADE = 0.78D;
    private static final Color DIVIDER = new Color(255, 255, 255, 220);

    private BufferedImage before;
    private Scene beforeScene;
    private Mode mode = Mode.OFF;
    private double swipeFraction = DEFAULT_SWIPE_FRACTION;
    private BufferedImage changes;
    private ChangesKey changesKey;
    private double changedShare;

    enum Mode {
        OFF,
        SWIPE,
        CHANGES;

        Mode next() {
            Mode[] modes = values();
            return modes[(ordinal() + 1) % modes.length];
        }
    }

    /**
     * What a map picture shows, so the before picture is only laid over a now picture of the same kind and zoom.
     */
    record Scene(RenderType type, MapStyle style, double blocksPerPixel, double centerX, double centerZ) {
        boolean looksLike(Scene other) {
            return type == other.type && style.equals(other.style);
        }
    }

    boolean hasBefore() {
        return before != null;
    }

    Mode mode() {
        return mode;
    }

    void setMode(Mode nextMode) {
        mode = nextMode;
    }

    void capture(BufferedImage layer, Scene scene) {
        BufferedImage copy = new BufferedImage(layer.getWidth(), layer.getHeight(), BufferedImage.TYPE_INT_RGB);
        copy.setAccelerationPriority(0F);
        Graphics2D graphics = copy.createGraphics();
        try {
            graphics.drawImage(layer, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        before = copy;
        beforeScene = scene;
        changes = null;
        changesKey = null;
    }

    void forget() {
        before = null;
        beforeScene = null;
        changes = null;
        changesKey = null;
        mode = Mode.OFF;
    }

    boolean isShowing() {
        return mode != Mode.OFF && before != null;
    }

    boolean isNearDivider(int screenX, int width) {
        return isShowing() && mode == Mode.SWIPE
                && Math.abs(screenX - dividerX(width)) <= StudioTheme.scale(DIVIDER_GRAB_DISTANCE);
    }

    void moveDivider(int screenX, int width) {
        swipeFraction = Math.max(0D, Math.min(1D, screenX / (double) Math.max(1, width)));
    }

    /**
     * Draws the comparison in place of the plain map picture.
     *
     * @param nowVersion changes whenever the now picture is redrawn, so the change mask is only rebuilt when needed
     * @param nowComplete whether every tile of the now picture has been drawn; changes are only counted then
     */
    void paint(Graphics2D canvas, BufferedImage now, long nowVersion, Scene nowScene, boolean nowComplete) {
        canvas.drawImage(now, 0, 0, null);
        if (!beforeScene.looksLike(nowScene)) {
            drawCaption(canvas, now.getWidth(), "Before and after needs the view and layers the hotload had");
            return;
        }
        if (mode == Mode.SWIPE) {
            paintSwipe(canvas, now.getWidth(), now.getHeight(), nowScene);
            return;
        }
        if (beforeScene.blocksPerPixel() != nowScene.blocksPerPixel()) {
            drawCaption(canvas, now.getWidth(), "Changes needs the zoom the hotload had");
            return;
        }
        if (!nowComplete) {
            drawCaption(canvas, now.getWidth(), "Comparing once the map has finished drawing");
            return;
        }
        int offsetX = (int) Math.round(beforeScreenX(now.getWidth(), nowScene));
        int offsetZ = (int) Math.round(beforeScreenZ(now.getHeight(), nowScene));
        canvas.drawImage(changesImage(now, nowVersion, offsetX, offsetZ), 0, 0, null);
        drawCaption(canvas, now.getWidth(), changesCaption());
    }

    private void paintSwipe(Graphics2D canvas, int width, int height, Scene now) {
        int dividerX = dividerX(width);
        double scale = beforeScene.blocksPerPixel() / now.blocksPerPixel();
        Graphics2D beforeSide = (Graphics2D) canvas.create();
        try {
            beforeSide.clipRect(0, 0, dividerX, height);
            beforeSide.drawImage(before,
                    (int) Math.round(beforeScreenX(width, now)),
                    (int) Math.round(beforeScreenZ(height, now)),
                    (int) Math.round(before.getWidth() * scale),
                    (int) Math.round(before.getHeight() * scale),
                    null);
        } finally {
            beforeSide.dispose();
        }
        canvas.setColor(DIVIDER);
        canvas.setStroke(new BasicStroke(StudioTheme.scale(2)));
        canvas.drawLine(dividerX, 0, dividerX, height);
        int radius = StudioTheme.scale(DIVIDER_HANDLE_RADIUS);
        canvas.fillOval(dividerX - radius, height / 2 - radius, radius * 2, radius * 2);
        canvas.setColor(StudioTheme.EDITOR_BACKGROUND);
        int gripHalfHeight = radius / 2;
        int gripGap = Math.max(1, radius / 4);
        canvas.drawLine(dividerX - gripGap, height / 2 - gripHalfHeight, dividerX - gripGap, height / 2 + gripHalfHeight);
        canvas.drawLine(dividerX + gripGap, height / 2 - gripHalfHeight, dividerX + gripGap, height / 2 + gripHalfHeight);
        drawSideLabel(canvas, "Before", dividerX - StudioTheme.scale(12), 1D);
        drawSideLabel(canvas, "Now", dividerX + StudioTheme.scale(12), 0D);
    }

    private int dividerX(int width) {
        return (int) Math.round(width * swipeFraction);
    }

    // Where the before picture's top left corner lands on screen now, after any panning, zooming or resizing since.
    private double beforeScreenX(int width, Scene now) {
        double beforeLeftWorld = beforeScene.centerX() - before.getWidth() * 0.5D * beforeScene.blocksPerPixel();
        return width * 0.5D + (beforeLeftWorld - now.centerX()) / now.blocksPerPixel();
    }

    private double beforeScreenZ(int height, Scene now) {
        double beforeTopWorld = beforeScene.centerZ() - before.getHeight() * 0.5D * beforeScene.blocksPerPixel();
        return height * 0.5D + (beforeTopWorld - now.centerZ()) / now.blocksPerPixel();
    }

    private BufferedImage changesImage(BufferedImage now, long nowVersion, int offsetX, int offsetZ) {
        ChangesKey key = new ChangesKey(nowVersion, offsetX, offsetZ, now.getWidth(), now.getHeight());
        if (changes != null && key.equals(changesKey)) {
            return changes;
        }
        int width = now.getWidth();
        int height = now.getHeight();
        int[] nowPixels = pixels(now);
        int[] beforePixels = pixels(before);
        BufferedImage mask = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        mask.setAccelerationPriority(0F);
        int[] maskPixels = pixels(mask);
        long compared = 0L;
        long changed = 0L;
        for (int y = 0; y < height; y++) {
            int beforeY = y - offsetZ;
            boolean rowInBefore = beforeY >= 0 && beforeY < before.getHeight();
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                int nowColor = nowPixels[index] & 0xFFFFFF;
                int beforeX = x - offsetX;
                boolean inBefore = rowInBefore && beforeX >= 0 && beforeX < before.getWidth();
                boolean differs = inBefore && (beforePixels[beforeY * before.getWidth() + beforeX] & 0xFFFFFF) != nowColor;
                if (inBefore) {
                    compared++;
                }
                if (differs) {
                    changed++;
                    maskPixels[index] = nowColor;
                } else {
                    maskPixels[index] = fade(nowColor);
                }
            }
        }
        changedShare = compared == 0L ? 0D : changed / (double) compared;
        changes = mask;
        changesKey = key;
        return mask;
    }

    private String changesCaption() {
        if (changedShare == 0D) {
            return "Nothing on screen changed in the hotload";
        }
        double percent = changedShare * 100D;
        String amount = percent < 0.1D ? "<0.1" : String.format(Locale.ROOT, percent < 10D ? "%.1f" : "%.0f", percent);
        return amount + "% of the map on screen changed in the hotload";
    }

    private static int[] pixels(BufferedImage image) {
        return ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }

    private static int fade(int color) {
        int red = (int) (((color >> 16) & 0xFF) * (1D - UNCHANGED_FADE) + ((UNCHANGED_FADE_TARGET >> 16) & 0xFF) * UNCHANGED_FADE);
        int green = (int) (((color >> 8) & 0xFF) * (1D - UNCHANGED_FADE) + ((UNCHANGED_FADE_TARGET >> 8) & 0xFF) * UNCHANGED_FADE);
        int blue = (int) ((color & 0xFF) * (1D - UNCHANGED_FADE) + (UNCHANGED_FADE_TARGET & 0xFF) * UNCHANGED_FADE);
        return (red << 16) | (green << 8) | blue;
    }

    private static void drawSideLabel(Graphics2D canvas, String text, int anchorX, double pushX) {
        canvas.setFont(StudioTheme.boldFont());
        int padding = StudioTheme.scale(8);
        int width = canvas.getFontMetrics().stringWidth(text) + padding * 2;
        int height = canvas.getFontMetrics().getHeight() + padding;
        int x = (int) Math.round(anchorX - width * pushX);
        int y = StudioTheme.scale(12);
        StudioOverlay.drawCard(canvas, x, y, width, height);
        canvas.setColor(StudioTheme.TEXT);
        canvas.drawString(text, x + padding, y + padding / 2 + canvas.getFontMetrics().getAscent());
    }

    private static void drawCaption(Graphics2D canvas, int width, String text) {
        canvas.setFont(StudioTheme.font());
        int padding = StudioTheme.scale(10);
        int captionWidth = canvas.getFontMetrics().stringWidth(text) + padding * 2;
        int height = canvas.getFontMetrics().getHeight() + padding;
        int x = (width - captionWidth) / 2;
        int y = StudioTheme.scale(12);
        StudioOverlay.drawCard(canvas, x, y, captionWidth, height);
        canvas.setColor(StudioTheme.TEXT);
        canvas.drawString(text, x + padding, y + padding / 2 + canvas.getFontMetrics().getAscent());
    }

    private record ChangesKey(long nowVersion, int offsetX, int offsetZ, int width, int height) {
    }
}
