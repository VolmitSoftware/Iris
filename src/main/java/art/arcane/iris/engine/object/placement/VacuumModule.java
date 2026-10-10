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
import com.volmit.FeatureRegistry;
import art.arcane.iris.builtin.vacuum.VacuumSettings;
import art.arcane.iris.builtin.IrisStages;
import art.arcane.iris.builtin.blocks.WrittenBlocks;
import art.arcane.iris.core.modules.IrisPlacement;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.project.noise.SimplexNoise;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.stage.PlacementStageContext;
import com.volmit.stage.StageGraph;

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

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:vacuum", "Vacuum", PlacementModules.VERSION);
    }

    @Override
    public void register(FeatureRegistry registry) {
        registry.registerHeldType(VacuumSettings.class);
    }

    @Override
    public void install(StageGraph graph) {
        graph.define(IrisStages.Vacuum.TERRAIN)
                .requires(IrisStages.Blocks.WRITE, IrisStages.Blocks.COMPLETE, IrisStages.Prepare.TRANSFORMS)
                .reach(IrisObjectVacuum.MAX_RADIUS_BLOCKS)
                .run(VacuumModule::bendTerrain);
    }

    private static void bendTerrain(PlacementStageContext context) {
        PreparedPlacement prepared = context.output(IrisStages.Prepare.TRANSFORMS);
        WrittenBlocks written = context.output(IrisStages.Blocks.WRITE);
        Engine engine = prepared.terrain().getEngine();
        if (!prepared.mode().bendsTerrain() || written.isEmpty() || engine == null) {
            return;
        }
        int baseY = written.blocks().stream().mapToInt(block -> block.position().y()).min().orElseThrow();
        int topY = written.blocks().stream().mapToInt(block -> block.position().y()).max().orElseThrow();
        new Bend(context, prepared, engine).apply(baseY, topY);
    }

    /** One placement's terrain bend over the columns within its radius of the object's footprint. */
    private static final class Bend {
        private final IrisPlacement placement;
        private final PreparedPlacement prepared;
        private final PlacementWorld world;
        private final IrisComplex complex;
        private final int radius;
        private final int step;
        private final double falloff;
        private final int jitter;
        private final double waveAmplitude;
        private final double waveScale;
        private final SimplexNoise waves;
        private final int lowestY;
        private final int highestY;

        private Bend(PlacementStageContext context, PreparedPlacement prepared, Engine engine) {
            VacuumProfile vacuum = prepared.mode().vacuum();
            this.placement = IrisPlacement.current();
            this.prepared = prepared;
            this.world = new PlacementWorld(context.access(), placement.states());
            this.complex = engine.getComplex();
            VacuumSettings settings = prepared.config().getVacuumSettings();
            this.radius = IrisObjectVacuum.resolveRadius(vacuum, settings);
            this.step = vacuum.columnStepBlocks();
            this.falloff = IrisObjectVacuum.resolveFalloff(settings);
            this.jitter = settings != null ? Math.max(0, settings.organicJitter()) : DEFAULT_ORGANIC_JITTER;
            this.waveAmplitude = IrisObjectVacuum.resolveWaveAmplitude(settings);
            this.waveScale = IrisObjectVacuum.resolveWaveScale(settings);
            this.waves = vacuum.edge() == VacuumProfile.Edge.WAVY && waveAmplitude > 0 ? new SimplexNoise(WAVE_SEED) : null;
            this.lowestY = IrisObjectVacuum.LOWEST_TARGET_Y;
            this.highestY = engine.getHeight() - 1;
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
            int originalY = prepared.terrain().getHighest(x, z, placement.object().getLoader(), true);
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
            return IrisObjectVacuum.waveOffset(distance, effectiveRadius, waves.noiseSigned(x * waveScale, z * waveScale), waveAmplitude);
        }

        private void raise(int x, int z, int fromY, int toY) {
            PlatformBlockState fill = complex != null ? complex.getRockStream().get(x, z) : null;
            if (B.isAir(fill)) {
                fill = IrisObject.States.STONE;
            }
            for (int y = fromY + 1; y <= toY; y++) {
                world.set(x, y, z, fill);
            }
        }

        private void carve(int x, int z, int fromY, int floorY) {
            for (int y = fromY; y >= floorY; y--) {
                world.set(x, y, z, IrisObject.States.AIR);
            }
        }
    }
}
