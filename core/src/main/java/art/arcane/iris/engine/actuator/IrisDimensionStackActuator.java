/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.engine.actuator;

import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.DimensionStackLayout;
import art.arcane.iris.engine.DimensionTerrainContext;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineAssignedActuator;
import art.arcane.iris.engine.framework.TreeBlockMaterial;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.BoundBlockState;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.iris.util.project.matter.TileWrapper;
import art.arcane.iris.util.project.matter.PreObjectMatterCell;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterMarker;
import art.arcane.volmlib.util.matter.MatterSlice;
import art.arcane.volmlib.util.matter.MatterStructurePOI;
import art.arcane.volmlib.util.matter.MatterUpdate;

import java.util.ArrayList;
import java.util.List;

public final class IrisDimensionStackActuator extends EngineAssignedActuator<PlatformBlockState> {
    private static final BoundBlockState AIR = BoundBlockState.of("AIR");
    private static final BoundBlockState BEDROCK = BoundBlockState.of("BEDROCK");
    private static final List<Class<?>> REPLACED_METADATA = List.of(
            TileWrapper.class,
            Identifier.class,
            String.class,
            TreeBlockMaterial.class,
            MatterCavern.class,
            HydrologyCaveCell.class,
            MatterMarker.class,
            MatterStructurePOI.class,
            MatterUpdate.class
    );

    private final RNG rng;

    public IrisDimensionStackActuator(Engine engine) {
        super(engine, "Dimension Stack");
        rng = new RNG(engine.getSeedManager().getTerrain());
    }

    @BlockCoordinates
    @Override
    public void onActuate(
            int x,
            int z,
            Hunk<PlatformBlockState> blocks,
            boolean multicore,
            ChunkContext context
    ) {
        if (getEngine().getDimensionStackContext() == null) {
            return;
        }
        MantleChunk<Matter> mantleChunk = context.isSpeculativeTerrain() ? null
                : getEngine().getMantle().getMantle().getChunk(x >> 4, z >> 4).use();
        try {
            MetadataCleaner metadata = new MetadataCleaner(mantleChunk, blocks.getHeight());
            for (int localX = 0; localX < blocks.getWidth(); localX++) {
                int worldX = x + localX;
                for (int localZ = 0; localZ < blocks.getDepth(); localZ++) {
                    renderLayers(
                            localX,
                            localZ,
                            worldX,
                            z + localZ,
                            blocks,
                            context.getDimensionStackLayout(localX, localZ),
                            metadata
                    );
                }
            }
        } finally {
            if (mantleChunk != null) {
                mantleChunk.release();
            }
        }
    }

