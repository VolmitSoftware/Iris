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

package art.arcane.iris.engine.modifier;

import art.arcane.iris.builtin.IrisSeed;
import art.arcane.iris.builtin.IrisStages;
import art.arcane.iris.builtin.MatterMantle;
import art.arcane.iris.builtin.carve.CarvingBiomeSettings;
import art.arcane.iris.builtin.carve.CarvingEnabledSettings;
import art.arcane.iris.builtin.carve.CarvingSettings;
import art.arcane.iris.builtin.carve.CaveDepthSettings;
import art.arcane.iris.builtin.carve.CaveLavaHeightSettings;
import art.arcane.iris.builtin.carve.CaveProfile;
import art.arcane.iris.builtin.carve.UpperDimensionCarvingSettings;
import art.arcane.iris.builtin.terrain.TerrainPalettes;
import art.arcane.iris.engine.mantle.components.CarveWorld;
import art.arcane.iris.engine.mantle.components.CaveCarving;
import com.volmit.IrisFeature;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.config.FeatureSettings;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.FeatureAttachment.Target;
import com.volmit.hunk.Hunk;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.service.CaveBiomes;
import com.volmit.stage.ChunkStageContext;
import com.volmit.stage.MantleStageContext;
import com.volmit.world.PackWorld;
import com.volmit.world.TerrainSampler;

import java.util.Optional;
import java.util.function.Function;

/**
 * Module {@code iris:carve}: caves. A mantle stage plans them into the mantle, reaching one block into each
 * neighboring chunk where blended cave walls cross the border; a chunk stage later cuts them out of the chunk's
 * terrain. The mantle stage is a port of what was Iris's carving mantle component, its work done by
 * {@link CaveCarving}, one per world. The chunk stage's work is done by {@link CaveApplying}.
 */
public final class CarveModule implements IrisModule {
    private static final String VERSION = "built-in";
    /** Blended cave walls reach one block past the chunk. */
    private static final int CAVE_REACH_BLOCKS = 1;
    private static final int CHUNK_SHIFT = 4;
    /** The centre column of a chunk, where per-chunk settings are read. */
    private static final int CHUNK_CENTRE_BLOCKS = 7;

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:carve", "Caves", VERSION);
    }

    @Override
    public void setup(ModuleSetup setup) {
        var registry = setup.registry();
        registry.registerSettings(CaveProfile.class);
        registry.registerSettings(CarvingSettings.class);
        registry.registerSettings(CarvingEnabledSettings.class);
        registry.registerSettings(CarvingBiomeSettings.class);
        registry.registerSettings(CaveDepthSettings.class);
        registry.registerSettings(CaveLavaHeightSettings.class);
        registry.registerSettings(UpperDimensionCarvingSettings.class);
        registry.registerService(CaveBiomes.class, new CaveBiomeBands());

        var graph = setup.stages();
        graph.define(IrisStages.Carve.CAVES).reach(CAVE_REACH_BLOCKS).run(CarveModule::planCaves);
        graph.define(IrisStages.Carve.APPLY).requires(IrisStages.Terrain.SHAPE).run(CarveModule::applyCaves);
    }

    private static void planCaves(MantleStageContext context) {
        if (!carves(context.settings(), context.originX(), context.originZ())) {
            return;
        }
        MatterMantle mantle = MatterMantle.of(context.mantle());
        caveCarving(context.world(), context.terrain(), context.settings(), context.originX(), context.originZ())
                .carve(mantle, context.terrain(), context.originX() >> CHUNK_SHIFT, context.originZ() >> CHUNK_SHIFT);
    }

    private static void applyCaves(ChunkStageContext context) {
        MatterMantle mantle = MatterMantle.of(context.mantle());
        Hunk<BlockState> blocks = context.hunk(BlockState.class);
        caveApplying(context.world(), context.terrain(), context.settings(), context.originX(), context.originZ())
                .apply(mantle, context.terrain(), context.originX() >> CHUNK_SHIFT, context.originZ() >> CHUNK_SHIFT, blocks, CaveDressing.of(context));
    }

    private static CaveCarving caveCarving(PackWorld world, TerrainSampler terrain, FeatureSettings settings, int originX, int originZ) {
        return world.state(CaveCarving.class, generating -> new CaveCarving(generating, carveWorld(generating, terrain),
                upperDimensionCarving(settings, originX, originZ), fluidColumns(generating)));
    }

    private static CaveApplying caveApplying(PackWorld world, TerrainSampler terrain, FeatureSettings settings, int originX, int originZ) {
        return world.state(CaveApplying.class, generating -> new CaveApplying(generating, carveWorld(generating, terrain),
                upperDimensionCarving(settings, originX, originZ)));
    }

    /** The columns the dimension's fluid palette fills with a fluid. */
    private static CaveCarving.FluidColumns fluidColumns(PackWorld world) {
        TerrainPalettes palettes = TerrainPalettes.of(world);
        BlockRegistry blocks = world.resources().blocks();
        return (worldX, worldZ) -> {
            BlockState fluid = palettes.fluidAt(worldX, worldZ);
            return fluid != null && blocks.hasTrait(fluid, BlockTrait.FLUID);
        };
    }

    /** What the carvers need of the world, from the dimension's settings and the first terrain they see (constant for the world). */
    private static CarveWorld carveWorld(PackWorld world, TerrainSampler terrain) {
        int lavaHeight = dimensionSetting(world, CaveLavaHeightSettings.class, CaveLavaHeightSettings::height, CaveLavaHeightSettings.DEFAULT_HEIGHT);
        return new CarveWorld(world.resources().noise(), IrisSeed.CARVE.of(world), terrain.bounds().maxY(), terrain.fluidHeight(), lavaHeight,
                world.minHeight());
    }

    private static boolean upperDimensionCarving(FeatureSettings settings, int originX, int originZ) {
        return dimensionEntry(settings, UpperDimensionCarvingSettings.class, originX + CHUNK_CENTRE_BLOCKS, originZ + CHUNK_CENTRE_BLOCKS)
                .map(UpperDimensionCarvingSettings::enabled).orElse(false);
    }

    /**
     * A dimension turns carving off with {@code carvingEnabled}. Listing {@code CARVED} in {@code disabledComponents}
     * also does: Iris then skips this stage's mantle pass like any disabled component.
     */
    private static boolean carves(FeatureSettings settings, int originX, int originZ) {
        return dimensionEntry(settings, CarvingEnabledSettings.class, originX + CHUNK_CENTRE_BLOCKS, originZ + CHUNK_CENTRE_BLOCKS)
                .map(CarvingEnabledSettings::enabled).orElse(true);
    }

    private static <F extends IrisFeature> Optional<F> dimensionEntry(FeatureSettings settings, Class<F> type, int x, int z) {
        return settings.at(type, x, z).stream().filter(entry -> entry.level() == Target.DIMENSION).map(Entry::value).findFirst();
    }

    private static <F extends IrisFeature, V> V dimensionSetting(PackWorld world, Class<F> type, Function<F, V> read, V fallback) {
        return world.settings().declared(type).stream().filter(entry -> entry.level() == Target.DIMENSION).map(Entry::value).map(read)
                .findFirst().orElse(fallback);
    }
}
