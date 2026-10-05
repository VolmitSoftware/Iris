package art.arcane.iris.generation.subterrain;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomePaletteLayer;
import art.arcane.iris.generation.decoration.IrisDecorationPart;
import art.arcane.iris.generation.decoration.IrisDecorator;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.stage.IrisCarveModifier;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.hunk.Hunk;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

public class SubterrainSurfaceMaterialsTest {
    @Test
    public void everyGeometryUsesOwningBiomeFloorCeilingAndWallWithoutChangingOccupancy() {
        for (IrisSubterrainFamily family : IrisSubterrainFamily.values()) {
            try (Fixture fixture = new Fixture(family)) {
                Map<SubterrainSurfaceMaterials.Surface, SubterrainPosition> surfaces = fixture.surfaces();
                for (SubterrainSurfaceMaterials.Surface surface : List.of(SubterrainSurfaceMaterials.Surface.FLOOR,
                        SubterrainSurfaceMaterials.Surface.CEILING, SubterrainSurfaceMaterials.Surface.WALL)) {
                    SubterrainPosition position = surfaces.get(surface);
                    assertTrue(family + " " + surface, position != null);
                    SubterrainCell before = fixture.cell(position);
                    NativeBlockState expected = switch (surface) {
                        case FLOOR -> fixture.floor;
                        case CEILING -> fixture.ceiling;
                        case WALL -> fixture.wall;
                        default -> fixture.fallback;
                    };
                    assertSame(expected, fixture.materials.state(position.x(), position.y(), position.z(), before));
                    assertEquals(before.room().reservedSolid(), SubterrainRasterizer.protectsPlacement(before));
                    assertEquals(before, fixture.cell(position));
                    int chunkX = position.x() >> 4;
                    int chunkZ = position.z() >> 4;
                    List<SubterrainPlan> local = fixture.planner.plansForBounds((chunkX << 4) - 1, (chunkZ << 4) - 1,
                            (chunkX << 4) + 16, (chunkZ << 4) + 16);
                    SubterrainSurfaceMaterials chunk = fixture.materials(local);
                    assertSame(expected, chunk.state(position.x(), position.y(), position.z(), before));
                }
                SubterrainPosition anchor = fixture.plan.anchor();
                SubterrainCell occupied = fixture.cell(anchor);
                assertTrue(occupied.occupied());
                assertSame(SubterrainRasterizer.state(occupied), fixture.materials.state(anchor.x(), anchor.y(), anchor.z(), occupied));
            }
        }
    }

    @Test
    public void finalPreservationRestoresBiomeMaterialRatherThanErasingIt() throws Exception {
        try (Fixture fixture = new Fixture(IrisSubterrainFamily.CENOTE)) {
            Engine engine = mock(Engine.class);
            when(engine.getComplex()).thenReturn(fixture.complex);
            when(engine.getData()).thenReturn(fixture.data);
            when(engine.getDimension()).thenReturn(fixture.dimension);
            when(engine.getWorld()).thenReturn(IrisWorld.builder().minHeight(-64).maxHeight(128).build());
            when(fixture.complex.getSubterrainPlanner()).thenReturn(fixture.planner);
            IrisCarveModifier modifier = mock(IrisCarveModifier.class, CALLS_REAL_METHODS);
            doReturn(engine).when(modifier).getEngine();
            Field random = IrisCarveModifier.class.getDeclaredField("rng");
            random.setAccessible(true);
            random.set(modifier, fixture.random);
            for (Map.Entry<SubterrainSurfaceMaterials.Surface, SubterrainPosition> entry : fixture.surfaces().entrySet()) {
                SubterrainPosition position = entry.getValue();
                Hunk<NativeBlockState> output = Hunk.newArrayHunk(16, 192, 16);
                int x = position.x() & 15;
                int y = position.y() + 64;
                int z = position.z() & 15;
                output.setRaw(x, y, z, null);
                modifier.preserveSubterrain((position.x() >> 4) << 4, (position.z() >> 4) << 4, output);
                NativeBlockState expected = fixture.materials.state(position.x(), position.y(), position.z(), fixture.cell(position));
                assertSame(fixture.cell(position).room().reservedSolid() ? expected : null, output.getRaw(x, y, z));
                output.setRaw(x, y, z, fixture.fallback);
                modifier.preserveSubterrain((position.x() >> 4) << 4, (position.z() >> 4) << 4, output);
                assertSame(expected, output.getRaw(x, y, z));
                if (!fixture.cell(position).room().reservedSolid()) {
                    for (String existingKey : List.of("minecraft:cave_air", "minecraft:water", "minecraft:lava")) {
                        NativeBlockState opening = B.getState(existingKey);
                        output.setRaw(x, y, z, opening);
                        modifier.preserveSubterrain((position.x() >> 4) << 4, (position.z() >> 4) << 4, output);
                        assertSame(opening, output.getRaw(x, y, z));
                    }
                }
            }
        }
    }

