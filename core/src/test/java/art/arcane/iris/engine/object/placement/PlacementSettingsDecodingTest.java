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

package art.arcane.iris.engine.object.placement;

import art.arcane.iris.builtin.blocks.ObjectReplace;
import art.arcane.iris.builtin.loot.ObjectLoot;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.loot.ObjectVanillaLoot;
import art.arcane.iris.builtin.spawn.PlacementMarker;
import art.arcane.iris.builtin.prepare.AxisRotationClamp;
import art.arcane.iris.builtin.prepare.SlopeClip;
import art.arcane.iris.builtin.stilt.StiltSettings;
import art.arcane.iris.builtin.vacuum.VacuumSettings;
import art.arcane.iris.builtin.validate.ObjectLimit;
import art.arcane.iris.core.loader.IrisData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.volmit.block.BlockSpec;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * The placement settings packs have always written inside an object placement, read into the settings classes of the
 * built-in modules (IrisApi types only) with the values Iris's own classes read from the same JSON. The JSON is the
 * overworld pack's.
 */
public class PlacementSettingsDecodingTest {
    private static final String MANGROVE = """
            {"chance": 0.45, "waterloggable": true, "clamp": {"maximumHeight": 225}, "density": 3,
             "place": ["trees/mangrove/mangrove1"]}
            """;
    private static final String CLAMPED_FROM_GROUND = """
            {"place": ["clutter/stonedirtsetter7"], "clamp": {"minimumHeight": 0}}
            """;
    private static final String CENTER_STILT = """
            {"mode": "CENTER_STILT", "stiltSettings": {"yMax": 4, "yRand": 1},
             "rotation": {"yAxis": {"min": 0, "max": 270, "interval": 90, "enabled": true}}}
            """;
    private static final String ORGANIC_STILT = """
            {"mode": "ORGANIC_STILT", "stiltSettings": {"organicScratch": 1, "organicJitter": 6, "enabled": true, "minHeight": 1, "maxHeight": 3}}
            """;
    private static final String STILT_WITH_PALETTE = """
            {"mode": "STILT", "stiltSettings": {"overStilt": 2, "organicMaxScan": 12,
             "palette": {"style": {"style": "PERLIN", "zoom": 0.2}, "zoom": 3,
                         "palette": [{"block": "minecraft:oak_log", "data": {"axis": "y"}, "weight": 2}, {"block": "minecraft:stone"}]}}}
            """;
    private static final String VACUUM = """
            {"mode": "VACUUM_WAVY", "vacuumSettings": {"radius": 24, "falloff": 1.5, "organicJitter": 2, "waveAmplitude": 5, "waveScale": 3.5}}
            """;
    private static final String STEEP_ONLY = """
            {"place": ["rocks/spire"], "slopeCondition": {"minimumSlope": 2, "maximumSlope": 3.5}}
            """;
    private static final String SPRUCE_LEAVES_TO_PERLIN_LEAVES = """
            {"place": ["trees/spruce/vgeneric1"], "snow": 0.5, "edit": [
              {"find": [{"block": "minecraft:dark_oak_fence"}, {"block": "minecraft:dark_oak_fence_gate"}],
               "replace": {"palette": [{"block": "minecraft:spruce_leaves"}]}},
              {"find": [{"block": "minecraft:spruce_leaves"}], "exact": true, "chance": 0.5,
               "replace": {"style": {"style": "PERLIN", "zoom": 0.2},
                           "palette": [{"block": "minecraft:oak_leaves"}, {"block": "minecraft:birch_leaves", "weight": 3}]}},
              "snippet/object-block-replacer/leaves", {}
            ]}
            """;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static final String LOOT_AND_MARKERS = """
            {"place": ["huts/camp"], "overrideGlobalLoot": true,
             "loot": [{"name": "camp/chest", "weight": 3, "exact": true, "filter": [{"block": "chest", "data": {"facing": "north"}}]},
                      {"name": "camp/barrel"}],
             "vanillaLoot": [{"name": "chests/simple_dungeon", "weight": 2, "filter": [{"block": "minecraft:barrel"}]}],
             "markers": [{"marker": "camp", "maximumMarkers": 4, "exact": true, "mark": [{"block": "campfire", "data": {"lit": true}}]}]}
            """;

