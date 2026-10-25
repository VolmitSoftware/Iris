package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.ChunkedDataCache;
import art.arcane.iris.generation.decoration.IrisOreBands;
import art.arcane.iris.generation.decoration.IrisOreGenerator;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyMaterials;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.image.IrisImageMapRuntime;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.generation.terrain.Terrain3DColumn;
import art.arcane.iris.generation.terrain.Terrain3DColumnFixtures;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Paints randomized columns (Terrain3D spans with cave ceilings, biome layers, water roles and their
 * materials, a mapped surface, bedrock, fluid, and surface and underground ores at every level) and compares
 * each block with the per-block algorithm the painter replaced.
 */
public class IrisTerrainNormalActuatorColumnParityTest {
    private static final int HEIGHT = 96;
    private static final int X = 21;
    private static final int Z = -7;
    private static final Map<WaterRole, String> ROLE_PALETTE = Map.of(
            WaterRole.CHANNEL, "bed", WaterRole.POND, "bed", WaterRole.MOUTH, "bed", WaterRole.CASCADE, "bed",
            WaterRole.WATERFALL, "bed", WaterRole.SHORE, "shore", WaterRole.POND_SHORE, "shore",
            WaterRole.DAM, "shore", WaterRole.BANK, "bank");

    @Rule
    public final PlatformBinding platform = PlatformBinding.of(IrisTerrainNormalActuatorColumnParityTest::distinctBlocks);

    @Test
    public void paintedColumnsMatchThePerBlockAlgorithm() {
        Random random = new Random(20260928L);
        for (int trial = 0; trial < 600; trial++) {
            Fixture fixture = new Fixture(random, trial);
            Hunk<NativeBlockState> painted = Hunk.newArrayHunk(1, HEIGHT, 1);
            new IrisTerrainNormalActuator(fixture.engine).paint(X, Z, painted, fixture.context);
            Hunk<NativeBlockState> expected = Hunk.newArrayHunk(1, HEIGHT, 1);
            fixture.reference(expected);
            for (int y = 0; y < HEIGHT; y++) {
                assertEquals("trial " + trial + " y " + y + " " + fixture,
                        key(expected.getRaw(0, y, 0)), key(painted.getRaw(0, y, 0)));
            }
        }
    }

    private static String key(NativeBlockState state) {
        return state == null ? "null" : state.key();
    }

