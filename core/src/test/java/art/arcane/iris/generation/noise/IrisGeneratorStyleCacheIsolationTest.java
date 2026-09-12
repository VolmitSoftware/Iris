package art.arcane.iris.generation.noise;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisGeneratorStyleCacheIsolationTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void cacheSeparatesSeedsAndEngineIdentity() throws Exception {
        Engine firstEngine = mock(Engine.class);
        Engine secondEngine = mock(Engine.class);
        AtomicReference<Engine> activeEngine = new AtomicReference<>(firstEngine);
        IrisData data = mock(IrisData.class);
        when(data.getEngine()).thenAnswer(ignored -> activeEngine.get());
        when(data.getDataFolder()).thenReturn(temporaryFolder.newFolder("pack"));
        IrisGeneratorStyle style = new IrisGeneratorStyle(NoiseStyle.SIMPLEX);

        CNG first = style.create(new RNG(41L), data);
        CNG firstAgain = style.create(new RNG(41L), data);
        CNG differentSeed = style.create(new RNG(42L), data);
        activeEngine.set(secondEngine);
        CNG differentEngine = style.create(new RNG(41L), data);

        assertSame(first, firstAgain);
        assertNotSame(first, differentSeed);
        assertNotSame(first, differentEngine);
        assertNotEquals(first.noise(12D, -7D), differentSeed.noise(12D, -7D), 0D);
    }

    @Test
    public void cacheEvictsOldEngineSeedEntries() {
        IrisData data = mock(IrisData.class);
        Engine engine = mock(Engine.class);
        when(data.getEngine()).thenReturn(engine);
        IrisGeneratorStyle style = new IrisGeneratorStyle(NoiseStyle.SIMPLEX);
        CNG first = style.create(new RNG(0L), data);

        for (long seed = 1L; seed <= 8L; seed++) {
            style.create(new RNG(seed), data);
        }

        assertNotSame(first, style.create(new RNG(0L), data));
    }
}
