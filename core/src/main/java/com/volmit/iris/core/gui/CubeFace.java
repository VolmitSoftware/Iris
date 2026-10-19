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

/**
 * The six sides of a block, named as in Minecraft's model files, with the fixed light each side gets in the object viewer.
 */
enum CubeFace {
    UP("up", 0, 1, 0, 1.00D),
    DOWN("down", 0, -1, 0, 0.48D),
    NORTH("north", 0, 0, -1, 0.62D),
    SOUTH("south", 0, 0, 1, 0.88D),
    EAST("east", 1, 0, 0, 0.80D),
    WEST("west", -1, 0, 0, 0.69D);

    private final String modelName;
    private final int stepX;
    private final int stepY;
    private final int stepZ;
    private final double shade;

    CubeFace(String modelName, int stepX, int stepY, int stepZ, double shade) {
        this.modelName = modelName;
        this.stepX = stepX;
        this.stepY = stepY;
        this.stepZ = stepZ;
        this.shade = shade;
    }

    String modelName() {
        return modelName;
    }

    int stepX() {
        return stepX;
    }

    int stepY() {
        return stepY;
    }

    int stepZ() {
        return stepZ;
    }

    double shade() {
        return shade;
    }

    /**
     * The side of the model that ends up facing this way once a blockstate turns the model, which Minecraft does around
     * the x axis first and the y axis second, each in steps of 90 degrees.
     */
    CubeFace beforeRotation(int rotationX, int rotationY) {
        int[] direction = {stepX, stepY, stepZ};
        direction = turnAroundY(direction, -rotationY);
        direction = turnAroundX(direction, -rotationX);
        return pointing(direction[0], direction[1], direction[2]);
    }

    static int[] turnAroundX(int[] vector, int degrees) {
        int cos = cosine(degrees);
        int sin = sine(degrees);
        return new int[]{vector[0], vector[1] * cos - vector[2] * sin, vector[1] * sin + vector[2] * cos};
    }

    static int[] turnAroundY(int[] vector, int degrees) {
        int cos = cosine(degrees);
        int sin = sine(degrees);
        return new int[]{vector[0] * cos - vector[2] * sin, vector[1], vector[0] * sin + vector[2] * cos};
    }

    private static int cosine(int degrees) {
        return switch (Math.floorMod(degrees, 360)) {
            case 90, 270 -> 0;
            case 180 -> -1;
            default -> 1;
        };
    }

    private static int sine(int degrees) {
        return switch (Math.floorMod(degrees, 360)) {
            case 90 -> 1;
            case 270 -> -1;
            default -> 0;
        };
    }

    private static CubeFace pointing(int x, int y, int z) {
        for (CubeFace face : values()) {
            if (face.stepX == x && face.stepY == y && face.stepZ == z) {
                return face;
            }
        }
        throw new IllegalArgumentException("Not a block side: " + x + ", " + y + ", " + z);
    }
}
