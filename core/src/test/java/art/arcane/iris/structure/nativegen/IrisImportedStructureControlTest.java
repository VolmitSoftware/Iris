/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.structure.nativegen;

import art.arcane.iris.structure.placement.IrisStructureSetFrequencyOverride;
import art.arcane.iris.structure.placement.IrisStructureStiltSettings;
import art.arcane.iris.structure.placement.IrisStructureTerrain;
import art.arcane.iris.structure.placement.IrisStructureTerrainMode;
import art.arcane.iris.structure.placement.IrisStructureYBand;

import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IrisImportedStructureControlTest {
    private static KList<String> keys(String... values) {
        KList<String> list = new KList<>();
        for (String value : values) {
            list.add(value);
        }
        return list;
    }

    @Test
    public void defaultPolicyGeneratesEveryRegisteredStructureKey() {
        IrisImportedStructureControl control = new IrisImportedStructureControl();
        assertTrue(control.shouldGenerate("minecraft:village_plains"));
        assertTrue(control.shouldGenerate("minecraft:stronghold"));
        assertTrue(control.shouldGenerate("minecraft:monument"));
        assertEquals(NativeStructureGenerationStatus.GENERATE_NATIVE,
                control.resolve("minecraft:monument", false).status());
        assertEquals(1D, control.frequencyMultiplier("minecraft:villages"), 0D);
    }

    @Test
    public void frequencyOverrideUsesExactNormalizedSetKeyAndLastEntry() {
        KList<IrisStructureSetFrequencyOverride> overrides = new KList<>();
        overrides.add(new IrisStructureSetFrequencyOverride()
                .setStructureSet("minecraft:nether_complexes")
                .setMultiplier(1.05D));
        overrides.add(new IrisStructureSetFrequencyOverride()
                .setStructureSet(" MINECRAFT:NETHER_COMPLEXES ")
                .setMultiplier(1.1D));
        IrisImportedStructureControl control = new IrisImportedStructureControl()
                .setFrequencyOverrides(overrides);

        assertTrue(control.hasFrequencyOverrides());
        assertEquals(1.1D, control.frequencyMultiplier("minecraft:nether_complexes"), 0D);
        assertEquals(1D, control.frequencyMultiplier("minecraft:nether_fossils"), 0D);
        assertEquals(1D, control.frequencyMultiplier("minecraft:nether_complexes_extra"), 0D);
    }

    @Test
    public void defaultPolicyGeneratesDatapackStructures() {
        IrisImportedStructureControl control = new IrisImportedStructureControl();
        assertTrue(control.isDatapackOverrides());
        assertTrue(control.shouldGenerate("nova_structures:desert_temple"));
    }

    @Test
    public void disabledBlacklistMatchesExactKey() {
        IrisImportedStructureControl control = new IrisImportedStructureControl()
                .setDisabled(keys("minecraft:stronghold"));
        assertFalse(control.shouldGenerate("minecraft:stronghold"));
        assertTrue(control.shouldGenerate("minecraft:village_plains"));
    }

    @Test
    public void disabledBlacklistMatchesNamespacePrefix() {
        IrisImportedStructureControl control = new IrisImportedStructureControl()
                .setDisabled(keys("minecraft:village"));
        assertFalse(control.shouldGenerate("minecraft:village_plains"));
        assertFalse(control.shouldGenerate("minecraft:village_taiga"));
        assertFalse(control.shouldGenerate("minecraft:village_snowy"));
        assertTrue(control.shouldGenerate("minecraft:stronghold"));
    }

    @Test
    public void exactBlacklistDoesNotExpandToStructureFamilies() {
        IrisImportedStructureControl control = new IrisImportedStructureControl()
                .setDisabledExact(keys("  MINECRAFT:RUINED_PORTAL  "));

        assertFalse(control.shouldGenerate("minecraft:ruined_portal"));
        assertEquals(NativeStructureGenerationStatus.DISABLED_BY_PACK,
                control.resolve(" MINECRAFT:RUINED_PORTAL ", false).status());
        assertTrue(control.shouldGenerate("minecraft:ruined_portal_nether"));
        assertEquals(NativeStructureGenerationStatus.GENERATE_NATIVE,
                control.resolve("minecraft:ruined_portal_nether", false).status());
    }

    @Test
    public void familyBlacklistRetainsPrefixMatchingBesideExactBlacklist() {
        IrisImportedStructureControl control = new IrisImportedStructureControl()
                .setDisabled(keys("minecraft:village"))
                .setDisabledExact(keys("minecraft:ruined_portal"));

        assertFalse(control.shouldGenerate("minecraft:village_plains"));
        assertFalse(control.shouldGenerate("minecraft:ruined_portal"));
        assertTrue(control.shouldGenerate("minecraft:ruined_portal_nether"));
    }

    @Test
    public void datapackOverridesFalseDoesNotDisableModOrDatapackNamespaces() {
        IrisImportedStructureControl control = new IrisImportedStructureControl()
                .setDatapackOverrides(false);
        assertTrue(control.shouldGenerate("nova_structures:desert_temple"));
        assertTrue(control.shouldGenerate("aquaculture:treasure_vault"));
        assertTrue(control.shouldGenerate("minecraft:village_plains"));
    }

    @Test
    public void disabledListControlsThirdPartyStructuresWhenOverridesAreFalse() {
        IrisImportedStructureControl control = new IrisImportedStructureControl()
                .setDisabled(keys("aquaculture:treasure_vault"))
                .setDatapackOverrides(false);
        assertTrue(control.shouldGenerate("nova_structures:desert_temple"));
        assertTrue(control.shouldGenerate("minecraft:village_plains"));
        assertFalse(control.shouldGenerate("aquaculture:treasure_vault"));
    }

    @Test
    public void datapackOverridesTrueAllowsDatapackKeysByDefault() {
        IrisImportedStructureControl control = new IrisImportedStructureControl()
                .setDatapackOverrides(true);
        assertTrue(control.shouldGenerate("nova_structures:desert_temple"));
    }

    @Test
    public void keyWithoutNamespaceIsNotTreatedAsDatapack() {
        IrisImportedStructureControl control = new IrisImportedStructureControl()
                .setDatapackOverrides(false);
        assertTrue(control.shouldGenerate("village_plains"));
    }

    @Test
    public void minecraftKeyIsNeverTreatedAsDatapack() {
        IrisImportedStructureControl control = new IrisImportedStructureControl()
                .setDatapackOverrides(false);
        assertTrue(control.shouldGenerate("minecraft:ancient_city"));
    }

    @Test
    public void emptyOrNullListEntriesNeverMatchEveryKey() {
        IrisImportedStructureControl control = new IrisImportedStructureControl()
                .setDisabled(keys("", null));
        assertTrue(control.shouldGenerate("minecraft:village_plains"));
        assertTrue(control.shouldGenerate("minecraft:stronghold"));
    }

    @Test
    public void nullKeyIsRejectedAsAnInvalidRegistryKey() {
        IrisImportedStructureControl control = new IrisImportedStructureControl();
        assertFalse(control.shouldGenerate(null));
        assertEquals(NativeStructureGenerationStatus.INVALID_REGISTRY_KEY,
                control.resolve(null, false).status());
    }

    @Test
    public void verticalShiftDefaultsToZeroAndAppliesUndergroundBaseOnlyUnderground() {
        IrisImportedStructureControl control = new IrisImportedStructureControl().setUndergroundYShift(-32);

        assertEquals(0, control.resolve("minecraft:village_plains", false).yShift());
        assertEquals(-32, control.resolve("minecraft:stronghold", true).yShift());
    }

    @Test
    public void matchingVerticalShiftsStackWithoutAffectingOtherStructures() {
        IrisVanillaStructureAdjustment broad = new IrisVanillaStructureAdjustment()
                .setMatch(keys("minecraft:trial"))
                .setYShift(-48);
        IrisVanillaStructureAdjustment exact = new IrisVanillaStructureAdjustment()
                .setMatch(keys("minecraft:trial_chambers"))
                .setYShift(-16);
        KList<IrisVanillaStructureAdjustment> adjustments = new KList<>();
        adjustments.add(broad);
        adjustments.add(exact);
        IrisImportedStructureControl control = new IrisImportedStructureControl()
                .setUndergroundYShift(8)
                .setAdjustments(adjustments);

        assertEquals(-56, control.resolve("minecraft:trial_chambers", true).yShift());
        assertEquals(8, control.resolve("minecraft:stronghold", true).yShift());
        assertEquals(0, control.resolve("minecraft:village_plains", false).yShift());
    }

    @Test
    public void postprocessingDefaultsAreDisabled() {
        IrisImportedStructureControl control = new IrisImportedStructureControl();
        assertNull(control.resolve("minecraft:village_plains", false).stilt());
        assertNull(control.resolve(null, false).stilt());
        assertFalse(control.resolve("minecraft:mineshaft_mesa", true).preserveSourceY());
    }

    @Test
    public void unconfiguredTerrainAndBandStayUnsetSoNativeDefaultsCanApply() {
        IrisImportedStructureControl control = new IrisImportedStructureControl();

        assertNull(control.resolve("minecraft:stronghold", true).terrain());
        assertNull(control.resolve("minecraft:stronghold", true).yBand());
    }

    @Test
    public void multipleMatchesUseTheLastConfiguredTerrainAndBand() {
        IrisStructureTerrain broadTerrain = new IrisStructureTerrain()
                .setMode(IrisStructureTerrainMode.SOURCE);
        IrisStructureTerrain exactTerrain = new IrisStructureTerrain()
                .setMode(IrisStructureTerrainMode.ENCASE)
                .setHorizontalPadding(4);
        IrisStructureYBand band = new IrisStructureYBand().setMin(-120).setMax(-20);
        IrisVanillaStructureAdjustment broad = new IrisVanillaStructureAdjustment()
                .setMatch(keys("minecraft:stronghold"))
                .setTerrain(broadTerrain);
        IrisVanillaStructureAdjustment exact = new IrisVanillaStructureAdjustment()
                .setMatch(keys("minecraft:stronghold"))
                .setTerrain(exactTerrain)
                .setYBand(band);
        KList<IrisVanillaStructureAdjustment> adjustments = new KList<>();
        adjustments.add(broad);
        adjustments.add(exact);
        IrisImportedStructureControl control = new IrisImportedStructureControl()
                .setAdjustments(adjustments);

        IrisNativeStructureDecision stronghold = control.resolve("minecraft:stronghold", true);
        assertSame(exactTerrain, stronghold.terrain());
        assertSame(band, stronghold.yBand());
        assertNull(control.resolve("minecraft:trial_chambers", true).terrain());
        assertNull(control.resolve("minecraft:trial_chambers", true).yBand());
    }

    @Test
    public void namespaceVacuumCanPreserveSpecificStructuresLater() {
        IrisVanillaStructureAdjustment namespace = new IrisVanillaStructureAdjustment()
                .setMatch(keys("towns_and_towers:"))
                .setTerrain(new IrisStructureTerrain().setMode(IrisStructureTerrainMode.VACUUM));
        IrisVanillaStructureAdjustment preserveShips = new IrisVanillaStructureAdjustment()
                .setMatch(keys(
                        "towns_and_towers:mimic_desert",
                        "towns_and_towers:pillager_outpost_ocean",
                        "towns_and_towers:village_ocean",
                        "towns_and_towers:wreckage_ocean"))
                .setTerrain(new IrisStructureTerrain().setMode(IrisStructureTerrainMode.PRESERVE));
        KList<IrisVanillaStructureAdjustment> adjustments = new KList<>();
        adjustments.add(namespace);
        adjustments.add(preserveShips);
        IrisImportedStructureControl control = new IrisImportedStructureControl()
                .setAdjustments(adjustments);

        assertEquals(IrisStructureTerrainMode.VACUUM,
                control.resolve("towns_and_towers:village_forest", false)
                        .terrain().resolvedMode());
        assertEquals(IrisStructureTerrainMode.PRESERVE,
                control.resolve("towns_and_towers:village_ocean", false)
                        .terrain().resolvedMode());
        assertEquals(IrisStructureTerrainMode.PRESERVE,
                control.resolve("towns_and_towers:mimic_desert", true)
                        .terrain().resolvedMode());
    }

    @Test
    public void preserveSourceYMatchesTheStructureFamilyAndKeepsExplicitShifts() {
        IrisVanillaStructureAdjustment adjustment = new IrisVanillaStructureAdjustment()
                .setMatch(keys("minecraft:mineshaft"))
                .setPreserveSourceY(true)
                .setYShift(4);
        IrisImportedStructureControl control = new IrisImportedStructureControl()
                .setUndergroundYShift(-64)
                .setAdjustments(new KList<IrisVanillaStructureAdjustment>().qadd(adjustment));

        IrisNativeStructureDecision mesa = control.resolve("minecraft:mineshaft_mesa", true);
        assertTrue(mesa.preserveSourceY());
        assertEquals(-60, mesa.yShift());
        assertTrue(control.resolve("minecraft:mineshaft", true).preserveSourceY());
        assertFalse(control.resolve("minecraft:stronghold", true).preserveSourceY());
    }

    @Test
    public void postprocessingUsesPrefixMatches() {
        IrisStructureStiltSettings stilt = new IrisStructureStiltSettings();
        IrisVanillaStructureAdjustment adjustment = new IrisVanillaStructureAdjustment()
                .setMatch(keys("minecraft:village"))
                .setStilt(stilt);
        IrisImportedStructureControl control = new IrisImportedStructureControl()
                .setAdjustments(new KList<IrisVanillaStructureAdjustment>().qadd(adjustment));

        assertSame(stilt, control.resolve("minecraft:village_taiga", false).stilt());
        assertNull(control.resolve("minecraft:woodland_mansion", false).stilt());
        assertNull(control.resolve("minecraft:stronghold", true).stilt());
    }

    @Test
    public void multipleMatchesUseTheLastConfiguredStilt() {
        IrisStructureStiltSettings broadStilt = new IrisStructureStiltSettings().setMaxDepth(32);
        IrisStructureStiltSettings specificStilt = new IrisStructureStiltSettings().setMaxDepth(96);
        IrisVanillaStructureAdjustment broad = new IrisVanillaStructureAdjustment()
                .setMatch(keys("minecraft:village"))
                .setStilt(broadStilt);
        IrisVanillaStructureAdjustment exactWithoutStilt = new IrisVanillaStructureAdjustment()
                .setMatch(keys("minecraft:village_plains"));
        IrisVanillaStructureAdjustment exactWithStilt = new IrisVanillaStructureAdjustment()
                .setMatch(keys("minecraft:village_plains"))
                .setStilt(specificStilt);
        KList<IrisVanillaStructureAdjustment> adjustments = new KList<>();
        adjustments.add(broad);
        adjustments.add(exactWithoutStilt);
        adjustments.add(exactWithStilt);
        IrisImportedStructureControl control = new IrisImportedStructureControl().setAdjustments(adjustments);

        IrisNativeStructureDecision plains = control.resolve("minecraft:village_plains", false);
        assertSame(specificStilt, plains.stilt());
        assertEquals(96, plains.stilt().getMaxDepth());
        assertSame(broadStilt, control.resolve("minecraft:village_desert", false).stilt());
    }

    @Test
    public void familyPrefixRequiresAResourceBoundary() {
        IrisImportedStructureControl control = new IrisImportedStructureControl()
                .setDisabled(keys("minecraft:village"));

        assertFalse(control.shouldGenerate("minecraft:village_plains"));
        assertTrue(control.shouldGenerate("minecraft:villager_outpost"));
    }

    @Test
    public void namespacePrefixMatchesEveryKeyInNamespace() {
        IrisImportedStructureControl control = new IrisImportedStructureControl()
                .setDisabled(keys("example:"));

        assertFalse(control.shouldGenerate("example:castle"));
        assertTrue(control.shouldGenerate("other:castle"));
    }

    @Test
    public void malformedNullPolicyListsFailWithTheirExactField() {
        IrisImportedStructureControl nullDisabled = new IrisImportedStructureControl().setDisabled(null);
        IrisImportedStructureControl nullDisabledExact = new IrisImportedStructureControl().setDisabledExact(null);
        IrisImportedStructureControl nullAdjustments = new IrisImportedStructureControl().setAdjustments(null);
        IrisImportedStructureControl nullFrequencyOverrides = new IrisImportedStructureControl()
                .setFrequencyOverrides(null);

        NullPointerException disabled = assertThrows(NullPointerException.class,
                () -> nullDisabled.shouldGenerate("minecraft:monument"));
        NullPointerException disabledExact = assertThrows(NullPointerException.class,
                () -> nullDisabledExact.shouldGenerate("minecraft:monument"));
        NullPointerException adjustments = assertThrows(NullPointerException.class,
                () -> nullAdjustments.resolve("minecraft:monument", false));
        NullPointerException frequencyOverrides = assertThrows(NullPointerException.class,
                () -> nullFrequencyOverrides.frequencyMultiplier("minecraft:villages"));

        assertTrue(disabled.getMessage().contains("importedStructures.disabled"));
        assertTrue(disabledExact.getMessage().contains("importedStructures.disabledExact"));
        assertTrue(adjustments.getMessage().contains("importedStructures.adjustments"));
        assertTrue(frequencyOverrides.getMessage().contains("importedStructures.frequencyOverrides"));
    }
}