    private Gson gson;

    @Before
    public void decodeLikeIrisDoes() throws IOException {
        File snippets = folder.newFolder("snippet", "object-block-replacer");
        Files.writeString(new File(snippets, "leaves.json").toPath(),
                "{\"find\": [{\"block\": \"minecraft:oak_leaves\"}], \"replace\": \"snippet/palette/autumn\"}");
        File palettes = folder.newFolder("snippet", "palette");
        Files.writeString(new File(palettes, "autumn.json").toPath(), "{\"palette\": [{\"block\": \"minecraft:red_terracotta\"}]}");
        IrisData data = mock(IrisData.class, CALLS_REAL_METHODS);
        doReturn(folder.getRoot()).when(data).getDataFolder();
        gson = new GsonBuilder().registerTypeAdapterFactory(data).create();
    }

    private ObjectPlacement placement(String json) {
        return gson.fromJson(json, ObjectPlacement.class);
    }

    @Test
    public void clampReadsTheHeightsThePackSetAndKeepsIrisDefaultsForTheRest() {
        ObjectLimit mangrove = placement(MANGROVE).clamp();
        assertEquals(-2048, mangrove.minimumHeight());
        assertEquals(225, mangrove.maximumHeight());
        assertTrue(mangrove.canPlace(225, 100));
        assertFalse(mangrove.canPlace(226, 100));

        ObjectLimit fromGround = placement(CLAMPED_FROM_GROUND).clamp();
        assertEquals(0, fromGround.minimumHeight());
        assertEquals(2048, fromGround.maximumHeight());
        assertFalse(fromGround.canPlace(10, -1));

        ObjectLimit untouched = placement("{}").clamp();
        assertEquals(-2048, untouched.minimumHeight());
        assertEquals(2048, untouched.maximumHeight());
    }

    @Test
    public void slopeConditionReadsItsBoundsAndNoConditionPassesEverySlope() {
        SlopeClip steep = placement(STEEP_ONLY).slopeCondition();
        assertEquals(2, steep.minimumSlope(), 0);
        assertEquals(3.5, steep.maximumSlope(), 0);
        assertFalse(steep.isDefault());
        assertTrue(steep.isValid(3));
        assertFalse(steep.isValid(1.9));
        assertFalse(steep.isValid(3.6));

        SlopeClip none = placement("{}").slopeCondition();
        assertTrue(none.isDefault());
        assertTrue(none.isValid(9999));
    }

    @Test
    public void stiltSettingsReadTheirFieldsAndIgnoreWhatTheyNeverHad() {
        StiltSettings center = placement(CENTER_STILT).stiltSettings().orElseThrow();
        assertEquals(4, center.yMax());
        assertEquals(1, center.yRand());
        assertEquals(0, center.overStilt());
        assertTrue(center.palette().isEmpty());
        assertEquals(48, center.organicMaxScan());
        assertEquals(3, center.organicJitter());
        assertEquals(0.55, center.organicScratch(), 0);

        StiltSettings organic = placement(ORGANIC_STILT).stiltSettings().orElseThrow();
        assertEquals(1, organic.organicScratch(), 0);
        assertEquals(6, organic.organicJitter());

        assertTrue(placement("{}").stiltSettings().isEmpty());
    }

    @Test
    public void stiltPaletteIsAPaletteSpecWithItsStyleZoomAndWeightedBlocks() {
        StiltSettings stilt = placement(STILT_WITH_PALETTE).stiltSettings().orElseThrow();

        assertEquals(2, stilt.overStilt());
        assertEquals(12, stilt.organicMaxScan());
        var palette = stilt.palette().orElseThrow();
        assertEquals("PERLIN", palette.style().style());
        assertEquals(3, palette.zoom(), 0);
        assertEquals(2, palette.palette().get(0).weight());
        assertEquals("y", palette.palette().get(0).data().get("axis"));
        assertEquals("minecraft:stone", palette.palette().get(1).block());
    }

