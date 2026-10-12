package art.arcane.iris.engine.modifier;

import art.arcane.iris.core.modules.ModuleSettings;
import art.arcane.iris.builtin.terrain.LayersSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.BuiltInPack;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.decorator.DecoratorTestEngines;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.object.world.biome.InferredType;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.engine.object.world.IrisWorld;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.volmlib.util.matter.MatterCavern;
import com.google.gson.Gson;
import com.volmit.host.ModuleLogs;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class CaveApplyingInferenceIsolationTest {
    @BeforeClass
    public static void bindPlatform() {
        IrisPlatforms.unbind();
        PlatformBlockState block = mock(PlatformBlockState.class);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        doReturn(block).when(registries).block(anyString());
        IrisPlatform platform = mock(IrisPlatform.class);
        doReturn(registries).when(platform).registries();
        IrisPlatforms.bind(platform);
    }

    @AfterClass
    public static void unbindPlatform() {
        IrisPlatforms.unbind();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void cavePaintingDoesNotMutateSharedBiomeInference() throws Exception {
        IrisBiome biome = new IrisBiome().setInferredType(InferredType.LAND);
        biome.setModuleSettings(BuiltInPack.settings(new Gson().fromJson("{\"layers\": []}", LayersSettings.class)));

        Engine engine = mock(Engine.class);
        doReturn(mock(IrisData.class)).when(engine).getData();
        IrisDimension dimension = mock(IrisDimension.class);
        doReturn(ModuleSettings.NONE).when(dimension).getModuleSettings();
        doReturn(dimension).when(engine).getDimension();
        doReturn(mock(IrisComplex.class)).when(engine).getComplex();
        doReturn(IrisWorld.builder().minHeight(-256).maxHeight(512).build()).when(engine).getWorld();
        doReturn(mock(SeedManager.class)).when(engine).getSeedManager();
        DecoratorTestEngines.withGeneration(engine);
        CaveApplying modifier = new CaveApplying(engine, ModuleLogs.discarding());

        Map<String, IrisBiome> customBiomes = new HashMap<>();
        customBiomes.put("shared", biome);
        CarveWallBuffer walls = new CarveWallBuffer(1);
        walls.put(0, 1, 0, new MatterCavern(true, "shared", (byte) 0));
        Method paintBoundaryZone = CaveApplying.class.getDeclaredMethod(
                "paintBoundaryZone",
                Hunk.class,
                CarveWallBuffer.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                Long2ObjectOpenHashMap.class,
                Map.class
        );
        paintBoundaryZone.setAccessible(true);
        paintBoundaryZone.invoke(
                modifier,
                mock(Hunk.class),
                walls,
                0,
                0,
                0,
                0,
                1,
                1,
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
