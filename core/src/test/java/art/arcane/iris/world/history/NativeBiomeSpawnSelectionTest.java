package art.arcane.iris.world.history;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.block.DataProvider;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

public class NativeBiomeSpawnSelectionTest {
    @Test
    public void savedSelectionUsesTheActualPhysicalHolderAndOwningRuntime() {
        IrisEngine engine = mock(IrisEngine.class);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        SavedBiomeRuntime saved = mock(SavedBiomeRuntime.class);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(router.biomes()).thenReturn(saved);
        NativeBiomeSpawnSelection retained = new NativeBiomeSpawnSelection(
                NativeBiomeSpawnSelection.Mode.RETAINED, "minecraft:plains");
        when(saved.nativeSpawnSelection(-17, 62, 31, "iris:old-physical")).thenReturn(retained);

        assertEquals(retained, NativeBiomeSpawnSelection.at(engine, -17, 62, 31, "iris:old-physical"));
        verify(saved).nativeSpawnSelection(-17, 62, 31, "iris:old-physical");
    }

    @Test
    public void generationScopesUseCurrentPredictionWithoutReadingSavedBiomes() {
        IrisEngine engine = mock(IrisEngine.class);
        when(engine.hasGenerationRuntimeScope()).thenReturn(true);

        assertEquals(NativeBiomeSpawnSelection.Mode.CURRENT,
                NativeBiomeSpawnSelection.at(engine, 0, 64, 0, "iris:new-physical").mode());
        verify(engine, never()).getGenerationHistoryRuntimeRouter();
    }

    @Test
    public void loadingSkipsTheAttemptAndUnsupportedNeverUsesCurrentInheritance() {
        IrisEngine engine = mock(IrisEngine.class);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        SavedBiomeRuntime saved = mock(SavedBiomeRuntime.class);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(router.biomes()).thenReturn(saved);
        when(saved.nativeSpawnSelection(0, 64, 0, "iris:old"))
                .thenThrow(new SavedBiomeUnavailableException("loading", true))
                .thenThrow(new SavedBiomeUnavailableException("unsupported", false));

        assertEquals(NativeBiomeSpawnSelection.Mode.LOADING,
                NativeBiomeSpawnSelection.at(engine, 0, 64, 0, "iris:old").mode());
        assertEquals(NativeBiomeSpawnSelection.Mode.NONE,
                NativeBiomeSpawnSelection.at(engine, 0, 64, 0, "iris:old").mode());
    }

    @Test
    public void retainedPhysicalMappingsAreIndependentOfLaterParentDerivativeChanges() {
        IrisData previous = retainedData("iris:same-physical", "minecraft:plains");
        IrisData current = retainedData("iris:same-physical", "minecraft:desert");
        Map<String, String> oldMappings = NativeBiomeSpawnSelection.retainedDerivatives(previous);
        Map<String, String> newMappings = NativeBiomeSpawnSelection.retainedDerivatives(current);

        assertEquals("minecraft:plains", oldMappings.get("iris:same-physical"));
        assertEquals("minecraft:desert", newMappings.get("iris:same-physical"));
        assertEquals(null, oldMappings.get("iris:unrelated-physical"));
        assertTrue(oldMappings.keySet().contains("iris:same-physical"));
    }

    private static IrisData retainedData(String physicalKey, String vanillaDerivative) {
        IrisData data = mock(IrisData.class, RETURNS_DEEP_STUBS);
        IrisDimension dimension = mock(IrisDimension.class);
        IrisBiome biome = mock(IrisBiome.class);
        IrisBiomeCustom custom = new IrisBiomeCustom();
        custom.setId("custom");
        when(data.getDimensionLoader().getPossibleKeys()).thenReturn(new String[]{"main"});
        when(data.getDimensionLoader().load("main")).thenReturn(dimension);
        when(dimension.getReachableBiomes(any(DataProvider.class))).thenReturn(new KList<IrisBiome>().qadd(biome));
        when(biome.isCustom()).thenReturn(true);
        when(biome.getCustomDerivitives()).thenReturn(new KList<IrisBiomeCustom>().qadd(custom));
        when(biome.getVanillaDerivativeKey()).thenReturn(vanillaDerivative);
        when(data.customBiomeResourceKey(dimension, custom)).thenReturn(physicalKey);
        return data;
    }
}
