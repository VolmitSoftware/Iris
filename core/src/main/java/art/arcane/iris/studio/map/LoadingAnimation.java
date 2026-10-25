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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/**
 * A little cube of blocks building itself while something loads: the blocks drop in one by one, the cube holds a
 * moment, fades, and starts over. Loads shorter than {@link #SHOW_AFTER_MILLIS} never show it, and once it shows it
 * stays for {@link #SHOW_AT_LEAST_MILLIS}, so it never flashes.
 */
final class LoadingAnimation {
    static final int SHOW_AFTER_MILLIS = 300;
    static final int SHOW_AT_LEAST_MILLIS = 700;
    static final int FRAME_MILLIS = 33;

    // Back to front, so each block lands on or beside ones already there.
    private static final int[][] BUILD_ORDER = {
            {0, 0, 0}, {1, 0, 0}, {0, 0, 1}, {1, 0, 1},
            {0, 1, 0}, {1, 1, 0}, {0, 1, 1}, {1, 1, 1}};
    private static final int STEP_MILLIS = 140;
    private static final int DROP_MILLIS = 260;
    private static final int HOLD_MILLIS = 450;
    private static final int FADE_MILLIS = 320;
    private static final int CYCLE_MILLIS = STEP_MILLIS * (BUILD_ORDER.length - 1) + DROP_MILLIS + HOLD_MILLIS + FADE_MILLIS;
    private static final double DROP_HEIGHT_BLOCKS = 1.6D;
    private static final double ISOMETRIC_WIDTH = Math.cos(Math.PI / 6D);
    private static final double ISOMETRIC_DEPTH = 0.5D;
    private static final float SIDE_SHADE = 0.78F;
    private static final float FAR_SIDE_SHADE = 0.6F;

    private LoadingAnimation() {
    }

    /**
     * Whether a load that began at {@code loadingSinceMillis} has taken long enough to show the animation.
     */
    static boolean isDue(long loadingSinceMillis) {
        return System.currentTimeMillis() - loadingSinceMillis >= SHOW_AFTER_MILLIS;
    }

    /**
     * How much longer the animation of a load that began at {@code loadingSinceMillis} has to stay up once the load is
     * done; zero when it never showed or has shown long enough.
     */
    static long remainingShowMillis(long loadingSinceMillis) {
        if (!isDue(loadingSinceMillis)) {
            return 0L;
        }
        long shownMillis = System.currentTimeMillis() - loadingSinceMillis - SHOW_AFTER_MILLIS;
        return Math.max(0L, SHOW_AT_LEAST_MILLIS - shownMillis);
    }

    /**
     * Draws the cube centred on a point, {@code blockSize} pixels to a block edge, as it looks
     * {@code elapsedMillis} after it first showed.
     */
    static void paint(Graphics2D graphics, double centerX, double centerY, double blockSize, long elapsedMillis) {
        long time = Math.floorMod(elapsedMillis, CYCLE_MILLIS);
        long fadeStart = CYCLE_MILLIS - FADE_MILLIS;
        if (time < fadeStart) {
            drawCube(graphics, centerX, centerY, blockSize, time);
            return;
        }
        fadeCube(graphics, centerX, centerY, blockSize, time, 1F - (time - fadeStart) / (float) FADE_MILLIS);
    }

    // Faded block by block, the blocks behind would show through; the whole cube is drawn first and faded as one.
    private static void fadeCube(Graphics2D graphics, double centerX, double centerY, double blockSize, long time, float opacity) {
        int extent = (int) Math.ceil(blockSize * (2D + DROP_HEIGHT_BLOCKS) * 2D);
        BufferedImage cube = new BufferedImage(extent, extent, BufferedImage.TYPE_INT_ARGB);
        Graphics2D cubeCanvas = cube.createGraphics();
        drawCube(cubeCanvas, extent / 2D, extent / 2D, blockSize, time);
        cubeCanvas.dispose();
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            canvas.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0F, opacity)));
            canvas.drawImage(cube, (int) Math.round(centerX - extent / 2D), (int) Math.round(centerY - extent / 2D), null);
        } finally {
            canvas.dispose();
        }
    }

    private static void drawCube(Graphics2D graphics, double centerX, double centerY, double blockSize, long time) {
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (int index = 0; index < BUILD_ORDER.length; index++) {
                long sinceDropStart = time - (long) index * STEP_MILLIS;
                if (sinceDropStart < 0) {
                    break;
                }
                double fallen = easeOut(Math.min(1D, sinceDropStart / (double) DROP_MILLIS));
                int[] block = BUILD_ORDER[index];
                drawBlock(canvas, centerX, centerY, blockSize, block[0], block[1] + DROP_HEIGHT_BLOCKS * (1D - fallen), block[2]);
            }
        } finally {
            canvas.dispose();
        }
    }

    private static double easeOut(double progress) {
        double remaining = 1D - progress;
        return 1D - remaining * remaining * remaining;
    }

    // The cube's middle, (1, 1, 1), lands on the centre point; the top and the two sides facing the viewer show.
    private static void drawBlock(Graphics2D canvas, double centerX, double centerY, double size, double x, double y, double z) {
        Color top = StudioTheme.ACCENT;
        canvas.setColor(top);
        canvas.fill(face(centerX, centerY, size, x, y, z, new double[][]{{0, 1, 0}, {1, 1, 0}, {1, 1, 1}, {0, 1, 1}}));
        canvas.setColor(shaded(top, SIDE_SHADE));
        canvas.fill(face(centerX, centerY, size, x, y, z, new double[][]{{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}}));
        canvas.setColor(shaded(top, FAR_SIDE_SHADE));
        canvas.fill(face(centerX, centerY, size, x, y, z, new double[][]{{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}}));
    }

    private static Path2D face(double centerX, double centerY, double size, double x, double y, double z, double[][] corners) {
        Path2D path = new Path2D.Double();
        for (int corner = 0; corner < corners.length; corner++) {
            double cornerX = x + corners[corner][0] - 1D;
            double cornerY = y + corners[corner][1] - 1D;
            double cornerZ = z + corners[corner][2] - 1D;
            double screenX = centerX + (cornerX - cornerZ) * ISOMETRIC_WIDTH * size;
            double screenY = centerY + (cornerX + cornerZ) * ISOMETRIC_DEPTH * size - cornerY * size;
            if (corner == 0) {
                path.moveTo(screenX, screenY);
            } else {
                path.lineTo(screenX, screenY);
            }
        }
        path.closePath();
        return path;
    }

    private static Color shaded(Color color, float shade) {
        return new Color(Math.round(color.getRed() * shade), Math.round(color.getGreen() * shade), Math.round(color.getBlue() * shade));
    }
}