    public static void clearNaturalMetadata(MantleChunk<Matter> chunk, ChunkContext context, int height) {
        MetadataCleaner metadata = new MetadataCleaner(chunk, height);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                DimensionStackLayout layout = context.getDimensionStackLayout(x, z);
                if (layout == null) {
                    continue;
                }
                for (int y = 0; y < height; y++) {
                    if (layout.isHostFeatureProtectedY(y)) {
                        metadata.clear(x, y, z);
                    }
                }
            }
        }
    }

    private void renderLayers(
            int localX,
            int localZ,
            int worldX,
            int worldZ,
            Hunk<PlatformBlockState> blocks,
            DimensionStackLayout layout,
            MetadataCleaner metadata
    ) {
        if (layout == null) {
            return;
        }
        List<DimensionStackLayout.Layer> layers = layout.layersBottomToTop();
        for (int layerIndex = 1; layerIndex < layers.size(); layerIndex++) {
            DimensionStackLayout.Layer layer = layers.get(layerIndex);
            clearGap(localX, localZ, blocks, layers.get(layerIndex - 1), layer, metadata);
            if (layer.visible()) {
                renderLayer(localX, localZ, worldX, worldZ, blocks, layer, metadata);
            }
        }
    }

    private void clearGap(
            int localX,
            int localZ,
            Hunk<PlatformBlockState> output,
            DimensionStackLayout.Layer lower,
            DimensionStackLayout.Layer upper,
            MetadataCleaner metadata
    ) {
        int minimumY = (int) Math.max(0L, (long) lower.contentTopY() + 1L);
        int maximumY = (int) Math.min(
                (long) output.getHeight() - 1L,
                (long) upper.localBaseY() - 1L
        );
        for (int y = minimumY; y <= maximumY; y++) {
            metadata.clear(localX, y, localZ);
            output.setRaw(localX, y, localZ, AIR.get());
        }
    }

    private void renderLayer(
            int localX,
            int localZ,
            int worldX,
            int worldZ,
            Hunk<PlatformBlockState> output,
            DimensionStackLayout.Layer layer,
            MetadataCleaner metadata
    ) {
        DimensionTerrainContext terrainContext = layer.terrainContext();
        IrisDimension dimension = terrainContext.getDimension();
        IrisData data = terrainContext.getData();
        IrisBiome biome = layer.biome();
        int surfaceDepth = Math.max(0, layer.normalTerrainHeight());
        int fluidDepth = Math.max(0, layer.fluidHeight() - layer.normalTerrainHeight());
        KList<PlatformBlockState> surfaceBlocks = biome == null
                ? null
                : biome.generateLayersWithSlope(
                        dimension,
                        worldX,
                        worldZ,
                        rng,
                        surfaceDepth,
                        layer.normalTerrainHeight(),
                        data,
                        terrainContext.getSlopeStream()
                );
        KList<PlatformBlockState> seaBlocks = biome == null || fluidDepth == 0
                ? null
                : biome.generateSeaLayers(worldX, worldZ, rng, fluidDepth, data);

        for (int y = layer.renderMaxY(); y >= layer.renderMinY(); y--) {
            int sourceY = y - layer.localBaseY();
            if (sourceY == 0 && dimension.isBedrock()) {
                writeBlock(output, metadata, localX, y, localZ, BEDROCK.get());
                continue;
            }
            if (sourceY > layer.normalTerrainHeight() && sourceY <= layer.fluidHeight()) {
                int fluidLayerDepth = layer.fluidY() - y;
                writeBlock(output, metadata, localX, y, localZ, HydrologyFluidLayerSelector.select(
                        seaBlocks,
                        fluidLayerDepth,
                        layer.fluidBlock(),
                        false
                ));
                continue;
            }
            if (sourceY > layer.normalTerrainHeight()) {
                continue;
            }
            int depth = layer.surfaceY() - y;
            if (depth == 0 && layer.surfaceBlock() != null) {
                writeBlock(output, metadata, localX, y, localZ, layer.surfaceBlock());
            } else if (surfaceBlocks != null && surfaceBlocks.hasIndex(depth)) {
                writeBlock(output, metadata, localX, y, localZ, surfaceBlocks.get(depth));
            } else {
                writeBlock(output, metadata, localX, y, localZ, layer.rockBlock());
            }
        }
    }

    private void writeBlock(
            Hunk<PlatformBlockState> output,
            MetadataCleaner metadata,
            int x,
            int y,
            int z,
            PlatformBlockState block
    ) {
        metadata.clear(x, y, z);
        output.setRaw(x, y, z, block);
    }

    static final class MetadataCleaner {
        private final MantleChunk<Matter> chunk;
        private final List<MatterSlice<?>>[] slicesBySection;

        @SuppressWarnings("unchecked")
        MetadataCleaner(MantleChunk<Matter> chunk, int height) {
            this.chunk = chunk;
            slicesBySection = (List<MatterSlice<?>>[]) new List<?>[(height + 15) >> 4];
        }

        void clear(int x, int y, int z) {
            if (chunk == null) {
                return;
            }
            synchronized (chunk) {
                int sectionIndex = y >> 4;
                captureNaturalFacts(sectionIndex, x, y & 15, z);
                List<MatterSlice<?>> slices = slicesBySection[sectionIndex];
                if (slices == null) {
                    slices = resolveSlices(sectionIndex);
                    slicesBySection[sectionIndex] = slices;
                }
                for (MatterSlice<?> slice : slices) {
                    slice.set(x, y & 15, z, null);
                }
            }
        }

        private void captureNaturalFacts(int sectionIndex, int x, int y, int z) {
            Matter section = chunk.get(sectionIndex);
            if (section == null) {
                return;
            }
            MatterSlice<MatterCavern> caverns = section.getSlice(MatterCavern.class);
            MatterSlice<HydrologyCaveCell> hydrology = section.getSlice(HydrologyCaveCell.class);
            MatterCavern cavern = caverns == null ? null : caverns.get(x, y, z);
            HydrologyCaveCell cell = hydrology == null ? null : hydrology.get(x, y, z);
            if (cavern == null && cell == null) {
                return;
            }
            MatterSlice<PreObjectMatterCell> journal = section.slice(PreObjectMatterCell.class);
            PreObjectMatterCell previous = journal.get(x, y, z);
            if (cavern != null) {
                previous = previous == null ? PreObjectMatterCell.cavern(cavern) : previous.captureCavern(cavern);
            }
            if (cell != null) {
                previous = previous == null ? PreObjectMatterCell.hydrology(cell) : previous.captureHydrology(cell);
            }
            journal.set(x, y, z, previous);
        }

        private List<MatterSlice<?>> resolveSlices(int sectionIndex) {
            Matter section = chunk.get(sectionIndex);
            if (section == null) {
                return List.of();
            }
            ArrayList<MatterSlice<?>> slices = new ArrayList<>(REPLACED_METADATA.size());
            for (Class<?> type : REPLACED_METADATA) {
                MatterSlice<?> slice = section.getSlice(type);
                if (slice != null) {
                    slices.add(slice);
                }
            }
            return List.copyOf(slices);
        }
    }
}
