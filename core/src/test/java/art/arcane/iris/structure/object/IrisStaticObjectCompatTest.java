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
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.testsupport.CompatTestPlatform;
import art.arcane.iris.pack.value.IrisPosition;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.pack.validation.CompatAction;
import art.arcane.iris.pack.validation.CompatFinding;
import art.arcane.iris.pack.validation.CompatStatus;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.PackValidationResult;
import art.arcane.iris.pack.PackValidator;
import art.arcane.volmlib.util.collection.KList;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Static objects (absolute-coordinate placements on the dimension) honour the version-content gate the same way a
 * one-object placement does: a palette key the server does not have keeps the object out of the static layer and
 * is reported against the static object, while a chance-1 edit rule that rewrites the key rescues it.
 */
public class IrisStaticObjectCompatTest {
    /** World Y of the entries below, as the static layer stores it (offset from the default dimension floor). */
    private static final int LAYER_Y = 100 + 64;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private CompatTestPlatform platform;
    private IrisSettings previousSettings;
    private IrisObjectRotation.StateRotator previousRotator;
    private IrisData data;

    @Before
    public void bindPlatform() {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        // Water, clay, dirt and sand are what a default dimension composes (fluid palette, river bed padding, the
        // default sand shore bench); without them the dimension itself is excluded and the static object is never
        // the deciding unit.
        platform = CompatTestPlatform.bind(temporaryFolder.getRoot(), List.of("minecraft:stone",
                "minecraft:cobblestone", "minecraft:air", "minecraft:water", "minecraft:clay", "minecraft:dirt",
                "minecraft:sand"));
        previousRotator = IrisObjectRotation.bindPlatformRotator((rotation, block, x, y, z) -> block);
    }

