/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.modded;

import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.block.state.properties.WallSide;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModdedStateRotator implements IrisObjectRotation.StateRotator {
    private static final String[] FACE_NAMES = {"north", "east", "south", "west", "up", "down"};
    private static final String[] WALL_FACE_NAMES = {"north", "south", "east", "west"};
    private static final int[][] ROTATION_CYCLE_MODS = {
            {0, 1}, {-1, 2}, {-1, 1}, {-2, 1}, {-1, 0}, {-2, -1}, {-1, -1}, {-1, -2},
            {0, -1}, {1, -2}, {1, -1}, {2, -1}, {1, 0}, {2, 1}, {1, 1}, {1, 2}
    };

    @Override
    public PlatformBlockState rotate(IrisObjectRotation rotation, PlatformBlockState state, int spinxx, int spinyy, int spinzz) {
        if (!(state instanceof ModdedBlockState fabric)) {
            return state;
        }
        BlockState d = fabric.handle();
        boolean deleted = false;

        try {
            int spinx = (int) (90D * (Math.ceil(Math.abs((spinxx % 360D) / 90D))));
            int spiny = (int) (90D * (Math.ceil(Math.abs((spinyy % 360D) / 90D))));
            int spinz = (int) (90D * (Math.ceil(Math.abs((spinzz % 360D) / 90D))));

            if (!rotation.canRotate()) {
                return state;
            }

            Property<?> facing = findFacing(d);
            Property<?> rotationProperty = findRotation(d);
            Property<?> axis = findAxis(d);
            Map<String, BooleanProperty> multipleFacing = findMultipleFacing(d);
            Map<String, EnumProperty<WallSide>> wall = findWall(d);
            Map<String, EnumProperty<RedstoneSide>> wire = findWire(d);

            if (facing != null) {
                Direction f = (Direction) d.getValue(facing);
                IrisBlockVector bv = new IrisBlockVector(f.getStepX(), f.getStepY(), f.getStepZ());
                bv = rotation.rotate(bv.clone(), spinx, spiny, spinz);
                Direction t = faceFor(bv);

                if (facing.getPossibleValues().contains(t)) {
                    d = apply(d, facing, t);
                } else if (!ModdedBlockResolution.isSolid(d.getBlock().defaultBlockState())) {
                    deleted = true;
                }
            } else if (rotationProperty != null) {
                int segment = (Integer) d.getValue(rotationProperty);
                int[] mods = ROTATION_CYCLE_MODS[segment];

                IrisBlockVector bv = new IrisBlockVector(mods[0], 0, mods[1]);
                bv = rotation.rotate(bv.clone(), spinx, spiny, spinz);
                int[] hex = hexMods(bv);

                if (hex[0] == 0 && hex[1] == 0) {
                    throw new IllegalArgumentException("Invalid face, only horizontal face are allowed for this property!");
                }
                double length = Math.sqrt((double) (hex[0] * hex[0] + hex[1] * hex[1]));
                float angle = (float) -Math.toDegrees(Math.atan2(hex[0] / length, hex[1] / length));
                d = apply(d, rotationProperty, RotationSegment.convertToSegment(angle));
            } else if (axis != null) {
                Direction.Axis current = (Direction.Axis) d.getValue(axis);
                IrisBlockVector bv = axisFaceVector(current);
                bv = rotation.rotate(bv.clone(), spinx, spiny, spinz);
                Direction.Axis a = axisOf(bv);

                if (!a.equals(current) && axis.getPossibleValues().contains(a)) {
                    d = apply(d, axis, a);
                }
            } else if (multipleFacing.size() >= 2) {
                List<String> trueFaces = new ArrayList<>();
                for (Map.Entry<String, BooleanProperty> entry : multipleFacing.entrySet()) {
                    if (d.getValue(entry.getValue())) {
                        trueFaces.add(entry.getKey());
                    }
                }

                List<String> faces = new ArrayList<>();
                for (String name : trueFaces) {
                    IrisBlockVector bv = faceVector(name);
                    bv = rotation.rotate(bv.clone(), spinx, spiny, spinz);
                    String r = faceName(faceFor(bv));

                    if (multipleFacing.containsKey(r)) {
                        faces.add(r);
                    }
                }

                for (String name : trueFaces) {
                    d = apply(d, multipleFacing.get(name), Boolean.FALSE);
                }

                for (String name : faces) {
                    d = apply(d, multipleFacing.get(name), Boolean.TRUE);
                }
            } else if (wall.size() == 4) {
                Map<String, WallSide> heights = new LinkedHashMap<>();

                for (String name : WALL_FACE_NAMES) {
                    WallSide h = (WallSide) d.getValue(wall.get(name));
                    IrisBlockVector bv = faceVector(name);
                    bv = rotation.rotate(bv.clone(), spinx, spiny, spinz);
                    String r = faceName(faceFor(bv));
                    if (wall.containsKey(r)) {
                        heights.put(r, h);
                    }
                }

                for (String name : WALL_FACE_NAMES) {
                    d = apply(d, wall.get(name), heights.getOrDefault(name, WallSide.NONE));
                }
            } else if (wire.size() == 4) {
                Map<String, RedstoneSide> connections = new LinkedHashMap<>();

                for (String name : WALL_FACE_NAMES) {
                    RedstoneSide connection = (RedstoneSide) d.getValue(wire.get(name));
                    IrisBlockVector bv = faceVector(name);
                    bv = rotation.rotate(bv.clone(), spinx, spiny, spinz);
                    String r = faceName(faceFor(bv));
                    if (wire.containsKey(r)) {
                        connections.put(r, connection);
                    }
                }

                for (String name : WALL_FACE_NAMES) {
                    d = apply(d, wire.get(name), connections.getOrDefault(name, RedstoneSide.NONE));
                }
            }
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }

        if (deleted) {
            return null;
        }

        return d == fabric.handle() ? state : fabric.withHandle(d, fabric.parsedProperties());
    }

    private static Property<?> findFacing(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals("facing") && property.getValueClass() == Direction.class) {
                return property;
            }
        }
        return null;
    }

    private static Property<?> findRotation(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals("rotation")
                    && property instanceof IntegerProperty integer
                    && isFullRotationCycle(integer)) {
                return property;
            }
        }
        return null;
    }

    private static boolean isFullRotationCycle(IntegerProperty property) {
        List<Integer> values = property.getPossibleValues();
        if (values.size() != ROTATION_CYCLE_MODS.length) {
            return false;
        }
        for (int value : values) {
            if (value < 0 || value >= ROTATION_CYCLE_MODS.length) {
                return false;
            }
        }
        return true;
    }

    private static Property<?> findAxis(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals("axis") && property.getValueClass() == Direction.Axis.class) {
                return property;
            }
        }
        return null;
    }

    private static Map<String, BooleanProperty> findMultipleFacing(BlockState state) {
        Map<String, BooleanProperty> properties = new LinkedHashMap<>();
        for (String name : FACE_NAMES) {
            for (Property<?> property : state.getProperties()) {
                if (property.getName().equals(name) && property instanceof BooleanProperty bool) {
                    properties.put(name, bool);
                }
            }
        }
        return properties;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, EnumProperty<WallSide>> findWall(BlockState state) {
        Map<String, EnumProperty<WallSide>> properties = new LinkedHashMap<>();
        for (String name : WALL_FACE_NAMES) {
            for (Property<?> property : state.getProperties()) {
                if (property.getName().equals(name) && property.getValueClass() == WallSide.class) {
                    properties.put(name, (EnumProperty<WallSide>) property);
                }
            }
        }
        return properties;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, EnumProperty<RedstoneSide>> findWire(BlockState state) {
        Map<String, EnumProperty<RedstoneSide>> properties = new LinkedHashMap<>();
        for (String name : WALL_FACE_NAMES) {
            for (Property<?> property : state.getProperties()) {
                if (property.getName().equals(name) && property.getValueClass() == RedstoneSide.class) {
                    properties.put(name, (EnumProperty<RedstoneSide>) property);
                }
            }
        }
        return properties;
    }

    private static Direction faceFor(IrisBlockVector v) {
        int x = (int) Math.round(v.getX());
        int y = (int) Math.round(v.getY());
        int z = (int) Math.round(v.getZ());

        if (x == 0 && z == -1) {
            return Direction.NORTH;
        }

        if (x == 0 && z == 1) {
            return Direction.SOUTH;
        }

        if (x == 1 && z == 0) {
            return Direction.EAST;
        }

        if (x == -1 && z == 0) {
            return Direction.WEST;
        }

        if (y > 0) {
            return Direction.UP;
        }

        if (y < 0) {
            return Direction.DOWN;
        }

        return Direction.SOUTH;
    }

    private static int[] hexMods(IrisBlockVector v) {
        int x = v.getBlockX();
        int y = v.getBlockY();
        int z = v.getBlockZ();

        if (x == 0 && z == -1) return new int[]{0, -1};
        if (x == 1 && z == -2) return new int[]{1, -2};
        if (x == 1 && z == -1) return new int[]{1, -1};
        if (x == 2 && z == -1) return new int[]{2, -1};
        if (x == 1 && z == 0) return new int[]{1, 0};
        if (x == 2 && z == 1) return new int[]{2, 1};
        if (x == 1 && z == 1) return new int[]{1, 1};
        if (x == 1 && z == 2) return new int[]{1, 2};
        if (x == 0 && z == 1) return new int[]{0, 1};
        if (x == -1 && z == 2) return new int[]{-1, 2};
        if (x == -1 && z == 1) return new int[]{-1, 1};
        if (x == -2 && z == 1) return new int[]{-2, 1};
        if (x == -1 && z == 0) return new int[]{-1, 0};
        if (x == -2 && z == -1) return new int[]{-2, -1};
        if (x == -1 && z == -1) return new int[]{-1, -1};
        if (x == -1 && z == -2) return new int[]{-1, -2};

        if (y > 0) {
            return new int[]{0, 0};
        }

        if (y < 0) {
            return new int[]{0, 0};
        }

        return new int[]{0, 1};
    }

    private static IrisBlockVector axisFaceVector(Direction.Axis axis) {
        if (axis == Direction.Axis.X) {
            return new IrisBlockVector(1, 0, 0);
        }
        if (axis == Direction.Axis.Y) {
            return new IrisBlockVector(0, 1, 0);
        }
        return new IrisBlockVector(0, 0, 1);
    }

    private static Direction.Axis axisOf(IrisBlockVector v) {
        if (Math.abs(v.getBlockX()) > Math.max(Math.abs(v.getBlockY()), Math.abs(v.getBlockZ()))) {
            return Direction.Axis.X;
        }

        if (Math.abs(v.getBlockY()) > Math.max(Math.abs(v.getBlockX()), Math.abs(v.getBlockZ()))) {
            return Direction.Axis.Y;
        }

        if (Math.abs(v.getBlockZ()) > Math.max(Math.abs(v.getBlockX()), Math.abs(v.getBlockY()))) {
            return Direction.Axis.Z;
        }

        return Direction.Axis.Y;
    }

    private static IrisBlockVector faceVector(String name) {
        return switch (name) {
            case "north" -> new IrisBlockVector(0, 0, -1);
            case "south" -> new IrisBlockVector(0, 0, 1);
            case "east" -> new IrisBlockVector(1, 0, 0);
            case "west" -> new IrisBlockVector(-1, 0, 0);
            case "up" -> new IrisBlockVector(0, 1, 0);
            case "down" -> new IrisBlockVector(0, -1, 0);
            default -> new IrisBlockVector(0, 0, 0);
        };
    }

    private static String faceName(Direction direction) {
        return direction.getSerializedName();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState apply(BlockState state, Property<?> property, Object value) {
        return state.setValue((Property<T>) property, (T) value);
    }
}
