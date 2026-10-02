package art.arcane.iris.modded;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EnginePlatformHooks;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedChunkGenerator;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModdedStructureBindingOrderTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void initialBindingPublishesReadyEngineBeforeStartingStructureSearches() throws Exception {
        NativeModdedChunkGenerator<?, ?, ?> nativeGenerator = mock(NativeModdedChunkGenerator.class);
        ModdedEngineBinding<Engine> binding = new ModdedEngineBinding<>(1L, TimeUnit.MILLISECONDS);
        IrisModdedChunkGenerator generator = generator(nativeGenerator, binding);
        NativeWorld world = mock(NativeWorld.class);
        NativeModdedServer server = mock(NativeModdedServer.class);
        Engine engine = readyEngine();
        when(nativeGenerator.represents(world)).thenReturn(true);
        when(server.generateStructures()).thenReturn(true);
        doAnswer(invocation -> {
            assertSame(engine, generator.awaitStructureEngine());
            assertSame(engine, binding.await("overworld"));
            return null;
        }).when(nativeGenerator).initializeStructureState(world);

        try (MockedStatic<NativeModdedServer> servers = mockStatic(NativeModdedServer.class);
             MockedStatic<ModdedWorldEngines> engines = mockStatic(ModdedWorldEngines.class);
             MockedStatic<ModdedIrisLog> log = mockStatic(ModdedIrisLog.class)) {
            servers.when(() -> NativeModdedServer.forWorld(world)).thenReturn(server);
            engines.when(() -> ModdedWorldEngines.get(eq(world), any(), any(), anyLong(), any())).thenReturn(engine);

            generator.bindLevel(world);

            verify(nativeGenerator).initializeStructureState(world);
        }
    }

    @Test
    public void levelBindingStartsDeferredStructuresAfterAnEarlierDataQueryBoundTheEngine() throws Exception {
        NativeModdedChunkGenerator<?, ?, ?> nativeGenerator = mock(NativeModdedChunkGenerator.class);
        ModdedEngineBinding<Engine> binding = new ModdedEngineBinding<>(1L, TimeUnit.MILLISECONDS);
        IrisModdedChunkGenerator generator = generator(nativeGenerator, binding);
        NativeWorld world = mock(NativeWorld.class);
        Engine engine = readyEngine();
        when(nativeGenerator.represents(world)).thenReturn(true);
        when(world.nativeHandle()).thenReturn(new Object());
        field("engine").set(generator, engine);
        field("boundLevel").set(generator, world);
        binding.complete(engine);
        doAnswer(invocation -> {
            assertSame(engine, generator.awaitStructureEngine());
            assertSame(engine, binding.await("overworld"));
            return null;
        }).when(nativeGenerator).initializeStructureState(world);

        generator.bindLevel(world);

        verify(nativeGenerator).initializeStructureState(world);
    }

    @Test
    public void repointPublishesReplacementBindingBeforeStartingStructureSearches() throws Exception {
        NativeModdedChunkGenerator<?, ?, ?> nativeGenerator = mock(NativeModdedChunkGenerator.class);
        ModdedEngineBinding<Engine> binding = new ModdedEngineBinding<>(1L, TimeUnit.MILLISECONDS);
        IrisModdedChunkGenerator generator = generator(nativeGenerator, binding);
        NativeWorld world = mock(NativeWorld.class);
        NativeModdedServer server = mock(NativeModdedServer.class);
        Engine replacement = readyEngine();
        when(nativeGenerator.represents(world)).thenReturn(true);
        when(server.generateStructures()).thenReturn(true);
        doAnswer(invocation -> {
            assertSame(replacement, generator.awaitStructureEngine());
            assertSame(replacement, binding.await("overworld"));
            return null;
        }).when(nativeGenerator).initializeStructureState(world);

        try (MockedStatic<NativeModdedServer> servers = mockStatic(NativeModdedServer.class);
             MockedStatic<ModdedWorldEngines> engines = mockStatic(ModdedWorldEngines.class)) {
            servers.when(() -> NativeModdedServer.forWorld(world)).thenReturn(server);
            engines.when(() -> ModdedWorldEngines.prepareReplacement(eq(world), eq("overworld"),
                    eq("overworld"), eq(42L), any())).thenReturn(replacement);

            generator.repointAndBind(world, "overworld", "overworld", 42L);

            verify(nativeGenerator).initializeStructureState(world);
        }
    }

    private static IrisModdedChunkGenerator generator(NativeModdedChunkGenerator<?, ?, ?> nativeGenerator,
                                                       ModdedEngineBinding<Engine> binding) throws Exception {
        IrisModdedChunkGenerator generator = mock(IrisModdedChunkGenerator.class, CALLS_REAL_METHODS);
        field("nativeGenerator").set(generator, nativeGenerator);
        field("engineBinding").set(generator, binding);
        field("dimensionKey").set(generator, "overworld");
        field("generationMode").set(generator, ModdedGenerationMode.PERSISTENT_RESTORE);
        field("announced").set(generator, new AtomicBoolean());
        field("importedFeatures").set(generator, mock(ModdedImportedFeatureStage.class));
        return generator;
    }

    private static Engine readyEngine() {
        Engine engine = mock(Engine.class);
        IrisData data = mock(IrisData.class);
        when(engine.getComplex()).thenReturn(mock(IrisComplex.class));
        when(engine.getData()).thenReturn(data);
        when(data.getDataFolder()).thenReturn(new File("build/structure-binding-test/pack"));
        when(engine.getPlatformHooks()).thenReturn(mock(EnginePlatformHooks.class));
        return engine;
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = IrisModdedChunkGenerator.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
