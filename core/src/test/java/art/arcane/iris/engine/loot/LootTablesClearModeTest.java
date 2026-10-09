package art.arcane.iris.engine.loot;

import art.arcane.iris.engine.object.loot.IrisLootMode;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LootTablesClearModeTest {
    @Test
    public void clearSuppressesItsOwnTablesAndReplaceDoesNot() {
        List<String> cleared = new ArrayList<>(List.of("placement", "dimension"));
        LootTables.injectSources(cleared, List.of("biome"), IrisLootMode.CLEAR, false);
        assertTrue("CLEAR must clear parents and contribute nothing", cleared.isEmpty());

        List<String> replaced = new ArrayList<>(List.of("placement", "dimension"));
        LootTables.injectSources(replaced, List.of("biome"), IrisLootMode.REPLACE, false);
        assertEquals(List.of("biome"), replaced);
    }

    @Test
    public void clearIgnoresTheFallbackFlag() {
        List<String> sources = new ArrayList<>(List.of("placement"));
        LootTables.injectSources(sources, List.of("biome"), IrisLootMode.CLEAR, true);
        assertTrue(sources.isEmpty());
    }
}
