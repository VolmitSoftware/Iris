package art.arcane.iris.modded;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.BiomeEnvironment;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.world.history.SavedBiomeUnavailableException;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.world.IrisWorld;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import org.junit.Test;
import org.junit.BeforeClass;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.block.B;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModdedSavedBiomeConsumersTest {
    @BeforeClass
    public static void initializeRuntimeTypes() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        PlatformBlockState air = mock(PlatformBlockState.class);
        try (MockedStatic<B> blocks = mockStatic(B.class)) {
            blocks.when(() -> B.getState("AIR")).thenReturn(air);
            Class.forName(EngineMantle.class.getName());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void loadingDefinitionsLeaveInitialFlagUnclaimed() {
        Engine engine = mock(Engine.class);
        EngineMantle engineMantle = mock(EngineMantle.class);
        Mantle<Matter> mantle = mock(Mantle.class);
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        ServerLevel level = mock(ServerLevel.class);
        when(engine.getMantle()).thenReturn(engineMantle);
        when(engine.getWorld()).thenReturn(mock(IrisWorld.class));
        when(engineMantle.getMantle()).thenReturn(mantle);
        when(mantle.isChunkLoaded(2, -1)).thenReturn(true);
        when(mantle.getChunk(2, -1)).thenReturn(chunk);
        when(chunk.use()).thenReturn(chunk);
        BiomeEnvironment environment = environment();
        when(engine.getSurfaceBiomeEnvironment(40, -8))
                .thenThrow(new SavedBiomeUnavailableException("Loading", true))
                .thenReturn(environment);
        IrisSettings settings = new IrisSettings();
        settings.getWorld().setAmbientEntitySpawningSystem(true);
        settings.getWorld().setMarkerEntitySpawningSystem(false);
        ModdedWorldManager manager = new ModdedWorldManager(engine);

        try (MockedStatic<IrisSettings> configured = mockStatic(IrisSettings.class);
             MockedStatic<ModdedEntitySpawner> spawns = mockStatic(ModdedEntitySpawner.class)) {
            configured.when(IrisSettings::get).thenReturn(settings);
            spawns.when(() -> ModdedEntitySpawner.chunksSafe(level, 2, -1)).thenReturn(true);
            assertThrows(SavedBiomeUnavailableException.class, () -> manager.initialSpawnChunk(level, 2, -1));
            verify(chunk, never()).raiseFlagUnchecked(eq(ModdedWorldManager.INITIAL_SPAWN_COMPLETION_FLAG), any());
            assertTrue(manager.initialSpawnChunk(level, 2, -1));
            verify(chunk).raiseFlagUnchecked(eq(ModdedWorldManager.INITIAL_SPAWN_COMPLETION_FLAG), any());
        } finally {
            manager.close();
        }
    }

    @Test
    public void playerEffectsRetryLoadingWithoutMovement() throws Exception {
        Engine engine = mock(Engine.class);
        IrisWorld world = mock(IrisWorld.class);
        ServerLevel level = mock(ServerLevel.class);
        ServerPlayer player = mock(ServerPlayer.class);
        when(engine.getWorld()).thenReturn(world);
        when(world.minHeight()).thenReturn(-64);
        when(player.isAlive()).thenReturn(true);
        when(player.level()).thenReturn(level);
        BiomeEnvironment environment = environment();
        when(engine.getBiomeEnvironment(0, 64, 0))
                .thenThrow(new SavedBiomeUnavailableException("Loading", true))
                .thenReturn(environment);
        IrisSettings settings = new IrisSettings();
        settings.getWorld().setEffectSystem(false);
        Class<?> stateType = Class.forName(ModdedEngineEffects.class.getName() + "$PlayerState");
        Constructor<?> constructor = stateType.getDeclaredConstructor(ServerPlayer.class);
        constructor.setAccessible(true);
        Object state = constructor.newInstance(player);
        Method tick = ModdedEngineEffects.class.getDeclaredMethod("tickPlayer", ServerLevel.class, stateType);
        tick.setAccessible(true);
        Field selected = stateType.getDeclaredField("environment");
        selected.setAccessible(true);

        try (MockedStatic<IrisSettings> configured = mockStatic(IrisSettings.class)) {
            configured.when(IrisSettings::get).thenReturn(settings);
            ModdedEngineEffects effects = new ModdedEngineEffects(engine);
            tick.invoke(effects, level, state);
            assertNull(selected.get(state));
            tick.invoke(effects, level, state);
            assertSame(environment, selected.get(state));
            verify(engine, times(2)).getBiomeEnvironment(0, 64, 0);
        }
    }

    private static BiomeEnvironment environment() {
        return new BiomeEnvironment(3L, mock(IrisBiome.class), mock(IrisRegion.class),
                mock(IrisDimension.class), mock(IrisData.class));
    }
}