    @Test
    public void floorDecoratorSubstratesSurviveBothPreservationPasses() throws Exception {
        try (Fixture fixture = new Fixture(IrisSubterrainFamily.CENOTE)) {
            Engine engine = mock(Engine.class);
            when(engine.getComplex()).thenReturn(fixture.complex);
            when(engine.getData()).thenReturn(fixture.data);
            when(engine.getDimension()).thenReturn(fixture.dimension);
            when(engine.getWorld()).thenReturn(IrisWorld.builder().minHeight(-64).maxHeight(128).build());
            when(fixture.complex.getSubterrainPlanner()).thenReturn(fixture.planner);
            IrisCarveModifier modifier = mock(IrisCarveModifier.class, CALLS_REAL_METHODS);
            doReturn(engine).when(modifier).getEngine();
            Field random = IrisCarveModifier.class.getDeclaredField("rng");
            random.setAccessible(true);
            random.set(modifier, fixture.random);
            Method restore = IrisCarveModifier.class.getDeclaredMethod("restoreSubterrainSolids", Hunk.class, int.class, int.class, boolean.class);
            restore.setAccessible(true);
            SubterrainPosition position = fixture.surfaces().get(SubterrainSurfaceMaterials.Surface.FLOOR);
            int chunkX = position.x() >> 4;
            int chunkZ = position.z() >> 4;
            int x = position.x() & 15;
            int y = position.y() + 64;
            int z = position.z() & 15;
            Hunk<NativeBlockState> output = Hunk.newArrayHunk(16, 192, 16);
            for (String key : List.of("minecraft:moss_block", "minecraft:crimson_nylium")) {
                NativeBlockState support = solid(key);
                IrisBlockData forceBlock = mock(IrisBlockData.class);
                when(forceBlock.getBlockData(fixture.data)).thenReturn(support);
                when(fixture.biome.getDecoratorBucket(IrisDecorationPart.NONE))
                        .thenReturn(new IrisDecorator[]{new IrisDecorator().setForceBlock(forceBlock)});
                output.setRaw(x, y, z, support);

                restore.invoke(modifier, output, chunkX, chunkZ, true);
                assertSame(support, output.getRaw(x, y, z));
                modifier.preserveSubterrain(chunkX << 4, chunkZ << 4, output);
                assertSame(support, output.getRaw(x, y, z));

                when(support.isOccluding()).thenReturn(false);
                modifier.preserveSubterrain(chunkX << 4, chunkZ << 4, output);
                assertSame(fixture.floor, output.getRaw(x, y, z));
            }
        }
    }

    @Test
    public void realMultiEntryPalettesRestoreDeterministicallyAcrossChunkVisitOrders() throws Exception {
        try (Fixture fixture = new Fixture(IrisSubterrainFamily.CENOTE)) {
            Map<SubterrainPosition, String> forward = fixture.restoredMaterials(false);
            Map<SubterrainPosition, String> reverse = fixture.restoredMaterials(true);
            Map<SubterrainPosition, String> repeated = fixture.restoredMaterials(false);
            assertEquals(forward, reverse);
            assertEquals(forward, repeated);
            assertTrue(forward.size() > 100);
            assertTrue(forward.values().stream().distinct().count() >= 6);
        }
    }

    @Test
    public void missingBiomeRetainsTheShellAndIsLookedUpOncePerResolver() {
        try (Fixture fixture = new Fixture(IrisSubterrainFamily.CENOTE)) {
            when(fixture.data.getBiomeLoader().load("room-biome")).thenReturn(null);
            for (SubterrainPosition position : fixture.surfaces().values()) {
                assertSame(fixture.fallback, fixture.materials.state(position.x(), position.y(), position.z(), fixture.cell(position)));
            }
            verify(fixture.data.getBiomeLoader(), times(1)).load("room-biome");
        }
    }