    @After
    public void restorePlatform() {
        IrisObjectRotation.restorePlatformRotator(previousRotator);
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
    public void missingPaletteKeyKeepsTheEntryOutOfTheLayerAndReportsItAsDropped() throws Exception {
        File pack = pack("static-drop");
        CompatTestPlatform.writeObjectWithBlocks(pack, "landmarks/bad", "minecraft:sulfur");
        CompatTestPlatform.writeObjectWithBlocks(pack, "landmarks/good", "minecraft:stone");
        data = IrisData.get(pack);
        IrisDimension dimension = dimension(entry("landmarks/bad", 0, 100, 0), entry("landmarks/good", 32, 100, 32));

        IrisStaticObjectLayer layer = IrisStaticObjectLayer.compile(dimension, data);

        assertFalse("the gated entry must not be stamped", layer.contains(0, LAYER_Y, 0));
        assertTrue("the entry that resolves still compiles", layer.contains(32, LAYER_Y, 32));

        List<CompatFinding> findings = data.getCompatReport().findings();
        assertEquals(findings.toString(), 1, findings.size());
        CompatFinding finding = findings.getFirst();
        assertEquals("minecraft:sulfur", finding.key());
        assertEquals(CompatAction.DROPPED, finding.action());
        assertEquals("static object", finding.subjectType());
        assertEquals("landmarks/bad", finding.subjectKey());
        assertTrue(finding.detail(), finding.detail().contains("main staticObjects[0]"));
    }

    @Test
    public void chanceOneEditRuleRescuesTheEntryAndTheRewrittenBlockIsStamped() throws Exception {
        File pack = pack("static-edit");
        CompatTestPlatform.writeObjectWithBlocks(pack, "bad", "minecraft:sulfur");
        data = IrisData.get(pack);
        IrisStaticObject entry = entry("bad", 0, 100, 0)
                .setEdit(new KList<>(replace(1F, "minecraft:sulfur", "minecraft:stone")));

        assertFalse(entry.evaluateCompat(data, "main", 0).excluded());
        IrisStaticObjectLayer layer = IrisStaticObjectLayer.compile(dimension(entry), data);

        assertTrue(layer.contains(0, LAYER_Y, 0));
        assertEquals("minecraft:stone", layer.blocks(0, 0).getFirst().state().key());
        assertTrue(data.getCompatReport().findings().toString(), data.getCompatReport().isEmpty());
    }

    @Test
    public void editRuleBelowChanceOneDoesNotCoverTheKey() throws Exception {
        File pack = pack("static-edit-chance");
        CompatTestPlatform.writeObjectWithBlocks(pack, "bad", "minecraft:sulfur");
        data = IrisData.get(pack);
        IrisStaticObject entry = entry("bad", 0, 100, 0)
                .setEdit(new KList<>(replace(0.5F, "minecraft:sulfur", "minecraft:stone")));

        assertTrue(entry.evaluateCompat(data, "main", 0).excluded());
        assertTrue(IrisStaticObjectLayer.compile(dimension(entry), data).isEmpty());
    }

    @Test
    public void missingEditPaletteExcludesOnlyTheEntryNeverTheDimension() throws Exception {
        File pack = pack("static-edit-missing");
        CompatTestPlatform.writeObjectWithBlocks(pack, "good", "minecraft:stone");
        data = IrisData.get(pack);
        IrisStaticObject entry = entry("good", 0, 100, 0)
                .setEdit(new KList<>(replace(1F, "minecraft:stone", "minecraft:sulfur")));
        IrisDimension dimension = dimension(entry);
        dimension.setLoader(data);

        CompatStatus dimensionStatus = dimension.evaluateCompat(data.getContentGate());
        assertFalse("a static object's own palette is the static object's problem, not the pack's: "
                + dimensionStatus.reasons(), dimensionStatus.excluded());
        assertTrue(dimensionStatus.reasons().toString(), dimensionStatus.reasons().isEmpty());
        CompatStatus status = entry.evaluateCompat(data, "main", 0);
        assertTrue(status.excluded());
        CompatFinding finding = status.reasons().getFirst();
        assertEquals(CompatAction.EXCLUDED, finding.action());
        assertEquals("static object", finding.subjectType());
        assertEquals("good", finding.subjectKey());
        assertTrue(finding.detail(), finding.detail().contains("main staticObjects[0] edit[0].replace.palette[0]"));
        assertTrue(IrisStaticObjectLayer.compile(dimension, data).isEmpty());
    }

    @Test
    public void dimensionFallbackRescuesTheEntryAndRecordsASubstitution() throws Exception {
        File pack = pack("static-fallback");
        CompatTestPlatform.write(pack, "dimensions/main.json",
                "{\"blockFallbacks\":{\"minecraft:sulfur\":\"minecraft:cobblestone\"}}");
        CompatTestPlatform.writeObjectWithBlocks(pack, "bad", "minecraft:sulfur");
        data = IrisData.get(pack);
        IrisStaticObject entry = entry("bad", 0, 100, 0);

        assertFalse(entry.evaluateCompat(data, "main", 0).excluded());
        IrisStaticObjectLayer layer = IrisStaticObjectLayer.compile(dimension(entry), data);

        assertTrue(layer.contains(0, LAYER_Y, 0));
        assertEquals("minecraft:cobblestone", layer.blocks(0, 0).getFirst().state().key());
        List<CompatFinding> findings = data.getCompatReport().findings();
        assertEquals(findings.toString(), 1, findings.size());
        assertEquals(CompatAction.SUBSTITUTED, findings.getFirst().action());
        assertEquals("static object", findings.getFirst().subjectType());
    }

    @Test
    public void aCompletePackRecordsNothingAndPlacesEveryEntry() throws Exception {
        File pack = pack("static-complete");
        CompatTestPlatform.writeObjectWithBlocks(pack, "a", "minecraft:stone");
        CompatTestPlatform.writeObjectWithBlocks(pack, "b", "minecraft:cobblestone");
        data = IrisData.get(pack);

        IrisStaticObjectLayer layer = IrisStaticObjectLayer.compile(
                dimension(entry("a", 0, 100, 0), entry("b", 16, 100, 16)), data);

        assertTrue(layer.contains(0, LAYER_Y, 0));
        assertTrue(layer.contains(16, LAYER_Y, 16));
        assertSame(CompatStatus.OK, entry("a", 0, 100, 0).evaluateCompat(null, "main", 0));
        assertTrue(data.getCompatReport().isEmpty());
    }

    @Test
    public void packValidationListsStaticObjectsTheServerCannotPlace() throws Exception {
        File pack = pack("static-validate");
        CompatTestPlatform.writeObjectWithBlocks(pack, "landmarks/bad", "minecraft:sulfur");
        CompatTestPlatform.write(pack, "dimensions/main.json",
                "{\"staticObjects\":[{\"object\":\"landmarks/bad\",\"position\":{\"x\":0,\"y\":100,\"z\":0}}]}");

        PackValidationResult result = PackValidator.validate(pack);

        assertTrue(result.getCompatFindings().toString(), result.getCompatFindings().stream().anyMatch(
                finding -> finding.action() == CompatAction.DROPPED
                        && "static object".equals(finding.subjectType())
                        && "landmarks/bad".equals(finding.subjectKey())
                        && "minecraft:sulfur".equals(finding.key())));
        assertFalse("a static object never makes the pack unusable: " + result.getBlockingErrors(),
                result.getBlockingErrors().stream().anyMatch(error -> error.contains("cannot generate on Minecraft")));
        assertTrue(result.getCompatFindings().toString(), result.getCompatFindings().stream()
                .noneMatch(finding -> "dimension".equals(finding.subjectType())));
    }

    private File pack(String name) throws Exception {
        return temporaryFolder.newFolder(name);
    }

    private static IrisDimension dimension(IrisStaticObject... entries) {
        IrisDimension dimension = new IrisDimension().setStaticObjects(new KList<>(entries));
        dimension.setLoadKey("main");
        return dimension;
    }

    private static IrisStaticObject entry(String key, int x, int y, int z) {
        return new IrisStaticObject().setObject(key).setPosition(new IrisPosition(x, y, z));
    }

    private static IrisObjectReplace replace(float chance, String find, String to) {
        IrisObjectReplace rule = new IrisObjectReplace();
        rule.setChance(chance);
        rule.getFind().add(new IrisBlockData(find));
        rule.getReplace().qclear();
        rule.getReplace().add(to);
        return rule;
    }
}
