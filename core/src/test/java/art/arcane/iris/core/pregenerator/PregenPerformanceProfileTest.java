package art.arcane.iris.core.pregenerator;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.platform.PlatformChunkGenerator;
import art.arcane.iris.util.project.stream.utility.CachedDoubleStream2D;
import art.arcane.iris.util.project.stream.utility.CachedStream2D;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PregenPerformanceProfileTest {
    @Test
    public void liveGeneratorUsesItsPlatformHotloadBoundary() {
        IrisSettings previousSettings = IrisSettings.settings;
        String previousFastCache = System.getProperty("iris.cache.fast");
        IrisSettings.settings = new IrisSettings();
        System.clearProperty("iris.cache.fast");
        Engine engine = engineWithProfile(1_024, false);
        PlatformChunkGenerator generator = generatorFor(engine);
        try {
            PregenPerformanceProfile.applyToGenerator(generator);

            assertEquals(4_096, IrisSettings.get().getPerformance().getNoiseCacheSize());
            assertTrue(Boolean.getBoolean("iris.cache.fast"));
            verify(generator).hotloadComplexAsync(30L, TimeUnit.SECONDS);
            verify(engine, never()).hotloadComplex();
        } finally {
            restore(previousSettings, previousFastCache);
        }
    }

    @Test
    public void eachExistingEngineRefreshesAgainstTheAppliedProfile() {
        IrisSettings previousSettings = IrisSettings.settings;
        String previousFastCache = System.getProperty("iris.cache.fast");
        IrisSettings.settings = new IrisSettings();
        IrisSettings.get().getPerformance().setNoiseCacheSize(4_096);
        System.clearProperty("iris.cache.fast");
        Engine overworldEngine = engineWithProfile(4_096, false);
        Engine netherEngine = engineWithProfile(4_096, false);
        PlatformChunkGenerator overworldGenerator = generatorFor(overworldEngine);
        PlatformChunkGenerator netherGenerator = generatorFor(netherEngine);
        try {
            PregenPerformanceProfile.applyToGenerator(overworldGenerator);
            PregenPerformanceProfile.applyToGenerator(netherGenerator);

            verify(overworldGenerator).hotloadComplexAsync(30L, TimeUnit.SECONDS);
            verify(netherGenerator).hotloadComplexAsync(30L, TimeUnit.SECONDS);
        } finally {
            restore(previousSettings, previousFastCache);
        }
    }

    @Test
    public void profileAppliedBeforeWorldCreationAvoidsLiveHotload() {
        IrisSettings previousSettings = IrisSettings.settings;
        String previousFastCache = System.getProperty("iris.cache.fast");
        IrisSettings.settings = new IrisSettings();
        System.clearProperty("iris.cache.fast");
        Engine engine = engineWithProfile(4_096, true);
        PlatformChunkGenerator generator = mock(PlatformChunkGenerator.class);
        when(generator.getEngine()).thenReturn(engine);
        try {
            PregenPerformanceProfile.apply();
            PregenPerformanceProfile.applyToGenerator(generator);

            verify(generator, never()).hotloadComplexAsync(30L, TimeUnit.SECONDS);
            verify(engine, never()).hotloadComplex();
        } finally {
            restore(previousSettings, previousFastCache);
        }
    }

    @SuppressWarnings("unchecked")
    private static Engine engineWithProfile(int cacheSize, boolean fastCache) {
        Engine engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        CachedDoubleStream2D heightStream = mock(CachedDoubleStream2D.class);
        CachedStream2D<IrisBiome> caveBiomeStream = mock(CachedStream2D.class);
        when(heightStream.getMaxSize()).thenReturn((long) cacheSize * 256L);
        when(caveBiomeStream.usesFastCache()).thenReturn(fastCache);
        when(complex.getHeightStream()).thenReturn(heightStream);
        when(complex.getCaveBiomeStream()).thenReturn(caveBiomeStream);
        when(engine.getComplex()).thenReturn(complex);
        return engine;
    }

    private static PlatformChunkGenerator generatorFor(Engine engine) {
        PlatformChunkGenerator generator = mock(PlatformChunkGenerator.class);
        when(generator.getEngine()).thenReturn(engine);
        when(generator.hotloadComplexAsync(30L, TimeUnit.SECONDS))
                .thenReturn(CompletableFuture.completedFuture(null));
        return generator;
    }

    private static void restore(IrisSettings settings, String fastCache) {
        IrisSettings.settings = settings;
        if (fastCache == null) {
            System.clearProperty("iris.cache.fast");
        } else {
            System.setProperty("iris.cache.fast", fastCache);
        }
    }
}
