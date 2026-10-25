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

import java.util.Optional;

/**
 * Something the orbit viewport shows, drawn into a picture for any camera.
 */
interface OrbitScene {
    boolean isEmpty();

    default OrbitCamera startingCamera() {
        return OrbitCamera.START;
    }

    default OrbitLimits limits() {
        return OrbitLimits.DEFAULT;
    }

    /**
     * @param coveredLeftWidth how much of the picture's left side something floats over, so the scene is centred and
     *                         fitted in the rest
     */
    OrbitPicture render(OrbitCamera camera, int width, int height, int coveredLeftWidth);

    /**
     * Whether {@link #renderOnScreen} may draw this scene, as far as is known before trying.
     */
    default boolean canRenderOnScreen() {
        return false;
    }

    /**
     * Draws a frame straight into the view's window, like {@link #render} would draw a picture.
     *
     * @return the frame, or nothing when the caller should render a picture instead
     */
    default Optional<OrbitPicture> renderOnScreen(ScreenTarget screen, OrbitCamera camera, int width, int height, int coveredLeftWidth) {
        return Optional.empty();
    }

    /**
     * Where {@link #render} puts the scene for this camera and picture size.
     */
    OrbitProjection projection(OrbitCamera camera, int width, int height, int coveredLeftWidth);
}
