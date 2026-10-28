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

package art.arcane.iris.modules.placement;

import art.arcane.iris.builtin.IrisSeedPurposes;
import art.arcane.iris.builtin.LegacyStreams;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectRun;
import art.arcane.iris.builtin.prepare.ObjectRotation;
import art.arcane.volmlib.util.math.IrisBlockVector;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.block.BlockTurn;
import com.volmit.geometry.BlockPosition;
import com.volmit.placement.PlacementContext;
import com.volmit.resource.PackResources;
import com.volmit.world.PackWorld;

import java.util.Optional;

/**
 * The object placement being run, as Iris's built-in placement stages see it: what is placed and with which settings
 * ({@link ObjectRun}, the run's input), the terrain it is placed on, and the block operations of the pack's registry.
 * Heights are above the dimension's bottom.
 */
public final class RunningPlacement {
    private static final double QUARTER_TURN_DEGREES = 90D;
    private static final double FULL_TURN_DEGREES = 360D;

    private final PlacementContext context;
    private final ObjectRun run;
    private final ObjectShape object;
    private final PlacementSettings settings;
    private final StageTerrain terrain;

    private RunningPlacement(PlacementContext context, ObjectRun run) {
        this.context = context;
        this.run = run;
        this.object = new ObjectShape(run.object(), context.world());
        this.settings = new PlacementSettings(run.settings(), context.world().resources());
        this.terrain = StageTerrain.of(context);
    }

    /** The placement {@code context} runs. */
    public static RunningPlacement of(PlacementContext context) {
        ObjectRun run = context.input(ObjectRun.class).orElseThrow(() -> new IllegalStateException(
                "Iris's object placement stages place an object run, and this placement was started without one"));
        return new RunningPlacement(context, run);
    }

    public ObjectShape object() {
        return object;
    }

    /** The settings as the caller passed them; the stages derive what they change (the rotation) themselves. */
    public PlacementSettings settings() {
        return settings;
    }

    public int x() {
        return run.x();
    }

    /** The height the caller asked for, negative when the placement samples it. */
    public int requestedY() {
        return run.requestedY();
    }

    public int z() {
        return run.z();
    }

    /** The cave the object is placed in; empty outside caves. */
    public Optional<ObjectRun.Cave> cave() {
        return run.cave();
    }

    /** Answers the questions of the terrain the placement was asked to place on. */
    public StageTerrain terrain() {
        return terrain;
    }

    /**
     * The terrain the placement anchors against: {@link #terrain()}, or, when the settings place on a heightmap, the same
     * terrain with its heights from the heightmap's noise, seeded from {@code rng}.
     */
    public StageTerrain terrainFor(RNG rng) {
        ObjectPlacement config = run.settings();
        if (config.heightmap().isEmpty()) {
            return terrain;
        }
        long heightmapSeed = rng.nextLong() + run.requestedY() + run.z() - run.x();
        LegacyStreams streams = world().extension(LegacyStreams.class).orElseThrow();
        return terrain.withHeights((x, z) -> streams.heightmapSurface(config, heightmapSeed, x, z));
    }

    /**
     * The noise that warps the placement's columns; empty when the settings do not warp. A generated world seeds it from
     * the world, so every placement of the world warps alike; a placement without one seeds it from {@code rng}.
     */
    public Optional<SurfaceWarp> surfaceWarp(RNG rng) {
        ObjectPlacement config = run.settings();
        if (!ObjectRun.warps(config.warp())) {
            return Optional.empty();
        }
        PackWorld world = world();
        long seed = world.running().isPresent() ? world.seed(IrisSeedPurposes.OBJECT_WARP) : rng.getSeed();
        return Optional.of(new SurfaceWarp(world.resources().noise().sampler(config.warp(), seed), config.warp().multiplier()));
    }

    /** {@code state} turned with the object by {@code rotation}, as the platform turns stairs, logs and other facing blocks. */
    public BlockState rotate(BlockState state, ObjectRotation rotation, int spinX, int spinY, int spinZ) {
        if (!rotation.canRotate()) {
            return state;
        }
        int turnsX = wholeQuarterTurns(spinX);
        int turnsY = wholeQuarterTurns(spinY);
        int turnsZ = wholeQuarterTurns(spinZ);
        BlockTurn turn = (x, y, z) -> {
            IrisBlockVector turned = rotation.rotate(new IrisBlockVector(x, y, z), turnsX, turnsY, turnsZ);
            return new BlockTurn.Direction(turned.getX(), turned.getY(), turned.getZ());
        };
        return blocks().rotate(state, turn);
    }

    /** {@code update} laid over {@code base}: the platform's merge of two states of one block, else {@code update}. */
    public BlockState merge(BlockState base, BlockState update) {
        return blocks().merge(base, update);
    }

    /** Whether {@code state} matches {@code other} as the platform compares them. */
    public boolean matches(BlockState state, BlockState other) {
        return blocks().matches(state, other);
    }

    public boolean hasTrait(BlockState state, BlockTrait trait) {
        return blocks().hasTrait(state, trait);
    }

    /** The resources of the object's pack, where the placement's markers are found. */
    public PackResources resources() {
        return world().resources();
    }

    /** Reports a buffered write as one of the object's own blocks, which the placing module reads once the object is placed. */
    public void objectBlockWritten(BlockPosition position) {
        run.objectBlockWritten(position);
    }

    /** Reports the height the object was anchored at, which the placement returns to its caller. */
    public void anchoredAt(int y) {
        run.anchoredAt(y);
    }

    private BlockRegistry blocks() {
        return world().resources().blocks();
    }

    private PackWorld world() {
        return context.world();
    }

    /** The spin in whole quarter turns, as the platform turns blocks: any part of a quarter turn counts as one. */
    private static int wholeQuarterTurns(int degrees) {
        return (int) (QUARTER_TURN_DEGREES * Math.ceil(Math.abs((degrees % FULL_TURN_DEGREES) / QUARTER_TURN_DEGREES)));
    }
}
