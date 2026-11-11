package art.arcane.iris.engine.framework.structure;

import art.arcane.iris.builtin.structure.StructureAdjustment;
import art.arcane.iris.builtin.structure.StructureSetFrequency;
import art.arcane.iris.builtin.structure.StructureSettings;
import art.arcane.iris.builtin.structure.StructureStilts;
import art.arcane.iris.builtin.structure.StructureTerrain;
import art.arcane.iris.builtin.structure.StructureTerrainMode;
import art.arcane.iris.builtin.structure.StructureYBand;
import com.volmit.resource.ResourceId;
import com.google.gson.Gson;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** What a dimension's iris:structure settings say about the platform's own structures (was IrisImportedStructureControlTest). */
public class ImportedStructuresTest {
    private static final Gson JSON = new Gson();

    @Test
    public void defaultSettingsImportNoStructureAndChangeNothing() {
        ImportedStructures defaults = imported(new StructureSettings());

        assertTrue(defaults.disables("minecraft:village_plains"));
        assertTrue(defaults.disables("nova_structures:desert_temple"));
        assertEquals(NativeStructureFit.NONE, defaults.adjustment("minecraft:stronghold", true));
        assertEquals(1D, defaults.setup().frequency(ResourceId.parse("minecraft:villages")), 0D);
        assertFalse(defaults.setup().protectsVanillaDefinitions());
    }

    @Test
    public void enabledSettingsImportEveryStructureTheyDoNotDisable() {
        ImportedStructures enabled = imported(settings(List.of(), List.of(), 0, true, List.of(), List.of()));

        assertFalse(enabled.disables("minecraft:village_plains"));
        assertFalse(enabled.disables("nova_structures:desert_temple"));
    }

    @Test
    public void settingsThatOnlyKeepDatapacksOffDefinitionsImportNothing() {
        StructureSettings datapacksOnly = JSON.fromJson("{\"datapackOverrides\": false}", StructureSettings.class);

        assertTrue(imported(datapacksOnly).disables("minecraft:village_plains"));
    }

    @Test
    public void disabledMatchesFamiliesAtResourceBoundariesAndWholeNamespaces() {
        ImportedStructures imported = imported(settings(List.of("minecraft:village", "example:", "", " "), List.of(), 0, true,
                List.of(), List.of()));

        assertTrue(imported.disables("minecraft:village_plains"));
        assertTrue(imported.disables("minecraft:village_taiga"));
        assertTrue(imported.disables("example:castle"));
        assertFalse(imported.disables("minecraft:villager_outpost"));
        assertFalse(imported.disables("minecraft:stronghold"));
        assertFalse(imported.disables("other:castle"));
    }

    @Test
    public void disabledExactComparesNormalizedCompleteKeysOnly() {
        ImportedStructures imported = imported(settings(List.of(), List.of("  MINECRAFT:RUINED_PORTAL  "), 0, true,
                List.of(), List.of()));

        assertTrue(imported.disables("minecraft:ruined_portal"));
        assertTrue(imported.disables(" MINECRAFT:RUINED_PORTAL "));
        assertFalse(imported.disables("minecraft:ruined_portal_nether"));
        assertFalse(imported.disables(""));
    }

    @Test
    public void datapackOverridesFalseProtectsDefinitionsWithoutDisablingAnyNamespace() {
        ImportedStructures imported = imported(settings(List.of("aquaculture:treasure_vault"), List.of(), 0, false,
                List.of(), List.of()));

        assertTrue(imported.setup().protectsVanillaDefinitions());
        assertFalse(imported.disables("nova_structures:desert_temple"));
        assertFalse(imported.disables("minecraft:village_plains"));
        assertTrue(imported.disables("aquaculture:treasure_vault"));
    }

    @Test
    public void frequencyOverridesUseTheNormalizedSetKeyAndTheLastEntry() {
        ImportedStructures imported = imported(settings(List.of(), List.of(), 0, true, List.of(
                new StructureSetFrequency("minecraft:nether_complexes", 1.05D),
                new StructureSetFrequency(" MINECRAFT:NETHER_COMPLEXES ", 1.1D),
                new StructureSetFrequency("no namespace", 3D)), List.of()));

        assertEquals(1.1D, imported.setup().frequency(ResourceId.parse("minecraft:nether_complexes")), 0D);
        assertEquals(1D, imported.setup().frequency(ResourceId.parse("minecraft:nether_fossils")), 0D);
        assertEquals(1, imported.setup().frequencies().size());
    }

