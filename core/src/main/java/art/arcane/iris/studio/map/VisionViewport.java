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

record VisionViewport(double centerX, double centerZ, double blocksPerPixel) {
    VisionViewport {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ)) {
            throw new IllegalArgumentException("Vision viewport center must be finite");
        }
        if (!Double.isFinite(blocksPerPixel) || blocksPerPixel <= 0D) {
            throw new IllegalArgumentException("Vision viewport scale must be finite and positive");
        }
    }

    double worldX(double screenX, int width) {
        return centerX + (screenX - width * 0.5D) * blocksPerPixel;
    }

    double worldZ(double screenZ, int height) {
        return centerZ + (screenZ - height * 0.5D) * blocksPerPixel;
    }

    VisionViewport zoomAt(
            double screenX,
            double screenZ,
            int width,
            int height,
            double factor,
            double minimumScale,
            double maximumScale
    ) {
        if (!Double.isFinite(factor) || factor <= 0D) {
            throw new IllegalArgumentException("Vision zoom factor must be finite and positive");
        }
        double anchorX = worldX(screenX, width);
        double anchorZ = worldZ(screenZ, height);
        double nextScale = Math.max(minimumScale, Math.min(maximumScale, blocksPerPixel * factor));
        return new VisionViewport(
                anchorX - (screenX - width * 0.5D) * nextScale,
                anchorZ - (screenZ - height * 0.5D) * nextScale,
                nextScale
        );
    }
}
