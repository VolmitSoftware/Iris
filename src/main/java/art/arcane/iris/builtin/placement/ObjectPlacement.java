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

package art.arcane.iris.builtin.placement;

import art.arcane.iris.builtin.prepare.ObjectRotation;
import art.arcane.iris.builtin.spawn.SpawnMarker;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.world.BlockPosition;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * The object placement being run, as Iris's built-in placement stages see it: what is placed and with which settings, the
 * terrain it is placed on, and the few block operations that only the platform can do. Iris implements it for each
 * placement and binds it to the placing thread while the placement graph runs; the stages read it through {@link #current()}.
 * Heights are in the coordinates of the placement stages: above the dimension's bottom.
 * <p>
 * It never hands out an engine, pack data or a platform type; everything it answers is an IrisApi, VolmLib or
 * {@code art.arcane.iris.builtin} value.
 */
public interface ObjectPlacement {
    /** The placement running on this thread. */
    static ObjectPlacement current() {
        return PlacementBinding.current();
    }

    /** Runs {@code placement} with {@code inputs} as {@link #current()} on this thread. */
    static <T> T runBound(ObjectPlacement inputs, Supplier<T> placement) {
        return PlacementBinding.run(inputs, placement);
    }

    ObjectShape object();

    /** The settings as the caller passed them; the stages derive what they change (the rotation) themselves. */
    PlacementSettings settings();

    int x();

    /** The height the caller asked for, negative when the placement samples it. */
    int requestedY();

    int z();

    /** The cave the object is placed in; empty outside caves. */
    Optional<CaveSpace> cave();

    /** Answers the questions of the terrain the placement was asked to place on. */
    PlacementTerrain terrain();

    /**
     * The terrain the placement anchors against: {@link #terrain()}, or, when the settings place on a heightmap, the same
     * terrain with its heights from the heightmap's noise, seeded from {@code rng}.
     */
    PlacementTerrain terrainFor(RNG rng);

    /** The noise that warps the placement's columns; empty when the settings do not warp. */
    Optional<SurfaceWarp> surfaceWarp(RNG rng);

    /** Iris's simplex noise of {@code seed}, whose signed reads fill -1 to 1. */
    PlaneNoise simplex(long seed);

    /** {@code state} turned with the object by {@code rotation}, as the platform turns stairs, logs and other facing blocks. */
    BlockState rotate(BlockState state, ObjectRotation rotation, int spinX, int spinY, int spinZ);

    /** {@code update} laid over {@code base}: the platform's merge of two states of one block, else {@code update}. */
    BlockState merge(BlockState base, BlockState update);

    /** Whether {@code state}, as the platform's own state, matches {@code other}: {@code state.matches(other)} of the platform. */
    boolean matches(BlockState state, BlockState other);

    /**
     * Whether {@code state} has {@code trait}, as the platform's own state answers it. Unlike the block registry it keeps
     * the identity of the states this placement handed out, so a content plugin's block, which shares its key with the
     * block it is drawn as, answers as itself ({@link BlockTrait#CUSTOM}).
     */
    boolean hasTrait(BlockState state, BlockTrait trait);

    /** The marker of the pack named {@code key}, if the pack has one. */
    Optional<SpawnMarker> marker(String key);

    /** Reports a buffered write as one of the object's own blocks, which Iris's placement listener hears after the commit. */
    void objectBlockWritten(BlockPosition position);

    /** Reports the height the object was anchored at, which the placement returns to its caller. */
    void anchoredAt(int y);
}