    @Test
    public void undergroundShiftAppliesOnlyToUndergroundStepsAndMatchingShiftsStack() {
        ImportedStructures imported = imported(settings(List.of(), List.of(), 8, true, List.of(), List.of(
                adjustment(List.of("minecraft:trial"), -48, null, false, null, null),
                adjustment(List.of("minecraft:trial_chambers"), -16, null, false, null, null))));

        assertEquals(-56, imported.adjustment("minecraft:trial_chambers", true).yShift());
        assertEquals(8, imported.adjustment("minecraft:stronghold", true).yShift());
        assertEquals(0, imported.adjustment("minecraft:village_plains", false).yShift());
    }

    @Test
    public void theLastMatchingTerrainBandAndStiltWin() {
        StructureTerrain encase = terrain("{\"mode\":\"ENCASE\",\"horizontalPadding\":4}");
        StructureStilts deep = stilts(96);
        StructureYBand band = JSON.fromJson("{\"min\":-120,\"max\":-20}", StructureYBand.class);
        ImportedStructures imported = imported(settings(List.of(), List.of(), 0, true, List.of(), List.of(
                adjustment(List.of("minecraft:stronghold"), 0, null, false, stilts(32), terrain("{\"mode\":\"SOURCE\"}")),
                adjustment(List.of("minecraft:stronghold"), 0, band, false, null, encase),
                adjustment(List.of("minecraft:stronghold"), 0, null, false, deep, null))));

        NativeStructureFit stronghold = imported.adjustment("minecraft:stronghold", true);
        assertSame(encase, stronghold.terrain());
        assertSame(deep, stronghold.stilts());
        assertSame(band, stronghold.yBand());
        assertEquals(NativeStructureFit.NONE, imported.adjustment("minecraft:trial_chambers", true));
    }

    @Test
    public void aLaterSpecificAdjustmentOverridesANamespaceTerrain() {
        ImportedStructures imported = imported(settings(List.of(), List.of(), 0, true, List.of(), List.of(
                adjustment(List.of("towns_and_towers:"), 0, null, false, null, terrain("{\"mode\":\"VACUUM\"}")),
                adjustment(List.of("towns_and_towers:village_ocean", "towns_and_towers:mimic_desert"), 0, null, false, null,
                        terrain("{\"mode\":\"PRESERVE\"}")))));

        assertEquals(StructureTerrainMode.VACUUM, mode(imported, "towns_and_towers:village_forest"));
        assertEquals(StructureTerrainMode.PRESERVE, mode(imported, "towns_and_towers:village_ocean"));
        assertEquals(StructureTerrainMode.PRESERVE, mode(imported, "towns_and_towers:mimic_desert"));
    }

    @Test
    public void preserveSourceYMatchesTheFamilyAndKeepsTheShifts() {
        ImportedStructures imported = imported(settings(List.of(), List.of(), -64, true, List.of(), List.of(
                adjustment(List.of("minecraft:mineshaft"), 4, null, true, null, null))));

        NativeStructureFit mesa = imported.adjustment("minecraft:mineshaft_mesa", true);
        assertTrue(mesa.keepsSourceY());
        assertEquals(-60, mesa.yShift());
        assertFalse(imported.adjustment("minecraft:stronghold", true).keepsSourceY());
    }

    @Test
    public void nullListsInThePacksJsonReadAsEmpty() {
        StructureSettings nulls = new Gson().fromJson(
                "{\"enabled\": true, \"disabled\": null, \"disabledExact\": null, \"adjustments\": null, \"frequencyOverrides\": null}",
                StructureSettings.class);
        ImportedStructures imported = imported(nulls);

        assertFalse(imported.disables("minecraft:monument"));
        assertEquals(NativeStructureFit.NONE, imported.adjustment("minecraft:monument", false));
        assertTrue(imported.setup().frequencies().isEmpty());
    }

    private static StructureTerrainMode mode(ImportedStructures imported, String key) {
        return imported.adjustment(key, false).terrain().resolvedMode();
    }

    private static StructureTerrain terrain(String json) {
        return JSON.fromJson(json, StructureTerrain.class);
    }

    private static StructureStilts stilts(int maxDepth) {
        return JSON.fromJson("{\"maxDepth\":" + maxDepth + "}", StructureStilts.class);
    }

    private static ImportedStructures imported(StructureSettings settings) {
        return new ImportedStructures(settings);
    }

    private static StructureSettings settings(List<String> disabled, List<String> disabledExact, int undergroundYShift,
                                              boolean datapackOverrides, List<StructureSetFrequency> frequencies,
                                              List<StructureAdjustment> adjustments) {
        return new StructureSettings(true, disabled, disabledExact, undergroundYShift, datapackOverrides, frequencies, adjustments);
    }

    private static StructureAdjustment adjustment(List<String> match, int yShift, StructureYBand band, boolean preserveSourceY,
                                                  StructureStilts stilt, StructureTerrain terrain) {
        return new StructureAdjustment(match, yShift, band, preserveSourceY, stilt, terrain);
    }
}
