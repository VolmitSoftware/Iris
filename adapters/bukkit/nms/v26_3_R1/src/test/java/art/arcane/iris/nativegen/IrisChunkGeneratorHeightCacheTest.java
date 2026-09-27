package art.arcane.iris.nativegen.v26_3_R1;

import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeChunkGenerator;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeTerrainColumns;
import art.arcane.iris.platform.bukkit.nms.BukkitTerrainColumnPolicy;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.GenerationSessionLease;
import art.arcane.iris.world.history.BoundaryColumnGeometry;
import art.arcane.iris.world.history.TerrainBoundarySignature;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.nativelib.terrain.NativeTerrainColumnPolicy;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

public final class IrisChunkGeneratorHeightCacheTest {
    @Rule
    public final PlatformLeakGuard leakGuard = PlatformLeakGuard.clean();

    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        NativeBlockTags.bindHeightmapFixtures();
    }

    @Test
    public void repeatedNativeHeightUsesLiveHeightQueriesAcrossRuntimeChanges() throws Exception {
        Fixture fixture = fixture();
        LevelHeightAccessor bounds = LevelHeightAccessor.create(-16, 32);
        when(fixture.engine().getHeight(-1, -33, false)).thenReturn(4);
        for (int index = 0; index < 100; index++) {
            assertEquals(-11, fixture.generator().getBaseHeight(
                    -1, -33, Heightmap.Types.WORLD_SURFACE, bounds, null));
        }
        verify(fixture.engine(), times(100)).getHeight(-1, -33, false);

        when(fixture.engine().getCacheID()).thenReturn(2);
        when(fixture.engine().getHeight(-1, -33, false)).thenReturn(7);
        assertEquals(-8, fixture.generator().getBaseHeight(-1, -33, Heightmap.Types.WORLD_SURFACE, bounds, null));
        verify(fixture.engine(), never()).getComplex();
        verify(fixture.engine(), never()).getMode();
    }

    @Test
    public void nativeHeightPredicatesSelectFluidOrSolidHeightWithoutGeneratingTerrain() throws Exception {
        Fixture fixture = fixture();
        LevelHeightAccessor bounds = LevelHeightAccessor.create(-16, 32);
        when(fixture.engine().getHeight(0, 0, false)).thenReturn(20, 25);
        when(fixture.engine().getHeight(0, 0, true)).thenReturn(8);

        assertEquals(5, fixture.generator().getBaseHeight(0, 0, Heightmap.Types.WORLD_SURFACE, bounds, null));
        assertEquals(10, fixture.generator().getBaseHeight(0, 0, Heightmap.Types.WORLD_SURFACE, bounds, null));
        assertEquals(-7, fixture.generator().getBaseHeight(0, 0, Heightmap.Types.OCEAN_FLOOR, bounds, null));
        verify(fixture.engine(), times(2)).getHeight(0, 0, false);
        verify(fixture.engine(), times(1)).getHeight(0, 0, true);
        verify(fixture.engine(), never()).getComplex();
        verify(fixture.engine(), never()).getMode();
    }

    @Test
    public void nativePredicatesAndAccessorBoundsRemainExact() throws Exception {
        NativeTerrainColumnPolicy policy = mock(NativeTerrainColumnPolicy.class);
        NativeTerrainColumnPolicy.ColumnSession session = mock(NativeTerrainColumnPolicy.ColumnSession.class);
        when(policy.openQuery(any())).thenReturn(session);
        when(policy.placementKey(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        doReturn(Optional.of(signature().geometry())).when(session).resolvedColumn();
        NativeChunkGenerator<?, ?, ?, ?, ?> generator = generator(policy);
        LevelHeightAccessor bounds = LevelHeightAccessor.create(-16, 32);
        for (int repeat = 0; repeat < 2; repeat++) {
            assertEquals(-11, generator.getBaseHeight(0, 0, Heightmap.Types.WORLD_SURFACE, bounds, null));
            assertEquals(-13, generator.getBaseHeight(0, 0, Heightmap.Types.OCEAN_FLOOR, bounds, null));
            assertEquals(-13, generator.getBaseHeight(0, 0, Heightmap.Types.MOTION_BLOCKING, bounds, null));
            assertEquals(-14, generator.getBaseHeight(0, 0, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bounds, null));
            assertEquals(-14, generator.getBaseHeight(0, 0, Heightmap.Types.WORLD_SURFACE,
                    LevelHeightAccessor.create(-16, 2), null));
            assertEquals(-14, generator.getBaseHeight(0, 0, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    LevelHeightAccessor.create(-14, 3), null));
        }
        verify(session, times(6)).resolvedColumn();
    }

    private static Fixture fixture() throws Exception {
        Engine engine = mock(Engine.class);
        when(engine.getCacheID()).thenReturn(1);
        when(engine.acquireGenerationLease(anyString())).thenReturn(GenerationSessionLease.noop());
        return new Fixture(generator(new BukkitTerrainColumnPolicy(engine, null)), engine);
    }

    private static NativeChunkGenerator<?, ?, ?, ?, ?> generator(NativeTerrainColumnPolicy policy) throws Exception {
        NativeChunkGenerator<?, ?, ?, ?, ?> generator = mock(NativeChunkGenerator.class, CALLS_REAL_METHODS);
        set(generator, "terrainColumns", new NativeTerrainColumns(policy));
        return generator;
    }

    private static void set(NativeChunkGenerator<?, ?, ?, ?, ?> generator, String name, Object value) throws Exception {
        Field field = NativeChunkGenerator.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(generator, value);
    }

    private static TerrainBoundarySignature signature() {
        BoundaryColumnGeometry geometry = BoundaryColumnGeometry.fromVoxels(-16, List.of(
                new BoundaryColumnGeometry.Voxel("minecraft:stone", BoundaryColumnGeometry.Phase.SOLID, "", false),
                new BoundaryColumnGeometry.Voxel("minecraft:water", BoundaryColumnGeometry.Phase.FLUID, "minecraft:water", false),
                new BoundaryColumnGeometry.Voxel("minecraft:oak_leaves[persistent=true]", BoundaryColumnGeometry.Phase.SOLID, "", false),
                new BoundaryColumnGeometry.Voxel("minecraft:short_grass", BoundaryColumnGeometry.Phase.SOLID, "", false),
                new BoundaryColumnGeometry.Voxel("minecraft:light[level=15]", BoundaryColumnGeometry.Phase.SOLID, "", false)));
        return new TerrainBoundarySignature(
                new TerrainBoundarySignature.Column(0, 0, 4, 4, OptionalInt.of(1), OptionalInt.empty()),
                new TerrainBoundarySignature.Samples(new TerrainBoundarySignature.VerticalLayout(-16, 5, 1),
                        new TerrainBoundarySignature.BiomeEncoding(List.of("minecraft:plains"), new short[]{0})), geometry);
    }

    private record Fixture(NativeChunkGenerator<?, ?, ?, ?, ?> generator, Engine engine) {
    }
}
