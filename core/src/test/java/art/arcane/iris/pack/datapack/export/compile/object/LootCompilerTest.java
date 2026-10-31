package art.arcane.iris.pack.datapack.export.compile.object;

import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.world.loot.IrisLoot;
import art.arcane.iris.world.loot.IrisLootTable;
import art.arcane.volmlib.util.collection.KList;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LootCompilerTest {
    @Test
    public void rarityBecomesProportionalWeight() {
        IrisLootTable table = table();
        List<ExportDiagnostic> diagnostics = new ArrayList<>();

        JsonObject json = new LootCompiler("iris_export", null).table(table, diagnostics);

        JsonObject pool = json.getAsJsonArray("pools").get(0).getAsJsonObject();
        JsonArray entries = pool.getAsJsonArray("entries");
        assertEquals(3, entries.size());
        assertEquals("minecraft:diamond", entries.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals(4, entries.get(0).getAsJsonObject().get("weight").getAsInt());
        assertEquals(2, entries.get(1).getAsJsonObject().get("weight").getAsInt());
        assertEquals(1, entries.get(2).getAsJsonObject().get("weight").getAsInt());
        assertEquals("minecraft:set_count", entries.get(1).getAsJsonObject().getAsJsonArray("modifier").get(0).getAsJsonObject().get("type").getAsString());
        JsonObject rolls = pool.getAsJsonObject("rolls");
        assertEquals(2, rolls.get("min").getAsInt());
        assertEquals(5, rolls.get("max").getAsInt());
        assertEquals(1.0 / 3, pool.getAsJsonObject("condition").get("chance").getAsDouble(), 1e-6);
        assertEquals("minecraft:chest", json.get("type").getAsString());
        assertTrue(diagnostics.stream().anyMatch(diagnostic -> diagnostic.limit() == ExportLimit.CUSTOM_ITEM));
        assertTrue(diagnostics.stream().anyMatch(diagnostic -> diagnostic.limit() == ExportLimit.LOOT_RARITY));
    }

    private static IrisLootTable table() {
        IrisLootTable table = new IrisLootTable();
        table.setLoadKey("treasure");
        table.setName("Treasure");
        table.setRarity(3);
        table.setMinPicked(2);
        table.setMaxPicked(5);
        KList<IrisLoot> loot = new KList<>();
        loot.add(new IrisLoot().setType("DIAMOND").setRarity(1));
        loot.add(new IrisLoot().setType("minecraft:gold_ingot").setRarity(2).setMinAmount(1).setMaxAmount(3));
        loot.add(new IrisLoot().setType("emerald").setRarity(4));
        loot.add(new IrisLoot().setType("mymod:relic").setRarity(1));
        table.setLoot(loot);
        return table;
    }
}
