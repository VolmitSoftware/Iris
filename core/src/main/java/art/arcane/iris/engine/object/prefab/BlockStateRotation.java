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

package art.arcane.iris.engine.object.prefab;

import art.arcane.iris.platform.bukkit.BukkitBlockState;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.math.IrisBlockVector;
import com.volmit.block.BlockTurn;
import org.bukkit.Axis;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.RedstoneWire;
import org.bukkit.block.data.type.Wall;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Turns a platform block state with an object: the facing, axis and connections of stairs, logs, walls and wires follow
 * the {@link BlockTurn} that turns the object's directions, such as an {@link ObjectTurn}'s. A platform other than
 * Bukkit binds its own {@link StateRotator}.
 */
public final class BlockStateRotation {
    private static volatile StateRotator platformRotator = null;

    /** How a platform turns its own block states. */
    public interface StateRotator {
        /** {@code state} turned by {@code turn}; null when a block that cannot face the new way is dropped. */
        PlatformBlockState rotate(BlockTurn turn, PlatformBlockState state);
    }

    /** Holds the Bukkit faces apart so a platform without Bukkit can load this class and bind its own rotator. */
    private static final class Faces {
        private static final List<BlockFace> WALL_FACES = List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST);
    }

    private BlockStateRotation() {
    }

    public static synchronized StateRotator bindPlatformRotator(StateRotator rotator) {
        StateRotator previous = platformRotator;
        platformRotator = Objects.requireNonNull(rotator, "rotator");
        return previous;
    }

    public static synchronized void restorePlatformRotator(StateRotator rotator) {
        platformRotator = rotator;
    }

    /** {@code state} turned by {@code turn}; null when a block that cannot face the new way is dropped. */
    public static PlatformBlockState rotate(ObjectTurn turn, PlatformBlockState state) {
        if (!turn.canRotate()) {
            return state;
        }
        return rotate(turn.blockTurn(), state);
    }

    /** {@code state} turned by {@code turn}; null when a block that cannot face the new way is dropped. */
    public static PlatformBlockState rotate(BlockTurn turn, PlatformBlockState state) {
        if (state == null) {
            return null;
        }
        StateRotator rotator = platformRotator;
        if (rotator != null) {
            return rotator.rotate(turn, state);
        }
        BlockData original = (BlockData) state.nativeHandle();
        if (!canRotateBlockData(original)) {
            return state;
        }
        BlockData rotated = rotate(turn, original.clone());
        return rotated == null ? null : BukkitBlockState.of(rotated);
    }

    /** {@code direction} turned by {@code turn}. */
    public static IrisBlockVector turned(BlockTurn turn, double x, double y, double z) {
        BlockTurn.Direction direction = turn.turn(x, y, z);
        return new IrisBlockVector(direction.x(), direction.y(), direction.z());
    }

    private static boolean canRotateBlockData(BlockData data) {
        return data instanceof Directional
                || data instanceof Rotatable
                || data instanceof Orientable
                || data instanceof MultipleFacing
                || data instanceof Wall
                || data instanceof RedstoneWire;
    }

    /** {@code data}, changed in place, turned by {@code turn}; null when it cannot face the new way. */
    public static BlockData rotate(BlockTurn turn, BlockData data) {
        BlockData d = data;
        try {
            if (d instanceof Directional g) {
                BlockFace f = g.getFacing();
                IrisBlockVector bv = turned(turn, f.getModX(), f.getModY(), f.getModZ());
                BlockFace t = faceOf(bv);

                if (g.getFaces().contains(t)) {
                    g.setFacing(t);
                } else if (!g.getMaterial().isSolid()) {
                    d = null;
                }
            } else if (d instanceof Rotatable g) {
                BlockFace f = g.getRotation();
                IrisBlockVector bv = turned(turn, f.getModX(), 0, f.getModZ());
                g.setRotation(sixteenWayFaceOf(bv));
            } else if (d instanceof Orientable g) {
                BlockFace f = faceOf(g.getAxis());
                IrisBlockVector bv = turned(turn, f.getModX(), f.getModY(), f.getModZ());
                Axis a = axisOf(bv);

                if (!a.equals(g.getAxis()) && g.getAxes().contains(a)) {
                    g.setAxis(a);
                }
            } else if (d instanceof MultipleFacing g) {
                List<BlockFace> faces = new KList<>();

                for (BlockFace i : g.getFaces()) {
                    IrisBlockVector bv = turned(turn, i.getModX(), i.getModY(), i.getModZ());
                    BlockFace r = faceOf(bv);

                    if (g.getAllowedFaces().contains(r)) {
                        faces.add(r);
                    }
                }

                for (BlockFace i : g.getFaces()) {
                    g.setFace(i, false);
                }

                for (BlockFace i : faces) {
                    g.setFace(i, true);
                }
            } else if (d instanceof Wall wall) {
                KMap<BlockFace, Wall.Height> faces = new KMap<>();

                for (BlockFace i : Faces.WALL_FACES) {
                    Wall.Height h = wall.getHeight(i);
                    IrisBlockVector bv = turned(turn, i.getModX(), i.getModY(), i.getModZ());
                    BlockFace r = faceOf(bv);
                    if (Faces.WALL_FACES.contains(r)) {
                        faces.put(r, h);
                    }
                }

                for (BlockFace i : Faces.WALL_FACES) {
                    wall.setHeight(i, faces.getOrDefault(i, Wall.Height.NONE));
                }
            } else if (d instanceof RedstoneWire wire) {
                Map<BlockFace, RedstoneWire.Connection> faces = new HashMap<>();

                Set<BlockFace> allowed = wire.getAllowedFaces();
                for (BlockFace i : allowed) {
                    RedstoneWire.Connection connection = wire.getFace(i);
                    IrisBlockVector bv = turned(turn, i.getModX(), i.getModY(), i.getModZ());
                    BlockFace r = faceOf(bv);
                    if (allowed.contains(r)) {
                        faces.put(r, connection);
                    }
                }

                for (BlockFace i : allowed) {
                    wire.setFace(i, faces.getOrDefault(i, RedstoneWire.Connection.NONE));
                }
            }
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }

        return d;
    }

    private static BlockFace faceOf(IrisBlockVector v) {
        int x = (int) Math.round(v.getX());
        int y = (int) Math.round(v.getY());
        int z = (int) Math.round(v.getZ());

        if (x == 0 && z == -1) {
            return BlockFace.NORTH;
        }
        if (x == 0 && z == 1) {
            return BlockFace.SOUTH;
        }
        if (x == 1 && z == 0) {
            return BlockFace.EAST;
        }
        if (x == -1 && z == 0) {
            return BlockFace.WEST;
        }
        if (y > 0) {
            return BlockFace.UP;
        }
        if (y < 0) {
            return BlockFace.DOWN;
        }
        return BlockFace.SOUTH;
    }

    private static BlockFace sixteenWayFaceOf(IrisBlockVector v) {
        int x = v.getBlockX();
        int y = v.getBlockY();
        int z = v.getBlockZ();

        if (x == 0 && z == -1) return BlockFace.NORTH;
        if (x == 1 && z == -2) return BlockFace.NORTH_NORTH_EAST;
        if (x == 1 && z == -1) return BlockFace.NORTH_EAST;
        if (x == 2 && z == -1) return BlockFace.EAST_NORTH_EAST;
        if (x == 1 && z == 0) return BlockFace.EAST;
        if (x == 2 && z == 1) return BlockFace.EAST_SOUTH_EAST;
        if (x == 1 && z == 1) return BlockFace.SOUTH_EAST;
        if (x == 1 && z == 2) return BlockFace.SOUTH_SOUTH_EAST;
        if (x == 0 && z == 1) return BlockFace.SOUTH;
        if (x == -1 && z == 2) return BlockFace.SOUTH_SOUTH_WEST;
        if (x == -1 && z == 1) return BlockFace.SOUTH_WEST;
        if (x == -2 && z == 1) return BlockFace.WEST_SOUTH_WEST;
        if (x == -1 && z == 0) return BlockFace.WEST;
        if (x == -2 && z == -1) return BlockFace.WEST_NORTH_WEST;
        if (x == -1 && z == -1) return BlockFace.NORTH_WEST;
        if (x == -1 && z == -2) return BlockFace.NORTH_NORTH_WEST;

        if (y > 0) {
            return BlockFace.UP;
        }
        if (y < 0) {
            return BlockFace.DOWN;
        }
        return BlockFace.SOUTH;
    }

    private static Axis axisOf(IrisBlockVector v) {
        if (Math.abs(v.getBlockX()) > Math.max(Math.abs(v.getBlockY()), Math.abs(v.getBlockZ()))) {
            return Axis.X;
        }
        if (Math.abs(v.getBlockY()) > Math.max(Math.abs(v.getBlockX()), Math.abs(v.getBlockZ()))) {
            return Axis.Y;
        }
        if (Math.abs(v.getBlockZ()) > Math.max(Math.abs(v.getBlockX()), Math.abs(v.getBlockY()))) {
            return Axis.Z;
        }
        return Axis.Y;
    }

    private static BlockFace faceOf(Axis axis) {
        return switch (axis) {
            case X -> BlockFace.EAST;
            case Y -> BlockFace.UP;
            case Z -> BlockFace.SOUTH;
        };
    }
}
