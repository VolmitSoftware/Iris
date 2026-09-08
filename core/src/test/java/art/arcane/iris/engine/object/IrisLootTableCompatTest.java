package art.arcane.iris.engine.object;

import art.arcane.iris.core.compat.CompatAction;
import art.arcane.iris.core.compat.CompatFinding;
import art.arcane.iris.core.compat.CompatRegistry;
import art.arcane.iris.core.compat.CompatStatus;
import art.arcane.iris.core.compat.ContentGate;
import art.arcane.iris.core.compat.PackCompatReport;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static art.arcane.iris.engine.object.PackCompatFixtures.find;
import static art.arcane.iris.engine.object.PackCompatFixtures.lootTable;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisLootTableCompatTest {
    @SuppressWarnings("unchecked")
    private static IrisData dataWith(PackCompatReport report, IrisLootTable... tables) {
        IrisData data = PackCompatFixtures.data(report);
        ResourceLoader<IrisLootTable> lootLoader = mock(ResourceLoader.class);
        when(data.getLootLoader()).thenReturn(lootLoader);
        for (IrisLootTable table : tables) {
            when(lootLoader.load(table.getLoadKey())).thenReturn(table);
        }
        return data;
    }

    private static ContentGate gate(PackCompatReport report) {
        return new ContentGate(null, Map.of(), report);
    }

    @Test
    public void evaluateCompatExcludesTableWhoseEntriesWereAllDropped() {
        PackCompatReport report = new PackCompatReport();
        IrisLootTable table = lootTable("chest/sulfur-cache");
        table.setLoader(PackCompatFixtures.data(report));
        // The gate walker drops every entry with a missing item and hands the cascade its reasons.
        CompatStatus walked = new CompatStatus(false, List.of(new CompatFinding(CompatRegistry.ITEM,
                "minecraft:sulfur_dust", CompatAction.DROPPED, "loot", "chest/sulfur-cache", "loot[0]")));

        CompatStatus status = table.cascadeEmptyLoot(walked);

        assertTrue(status.excluded());
        CompatFinding cascade = find(report, CompatAction.EXCLUDED, "loot", "chest/sulfur-cache");
        assertNotNull(cascade);
        assertEquals("minecraft:sulfur_dust", cascade.key());
        assertEquals(CompatRegistry.ITEM, cascade.registry());
        assertEquals("no loot entries remain", cascade.detail());
    }

    @Test
    public void evaluateCompatKeepsTableWithSurvivingEntries() {
        PackCompatReport report = new PackCompatReport();
        IrisLootTable table = lootTable("chest/mixed");
        table.setLoader(PackCompatFixtures.data(report));
        table.setLoot(new KList<>(new IrisLoot()));
        CompatStatus walked = new CompatStatus(false, List.of(new CompatFinding(CompatRegistry.ITEM,
                "minecraft:sulfur_dust", CompatAction.DROPPED, "loot", "chest/mixed", "loot[1]")));

        assertFalse(table.cascadeEmptyLoot(walked).excluded());
    }

    @Test
    public void evaluateCompatKeepsTableThatNeverDeclaredLoot() {
        PackCompatReport report = new PackCompatReport();
        IrisLootTable table = lootTable("chest/empty");
        table.setLoader(PackCompatFixtures.data(report));

        assertFalse(table.evaluateCompat(gate(report)).excluded());
        assertTrue(report.isEmpty());
    }

    @Test
    public void lootReferenceSkipsExcludedAndUnloadableTables() {
        PackCompatReport report = new PackCompatReport();
        IrisLootTable kept = lootTable("chest/common");
        IrisLootTable excluded = PackCompatFixtures.exclude(lootTable("chest/sulfur-cache"),
                CompatRegistry.ITEM, "minecraft:sulfur_dust");
        IrisData data = dataWith(report, kept, excluded);
        IrisLootReference reference = new IrisLootReference()
                .setTables(new KList<>("chest/common", "chest/sulfur-cache", "chest/gone"));

        KList<IrisLootTable> tables = reference.getLootTables(() -> data);

        assertEquals(1, tables.size());
        assertEquals("chest/common", tables.get(0).getLoadKey());
        CompatFinding dropped = find(report, CompatAction.DROPPED, "loot reference", "chest/sulfur-cache");
        assertNotNull(dropped);
        assertEquals("minecraft:sulfur_dust", dropped.key());
    }
}