    private static IrisPlatform distinctBlocks() {
        Map<String, NativeBlockState> blocks = new HashMap<>();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block(anyString())).thenAnswer(invocation ->
                blocks.computeIfAbsent(invocation.getArgument(0), Fixture::state));
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        return platform;
    }

    private static final class Fixture {
        private final Engine engine = mock(Engine.class);
        private final ChunkContext context = mock(ChunkContext.class);
        private final IrisComplex complex = mock(IrisComplex.class);
        private final IrisData data = mock(IrisData.class);
        private final IrisBiome biome = mock(IrisBiome.class);
        private final IrisRegion region = new IrisRegion();
        private final IrisDimension dimension;
        private final RNG rng = new RNG(0L);
        private final NativeBlockState rock = state("rock");
        private final NativeBlockState fluid = state("water");
        private final NativeBlockState waterFluid = state("river_water");
        private final NativeBlockState mapped;
        private final Terrain3DColumn terrainColumn;
        private final WaterRole role;
        private final IrisHydrologyMaterials materials;
        private final Map<String, NativeBlockState> palettes = new HashMap<>();
        private final int he;
        private final int surfaceFluid;
        private final KList<IrisOreGenerator> biomeSurface = new KList<>();
        private final KList<IrisOreGenerator> biomeUnderground = new KList<>();
        private final KList<IrisOreGenerator> regionSurface = new KList<>();
        private final KList<IrisOreGenerator> regionUnderground = new KList<>();
        private final KList<IrisOreGenerator> dimensionSurface = new KList<>();
        private final KList<IrisOreGenerator> dimensionUnderground = new KList<>();
        private final String description;

        private Fixture(Random random, int trial) {
            he = 3 + random.nextInt(HEIGHT);
            surfaceFluid = random.nextInt(HEIGHT + 4);
            boolean bedrock = random.nextBoolean();
            mapped = random.nextInt(4) == 0 ? state("mapped") : null;
            int layerCount = random.nextInt(6);
            int seaCount = random.nextInt(3);
            int ceilingCount = random.nextInt(3);
            terrainColumn = random.nextBoolean() ? null : spans(random, Math.min(he, HEIGHT - 1));
            role = random.nextBoolean() ? WaterRole.NONE : WaterRole.values()[random.nextInt(WaterRole.values().length)];
            boolean gravityLayers = random.nextInt(4) == 0;
            materials = role == WaterRole.NONE || random.nextInt(3) == 0 ? null : new IrisHydrologyMaterials()
                    .setBed(random.nextBoolean() ? palette("bed") : null)
                    .setShore(random.nextBoolean() ? palette("shore") : null)
                    .setBank(random.nextBoolean() ? palette("bank") : null)
                    .setShoreDepth(1 + random.nextInt(8))
                    .setAllowGravityBlocks(random.nextBoolean());
            for (int level = 0; level < 6; level++) {
                KList<IrisOreGenerator> target = List.of(biomeSurface, biomeUnderground, regionSurface,
                        regionUnderground, dimensionSurface, dimensionUnderground).get(level);
                int count = random.nextInt(3) == 0 ? 0 : random.nextInt(4);
                for (int index = 0; index < count; index++) {
                    double min = random.nextInt(HEIGHT);
                    target.add(ore(min, min + random.nextInt(30), 0.3D + random.nextDouble() * 0.7D,
                            level % 2 == 0, 97L * trial + 13L * level + index));
                }
            }
            KList<IrisOreGenerator> regionOres = new KList<>(regionSurface);
            regionOres.addAll(regionUnderground);
            region.setOres(regionOres);
            KList<IrisOreGenerator> dimensionOres = new KList<>(dimensionSurface);
            dimensionOres.addAll(dimensionUnderground);
            dimension = new IrisDimension().setBedrock(bedrock).setHideOresForHiddenOre(false);
            dimension.setOres(dimensionOres);

            when(engine.getDimension()).thenReturn(dimension);
            when(engine.getComplex()).thenReturn(complex);
            when(engine.getData()).thenReturn(data);
            when(engine.getSeedManager()).thenReturn(mock(SeedManager.class));
            IrisImageMapRuntime imageMap = mock(IrisImageMapRuntime.class);
            when(imageMap.sampleSurfaceBlock(anyDouble(), anyDouble())).thenReturn(mapped);
            when(complex.getImageMapRuntime()).thenReturn(imageMap);
            when(complex.getWaterHeadStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> (double) surfaceFluid));
            when(complex.getWaterRoleStream()).thenReturn(ProceduralStream.of((x, z) -> role, Interpolated.of(value -> 0D, value -> role)));
            when(complex.resolveWaterMaterials(X, Z)).thenReturn(materials);
            when(complex.resolveWaterFluid(X, Z)).thenReturn(waterFluid);
            when(complex.naturalTrueHeight(X, Z)).thenReturn(he + 5);
            when(complex.terrainColumn(X, Z)).thenReturn(terrainColumn);
            when(complex.naturalTerrainColumn(X, Z)).thenReturn(terrainColumn);
            when(context.getRoundedHeight(0, 0)).thenReturn(he);
            when(context.getBiome()).thenReturn(new ChunkedDataCache<>(
                    ProceduralStream.of((x, z) -> biome, Interpolated.of(value -> 0D, value -> biome)), X, Z, false));
            when(context.getRegion()).thenReturn(new ChunkedDataCache<>(
                    ProceduralStream.of((x, z) -> region, Interpolated.of(value -> 0D, value -> region)), X, Z, false));
            when(context.getRock()).thenReturn(new ChunkedDataCache<>(
                    ProceduralStream.of((x, z) -> rock, Interpolated.of(value -> 0D, value -> rock)), X, Z, false));
            when(context.getFluid()).thenReturn(new ChunkedDataCache<>(
                    ProceduralStream.of((x, z) -> fluid, Interpolated.of(value -> 0D, value -> fluid)), X, Z, false));
            when(biome.generateLayers(eq(dimension), anyDouble(), anyDouble(), any(RNG.class),
                    anyInt(), anyInt(), eq(data), eq(complex))).thenAnswer(invocation -> layers(gravityLayers ? "sand" : "layer", layerCount));
            when(biome.generateSeaLayers(anyDouble(), anyDouble(), any(RNG.class), anyInt(), eq(data)))
                    .thenAnswer(invocation -> layers("sea", seaCount));
            when(biome.generateCeilingLayers(eq(dimension), anyDouble(), anyDouble(), any(RNG.class),
                    anyInt(), anyInt(), eq(data), eq(complex))).thenAnswer(invocation -> layers("ceiling", ceilingCount));
            when(biome.getSurfaceOreBands()).thenReturn(IrisOreBands.of(biomeSurface));
            when(biome.getUndergroundOreBands()).thenReturn(IrisOreBands.of(biomeUnderground));
            description = "he=" + he + " fluid=" + surfaceFluid + " bedrock=" + bedrock + " mapped=" + (mapped != null)
                    + " layers=" + layerCount + " column=" + terrainColumn + " role=" + role + " materials=" + materials;
        }

        @Override
        public String toString() {
            return description;
        }

        private void reference(Hunk<NativeBlockState> h) {
            int chunkHeight = h.getHeight();
            int xf = 0;
            int zf = 0;
            int realX = X;
            int realZ = Z;
            NativeBlockState bedrockState = B.getState("BEDROCK");
            NativeBlockState airState = B.getState("AIR");
            boolean wet = role.wet();
            boolean seaFloor = !wet || role == WaterRole.INLET;
            IrisHydrologyMaterials materials = role == WaterRole.INLET ? null : this.materials;
            int he = Math.min(chunkHeight, this.he);
            int surfaceFluidHeight = Math.min(chunkHeight, this.surfaceFluid);
            int hf = Math.max(surfaceFluidHeight, he);
            if (hf < 0) {
                return;
            }
            int topY = Math.min(hf, chunkHeight - 1);
            int terrainSpan = terrainColumn == null ? -1 : terrainColumn.spanCount() - 1;
            int layerSurfaceY = he;
            int layerCeilingY = 0;
            boolean exposeStrata = role != WaterRole.NONE && seaFloor;
            Terrain3DColumn naturalColumn = exposeStrata ? terrainColumn : null;
            int cut = 0;
            boolean keepGravityAway = materials != null && !materials.isAllowGravityBlocks();
            String paletteName = materials == null ? null : ROLE_PALETTE.get(role);
            NativeBlockState paletteBlock = paletteName == null ? null : palettes.get(paletteName);
            int paintDepth = paletteBlock == null ? 0 : paletteName.equals("shore") ? materials.getShoreDepth() : 1;
            NativeBlockState columnFluid = wet ? waterFluid : fluid;
            OldBounds biomeSurfaceBounds = new OldBounds(biomeSurface);
            OldBounds regionSurfaceBounds = new OldBounds(regionSurface);
            OldBounds dimensionSurfaceBounds = new OldBounds(dimensionSurface);
            OldBounds biomeUndergroundBounds = new OldBounds(biomeUnderground);
            OldBounds regionUndergroundBounds = new OldBounds(regionUnderground);
            OldBounds dimensionUndergroundBounds = new OldBounds(dimensionUnderground);
            boolean hasSurfaceOres = biomeSurfaceBounds.hasOres || regionSurfaceBounds.hasOres || dimensionSurfaceBounds.hasOres;
            boolean hasUndergroundOres = biomeUndergroundBounds.hasOres || regionUndergroundBounds.hasOres || dimensionUndergroundBounds.hasOres;
            KList<NativeBlockState> blocks = null;
            KList<NativeBlockState> ceilingBlocks = null;
            KList<NativeBlockState> fblocks = null;

            for (int i = topY; i >= 0; i--) {
                if (i == 0 && dimension.isBedrock()) {
                    h.setRaw(xf, i, zf, bedrockState);
                    continue;
                }
                if (terrainColumn != null && i <= he) {
                    while (terrainSpan >= 0 && i < terrainColumn.ceiling(terrainSpan)) {
                        terrainSpan--;
                        blocks = null;
                        ceilingBlocks = null;
                    }
                    if (terrainSpan < 0 || i > terrainColumn.floor(terrainSpan)) {
                        h.setRaw(xf, i, zf, airState);
                        continue;
                    }
                    layerSurfaceY = terrainColumn.floor(terrainSpan);
                    layerCeilingY = terrainColumn.ceiling(terrainSpan);
                }
                NativeBlockState ore = null;
                if (hasSurfaceOres) {
                    if (biomeSurfaceBounds.contains(i)) {
                        ore = scan(biomeSurface, realX, i, realZ);
                    }
                    if (ore == null && regionSurfaceBounds.contains(i)) {
                        ore = scan(regionSurface, realX, i, realZ);
                    }
                    if (ore == null && dimensionSurfaceBounds.contains(i)) {
                        ore = scan(dimensionSurface, realX, i, realZ);
                    }
                }
                if (ore != null) {
                    h.setRaw(xf, i, zf, ore);
                    continue;
                }
                if (i > he && i <= hf) {
                    int fdepth = hf - i;
                    if (seaFloor && fblocks == null) {
                        fblocks = biome.generateSeaLayers(realX, realZ, rng, hf - he, data);
                    }
                    h.setRaw(xf, i, zf, HydrologyFluidLayerSelector.select(fblocks, fdepth, columnFluid, !seaFloor));
                    continue;
                }
                if (i <= he) {
                    int depth = layerSurfaceY - i;
                    if (depth == 0 && mapped != null) {
                        h.setRaw(xf, i, zf, mapped);
                        continue;
                    }
                    if (blocks == null) {
                        if (exposeStrata) {
                            int naturalSurfaceY = naturalColumn == null ? he + 5 : naturalColumn.surfaceY(layerSurfaceY);
                            cut = Math.max(0, naturalSurfaceY - layerSurfaceY);
                        }
                        blocks = biome.generateLayers(dimension, realX, realZ, rng,
                                layerSurfaceY + cut, layerSurfaceY + cut, data, complex);
                    }
                    if (layerCeilingY > 0 && depth >= 2 && i - layerCeilingY < 2) {
                        if (ceilingBlocks == null) {
                            ceilingBlocks = biome.generateCeilingLayers(dimension, realX, realZ, rng, 2, layerCeilingY, data, complex);
                        }
                        int ceilingDepth = i - layerCeilingY;
                        h.setRaw(xf, i, zf, ceilingBlocks.hasIndex(ceilingDepth) ? ceilingBlocks.get(ceilingDepth) : rock);
                        continue;
                    }
                    int strataIndex = IrisTerrainNormalActuator.strataIndex(depth, cut, blocks.size());
                    NativeBlockState layerBlock = paletteBlock != null && depth < paintDepth ? paletteBlock
                            : blocks.hasIndex(strataIndex) ? blocks.get(strataIndex) : null;
                    if (layerBlock != null) {
                        if (keepGravityAway && IrisProceduralBlocks.isGravityAffected(layerBlock)) {
                            layerBlock = rock;
                        }
                        h.setRaw(xf, i, zf, layerBlock);
                        continue;
                    }
                    if (hasUndergroundOres) {
                        if (biomeUndergroundBounds.contains(i)) {
                            ore = scan(biomeUnderground, realX, i, realZ);
                        }
                        if (ore == null && regionUndergroundBounds.contains(i)) {
                            ore = scan(regionUnderground, realX, i, realZ);
                        }
                        if (ore == null && dimensionUndergroundBounds.contains(i)) {
                            ore = scan(dimensionUnderground, realX, i, realZ);
                        }
                    }
                    h.setRaw(xf, i, zf, ore != null ? ore : rock);
                }
            }
        }

        private NativeBlockState scan(KList<IrisOreGenerator> ores, int x, int y, int z) {
            for (IrisOreGenerator ore : ores) {
                NativeBlockState state = ore.generate(x, y, z, rng, data);
                if (state != null) {
                    return state;
                }
            }
            return null;
        }

        private static KList<NativeBlockState> layers(String name, int count) {
            KList<NativeBlockState> layers = new KList<>();
            for (int index = 0; index < count; index++) {
                layers.add(state(index % 2 == 0 && name.equals("sand") ? name : name + index));
            }
            return layers;
        }

        private IrisMaterialPalette palette(String name) {
            IrisMaterialPalette palette = mock(IrisMaterialPalette.class);
            NativeBlockState block = state(name);
            palettes.put(name, block);
            when(palette.get(any(RNG.class), anyDouble(), anyDouble(), anyDouble(), eq(data))).thenReturn(block);
            return palette;
        }

        private static Terrain3DColumn spans(Random random, int top) {
            List<Integer> boundaries = new ArrayList<>();
            boundaries.add(0);
            int cursor = random.nextInt(Math.max(1, top / 3) + 1);
            boundaries.add(cursor);
            while (cursor + 3 < top && random.nextBoolean()) {
                int ceiling = cursor + 2 + random.nextInt(Math.max(1, (top - cursor) / 2));
                if (ceiling >= top) {
                    break;
                }
                int floor = ceiling + random.nextInt(top - ceiling + 1);
                boundaries.add(ceiling);
                boundaries.add(floor);
                cursor = floor;
            }
            return Terrain3DColumnFixtures.spans(top, boundaries.stream().mapToInt(Integer::intValue).toArray());
        }

        private IrisOreGenerator ore(double min, double max, double threshold, boolean surface, long seed) {
            IrisOreGenerator generator = new IrisOreGenerator();
            IrisMaterialPalette palette = mock(IrisMaterialPalette.class);
            NativeBlockState state = state("ore" + seed);
            when(palette.getPalette()).thenReturn(new KList<>(mock(IrisBlockData.class)));
            when(palette.get(any(), anyDouble(), anyDouble(), anyDouble(), any())).thenReturn(state);
            CNG chance = mock(CNG.class);
            when(chance.noise(anyDouble(), anyDouble(), anyDouble())).thenAnswer(invocation -> {
                long hash = seed * 31 + Double.doubleToLongBits(invocation.getArgument(1));
                return new Random(hash).nextDouble();
            });
            IrisGeneratorStyle style = mock(IrisGeneratorStyle.class);
            when(style.create(any(), any())).thenReturn(chance);
            return generator.setPalette(palette).setChanceStyle(style).setThreshold(threshold)
                    .setRange(new IrisRange(min, max)).setGenerateSurface(surface);
        }

        private static NativeBlockState state(String key) {
            NativeBlockState state = mock(NativeBlockState.class);
            when(state.key()).thenReturn("minecraft:" + key);
            return state;
        }
    }

    private static final class OldBounds {
        private final boolean hasOres;
        private final double min;
        private final double max;

        private OldBounds(KList<IrisOreGenerator> ores) {
            double low = Double.POSITIVE_INFINITY;
            double high = Double.NEGATIVE_INFINITY;
            for (IrisOreGenerator ore : ores) {
                low = Math.min(low, ore.getRange().getMin());
                high = Math.max(high, ore.getRange().getMax());
            }
            hasOres = !ores.isEmpty();
            min = low;
            max = high;
        }

        private boolean contains(int y) {
            return hasOres && y >= min && y <= max;
        }
    }
}
