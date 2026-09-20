package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeBiomeRegistry;
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class ModdedBiomeWriterCacheTest {
    @Test
    public void unavailableServerFallsBackToBiomeIdZero() {
        ModdedBiomeWriter writer = new ModdedBiomeWriter(new NativeBiomeRegistry(() -> null, "minecraft:plains"));

        assertEquals(0, writer.biomeIdFor("minecraft:plains"));
        assertEquals(0, writer.biomeIdFor("minecraft:plains"));
    }

    @Test
    public void unavailableServerYieldsAnEmptyMutableBiomeList() {
        ModdedBiomeWriter writer = new ModdedBiomeWriter(new NativeBiomeRegistry(() -> null, "minecraft:plains"));

        List<NativeBiome> first = writer.allBiomes();
        List<NativeBiome> second = writer.allBiomes();

        assertTrue(first.isEmpty());
        assertNotSame("callers must never share the writer cache instance", first, second);
        first.add(null);
        assertTrue(second.isEmpty());
    }
}
