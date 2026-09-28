package art.arcane.iris.world.history;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SavedBiomeCapture {
    private SavedBiomeCapture() {
    }

    public static SavedBiomeChunk capture(Engine engine, GenerationHistory.GenerationStage stage, SavedBiomeRuntime saved, FloatingBiomeOverlay floating) throws IOException {
        int minimumY = engine.getMinHeight();
        int height = engine.getHeight();
        if (floating != null && floating.height() != height) {
            throw new IllegalArgumentException("Floating biome overlay has a different generation height.");
        }
        int startX = Math.multiplyExact(stage.chunkX(), 16);
        int startZ = Math.multiplyExact(stage.chunkZ(), 16);
        long activationId = stage.activation().activationId();
        SavedBiomeChunk.Builder result = SavedBiomeChunk.builder(new SavedBiomeChunk.Header(
                stage.chunkX(), stage.chunkZ(), activationId, minimumY, height));
        TransitionGenerationPlan transition = engine.getComplex().getTransitionGenerationPlan();
        Map<Long, Optional<SavedBiomeChunk>> historical = new HashMap<>();
        Map<Integer, List<SavedBiomeChunk.Span>> vertical = new HashMap<>(16);
        Cells cells = new Cells(activationId);
        Column column = new Column(engine, minimumY, height);
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int blockX = startX + localX;
                int blockZ = startZ + localZ;
                IrisRegion region = engine.getRegion(blockX, blockZ);
                IrisBiome surface = engine.getSurfaceBiome(blockX, blockZ);
                int surfaceY = minimumY + engine.getHeight(blockX, blockZ);
                SavedBiomeChunk.Cell surfaceCell = cells.cell(surface, region);
                if (floating != null) {
                    FloatingBiomeOverlay.Identity floatingSurface = floating.surfaceAt(localX, localZ);
                    if (floatingSurface != null) {
                        surfaceCell = overlay(surfaceCell, floatingSurface);
                        surfaceY = minimumY + floating.surfaceYAt(localX, localZ);
                    }
                }
                if (transition != null) {
                    surfaceCell = historicalCell(new Sample(blockX, surfaceY, blockZ, true), surfaceCell, transition, saved, historical);
                }
                int quartX = localX & ~3;
                int quartZ = localZ & ~3;
                int quartKey = quartX * 16 + quartZ;
                List<SavedBiomeChunk.Span> spans = vertical.get(quartKey);
                if (spans == null) {
                    spans = captureVertical(column, startX + quartX, startZ + quartZ, cells, transition, saved, historical, floating);
                    vertical.put(quartKey, spans);
                }
                SavedBiomeChunk.Cell caveBase = cells.cell(engine.getCaveBiome(blockX, blockZ), region);
                result.column(localX, localZ, new SavedBiomeChunk.Column(surfaceCell, caveBase, spans));
            }
        }
        return result.build();
    }

    private static List<SavedBiomeChunk.Span> captureVertical(Column column, int blockX, int blockZ, Cells cells,
                                                             TransitionGenerationPlan transition, SavedBiomeRuntime saved,
                                                             Map<Long, Optional<SavedBiomeChunk>> historical, FloatingBiomeOverlay floating) throws IOException {
        column.engine().getBiomeOrMantleColumn(blockX, blockZ, 4, column.biomes(), column.regions());
        int minimumY = column.minimumY();
        int maximumY = minimumY + column.height();
        List<SavedBiomeChunk.Span> spans = new ArrayList<>();
        SavedBiomeChunk.Cell previous = null;
        int startY = minimumY;
        for (int index = 0; index < column.biomes().length; index++) {
            int worldY = minimumY + index * 4;
            SavedBiomeChunk.Cell current = cells.cell(column.biomes()[index], column.regions()[index]);
            if (floating != null) {
                current = overlay(current, floating.volumeAt(blockX & 15, worldY - minimumY, blockZ & 15));
            }
            if (transition != null) {
                current = historicalCell(new Sample(blockX, worldY, blockZ, false), current, transition, saved, historical);
            }
            if (previous != null && !previous.equals(current)) {
                spans.add(new SavedBiomeChunk.Span(startY, worldY, previous));
                startY = worldY;
            }
            previous = current;
        }
        spans.add(new SavedBiomeChunk.Span(startY, maximumY, Objects.requireNonNull(previous, "biome")));
        return List.copyOf(spans);
    }

    private static SavedBiomeChunk.Cell historicalCell(Sample sample, SavedBiomeChunk.Cell current,
                                                        TransitionGenerationPlan transition, SavedBiomeRuntime saved,
                                                        Map<Long, Optional<SavedBiomeChunk>> historical) throws IOException {
        TransitionGenerationPlan.TerrainSample terrain = transition.terrainSampleAt(sample.x(), sample.z());
        if (transition.historicalPhysicalBiomeKeyAt(sample.x(), sample.y(), sample.z(), terrain).isEmpty()) {
            return current;
        }
        TerrainBoundarySignature signature = Objects.requireNonNull(terrain.nearestSignature(), "historical biome boundary");
        int chunkX = Math.floorDiv(signature.blockX(), 16);
        int chunkZ = Math.floorDiv(signature.blockZ(), 16);
        long key = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
        Optional<SavedBiomeChunk> recorded = historical.get(key);
        if (recorded == null) {
            try {
                recorded = saved.snapshot(chunkX, chunkZ);
            } catch (SavedBiomeUnavailableException unavailable) {
                recorded = Optional.empty();
            }
            historical.put(key, recorded);
        }
        if (recorded.isEmpty()) {
            return SavedBiomeChunk.Cell.unresolved(saved.activationAt(chunkX, chunkZ));
        }
        SavedBiomeChunk source = recorded.get();
        int localX = Math.floorMod(signature.blockX(), 16);
        int localZ = Math.floorMod(signature.blockZ(), 16);
        return sample.surface() ? source.surfaceAt(localX, localZ)
                : source.biomeAt(localX, Math.max(source.header().minimumY(),
                Math.min(sample.y(), source.header().maximumYExclusive() - 1)), localZ);
    }

    private static SavedBiomeChunk.Cell overlay(SavedBiomeChunk.Cell current, FloatingBiomeOverlay.Identity floating) {
        return floating == null ? current
                : new SavedBiomeChunk.Cell(current.activationId(), floating.biomeKey(), floating.regionKey());
    }

    private static SavedBiomeChunk.Cell cell(long activationId, IrisBiome biome, IrisRegion region) {
        return new SavedBiomeChunk.Cell(activationId, Objects.requireNonNull(biome, "biome").getLoadKey(),
                Objects.requireNonNull(region, "region").getLoadKey());
    }

    private record Column(Engine engine, int minimumY, int height, IrisBiome[] biomes, IrisRegion[] regions) {
        private Column(Engine engine, int minimumY, int height) {
            this(engine, minimumY, height, new IrisBiome[(height + 3) / 4], new IrisRegion[(height + 3) / 4]);
        }
    }

    private static final class Cells {
        private final long activationId;
        private IrisBiome[] biomes = new IrisBiome[8];
        private IrisRegion[] regions = new IrisRegion[8];
        private SavedBiomeChunk.Cell[] cells = new SavedBiomeChunk.Cell[8];
        private int size;

        private Cells(long activationId) {
            this.activationId = activationId;
        }

        private SavedBiomeChunk.Cell cell(IrisBiome biome, IrisRegion region) {
            for (int index = 0; index < size; index++) {
                if (biomes[index] == biome && regions[index] == region) {
                    return cells[index];
                }
            }
            SavedBiomeChunk.Cell created = SavedBiomeCapture.cell(activationId, biome, region);
            if (size == cells.length) {
                biomes = Arrays.copyOf(biomes, size * 2);
                regions = Arrays.copyOf(regions, size * 2);
                cells = Arrays.copyOf(cells, size * 2);
            }
            biomes[size] = biome;
            regions[size] = region;
            cells[size++] = created;
            return created;
        }
    }

    private record Sample(int x, int y, int z, boolean surface) {
    }
}
