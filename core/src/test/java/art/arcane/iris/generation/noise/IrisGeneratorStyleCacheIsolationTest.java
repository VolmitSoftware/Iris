package art.arcane.iris.generation.noise;

import art.arcane.iris.generation.runtime.PackRuntime;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisGeneratorStyleCacheIsolationTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void persistedCacheSignaturesUseStableStyleNames() {
        assertEquals(-492944473, new IrisGeneratorStyle(NoiseStyle.SIMPLEX).prebakeSignature());
        assertEquals(992324154, new IrisGeneratorStyle(NoiseStyle.FLAT).prebakeSignature());
        assertEquals(-848988881, new IrisGeneratorStyle(NoiseStyle.STATIC).prebakeSignature());
        assertEquals(990163649, new IrisGeneratorStyle().setStyle(null).prebakeSignature());
        assertEquals(1067243926, new IrisGeneratorStyle(NoiseStyle.SIMPLEX)
                .setFracture(new IrisGeneratorStyle(NoiseStyle.STATIC)).prebakeSignature());
    }

    @Test
    public void runtimeSeparatesSeedsAndOwners() throws Exception {
        IrisData data = mock(IrisData.class);
        when(data.getDataFolder()).thenReturn(temporaryFolder.newFolder("pack"));
        PackRuntime owner = PackRuntime.standalone(data);
        PackRuntime otherOwner = PackRuntime.standalone(data);
        IrisGeneratorStyle style = new IrisGeneratorStyle(NoiseStyle.SIMPLEX);

        CNG first = style.create(new RNG(41L), owner);
        CNG firstAgain = style.create(new RNG(41L), owner);
        CNG differentSeed = style.create(new RNG(42L), owner);
        CNG differentOwner = style.create(new RNG(41L), otherOwner);

        assertSame(first, firstAgain);
        assertNotSame(first, differentSeed);
        assertNotSame(first, differentOwner);
        assertNotEquals(first.noise(12D, -7D), differentSeed.noise(12D, -7D), 0D);
    }

    @Test
    public void runtimeEvictsOldSeedEntries() {
        PackRuntime owner = PackRuntime.standalone(mock(IrisData.class));
        IrisGeneratorStyle style = new IrisGeneratorStyle(NoiseStyle.SIMPLEX);
        CNG first = style.create(new RNG(0L), owner);

        for (long seed = 1L; seed <= 8L; seed++) {
            style.create(new RNG(seed), owner);
        }

        assertNotSame(first, style.create(new RNG(0L), owner));
    }
}
