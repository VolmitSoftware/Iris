/*
 * Iris is a World Generator for Minecraft Servers
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

package art.arcane.iris.engine.object.procedural;

import art.arcane.iris.builtin.anchor.ObjectPlaceMode;
import art.arcane.iris.builtin.carve.CarvingMode;
import art.arcane.iris.builtin.objects.ProceduralObjectSettings;
import art.arcane.iris.builtin.objects.coral.Coral;
import art.arcane.iris.builtin.objects.coral.CoralForm;
import art.arcane.iris.builtin.objects.crystal.Crystal;
import art.arcane.iris.builtin.objects.crystal.CrystalDistribution;
import art.arcane.iris.builtin.objects.formation.Formation;
import art.arcane.iris.builtin.objects.formation.FormationForm;
import art.arcane.iris.builtin.objects.fungi.Fungus;
import art.arcane.iris.builtin.objects.ruin.Ruin;
import art.arcane.iris.builtin.objects.tree.TreeLeafMode;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * The procedural objects the overworld pack's biomes write ({@code snippet/procedural-objects/frozen/...}) read into the module settings
 * classes (IrisApi types only) with the values Iris's own classes read from the same JSON, and made placeable.
 */
public class ProceduralSettingsDecodingTest {
    private static final String ICEBERG = """
            {"name": "glacial-iceberg", "chance": 0.08, "density": 1, "variants": 24, "seed": 5055001, "mode": "CENTER_HEIGHT",
             "carvingSupport": "SURFACE_ONLY", "surfaceSupportBuffer": 1, "block": "minecraft:packed_ice",
             "blockPalette": "snippet/palette/frozen/magical-ice", "capBlock": "minecraft:blue_ice",
             "rotation": {"enabled": true, "yAxis": {"enabled": true, "min": 0, "max": 360, "interval": 45}},
             "form": "ICEBERG", "translate": {"y": -4}, "heightMin": 12, "heightMax": 22, "baseWidthMin": 5, "baseWidthMax": 8,
             "icebergPeaks": 4, "roughness": 0.42, "jitter": 0.14}
            """;
    private static final String FROST_BLOOM = """
            {"mode": "CENTER_HEIGHT", "carvingSupport": "SURFACE_ONLY",
             "rotation": {"enabled": true, "yAxis": {"enabled": true, "min": 0, "max": 360, "interval": 45}},
             "growthSurface": "FLOOR", "block": "minecraft:packed_ice", "blockPalette": "snippet/palette/frozen/magical-ice",
             "tipBlock": "minecraft:blue_ice", "baseBlock": "minecraft:blue_ice", "tipChance": 0.65, "name": "glacial-frost-bloom",
             "chance": 0.28, "density": 2, "variants": 24, "seed": 5055031, "translate": {"y": -1}, "baseRadius": 1.8, "baseNoise": 0.4,
             "shardCountMin": 3, "shardCountMax": 6, "shardLengthMin": 2, "shardLengthMax": 5, "shardBaseRadius": 1.1,
             "shardTaper": 1.0, "spreadAngle": 68, "distribution": "GOLDEN_ANGLE", "jitter": 0.35}
            """;
    private static final String BIOME_SNIPPET = "\"snippet/procedural-objects/frozen/magical-ice-spikes-biome\"";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Gson gson;

    @Before
    public void decodeLikeIrisDoes() throws IOException {
        File palettes = folder.newFolder("snippet", "palette", "frozen");
        Files.writeString(new File(palettes, "magical-ice.json").toPath(),
                "{\"style\": {\"style\": \"SIMPLEX\", \"zoom\": 0.4}, \"palette\": [{\"block\": \"minecraft:packed_ice\"}, {\"block\": \"minecraft:blue_ice\", \"weight\": 2}]}");
        File objects = folder.newFolder("snippet", "procedural-objects", "frozen");
        Files.writeString(new File(objects, "magical-ice-spikes-biome.json").toPath(),
                "{\"formations\": [" + ICEBERG + "], \"crystals\": [" + FROST_BLOOM + "]}");
        IrisData data = mock(IrisData.class, CALLS_REAL_METHODS);
        doReturn(folder.getRoot()).when(data).getDataFolder();
        gson = new GsonBuilder().registerTypeAdapterFactory(data).create();
    }

