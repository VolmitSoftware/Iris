/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.testsupport.CompatTestPlatform;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.pack.validation.CompatAction;
import art.arcane.iris.pack.validation.CompatFinding;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.block.DataProvider;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisObjectPlacementCompatTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private CompatTestPlatform platform;
    private IrisSettings previousSettings;
    private IrisData data;

    @Before
    public void bindPlatform() {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        platform = CompatTestPlatform.bind(temporaryFolder.getRoot(),
                List.of("minecraft:stone", "minecraft:cobblestone", "minecraft:air", "minecraft:oak_log"));
    }

    @After
    public void restorePlatform() {
        if (data != null) {
            data.close();
            data = null;
        }
        if (platform != null) {
            platform.unbind();
            platform = null;
        }
        IrisSettings.settings = previousSettings;
    }

    @Test
    public void missingPaletteKeyDropsTheObjectAndRecordsAFinding() throws Exception {
        File pack = pack("drop");
        CompatTestPlatform.writeObject(pack, "good", "minecraft:stone");
        CompatTestPlatform.writeObject(pack, "bad", "minecraft:stone", "minecraft:sulfur");
        data = IrisData.get(pack);
        IrisObjectPlacement placement = placement("good", "bad");

        assertFalse(placement.isCompatExcluded(data));
        assertEquals(new KList<String>().qadd("good"), placement.compatPlace(data));

        List<CompatFinding> findings = data.getCompatReport().findings();
        assertEquals(findings.toString(), 1, findings.size());
        assertEquals("minecraft:sulfur", findings.getFirst().key());
        assertEquals(CompatAction.DROPPED, findings.getFirst().action());
        assertEquals("object", findings.getFirst().subjectType());
        assertEquals("bad", findings.getFirst().subjectKey());
    }

    @Test
    public void editRuleWithFullChanceKeepsTheObject() throws Exception {
        File pack = pack("edit-keeps");
        CompatTestPlatform.writeObject(pack, "bad", "minecraft:sulfur");
        data = IrisData.get(pack);
        IrisObjectPlacement placement = placement("bad");
        placement.getEdit().add(replace(1F, false, "minecraft:sulfur", "minecraft:stone"));

        assertFalse(placement.isCompatExcluded(data));
        assertEquals(new KList<String>().qadd("bad"), placement.compatPlace(data));
        assertTrue(data.getCompatReport().findings().toString(), data.getCompatReport().isEmpty());
    }

    @Test
    public void editRuleBelowFullChanceDoesNotCoverTheMissingKey() throws Exception {
        File pack = pack("edit-chance");
        CompatTestPlatform.writeObject(pack, "bad", "minecraft:sulfur");
        data = IrisData.get(pack);
        IrisObjectPlacement placement = placement("bad");
        placement.getEdit().add(replace(0.5F, false, "minecraft:sulfur", "minecraft:stone"));

        assertTrue(placement.isCompatExcluded(data));
        assertTrue(placement.compatPlace(data).isEmpty());
    }

    @Test
    public void exactEditRuleOnlyCoversTheMatchingState() throws Exception {
        File pack = pack("edit-exact");
        CompatTestPlatform.writeObject(pack, "axisY", "minecraft:sulfur[axis=y]");
        CompatTestPlatform.writeObject(pack, "axisX", "minecraft:sulfur[axis=x]");
        data = IrisData.get(pack);
        IrisObjectPlacement placement = placement("axisY", "axisX");
        IrisObjectReplace rule = replace(1F, true, "minecraft:sulfur", "minecraft:stone");
        rule.getFind().getFirst().getData().put("axis", "y");
        placement.getEdit().add(rule);

        assertEquals(new KList<String>().qadd("axisY"), placement.compatPlace(data));
    }

    @Test
    public void nonExactEditRuleCoversEveryStateOfTheSameBlock() throws Exception {
        File pack = pack("edit-non-exact");
        CompatTestPlatform.writeObject(pack, "axisX", "minecraft:sulfur[axis=x]");
        data = IrisData.get(pack);
        IrisObjectPlacement placement = placement("axisX");
        placement.getEdit().add(replace(1F, false, "minecraft:sulfur", "minecraft:stone"));

        assertFalse(placement.isCompatExcluded(data));
    }

    @Test
    public void missingReplacePaletteOptionExcludesTheWholePlacement() throws Exception {
        File pack = pack("replace-missing");
        CompatTestPlatform.writeObject(pack, "good", "minecraft:stone");
        data = IrisData.get(pack);
        IrisObjectPlacement placement = placement("good");
        placement.getEdit().add(replace(1F, false, "minecraft:stone", "minecraft:sulfur"));

        assertTrue(placement.isCompatExcluded(data));
        assertTrue(placement.compatPlace(data).isEmpty());

        List<CompatFinding> findings = data.getCompatReport().findings();
        assertEquals(findings.toString(), 1, findings.size());
        assertEquals(CompatAction.EXCLUDED, findings.getFirst().action());
        assertEquals("placement", findings.getFirst().subjectType());
        assertTrue(findings.getFirst().detail(), findings.getFirst().detail().contains("edit[0].replace.palette[0]"));
    }

    @Test
    public void missingFindKeyOnTheMatchSideIsIgnored() throws Exception {
        File pack = pack("find-missing");
        CompatTestPlatform.writeObject(pack, "good", "minecraft:stone");
        data = IrisData.get(pack);
        IrisObjectPlacement placement = placement("good");
        placement.getEdit().add(replace(1F, false, "minecraft:sulfur", "minecraft:cobblestone"));

        assertFalse(placement.isCompatExcluded(data));
        assertTrue(data.getCompatReport().findings().toString(), data.getCompatReport().isEmpty());
    }

    @Test
    public void missingMarkerBlockIsMatchOnly() throws Exception {
        File pack = pack("marker-missing");
        CompatTestPlatform.writeObject(pack, "good", "minecraft:stone");
        data = IrisData.get(pack);
        IrisObjectPlacement placement = placement("good");
        IrisObjectMarker marker = new IrisObjectMarker();
        marker.setMarker("thing");
        marker.getMark().add(new IrisBlockData("minecraft:sulfur"));
        placement.getMarkers().add(marker);

        assertFalse(placement.isCompatExcluded(data));
        assertEquals(new KList<String>().qadd("good"), placement.compatPlace(data));
        assertTrue(data.getCompatReport().isEmpty());
    }

    @Test
    public void dimensionFallbackRescuesTheObjectAndRecordsASubstitution() throws Exception {
        File pack = pack("fallback");
        CompatTestPlatform.write(pack, "dimensions/main.json",
                "{\"blockFallbacks\":{\"minecraft:sulfur\":\"minecraft:cobblestone\"}}");
        CompatTestPlatform.writeObject(pack, "bad", "minecraft:sulfur");
        data = IrisData.get(pack);
        IrisObjectPlacement placement = placement("bad");

        assertFalse(placement.isCompatExcluded(data));
        assertEquals(new KList<String>().qadd("bad"), placement.compatPlace(data));

        List<CompatFinding> findings = data.getCompatReport().findings();
        assertEquals(findings.toString(), 1, findings.size());
        assertEquals(CompatAction.SUBSTITUTED, findings.getFirst().action());
    }

    @Test
    public void blockBackupRescuesAReplacePaletteEntry() throws Exception {
        File pack = pack("backup");
        CompatTestPlatform.writeObject(pack, "good", "minecraft:stone");
        data = IrisData.get(pack);
        IrisObjectPlacement placement = placement("good");
        IrisObjectReplace rule = replace(1F, false, "minecraft:stone", "minecraft:sulfur");
        rule.getReplace().getPalette().getFirst().setBackup(new IrisBlockData("minecraft:cobblestone"));
        placement.getEdit().add(rule);

        assertFalse(placement.isCompatExcluded(data));
        assertEquals(CompatAction.SUBSTITUTED, data.getCompatReport().findings().getFirst().action());
    }

    @Test
    public void everyObjectDroppedExcludesThePlacement() throws Exception {
        File pack = pack("all-dropped");
        CompatTestPlatform.writeObject(pack, "bad", "minecraft:sulfur");
        data = IrisData.get(pack);
        IrisObjectPlacement placement = placement("bad");

        assertTrue(placement.isCompatExcluded(data));
        assertTrue(placement.compatPlace(data).isEmpty());
        assertTrue(data.getCompatReport().findings().stream()
                .anyMatch(finding -> finding.action() == CompatAction.EXCLUDED
                        && "placement".equals(finding.subjectType())));
    }

    @Test
    public void survivingPoolKeepsAuthoredOrderAndGetObjectOnlyDrawsSurvivors() throws Exception {
        File pack = pack("draw");
        CompatTestPlatform.writeObject(pack, "a", "minecraft:stone");
        CompatTestPlatform.writeObject(pack, "bad", "minecraft:sulfur");
        CompatTestPlatform.writeObject(pack, "b", "minecraft:cobblestone");
        data = IrisData.get(pack);
        IrisObjectPlacement placement = placement("a", "bad", "b");

        assertEquals(new KList<String>().qadd("a").qadd("b"), placement.compatPlace(data));

        IrisData loaded = data;
        DataProvider provider = () -> loaded;
        for (int seed = 0; seed < 64; seed++) {
            IrisObject drawn = placement.getObject(provider, new RNG(seed));
            assertTrue("Draw must never return the dropped object",
                    drawn != null && !"bad".equals(drawn.getLoadKey()));
        }
    }

    @Test
    public void aCompletePackLeavesThePoolIdentical() throws Exception {
        File pack = pack("complete");
        CompatTestPlatform.writeObject(pack, "a", "minecraft:stone");
        CompatTestPlatform.writeObject(pack, "b", "minecraft:cobblestone");
        data = IrisData.get(pack);
        IrisObjectPlacement placement = placement("a", "b");

        assertTrue("A complete pack must hand back the authored pool untouched",
                placement.getPlace() == placement.compatPlace(data));
        assertTrue(data.getCompatReport().isEmpty());
    }

    private File pack(String name) throws Exception {
        return temporaryFolder.newFolder(name);
    }

    private static IrisObjectPlacement placement(String... place) {
        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setPlace(new KList<>(place));
        return placement;
    }

    private static IrisObjectReplace replace(float chance, boolean exact, String find, String to) {
        IrisObjectReplace rule = new IrisObjectReplace();
        rule.setChance(chance);
        rule.setExact(exact);
        rule.getFind().add(new IrisBlockData(find));
        rule.getReplace().qclear();
        rule.getReplace().add(to);
        return rule;
    }
}