    @Test
    public void unsafeSurfaceChoicesFallBackToTheRetainedSolid() {
        try (Fixture fixture = new Fixture(IrisSubterrainFamily.CENOTE)) {
            SubterrainPosition position = fixture.surfaces().get(SubterrainSurfaceMaterials.Surface.FLOOR);
            NativeBlockState partial = solid("minecraft:oak_slab");
            when(partial.isOccluding()).thenReturn(false);
            NativeBlockState wet = solid("minecraft:stone[waterlogged=true]");
            when(wet.isWaterLogged()).thenReturn(true);
            NativeBlockState fluid = solid("minecraft:water");
            when(fluid.isFluid()).thenReturn(true);
            NativeBlockState custom = solid("minecraft:stone");
            when(custom.isCustom()).thenReturn(true);
            NativeBlockState gravity = solid("minecraft:gravel");
            for (NativeBlockState unsafe : List.of(partial, wet, fluid, custom, gravity)) {
                fixture.floorPalette(unsafe);
                assertSame(fixture.fallback, fixture.materials.state(position.x(), position.y(), position.z(), fixture.cell(position)));
            }
            fixture.floorPalette(null);
            assertSame(fixture.fallback, fixture.materials.state(position.x(), position.y(), position.z(), fixture.cell(position)));
        }
    }

    private static NativeBlockState solid(String key) {
        NativeBlockState state = mock(NativeBlockState.class);
        when(state.key()).thenReturn(key);
        when(state.isSolid()).thenReturn(true);
        when(state.isOccluding()).thenReturn(true);
        return state;
    }

    private static final class Fixture implements AutoCloseable {
        private final NativeBlockState fallback = solid("minecraft:stone");
        private final NativeBlockState floor = solid("minecraft:clay");
        private final NativeBlockState ceiling = solid("minecraft:calcite");
        private final NativeBlockState wall = solid("minecraft:andesite");
        private final IrisDimension dimension = new IrisDimension();
        private final IrisComplex complex = mock(IrisComplex.class);
        private final IrisData data = mock(IrisData.class);
        private final IrisBiome biome = mock(IrisBiome.class);
        private final RNG random = new RNG(44339L);
        private final SubterrainPlanner planner;
        private final SubterrainPlan plan;
        private final List<SubterrainPlan> plans;
        private final SubterrainSurfaceMaterials materials;
        private final MockedStatic<B> blocks = mockStatic(B.class);

        @SuppressWarnings("unchecked")
        private Fixture(IrisSubterrainFamily family) {
            IrisSubterrainFeature feature = new IrisSubterrainFeature().setId("room").setBiome("room-biome")
                    .setFamily(family).setProbability(1).setRadius(12).setHeight(16).setLength(200)
                    .setChimneyHeight(8).setWorldYRange(new IrisRange(-48, 64));
            planner = new SubterrainPlanner(new SubterrainPlanner.Options(List.of(feature), 83L, -64, 128, (x, z) -> "test-region"));
            plan = planner.plansForBounds(-512, -512, 512, 512).getFirst();
            plans = planner.plansForBounds(plan.bounds().minX() - 1, plan.bounds().minZ() - 1,
                    plan.bounds().maxX() + 1, plan.bounds().maxZ() + 1);
            ResourceLoader<IrisBiome> loader = mock(ResourceLoader.class);
            when(data.getBiomeLoader()).thenReturn(loader);
            when(loader.load("room-biome")).thenReturn(biome);
            when(biome.getDecoratorBucket(IrisDecorationPart.NONE)).thenReturn(new IrisDecorator[0]);
            IrisBiomePaletteLayer wallPalette = mock(IrisBiomePaletteLayer.class);
            when(biome.getWall()).thenReturn(wallPalette);
            when(wallPalette.get(eq(random), anyDouble(), anyDouble(), anyDouble(), eq(data))).thenReturn(wall);
            when(biome.generateCeilingLayers(eq(dimension), anyDouble(), anyDouble(), eq(random), eq(1), anyInt(), eq(data), eq(complex)))
                    .thenReturn(new KList<>(ceiling));
            floorPalette(floor);
            NativeBlockState air = mock(NativeBlockState.class);
            when(air.key()).thenReturn("minecraft:cave_air");
            NativeBlockState water = mock(NativeBlockState.class);
            when(water.key()).thenReturn("minecraft:water");
            NativeBlockState lava = mock(NativeBlockState.class);
            when(lava.key()).thenReturn("minecraft:lava");
            blocks.when(() -> B.getState(anyString())).thenAnswer(invocation -> switch ((String) invocation.getArgument(0)) {
                case "minecraft:water" -> water;
                case "minecraft:lava" -> lava;
                case "minecraft:cave_air" -> air;
                default -> fallback;
            });
            materials = materials(plans);
        }

