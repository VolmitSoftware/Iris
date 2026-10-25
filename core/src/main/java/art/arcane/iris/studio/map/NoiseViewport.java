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

record NoiseViewport(double centerX, double centerZ, double blocksPerPixel) {
    static final double MIN_BLOCKS_PER_PIXEL = 0.0001D;
    static final double MAX_BLOCKS_PER_PIXEL = 1_000_000D;

    NoiseViewport {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ)) {
            throw new IllegalArgumentException("Viewport center must be finite");
        }
        if (!Double.isFinite(blocksPerPixel) || blocksPerPixel <= 0D) {
            throw new IllegalArgumentException("Viewport scale must be finite and positive");
        }
    }

    double worldX(double screenX, int width) {
        return centerX + ((screenX - (width / 2D)) * blocksPerPixel);
    }

    double worldZ(double screenZ, int height) {
        return centerZ + ((screenZ - (height / 2D)) * blocksPerPixel);
    }

    double screenX(double worldX, int width) {
        return ((worldX - centerX) / blocksPerPixel) + (width / 2D);
    }

    double screenZ(double worldZ, int height) {
        return ((worldZ - centerZ) / blocksPerPixel) + (height / 2D);
    }

    NoiseViewport panPixels(double deltaX, double deltaZ) {
        return new NoiseViewport(
                centerX - (deltaX * blocksPerPixel),
                centerZ - (deltaZ * blocksPerPixel),
                blocksPerPixel
        );
    }

    NoiseViewport zoomAt(double screenX, double screenZ, int width, int height, double factor) {
        if (!Double.isFinite(factor) || factor <= 0D) {
            throw new IllegalArgumentException("Zoom factor must be finite and positive");
        }
        double anchorX = worldX(screenX, width);
        double anchorZ = worldZ(screenZ, height);
        double nextScale = Math.max(MIN_BLOCKS_PER_PIXEL,
                Math.min(MAX_BLOCKS_PER_PIXEL, blocksPerPixel * factor));
        double nextCenterX = anchorX - ((screenX - (width / 2D)) * nextScale);
        double nextCenterZ = anchorZ - ((screenZ - (height / 2D)) * nextScale);
        return new NoiseViewport(nextCenterX, nextCenterZ, nextScale);
    }
}
