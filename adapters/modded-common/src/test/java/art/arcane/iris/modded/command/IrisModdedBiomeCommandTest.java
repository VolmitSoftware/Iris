package art.arcane.iris.modded.command;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisModdedBiomeCommandTest {
    @Test
    public void biomeKeysAreSortedAndLimitedToReachableBiomes() {
        KList<IrisBiome> reachable = new KList<>();
        IrisBiome river = new IrisBiome();
        river.setLoadKey("river");
        IrisBiome natural = new IrisBiome();
        natural.setLoadKey("natural");
        reachable.add(river);
        reachable.add(natural);
        assertEquals(
                List.of("natural", "river"),
                List.copyOf(ModdedCommandSuggestions.reachableBiomeKeys(reachable))
        );
        assertTrue(ModdedCommandSuggestions.isReachableBiome(reachable, "river"));
        assertFalse(ModdedCommandSuggestions.isReachableBiome(reachable, "unused"));
    }
}