        private Map<SubterrainPosition, String> restoredMaterials(boolean reverse) throws Exception {
            IrisBiome authored = new IrisBiome().setLayers(new KList<>(palette("minecraft:clay", "minecraft:terracotta")))
                    .setCaveCeilingLayers(new KList<>(palette("minecraft:calcite", "minecraft:white_concrete")))
                    .setWall(palette("minecraft:andesite", "minecraft:tuff"));
            when(data.getBiomeLoader().load("room-biome")).thenReturn(authored);
            Engine engine = mock(Engine.class);
            when(engine.getComplex()).thenReturn(complex);
            when(engine.getData()).thenReturn(data);
            when(engine.getDimension()).thenReturn(dimension);
            when(engine.getWorld()).thenReturn(IrisWorld.builder().minHeight(-64).maxHeight(128).build());
            when(complex.getSubterrainPlanner()).thenReturn(planner);
            IrisCarveModifier modifier = mock(IrisCarveModifier.class, CALLS_REAL_METHODS);
            doReturn(engine).when(modifier).getEngine();
            Field randomField = IrisCarveModifier.class.getDeclaredField("rng");
            randomField.setAccessible(true);
            randomField.set(modifier, random);
            List<SubterrainPosition> chunks = new ArrayList<>();
            SubterrainBounds bounds = plan.bounds();
            for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++) {
                for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) {
                    chunks.add(new SubterrainPosition(x, 0, z));
                }
            }
            if (reverse) {
                Collections.reverse(chunks);
            }
            Map<SubterrainPosition, String> result = new HashMap<>();
            for (SubterrainPosition chunk : chunks) {
                Hunk<NativeBlockState> output = Hunk.newArrayHunk(16, 192, 16);
                output.fill(fallback);
                for (int draw = 0; draw < 19; draw++) {
                    random.nextLong();
                }
                modifier.preserveSubterrain(chunk.x() << 4, chunk.z() << 4, output);
                modifier.preserveSubterrain(chunk.x() << 4, chunk.z() << 4, output);
                SubterrainRasterizer.rasterize(planner, chunk.x(), chunk.z(), (x, y, z, cell) -> {
                    if (cell.solid() && materials.surface(x, y, z, cell) != SubterrainSurfaceMaterials.Surface.INTERIOR) {
                        result.put(new SubterrainPosition(x, y, z), output.getRaw(x & 15, y + 64, z & 15).key());
                    }
                });
            }
            return result;
        }

        private IrisBiomePaletteLayer palette(String first, String second) {
            NativeBlockState firstState = solid(first);
            NativeBlockState secondState = solid(second);
            blocks.when(() -> B.getStateOrNull(first, false)).thenReturn(firstState);
            blocks.when(() -> B.getStateOrNull(second, false)).thenReturn(secondState);
            return new IrisBiomePaletteLayer().setZoom(1)
                    .setPalette(new KList<>(new IrisBlockData(first), new IrisBlockData(second)));
        }

        private void floorPalette(NativeBlockState state) {
            when(biome.generateLayers(eq(dimension), anyDouble(), anyDouble(), eq(random), eq(1), anyInt(), eq(data), eq(complex)))
                    .thenReturn(state == null ? new KList<>() : new KList<>(state));
        }

        private SubterrainSurfaceMaterials materials(List<SubterrainPlan> candidates) {
            return new SubterrainSurfaceMaterials(planner, candidates, dimension, complex, data, random, -64);
        }

        private SubterrainCell cell(SubterrainPosition position) {
            return planner.sample(plans, position.x(), position.y(), position.z());
        }

        private Map<SubterrainSurfaceMaterials.Surface, SubterrainPosition> surfaces() {
            Map<SubterrainSurfaceMaterials.Surface, SubterrainPosition> result = new EnumMap<>(SubterrainSurfaceMaterials.Surface.class);
            SubterrainBounds bounds = plan.bounds();
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                        SubterrainCell cell = planner.sample(plans, x, y, z);
                        if (!cell.solid()) {
                            continue;
                        }
                        result.putIfAbsent(materials.surface(x, y, z, cell), new SubterrainPosition(x, y, z));
                        if (result.size() == SubterrainSurfaceMaterials.Surface.values().length) {
                            return result;
                        }
                    }
                }
            }
            return result;
        }

        @Override
        public void close() {
            blocks.close();
        }
    }
}
