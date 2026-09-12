package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineMetrics;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.cave.IrisCaveProfile;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MantleCarvingComponentScratchTest {
    private MantleCarvingComponent component;
    private IrisDimension dimension;
    private ChunkContext context;
    private MantleWriter writer;

    @Before
    @SuppressWarnings("unchecked")
    public void prepare() {
        Engine engine = mock(Engine.class);
        EngineMantle mantle = mock(EngineMantle.class);
        when(mantle.getEngine()).thenReturn(engine);
        component = new MantleCarvingComponent(mantle);
        dimension = new IrisDimension();
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getWorld()).thenReturn(mock(IrisWorld.class));
        when(engine.getMetrics()).thenReturn(new EngineMetrics(16));
        IrisComplex complex = mock(IrisComplex.class);
        when(complex.allowsMantleChunkWrite(anyInt(), anyInt())).thenReturn(true);
        ProceduralStream<Double> heights = mock(ProceduralStream.class);
        when(heights.getDouble(anyDouble(), anyDouble())).thenAnswer(invocation -> {
            double x = invocation.getArgument(0);
            return x < 0D ? 90D : 70D;
        });
        when(complex.getNaturalHeightStream()).thenReturn(heights);
        ProceduralStream<IrisRegion> regions = mock(ProceduralStream.class);
        ProceduralStream<IrisBiome> biomes = mock(ProceduralStream.class);
        ProceduralStream<PlatformBlockState> fluids = mock(ProceduralStream.class);
        when(regions.get(anyDouble(), anyDouble())).thenReturn(new IrisRegion());
        when(biomes.get(anyDouble(), anyDouble())).thenReturn(new IrisBiome());
        when(complex.getRegionStream()).thenReturn(regions);
        when(complex.getNaturalTrueBiomeStream()).thenReturn(biomes);
        when(complex.getCaveBiomeStream()).thenReturn(biomes);
        when(complex.getFluidStream()).thenReturn(fluids);
        context = mock(ChunkContext.class);
        when(context.getComplex()).thenReturn(complex);
        writer = mock(MantleWriter.class);
        Mantle<Matter> storage = mock(Mantle.class);
        when(storage.getWorldHeight()).thenReturn(128);
        when(writer.getMantle()).thenReturn(storage);
    }

    @Test
    public void nestedGenerationPreservesOuterColumnHeights() throws Exception {
        IrisCaveCarver3D carver = installProfile();
        AtomicBoolean entered = new AtomicBoolean();
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            int chunkX = invocation.getArgument(1);
            int[] heights = invocation.getArgument(7);
            int expectedHeight = chunkX < 0 ? 90 : 70;
            assertEquals(expectedHeight, heights[0]);
            calls.incrementAndGet();
            if (entered.compareAndSet(false, true)) {
                component.generateLayer(writer, -3, -3, context);
            }
            for (int height : heights) {
                assertEquals(expectedHeight, height);
            }
            return 0;
        }).when(carver).carve(any(), anyInt(), anyInt(), any(), anyDouble(), anyDouble(),
                any(), any(), any(), any(), any());

        component.generateLayer(writer, 2, 2, context);

        assertEquals(2, calls.get());
        assertReleased();
    }

    @Test
    public void generationReleasesConfigurationReferencesWhenCarvingFails() throws Exception {
        IrisCaveCarver3D carver = installProfile();
        doAnswer(invocation -> {
            throw new IllegalStateException("Carving failed");
        }).when(carver).carve(any(), anyInt(), anyInt(), any(), anyDouble(), anyDouble(),
                any(), any(), any(), any(), any());
        assertThrows(IllegalStateException.class, () -> component.generateLayer(writer, 2, 2, context));
        assertReleased();
    }

    @Test
    public void changedProfilesReuseNumericBuffersWithoutKeepingProfileKeys() throws Exception {
        Object firstBuffer = null;
        for (int index = 0; index < 4; index++) {
            installProfile();
            component.generateLayer(writer, 2, 2, context);
            Object scratch = assertReleased();
            List<?> buffers = (List<?>) field(scratch, "weightBuffers");
            assertEquals(1, buffers.size());
            if (firstBuffer == null) {
                firstBuffer = buffers.get(0);
            } else {
                assertSame(firstBuffer, buffers.get(0));
            }
        }
    }

    private IrisCaveCarver3D installProfile() throws Exception {
        IrisCaveProfile profile = new IrisCaveProfile().setEnabled(true);
        dimension.setCaveProfile(profile);
        IrisCaveCarver3D carver = mock(IrisCaveCarver3D.class);
        Map<IrisCaveProfile, IrisCaveCarver3D> carvers = new IdentityHashMap<>();
        carvers.put(profile, carver);
        Field cache = MantleCarvingComponent.class.getDeclaredField("profileCarvers");
        cache.setAccessible(true);
        cache.set(component, carvers);
        return carver;
    }

    private Object assertReleased() throws Exception {
        Field scratchField = MantleCarvingComponent.class.getDeclaredField("BLEND_SCRATCH");
        scratchField.setAccessible(true);
        ThreadLocal<?> scratchCache = (ThreadLocal<?>) scratchField.get(null);
        Object scratch = scratchCache.get();
        for (String name : List.of("profileField", "kernelProfiles", "fieldRegions", "fieldSurfaceBiomes", "fieldCaveBiomes")) {
            for (Object value : (Object[]) field(scratch, name)) {
                assertNull(value);
            }
        }
        assertEquals(0, ((Map<?, ?>) field(scratch, "columnProfileWeights")).size());
        assertEquals(0, ((List<?>) field(scratch, "profileOrder")).size());
        assertFalse((boolean) field(scratch, "inUse"));
        return scratch;
    }

    private Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
}
