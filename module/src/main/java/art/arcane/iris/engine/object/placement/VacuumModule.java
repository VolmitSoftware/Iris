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

package art.arcane.iris.engine.object.placement;

import art.arcane.iris.builtin.anchor.VacuumProfile;
import art.arcane.iris.builtin.blocks.WrittenBlocks;
import art.arcane.iris.builtin.prepare.TransformedBounds;
import art.arcane.iris.modules.placement.EdgeSimplex;
import art.arcane.iris.modules.placement.PlacementStages;
import art.arcane.iris.modules.placement.PreparedPlacement;
import art.arcane.iris.modules.placement.RunningPlacement;
import art.arcane.iris.modules.placement.StageTerrain;
import art.arcane.iris.modules.terrain.TerrainPalettes;
import art.arcane.iris.builtin.vacuum.VacuumSettings;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.stage.PlacementStageContext;
import com.volmit.geometry.BlockPosition;

/**
 * Module {@code iris:vacuum}: bends the terrain around an object of a vacuum mode so it meets the object's
 * base, raising columns with rock and carving them down, fading out across the bend's radius.
 */
public final class VacuumModule implements IrisModule {
    private static final long WAVE_SEED = 7392113L;
    private static final int DEFAULT_ORGANIC_JITTER = 4;
    private static final long HASH_X = 341873128712L;
    private static final long HASH_Z = 132897987541L;
    private static final int HASH_BUCKETS = 1000;
    private static final double HALF = 0.5;
    private static final BlockState STONE = new BlockState("minecraft:stone");
    private static final BlockState AIR = new BlockState("minecraft:cave_air");

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:vacuum", "Vacuum", PlacementModules.VERSION);
    }

    @Override
    public void setup(ModuleSetup setup) {
        setup.stages().define(PlacementStages.Vacuum.TERRAIN)
                .requires(PlacementStages.Blocks.WRITE, PlacementStages.Blocks.COMPLETE, PlacementStages.Prepare.TRANSFORMS)
                .reach(IrisObjectVacuum.MAX_RADIUS_BLOCKS)
                .run(VacuumModule::bendTerrain);
    }

    private static void bendTerrain(PlacementStageContext context) {
        PreparedPlacement prepared = context.output(PlacementStages.Prepare.TRANSFORMS);
        WrittenBlocks written = context.output(PlacementStages.Blocks.WRITE);
        if (!prepared.mode().bendsTerrain() || written.isEmpty() || !prepared.terrain().hasWorld()) {
            return;
        }
        int baseY = written.positions().stream().mapToInt(BlockPosition::y).min().orElseThrow();
        int topY = written.positions().stream().mapToInt(BlockPosition::y).max().orElseThrow();
        new Bend(context, prepared).apply(baseY, topY);
    }

    /** One placement's terrain bend over the columns within its radius of the object's footprint. */
    private static final class Bend {
        private final RunningPlacement placement;
        private final PreparedPlacement prepared;
        private final StageTerrain terrain;
        private final PlacementWorld world;
        private final PlacementStageContext context;
        private final int radius;
        private final int step;
        private final double falloff;
        private final int jitter;
        private final double waveAmplitude;
        private final double waveScale;
        private final EdgeSimplex waves;
        private final int lowestY;
        private final int highestY;

        private Bend(PlacementStageContext context, PreparedPlacement prepared) {
            VacuumProfile vacuum = prepared.mode().vacuum();
            this.placement = prepared.placement();
            this.prepared = prepared;
            this.terrain = prepared.terrain();
            this.world = new PlacementWorld(context.access(), prepared.traits());
            this.context = context;
            VacuumSettings settings = prepared.settings().vacuum().orElse(null);
            this.radius = IrisObjectVacuum.resolveRadius(vacuum, settings);
            this.step = vacuum.columnStepBlocks();
            this.falloff = IrisObjectVacuum.resolveFalloff(settings);
            this.jitter = settings != null ? Math.max(0, settings.organicJitter()) : DEFAULT_ORGANIC_JITTER;
            this.waveAmplitude = IrisObjectVacuum.resolveWaveAmplitude(settings);
            this.waveScale = IrisObjectVacuum.resolveWaveScale(settings);
            this.waves = vacuum.edge() == VacuumProfile.Edge.WAVY && waveAmplitude > 0 ? new EdgeSimplex(WAVE_SEED) : null;
            this.lowestY = IrisObjectVacuum.LOWEST_TARGET_Y;
            this.highestY = terrain.worldHeight() - 1;
        }

        private void apply(int baseY, int topY) {
            TransformedBounds bounds = prepared.bounds();
            for (int dx = bounds.minX() - radius; dx <= bounds.maxX() + radius; dx += step) {
                for (int dz = bounds.minZ() - radius; dz <= bounds.maxZ() + radius; dz += step) {
                    bendColumn(bounds, dx, dz, baseY - 1, topY);
                }
            }
        }

        private void bendColumn(TransformedBounds bounds, int dx, int dz, int meetY, int topY) {
            int x = placement.x() + dx;
            int z = placement.z() + dz;
            double effectiveRadius = effectiveRadius(x, z);
            int originalY = terrain.highest(x, z, true);
            int targetY = IrisObjectVacuum.columnTargetY(dx, dz, bounds.minX(), bounds.maxX(), bounds.minZ(), bounds.maxZ(),
                    effectiveRadius, falloff, originalY, meetY) + waveOffset(bounds, dx, dz, x, z, effectiveRadius);
            if (targetY == originalY) {
                return;
            }
            targetY = IrisObjectVacuum.clampTargetY(targetY, lowestY, highestY);
            if (targetY > originalY) {
                raise(x, z, originalY, targetY);
            } else if (targetY < originalY) {
                boolean inside = IrisObjectVacuum.outset(dx, bounds.minX(), bounds.maxX()) == 0
                        && IrisObjectVacuum.outset(dz, bounds.minZ(), bounds.maxZ()) == 0;
                carve(x, z, originalY, IrisObjectVacuum.carveFloorY(targetY, topY, inside));
            }
        }

        /** The radius, perturbed per column for the organic mode. */
        private double effectiveRadius(int x, int z) {
            if (prepared.mode().vacuum().edge() != VacuumProfile.Edge.JITTERED || jitter <= 0) {
                return radius;
            }
            long hash = ((long) x * HASH_X) ^ ((long) z * HASH_Z);
            double noise = ((Math.abs(hash) % HASH_BUCKETS) / (double) HASH_BUCKETS) - HALF;
            return Math.max(1.0, radius + (noise * 2.0 * jitter));
        }

        private int waveOffset(TransformedBounds bounds, int dx, int dz, int x, int z, double effectiveRadius) {
            if (waves == null) {
                return 0;
            }
            int outX = IrisObjectVacuum.outset(dx, bounds.minX(), bounds.maxX());
            int outZ = IrisObjectVacuum.outset(dz, bounds.minZ(), bounds.maxZ());
            double distance = Math.sqrt((double) (outX * outX) + (double) (outZ * outZ));
            return IrisObjectVacuum.waveOffset(distance, effectiveRadius, waves.signed(x * waveScale, z * waveScale), waveAmplitude);
        }

        private void raise(int x, int z, int fromY, int toY) {
            BlockState fill = TerrainPalettes.of(context.world()).rockAt(x, z);
            if (fill == null || world.has(fill, BlockTrait.AIR)) {
                fill = STONE;
            }
            for (int y = fromY + 1; y <= toY; y++) {
                world.set(x, y, z, fill);
            }
        }

        private void carve(int x, int z, int fromY, int floorY) {
            for (int y = fromY; y >= floorY; y--) {
                world.set(x, y, z, AIR);
            }
        }
    }
}
