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
 * The six sides of a block, named as in Minecraft's model files, with the light Minecraft gives each side.
 */
enum CubeFace {
    UP("up", 0, 1, 0, 1.0D),
    DOWN("down", 0, -1, 0, 0.5D),
    NORTH("north", 0, 0, -1, 0.8D),
    SOUTH("south", 0, 0, 1, 0.8D),
    EAST("east", 1, 0, 0, 0.6D),
    WEST("west", -1, 0, 0, 0.6D);

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

    static CubeFace named(String modelName) {
        for (CubeFace face : values()) {
            if (face.modelName.equals(modelName)) {
                return face;
            }
        }
        return null;
    }

    /**
     * The side a direction points at most, like Minecraft picks for a tilted face.
     */
    static CubeFace nearest(Vec3 direction) {
        CubeFace nearest = UP;
        double best = Double.NEGATIVE_INFINITY;
        for (CubeFace face : values()) {
            double alignment = face.stepX * direction.x() + face.stepY * direction.y() + face.stepZ * direction.z();
            if (alignment > best) {
                best = alignment;
                nearest = face;
            }
        }
        return nearest;
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

    Vec3 direction() {
        return new Vec3(stepX, stepY, stepZ);
    }

    CubeFace opposite() {
        return switch (this) {
            case UP -> DOWN;
            case DOWN -> UP;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
        };
    }

    int axis() {
        return stepX != 0 ? 0 : stepY != 0 ? 1 : 2;
    }

    boolean pointsPositive() {
        return stepX + stepY + stepZ > 0;
    }
}
