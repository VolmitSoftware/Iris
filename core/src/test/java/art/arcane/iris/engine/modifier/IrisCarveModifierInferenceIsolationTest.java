package art.arcane.iris.engine.modifier;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.InferredType;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisDimensionCarvingResolver;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.engine.object.IrisWorld;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.MatterCavern;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.junit.ClassRule;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class IrisCarveModifierInferenceIsolationTest {
    @ClassRule
    public static final PlatformBinding PLATFORM = PlatformBinding.mockPlatform();

    @Test
    @SuppressWarnings("unchecked")
    public void cavePaintingDoesNotMutateSharedBiomeInference() throws Exception {
        IrisBiome biome = new IrisBiome().setInferredType(InferredType.LAND);
        biome.getLayers().clear();
        biome.getCaveCeilingLayers().clear();

        Engine engine = mock(Engine.class);
        doReturn(mock(IrisData.class)).when(engine).getData();
        doReturn(mock(IrisDimension.class)).when(engine).getDimension();
        doReturn(mock(IrisComplex.class)).when(engine).getComplex();
        doReturn(IrisWorld.builder().minHeight(-256).maxHeight(512).build()).when(engine).getWorld();
        IrisCarveModifier modifier = mock(IrisCarveModifier.class, CALLS_REAL_METHODS);
        doReturn(engine).when(modifier).getEngine();

        Map<String, IrisBiome> customBiomes = new HashMap<>();
        customBiomes.put("shared", biome);
        CarveWallBuffer walls = new CarveWallBuffer(1);
        walls.put(0, 1, 0, new MatterCavern(true, "shared", (byte) 0));
        Method paintBoundaryZone = IrisCarveModifier.class.getDeclaredMethod(
                "paintBoundaryZone",
                Hunk.class,
                MantleChunk.class,
                CarveWallBuffer.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                IrisDimensionCarvingResolver.State.class,
                Long2ObjectOpenHashMap.class,
                Map.class
        );
        paintBoundaryZone.setAccessible(true);
        paintBoundaryZone.invoke(
                modifier,
                mock(Hunk.class),
                mock(MantleChunk.class),
                walls,
                0,
                0,
                0,
                0,
                1,
                1,
                new IrisDimensionCarvingResolver.State(),
                new Long2ObjectOpenHashMap<IrisBiome>(),
                customBiomes
        );

        IrisBiome fallback = new IrisBiome();
        ProceduralStream<IrisBiome> landStream = mock(ProceduralStream.class);
        doReturn(fallback).when(landStream).get(anyDouble(), anyDouble());
        IrisComplex complex = mock(IrisComplex.class, CALLS_REAL_METHODS);
        Field landBiomeStream = IrisComplex.class.getDeclaredField("landBiomeStream");
        landBiomeStream.setAccessible(true);
        landBiomeStream.set(complex, landStream);
        IrisRegion region = mock(IrisRegion.class);
        doReturn(0D).when(region).getShoreHeight(anyDouble(), anyDouble());
        Method fixBiomeType = IrisComplex.class.getDeclaredMethod(
                "fixBiomeType",
                Double.class,
                IrisBiome.class,
                IrisRegion.class,
                Double.class,
                Double.class,
                double.class
        );
        fixBiomeType.setAccessible(true);
        IrisBiome resolved = (IrisBiome) fixBiomeType.invoke(complex, 10D, biome, region, 0D, 0D, 0D);
        assertSame(biome, resolved);
        assertEquals(InferredType.LAND, biome.getInferredType());
    }
}
