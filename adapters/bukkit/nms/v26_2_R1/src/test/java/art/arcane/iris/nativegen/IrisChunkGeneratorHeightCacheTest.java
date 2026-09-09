package art.arcane.iris.nativegen;

import art.arcane.iris.core.nms.v26_2_R1.IrisChunkGenerator;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.GenerationSessionLease;
import art.arcane.iris.engine.history.BoundaryColumnGeometry;
import art.arcane.iris.engine.history.TerrainBoundarySignature;
import art.arcane.iris.testsupport.PlatformLeakGuard;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class IrisChunkGeneratorHeightCacheTest {
    @Rule
    public final PlatformLeakGuard leakGuard = PlatformLeakGuard.clean();

    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void repeatedNativeHeightSurvivesResolvedTerrainEvictionAndRuntimeChange() throws Exception {
        Fixture fixture = fixture();
        LevelHeightAccessor bounds = LevelHeightAccessor.create(-16, 32);
        assertEquals(-11, fixture.generator().getBaseHeight(-1, -33, Heightmap.Types.WORLD_SURFACE, bounds, null));
        when(fixture.complex().resolvedTerrainColumn(-1, -33))
                .thenThrow(new IllegalStateException("Full terrain was evicted"));

        for (int index = 0; index < 100; index++) {
            assertEquals(-11, fixture.generator().getBaseHeight(
                    -1, -33, Heightmap.Types.WORLD_SURFACE, bounds, null));
        }
        verify(fixture.complex(), times(1)).resolvedTerrainColumn(-1, -33);

        when(fixture.engine().getCacheID()).thenReturn(2);
        doReturn(Optional.of(signature())).when(fixture.complex()).resolvedTerrainColumn(-1, -33);
        assertEquals(-11, fixture.generator().getBaseHeight(-1, -33, Heightmap.Types.WORLD_SURFACE, bounds, null));
        verify(fixture.complex(), times(2)).resolvedTerrainColumn(-1, -33);
    }

    @Test
    public void ordinaryHeightRemainsLiveUntilResolvedTransitionTerrainExists() throws Exception {
        Fixture fixture = fixture();
        LevelHeightAccessor bounds = LevelHeightAccessor.create(-16, 32);
        when(fixture.complex().resolvedTerrainColumn(0, 0))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.of(signature()));
        when(fixture.engine().getHeight(0, 0, false)).thenReturn(20, 25);

        assertEquals(5, fixture.generator().getBaseHeight(0, 0, Heightmap.Types.WORLD_SURFACE, bounds, null));
        assertEquals(10, fixture.generator().getBaseHeight(0, 0, Heightmap.Types.WORLD_SURFACE, bounds, null));
        assertEquals(-11, fixture.generator().getBaseHeight(0, 0, Heightmap.Types.WORLD_SURFACE, bounds, null));
        assertEquals(-11, fixture.generator().getBaseHeight(0, 0, Heightmap.Types.WORLD_SURFACE, bounds, null));
        verify(fixture.engine(), times(2)).getHeight(0, 0, false);
        verify(fixture.complex(), times(3)).resolvedTerrainColumn(0, 0);
    }

    @Test
    public void nativePredicatesAndAccessorBoundsRemainExact() throws Exception {
        Fixture fixture = fixture();
        LevelHeightAccessor bounds = LevelHeightAccessor.create(-16, 32);
        for (int repeat = 0; repeat < 2; repeat++) {
            assertEquals(-11, fixture.generator().getBaseHeight(0, 0, Heightmap.Types.WORLD_SURFACE, bounds, null));
            assertEquals(-13, fixture.generator().getBaseHeight(0, 0, Heightmap.Types.OCEAN_FLOOR, bounds, null));
            assertEquals(-13, fixture.generator().getBaseHeight(0, 0, Heightmap.Types.MOTION_BLOCKING, bounds, null));
            assertEquals(-14, fixture.generator().getBaseHeight(0, 0, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bounds, null));
            assertEquals(-14, fixture.generator().getBaseHeight(0, 0, Heightmap.Types.WORLD_SURFACE,
                    LevelHeightAccessor.create(-16, 2), null));
            assertEquals(-14, fixture.generator().getBaseHeight(0, 0, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    LevelHeightAccessor.create(-14, 3), null));
        }
        verify(fixture.complex(), times(6)).resolvedTerrainColumn(0, 0);
    }

    private static Fixture fixture() throws Exception {
        Engine engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getCacheID()).thenReturn(1);
        when(engine.acquireGenerationLease(anyString())).thenReturn(GenerationSessionLease.noop());
        when(complex.resolvedTerrainColumn(anyInt(), anyInt())).thenReturn(Optional.of(signature()));
        IrisChunkGenerator generator = mock(IrisChunkGenerator.class, CALLS_REAL_METHODS);
        set(generator, "engine", engine);
        set(generator, "terrainHeights", new NativeTerrainHeightCache());
        return new Fixture(generator, engine, complex);
    }

    private static void set(IrisChunkGenerator generator, String name, Object value) throws Exception {
        Field field = IrisChunkGenerator.class.getDeclaredField(name);
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

    private record Fixture(IrisChunkGenerator generator, Engine engine, IrisComplex complex) {
    }
}
