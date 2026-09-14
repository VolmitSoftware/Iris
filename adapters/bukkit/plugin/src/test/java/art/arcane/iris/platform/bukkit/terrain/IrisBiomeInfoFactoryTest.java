package art.arcane.iris.platform.bukkit.terrain;

import art.arcane.iris.api.terrain.IrisBiomeInfo;
import art.arcane.iris.api.terrain.IrisCustomBiomeInfo;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.generation.runtime.BiomeEnvironment;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class IrisBiomeInfoFactoryTest {
    @Test
    public void historicalMetadataUsesTheRetainedEnvironmentInsteadOfTheActivePack() {
        IrisBiomeCustom custom = new IrisBiomeCustom().setId("amber-forest");
        Fixture fixture = fixture(List.of(custom));
        IrisData activeData = mock(IrisData.class);
        when(fixture.engine().getData()).thenReturn(activeData);
        when(fixture.data().customBiomeResourceKey(fixture.dimension(), custom)).thenReturn("iris:biomes/retained");
        List<Throwable> failures = new ArrayList<>();

        IrisBiomeInfo info = IrisBiomeInfoFactory.surface(fixture.engine(), -17, 32, failures::add);

        assertEquals("forests/amber", info.key());
        assertEquals("Amber Forest", info.name());
        assertEquals("temperate", info.regionKey());
        assertEquals("Temperate Region", info.regionName());
        assertEquals("minecraft:forest", info.derivativeKey());
        assertEquals("minecraft:birch_forest", info.vanillaDerivativeKey());
        assertEquals("land", info.type());
        assertEquals(List.of(new IrisCustomBiomeInfo("amber-forest", "iris:biomes/retained")), info.customDerivatives());
        assertTrue(failures.isEmpty());
        verify(fixture.engine()).getSurfaceBiomeEnvironment(-17, 32);
        verifyNoMoreInteractions(fixture.engine());
        verify(fixture.data()).customBiomeResourceKey(fixture.dimension(), custom);
        verifyNoInteractions(activeData);
    }

    @Test
    public void allCustomDerivativesRemainInAuthoredOrderWithoutSelectingAVariant() {
        IrisBiomeCustom first = new IrisBiomeCustom().setId("amber");
        IrisBiomeCustom second = new IrisBiomeCustom().setId("copper");
        Fixture fixture = fixture(List.of(first, second));
        when(fixture.data().customBiomeResourceKey(fixture.dimension(), first)).thenReturn("iris:biomes/first");
        when(fixture.data().customBiomeResourceKey(fixture.dimension(), second)).thenReturn("iris:biomes/second");

        IrisBiomeInfo info = IrisBiomeInfoFactory.surface(fixture.engine(), -17, 32, failure -> {
            throw new AssertionError(failure);
        });

        assertEquals(List.of(
                new IrisCustomBiomeInfo("amber", "iris:biomes/first"),
                new IrisCustomBiomeInfo("copper", "iris:biomes/second")), info.customDerivatives());
        verify(fixture.engine()).getSurfaceBiomeEnvironment(-17, 32);
        verifyNoMoreInteractions(fixture.engine());
    }

    @Test
    public void aVanillaBiomeDoesNotResolveAnyCustomRegistryKeys() {
        Fixture fixture = fixture(List.of());

        IrisBiomeInfo info = IrisBiomeInfoFactory.surface(fixture.engine(), -17, 32, failure -> {
            throw new AssertionError(failure);
        });

        assertEquals("Amber Forest", info.name());
        assertTrue(info.customDerivatives().isEmpty());
        verifyNoInteractions(fixture.data());
    }

    @Test
    public void aMissingMappingPreservesTheAuthoredIdAndOtherMetadata() {
        IrisBiomeCustom first = new IrisBiomeCustom().setId("amber");
        IrisBiomeCustom second = new IrisBiomeCustom().setId("copper");
        Fixture fixture = fixture(List.of(first, second));
        IllegalStateException missing = new IllegalStateException("Missing historical registry mapping");
        when(fixture.data().customBiomeResourceKey(fixture.dimension(), first)).thenThrow(missing);
        when(fixture.data().customBiomeResourceKey(fixture.dimension(), second)).thenReturn("iris:biomes/second");
        List<Throwable> failures = new ArrayList<>();

        IrisBiomeInfo info = IrisBiomeInfoFactory.surface(fixture.engine(), -17, 32, failures::add);

        assertEquals("Amber Forest", info.name());
        assertEquals(List.of(
                new IrisCustomBiomeInfo("amber", ""),
                new IrisCustomBiomeInfo("copper", "iris:biomes/second")), info.customDerivatives());
        assertEquals(1, failures.size());
        assertSame(missing, failures.getFirst().getCause());
        assertTrue(failures.getFirst().getMessage().contains("amber"));
    }

    @Test
    public void anUnclassifiedBiomeReportsAnEmptyType() {
        Fixture fixture = fixture(List.of());
        when(fixture.biome().getInferredType()).thenReturn(null);

        IrisBiomeInfo info = IrisBiomeInfoFactory.surface(fixture.engine(), -17, 32, failure -> {
            throw new AssertionError(failure);
        });

        assertEquals("", info.type());
    }

    private static Fixture fixture(List<IrisBiomeCustom> customBiomes) {
        Engine engine = mock(Engine.class);
        IrisBiome biome = mock(IrisBiome.class);
        IrisRegion region = mock(IrisRegion.class);
        IrisDimension dimension = mock(IrisDimension.class);
        IrisData data = mock(IrisData.class);
        KList<IrisBiomeCustom> derivatives = new KList<>();
        derivatives.addAll(customBiomes);
        when(biome.getLoadKey()).thenReturn("forests/amber");
        when(biome.getName()).thenReturn("Amber Forest");
        when(biome.getDerivativeKey()).thenReturn("minecraft:forest");
        when(biome.getVanillaDerivativeKey()).thenReturn("minecraft:birch_forest");
        when(biome.getInferredType()).thenReturn(InferredType.LAND);
        when(biome.getCustomDerivitives()).thenReturn(derivatives);
        when(region.getLoadKey()).thenReturn("temperate");
        when(region.getName()).thenReturn("Temperate Region");
        when(engine.getSurfaceBiomeEnvironment(-17, 32)).thenReturn(
                new BiomeEnvironment(4L, biome, region, dimension, data));
        return new Fixture(engine, biome, dimension, data);
    }

    private record Fixture(Engine engine, IrisBiome biome, IrisDimension dimension, IrisData data) {
    }
}
