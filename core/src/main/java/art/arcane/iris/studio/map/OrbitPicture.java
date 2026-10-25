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

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.OptionalDouble;

/**
 * A drawn view of a scene, with how far toward the camera from the pivot the surface drawn in the middle of the view
 * lies; orbiting turns around that surface. Swing paints the patches. A whole picture is one patch covering the view;
 * a frame drawn straight into the view's window is already on screen, and only keeps the patches Swing paints where
 * panels float over that window.
 *
 * @param depthInMiddle empty when nothing was drawn in the middle
 * @param catchingUp    when the scene wasn't all drawn yet, so drawing the same camera again shows more
 */
record OrbitPicture(int width, int height, boolean onScreen, List<PicturePatch> patches, OptionalDouble depthInMiddle,
                    boolean catchingUp) {
    static OrbitPicture whole(BufferedImage image, OptionalDouble depthInMiddle) {
        return new OrbitPicture(image.getWidth(), image.getHeight(), false, List.of(new PicturePatch(0, 0, image)), depthInMiddle, false);
    }

    static OrbitPicture onScreen(int width, int height, List<PicturePatch> patches, OptionalDouble depthInMiddle) {
        return new OrbitPicture(width, height, true, patches, depthInMiddle, false);
    }

    OrbitPicture stillCatchingUp() {
        return new OrbitPicture(width, height, onScreen, patches, depthInMiddle, true);
    }

    /**
     * @param depths one per pixel, row by row; negative infinity where nothing was drawn
     */
    static OrbitPicture of(BufferedImage image, double[] depths, OrbitProjection projection) {
        int middleX = (int) projection.screenCenterX();
        int middleY = (int) projection.screenCenterY();
        if (middleX < 0 || middleY < 0 || middleX >= image.getWidth() || middleY >= image.getHeight()) {
            return whole(image, OptionalDouble.empty());
        }
        return whole(image, finite(depths[middleY * image.getWidth() + middleX]));
    }

    static OptionalDouble finite(double depth) {
        return Double.isFinite(depth) ? OptionalDouble.of(depth) : OptionalDouble.empty();
    }
}
