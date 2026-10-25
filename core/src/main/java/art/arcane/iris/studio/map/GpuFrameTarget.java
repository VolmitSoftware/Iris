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

import java.util.OptionalDouble;

import static org.lwjgl.opengl.GL11.GL_DEPTH_COMPONENT;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.glReadPixels;

/**
 * Where the graphics card draws a frame: a picture read back for Swing, or straight into a window. Only for the GPU
 * thread.
 */
interface GpuFrameTarget {
    double FARTHEST_DEPTH = 1D;

    /**
     * Makes the target the one drawn into, at the given size.
     */
    void bindSized(int width, int height);

    /**
     * Ends the frame drawn since {@link #bindSized}.
     *
     * @param depthRange the range the clip matrix was made for
     */
    OrbitPicture finish(OrbitProjection projection, double depthRange);

    /**
     * Reads the one depth a picture keeps, the middle's, from what is bound.
     */
    static OptionalDouble depthInMiddle(OrbitProjection projection, double depthRange, int width, int height) {
        int middleX = (int) projection.screenCenterX();
        int middleY = (int) projection.screenCenterY();
        if (middleX < 0 || middleY < 0 || middleX >= width || middleY >= height) {
            return OptionalDouble.empty();
        }
        float[] middleDepth = new float[1];
        glReadPixels(middleX, height - 1 - middleY, 1, 1, GL_DEPTH_COMPONENT, GL_FLOAT, middleDepth);
        return OrbitPicture.finite(blockDepth(middleDepth[0], depthRange));
    }

    // The depth buffer holds 0 at the near plane and 1 at the far one, nearer being smaller.
    private static double blockDepth(float bufferDepth, double depthRange) {
        if (bufferDepth >= FARTHEST_DEPTH) {
            return Double.NEGATIVE_INFINITY;
        }
        return -(2D * bufferDepth - 1D) * depthRange;
    }
}