    @Test
    public void aFormationReadsEveryFieldIrisReadFromIt() {
        Formation iceberg = gson.fromJson(ICEBERG, Formation.class);

        assertEquals("glacial-iceberg", iceberg.name());
        assertEquals(0.08, iceberg.chance(), 0);
        assertEquals(24, iceberg.variants());
        assertEquals(5055001L, iceberg.seed());
        assertEquals(ObjectPlaceMode.CENTER_HEIGHT, iceberg.mode());
        assertEquals(CarvingMode.SURFACE_ONLY, iceberg.carvingSupport());
        assertEquals(1, iceberg.surfaceSupportBuffer());
        assertEquals("minecraft:packed_ice", iceberg.block());
        assertEquals("minecraft:blue_ice", iceberg.capBlock().orElseThrow());
        assertEquals(FormationForm.ICEBERG, iceberg.form());
        assertEquals(-4, iceberg.translate().y());
        assertTrue(iceberg.rotation().yAxis().enabled());
        assertEquals(45, iceberg.rotation().yAxis().interval(), 0);
        assertEquals(360, iceberg.rotation().yAxis().max(), 0);
        assertEquals(4, iceberg.icebergPeaks());
        assertEquals(0.42, iceberg.roughness(), 0);
    }

    @Test
    public void palettesMayBeSnippetsAndReadAsPaletteSpecs() {
        Formation iceberg = gson.fromJson(ICEBERG, Formation.class);

        var palette = iceberg.blockPalette().orElseThrow();
        assertEquals("SIMPLEX", palette.style().style());
        assertEquals(0.4, palette.style().zoom(), 0);
        assertEquals(2, palette.palette().size());
        assertEquals(2, palette.palette().get(1).weight());
        assertTrue(gson.fromJson("{}", Formation.class).blockPalette().isEmpty());
    }

    @Test
    public void aCrystalReadsItsFieldsAndEnums() {
        Crystal bloom = gson.fromJson(FROST_BLOOM, Crystal.class);

        assertEquals("glacial-frost-bloom", bloom.name());
        assertEquals(CrystalDistribution.GOLDEN_ANGLE, bloom.distribution());
        assertEquals(1.8, bloom.baseRadius(), 0);
        assertEquals(6, bloom.shardCountMax());
        assertEquals("minecraft:blue_ice", bloom.baseBlock());
        assertEquals(-1, bloom.translate().y());
    }

    @Test
    public void aBiomeHoldsItsProceduralObjectsFromASnippet() {
        ProceduralObjectSettings objects = gson.fromJson(BIOME_SNIPPET, ProceduralObjectSettings.class);

        assertEquals(1, objects.formations().size());
        assertEquals(1, objects.crystals().size());
        assertEquals("glacial-iceberg", objects.formations().get(0).name());
        assertFalse(new ResolvedProceduralObjects(objects).isEmpty());
    }

    @Test
    public void everyKindPlacesWithItsSettingsAndMakesTheSamePlacementEachTime() {
        ResolvedProceduralObjects objects = new ResolvedProceduralObjects(gson.fromJson("{\"coral\": [{}], \"fungi\": [{}], \"ruins\": [{}], "
                + "\"formations\": [" + ICEBERG + "], \"crystals\": [" + FROST_BLOOM + "]}", ProceduralObjectSettings.class));

        List<IrisProceduralPlacement> placements = objects.getAllPlacements();
        assertEquals(5, placements.size());
        assertSame("one placeable per settings, kept", placements.get(0), objects.getAllPlacements().get(0));

        IrisProceduralPlacement iceberg = placements.get(1);
        assertEquals("glacial-iceberg", iceberg.getName());
        assertEquals(0.08, iceberg.getChance(), 0);
        IrisObjectPlacement placement = iceberg.asPlacement();
        assertEquals(ObjectPlaceMode.CENTER_HEIGHT, placement.getMode());
        assertEquals(1, placement.getSurfaceSupportBuffer());
        assertEquals(-4, placement.getTranslate().getY());
        assertEquals(45, placement.getRotation().getYAxis().interval(), 0);
        assertTrue(placement.getRotation().isEnabled());
        assertFalse(iceberg.isPlausible());
    }

    @Test
    public void emptyKindsHaveIrisDefaults() {
        Coral coral = gson.fromJson("{}", Coral.class);
        assertEquals("coral", coral.name());
        assertEquals(0.4, coral.chance(), 0);
        assertEquals(CoralForm.BRANCHING, coral.form());
        assertEquals("minecraft:tube_coral_block", coral.block());
        assertTrue(coral.underwater());
        assertTrue(coral.tipBlock().isEmpty());
        assertTrue(coral.stiltSettings().isEmpty());

        assertEquals(ObjectPlaceMode.MIN_HEIGHT, gson.fromJson("{}", Ruin.class).mode());
        assertEquals("fungus", gson.fromJson("{}", Fungus.class).name());
        assertEquals(3, gson.fromJson("{}", Formation.class).surfaceSupportBuffer());
        assertEquals(TreeLeafMode.TRIMMED, TreeLeafMode.valueOf("TRIMMED"));
    }
}