    @Test
    public void vacuumSettingsReadEveryField() {
        VacuumSettings vacuum = placement(VACUUM).vacuumSettings().orElseThrow();
        assertEquals(24, vacuum.radius());
        assertEquals(1.5, vacuum.falloff(), 0);
        assertEquals(2, vacuum.organicJitter());
        assertEquals(5, vacuum.waveAmplitude());
        assertEquals(3.5, vacuum.waveScale(), 0);

        VacuumSettings defaults = gson.fromJson("{}", VacuumSettings.class);
        assertEquals(0, defaults.radius());
        assertEquals(2.0, defaults.falloff(), 0);
        assertEquals(4, defaults.organicJitter());
        assertEquals(3, defaults.waveAmplitude());
        assertEquals(5.0, defaults.waveScale(), 0);
    }

    @Test
    public void editsReadTheirFindListPaletteExactnessAndChance() {
        List<ObjectReplace> edits = placement(SPRUCE_LEAVES_TO_PERLIN_LEAVES).edit();

        ObjectReplace fences = edits.get(0);
        assertEquals(List.of("minecraft:dark_oak_fence", "minecraft:dark_oak_fence_gate"), fences.find().stream().map(BlockSpec::block).toList());
        assertEquals("minecraft:spruce_leaves", fences.replace().palette().get(0).block());
        assertFalse(fences.exact());
        assertEquals(1F, fences.chance(), 0);
        assertEquals("STATIC", fences.replace().style().style());

        ObjectReplace leaves = edits.get(1);
        assertTrue(leaves.exact());
        assertEquals(0.5F, leaves.chance(), 0);
        assertEquals("PERLIN", leaves.replace().style().style());
        assertEquals(3, leaves.replace().palette().get(1).weight());
    }

    @Test
    public void editsMayBeSnippetsWhoseFieldsMayBeSnippetsToo() {
        ObjectReplace snippet = placement(SPRUCE_LEAVES_TO_PERLIN_LEAVES).edit().get(2);

        assertNotNull(snippet);
        assertEquals("minecraft:oak_leaves", snippet.find().get(0).block());
        assertEquals("minecraft:red_terracotta", snippet.replace().palette().get(0).block());

        ObjectReplace empty = placement(SPRUCE_LEAVES_TO_PERLIN_LEAVES).edit().get(3);
        assertTrue(empty.find().isEmpty());
        assertEquals("stone", empty.replace().palette().get(0).block());
    }

    @Test
    public void rotationAxesReadTheirAnglesAndStepsAndTheYAxisIsOnByDefault() {
        AxisRotationClamp yAxis = placement(CENTER_STILT).rotation().yAxis();
        assertTrue(yAxis.enabled());
        assertEquals(0, yAxis.min(), 0);
        assertEquals(270, yAxis.max(), 0);
        assertEquals(90, yAxis.interval(), 0);

        AxisRotationClamp defaults = placement("{}").rotation().yAxis();
        assertTrue(defaults.enabled());
        assertEquals(90, defaults.interval(), 0);
        assertFalse(placement("{}").rotation().xAxis().enabled());
    }

    @Test
    public void lootAndMarkersReadTheFieldsTheyAlwaysHad() {
        ObjectPlacement placement = placement(LOOT_AND_MARKERS);

        assertTrue(placement.overrideGlobalLoot());
        ObjectLoot chest = placement.loot().get(0);
        assertEquals("camp/chest", chest.name());
        assertEquals(3, chest.weight());
        assertTrue(chest.exact());
        assertEquals("chest", chest.filter().getFirst().block());
        assertEquals("north", chest.filter().getFirst().data().get("facing"));
        ObjectLoot barrel = placement.loot().get(1);
        assertEquals(1, barrel.weight());
        assertFalse(barrel.exact());
        assertTrue(barrel.filter().isEmpty());

        ObjectVanillaLoot dungeon = placement.vanillaLoot().getFirst();
        assertEquals("chests/simple_dungeon", dungeon.name());
        assertEquals(2, dungeon.weight());
        assertEquals("minecraft:barrel", dungeon.filter().getFirst().block());

        PlacementMarker marker = placement.markers().getFirst();
        assertEquals("camp", marker.marker());
        assertEquals(4, marker.maximumMarkers());
        assertTrue(marker.exact());
        assertEquals("true", marker.mark().getFirst().data().get("lit"));

        assertTrue(placement("{}").loot().isEmpty());
        assertEquals(8, new PlacementMarker().maximumMarkers());
    }
}
