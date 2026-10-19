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

import art.arcane.iris.builtin.IrisStages;
import art.arcane.iris.builtin.anchor.StiltShape;
import art.arcane.iris.builtin.placement.ObjectPlacement;
import art.arcane.iris.builtin.placement.PlacementSettings;
import art.arcane.iris.builtin.prepare.PreparedPlacement;
import art.arcane.iris.builtin.stilt.StiltLayers;
import art.arcane.iris.builtin.stilt.StiltSettings;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.FeatureRegistry;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.object.ObjectBlock;
import com.volmit.stage.BlockStageContext;
import com.volmit.stage.PlacementStageContext;
import com.volmit.stage.StageGraph;
import com.volmit.world.BlockPosition;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Module {@code iris:stilt}: once the object is written, repeats its bottom layer down to the ground, or
 * for a ceiling hang its top layer up into the ceiling. Straight stilts reach the surface, eroded ones taper
 * toward the edges, organic ones stop at the first solid block with a ragged, broken tip.
 */
public final class StiltModule implements IrisModule {
    private static final Set<String> SOIL_MATERIALS = Set.of("minecraft:grass_block", "minecraft:mycelium",
            "minecraft:podzol", "minecraft:dirt_path");
    private static final BlockState SOIL_REPLACEMENT = new BlockState("minecraft:dirt");
    private static final Set<String> NO_STILT_SUFFIXES = Set.of("_stairs", "_slab");
    private static final String PATH_MATERIAL = "minecraft:dirt_path";
    private static final long HASH_X = 341873128712L;
    private static final long HASH_Y = 132897987541L;
    private static final long HASH_Z = 735791245321L;
    private static final int HASH_BUCKETS = 1000;
    private static final double SKIP_CHANCE_SCALE = 0.7;
    private static final int DEFAULT_ORGANIC_MAX_SCAN = 48;
    private static final int DEFAULT_ORGANIC_JITTER = 3;
    private static final double DEFAULT_ORGANIC_SCRATCH = 0.55;
    private static final int UNANCHORED_ORGANIC_DEPTH = 8;
    private static final double ERODE_SOLID_FRACTION = 0.4;
    private static final double ERODE_TAPER_EXPONENT = 1.5;

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:stilt", "Stilts", PlacementModules.VERSION);
    }

    @Override
    public void install(StageGraph graph) {
        graph.define(IrisStages.Stilt.LAYERS)
                .requires(IrisStages.Prepare.TRANSFORMS)
                // Ordered after boring only so it shares the block pass that writes the object.
                .softAfter(IrisStages.Bore.CLEAR.ref())
                .accumulate(StiltLayers::new, StiltModule::collectLayer);
        graph.define(IrisStages.Stilt.FILL)
                .requires(IrisStages.Stilt.LAYERS, IrisStages.Anchor.FINAL, IrisStages.Blocks.COMPLETE)
                .run(StiltModule::fill);
    }

    @Override
    public void register(FeatureRegistry registry) {
        registry.registerHeldType(StiltSettings.class);
    }

    private static void collectLayer(BlockStageContext context, StiltLayers layers) {
        PreparedPlacement prepared = context.output(IrisStages.Prepare.TRANSFORMS);
        if (prepared.mode().stilt() == StiltShape.NONE) {
            return;
        }
        ObjectBlock block = context.block();
        if (!shouldStilt(prepared, block.state())) {
            return;
        }
        layers.include(new ObjectBlock(prepared.transform(block.position()), block.state(), block.blockEntity()));
    }

    /** Whether the block is solid ground a stilt can repeat: not a stair, a slab or a path. */
    private static boolean shouldStilt(PreparedPlacement prepared, BlockState state) {
        if (!prepared.traits().has(state, BlockTrait.OCCLUDING)) {
            return false;
        }
        String material = state.material().toString();
        return NO_STILT_SUFFIXES.stream().noneMatch(material::endsWith) && !material.equals(PATH_MATERIAL);
    }

    private static void fill(PlacementStageContext context) {
        PreparedPlacement prepared = context.output(IrisStages.Prepare.TRANSFORMS);
        StiltShape shape = prepared.mode().stilt();
        StiltLayers layers = context.output(IrisStages.Stilt.LAYERS);
        if (shape == StiltShape.NONE || layers.isEmpty()) {
            return;
        }
        StiltColumns columns = new StiltColumns(context, prepared, layers);
        List<ObjectBlock> layer = shape == StiltShape.CEILING ? layers.top() : layers.bottom();
        for (ObjectBlock block : layer) {
            columns.grow(block);
        }
    }

    /** One placement's stilt, grown column by column under (or above) the blocks of its repeated layer. */
    private static final class StiltColumns {
        private final ObjectPlacement placement;
        private final PreparedPlacement prepared;
        private final PlacementSettings config;
        private final StiltSettings settings;
        private final BlockState palette;
        private final StiltShape shape;
        private final PlacementWorld world;
        private final PlacementStageContext context;
        private final RNG rng;
        private final int anchorY;
        private final int layerY;
        private final ErodeShape erode;

        private StiltColumns(PlacementStageContext context, PreparedPlacement prepared, StiltLayers layers) {
            this.placement = ObjectPlacement.current();
            this.prepared = prepared;
            this.context = context;
            this.config = prepared.settings();
            this.settings = config.stilt().orElse(null);
            this.shape = prepared.mode().stilt();
            this.world = new PlacementWorld(context.access(), prepared.traits());
            this.rng = new RNG(context.random().nextLong());
            this.anchorY = context.output(IrisStages.Anchor.FINAL).y();
            this.palette = settings == null ? null : paletteBlock(settings);
            this.layerY = shape == StiltShape.CEILING ? layers.topY() : layers.bottomY();
            this.erode = shape == StiltShape.ERODE ? ErodeShape.of(layers.bottom()) : null;
        }

        /** The block the settings' palette picks for this placement, the same for every column; null without a palette. */
        private BlockState paletteBlock(StiltSettings stilt) {
            return stilt.palette()
                    .map(spec -> context.resources().blocks().palette(spec, rng.getSeed()))
                    .map(picker -> picker.at(placement.x(), anchorY, placement.z()))
                    .orElse(null);
        }

        private void grow(ObjectBlock block) {
            BlockPosition offset = block.position();
            BlockState state = stiltState(block, offset);
            if (!world.has(state, BlockTrait.OCCLUDING)) {
                return;
            }
            int x = placement.x() + offset.x();
            int z = placement.z() + offset.z();
            if (prepared.isWarped()) {
                x += prepared.warpOffset(offset.x() + placement.x(), offset.y() + anchorY, offset.z() + placement.z());
                z += prepared.warpOffset(offset.z() + placement.z(), offset.y() + anchorY, offset.x() + placement.x());
            }
            switch (shape) {
                case ORGANIC -> growOrganic(state, x, z, -1);
                case CEILING -> growOrganic(state, x, z, 1);
                case STRAIGHT, ERODE -> growToSurface(state, offset, x, z);
                case NONE -> {
                }
            }
        }

        /** The block the stilt repeats: the palette's when the settings have one, soil turned to dirt, edited and turned with the object. */
        private BlockState stiltState(ObjectBlock block, BlockPosition offset) {
            BlockState state = block.state();
            if (palette != null) {
                state = palette;
            } else if (SOIL_MATERIALS.contains(state.material().toString())) {
                state = SOIL_REPLACEMENT;
            }
            state = prepared.rotate(state);
            if (config.edit().isEmpty()) {
                return state;
            }
            return ObjectEdits.apply(config.edit(), state, Optional.empty(), rng, offset.x() + placement.x(), offset.y() + anchorY,
                    offset.z() + placement.z(), ResolvedReplaces.of(context, prepared.terrain()), placement).data();
        }

        private void growToSurface(BlockState state, BlockPosition offset, int x, int z) {
            int top = layerY + anchorY;
            int highest = prepared.terrain().highest(x, z, true);
            if (state.properties().containsKey(BlocksModule.WATERLOGGED)
                    && BlocksModule.shouldWaterlog(world, config, placement.requestedY(), x, highest, z)) {
                state = state.withProperty(BlocksModule.WATERLOGGED, BlocksModule.TRUE);
            }
            int lowerBound = highest - 1;
            if (settings != null) {
                lowerBound -= settings.overStilt() - rng.i(0, settings.yRand());
                if (settings.yMax() != 0) {
                    lowerBound -= Math.min(settings.yMax() - (top - highest), 0);
                }
            }
            if (erode != null) {
                lowerBound = top - erode.depth(offset, top - lowerBound);
            }
            for (int y = top; y > lowerBound; y--) {
                if (world.has(world.get(x, y, z), BlockTrait.FLUID)) {
                    return;
                }
                if (erode != null && erode.breaksAt(x, y, z, top - y, top - lowerBound)) {
                    continue;
                }
                if (world.has(state, BlockTrait.VINE)) {
                    state = BlocksModule.attachVineFaces(world, state, x, y, z);
                }
                world.set(x, y, z, state);
            }
        }

        /** Grows from the layer toward the first solid block in {@code direction}, -1 down or 1 up. */
        private void growOrganic(BlockState state, int x, int z, int direction) {
            int maxScan = settings != null ? Math.max(1, settings.organicMaxScan()) : DEFAULT_ORGANIC_MAX_SCAN;
            int jitterMax = settings != null ? Math.max(0, settings.organicJitter()) : DEFAULT_ORGANIC_JITTER;
            double scratch = settings != null ? Math.max(0, Math.min(1, settings.organicScratch())) : DEFAULT_ORGANIC_SCRATCH;
            long columnHash = ((long) x * HASH_X) ^ ((long) z * HASH_Y);
            int jitter = jitterMax > 0 ? (int) (Math.abs(columnHash) % (jitterMax + 1)) : 0;
            int start = layerY + anchorY;
            int scanned = 0;
            int solidY = start + direction;
            while (scanned < maxScan && !world.isSolid(x, solidY, z)) {
                solidY += direction;
                scanned++;
            }
            int end = (scanned < maxScan ? solidY - direction : start + direction * Math.min(maxScan, UNANCHORED_ORGANIC_DEPTH))
                    - direction * jitter;
            int length = (end - start) * direction;
            for (int step = 0; step <= length; step++) {
                int y = start + step * direction;
                if (scratch > 0 && length > 0 && isScratched(x, y, z, (double) step / length, scratch)) {
                    continue;
                }
                world.set(x, y, z, state);
            }
        }

        /** Whether the broken tip skips this block: more often the deeper it lies in the scratched fraction. */
        private static boolean isScratched(int x, int y, int z, double depthRatio, double scratch) {
            if (depthRatio <= 1.0 - scratch) {
                return false;
            }
            double skipChance = (depthRatio - (1.0 - scratch)) / scratch;
            return hashFraction(x, y, z) < skipChance * SKIP_CHANCE_SCALE;
        }
    }

    /** An eroded stilt: deepest under the layer's centroid, shallower toward its edge, broken up at depth. */
    private record ErodeShape(double centroidX, double centroidZ, double maxDistance) {
        static ErodeShape of(List<ObjectBlock> bottom) {
            double centroidX = bottom.stream().mapToInt(block -> block.position().x()).average().orElse(0);
            double centroidZ = bottom.stream().mapToInt(block -> block.position().z()).average().orElse(0);
            double maxDistance = bottom.stream()
                    .mapToDouble(block -> Math.hypot(block.position().x() - centroidX, block.position().z() - centroidZ))
                    .max().orElse(1);
            return new ErodeShape(centroidX, centroidZ, Math.max(1, maxDistance));
        }

        int depth(BlockPosition offset, int fullDepth) {
            double distance = Math.min(1.0, Math.hypot(offset.x() - centroidX, offset.z() - centroidZ) / maxDistance);
            return (int) (fullDepth * Math.pow(1.0 - distance, ERODE_TAPER_EXPONENT));
        }

        boolean breaksAt(int x, int y, int z, int depth, int fullDepth) {
            double depthRatio = fullDepth > 0 ? (double) depth / fullDepth : 0;
            if (depthRatio <= ERODE_SOLID_FRACTION) {
                return false;
            }
            double skipChance = (depthRatio - ERODE_SOLID_FRACTION) / (1.0 - ERODE_SOLID_FRACTION);
            return hashFraction(x, y, z) < skipChance * SKIP_CHANCE_SCALE;
        }
    }

    private static double hashFraction(int x, int y, int z) {
        long hash = ((long) x * HASH_X) ^ ((long) y * HASH_Y) ^ ((long) z * HASH_Z);
        return (Math.abs(hash) % HASH_BUCKETS) / (double) HASH_BUCKETS;
    }
}
