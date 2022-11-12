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

package com.volmit.iris.util.data;

/**
 * Dimensions
 *
 * @author cyberpwn
 */
public class Dimension {
    private final int width;
    private final int height;
    private final int depth;

    /**
     * Make a dimension
     *
     * @param width  width of this (X)
     * @param height the height (Y)
     * @param depth  the depth (Z)
     */
    public Dimension(int width, int height, int depth) {
        this.width = width;
        this.height = height;
        this.depth = depth;
    }

    /**
     * Make a dimension
     *
     * @param width  width of this (X)
     * @param height the height (Y)
     */
    public Dimension(int width, int height) {
        this.width = width;
        this.height = height;
        this.depth = 0;
    }

    /**
     * Get the direction of the flat part of this dimension (null if no thin
     * face)
     *
     * @return the direction of the flat pane or null
     */
    public DimensionFace getPane() {
        if (width == 1) {
            return DimensionFace.X;
        }

        if (height == 1) {
            return DimensionFace.Y;
        }

        if (depth == 1) {
            return DimensionFace.Z;
        }

        return null;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getDepth() {
        return depth;
    }
}