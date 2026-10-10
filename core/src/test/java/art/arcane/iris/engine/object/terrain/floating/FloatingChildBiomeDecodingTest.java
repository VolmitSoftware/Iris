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

package art.arcane.iris.engine.object.terrain.floating;

import art.arcane.iris.builtin.floating.FloatingBottomPaletteMode;
import art.arcane.iris.builtin.floating.FloatingChildBiome;
import art.arcane.iris.builtin.floating.OverrideMode;
import art.arcane.iris.builtin.floating.TopShapeMode;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/** The floating child of the overworld pack's magnetics/dunes biome, read into {@link FloatingChildBiome} with the values Iris's own class read from the same JSON. */
public class FloatingChildBiomeDecodingTest {
    private static final String DUNES_CHILD = """
            {"biome": "magnetics/dunes", "rarity": 1,
             "footprintStyle": {"style": "CELLULAR_IRIS", "zoom": 0.55, "fracture": {"style": "SIMPLEX", "zoom": 0.2, "multiplier": 4}},
             "footprintThreshold": 0.52, "pickerStyle": {"style": "SIMPLEX", "zoom": 4.0}, "altitudeStyle": {"style": "SIMPLEX", "zoom": 2.6},
             "minHeightAboveSurface": 90, "maxHeightAboveSurface": 130, "topShapeMode": "NOISE", "maxTopHeight": 16,
             "topShapeStyle": {"style": "FRACTAL_SMOKE", "zoom": 0.45, "fracture": {"style": "SIMPLEX", "zoom": 0.22, "multiplier": 4}},
             "topShapeAmp": 0.75, "edgeTaperWidth": 14, "edgeTaperExponent": 1.45,
             "edgeTaperVariationStyle": {"style": "SIMPLEX", "zoom": 0.18}, "edgeTaperVariationAmplitude": 4,
             "bottomStyle": {"style": "SIMPLEX_VASCULAR", "zoom": 0.28}, "bottomDepthMin": 5, "bottomDepthMax": 48, "bottomExponent": 2.35,
             "bottomPaletteMode": "CUSTOM",
             "bottomPalette": [{"minHeight": 2, "maxHeight": 5, "palette": [{"block": "minecraft:sandstone"}, {"block": "minecraft:smooth_sandstone"}]}],
             "maxThickness": 72, "wallWarpStyle": {"style": "SIMPLEX", "zoom": 0.24}, "wallWarpAmplitude": 5,
             "carving": "carving/sand-hollows-child", "carveStyle": {"style": "SIMPLEX", "zoom": 0.2}, "carveThreshold": 0.86,
             "inheritDecorators": true, "inheritObjects": false, "objectShrinkFactor": 0.55, "topObjectMode": "MERGE",
             "topObjectOverrides": [{"place": ["clutter/boulder1"], "chance": 0.55, "mode": "CENTER_HEIGHT", "density": 5},
                                    {"place": ["clutter/shrub1"], "chance": 0.42, "density": 4}],
             "bottomObjectMode": "REPLACE",
             "bottomObjectOverrides": [{"place": ["clutter/bone4"], "chance": 0.04, "mode": "FAST_MIN_HEIGHT", "density": 1}]}
            """;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Gson gson;

    @Before
    public void decodeLikeIrisDoes() {
        IrisData data = mock(IrisData.class, CALLS_REAL_METHODS);
        doReturn(folder.getRoot()).when(data).getDataFolder();
        gson = new GsonBuilder().registerTypeAdapterFactory(data).create();
    }

    @Test
    public void theMagneticsDunesChildReadsEveryFieldIrisReadFromIt() {
        FloatingChildBiome child = gson.fromJson(DUNES_CHILD, FloatingChildBiome.class);

        assertEquals("magnetics/dunes", child.biome());
        assertEquals("CELLULAR_IRIS", child.footprintStyle().style());
        assertEquals(0.55, child.footprintStyle().zoom(), 0);
        assertEquals("SIMPLEX", child.footprintStyle().fracture().orElseThrow().style());
        assertEquals(4, child.footprintStyle().fracture().orElseThrow().multiplier(), 0);
        assertEquals(0.52, child.footprintThreshold(), 0);
        assertEquals(4.0, child.pickerStyle().zoom(), 0);
        assertEquals(90, child.minHeightAboveSurface());
        assertEquals(TopShapeMode.NOISE, child.topShapeMode());
        assertEquals(14, child.edgeTaperWidth());
        assertEquals(1.45, child.edgeTaperExponent(), 0);
        assertEquals(4, child.edgeTaperVariationAmplitude(), 0);
        assertEquals(FloatingBottomPaletteMode.CUSTOM, child.bottomPaletteMode());
        assertEquals(1, child.bottomPalette().size());
        assertEquals(5, child.bottomPalette().get(0).maxHeight());
        assertEquals("SIMPLEX", child.wallWarpStyle().orElseThrow().style());
        assertEquals(5, child.wallWarpAmplitude(), 0);
        assertEquals("carving/sand-hollows-child", child.carving());
        assertEquals(0.86, child.carveThreshold(), 0);
        assertFalse(child.inheritObjects());
        assertEquals(0.55, child.objectShrinkFactor(), 0);
        assertEquals(OverrideMode.MERGE, child.topObjectMode());
        assertEquals(2, child.topObjectOverrides().size());
        assertEquals(5, child.topObjectOverrides().get(0).getDensity());
        assertEquals(OverrideMode.REPLACE, child.bottomObjectMode());
        assertEquals("clutter/bone4", child.bottomObjectOverrides().get(0).getPlace().get(0));
    }

    @Test
    public void anEmptyChildHasIrisDefaults() {
        FloatingChildBiome child = gson.fromJson("{}", FloatingChildBiome.class);

        assertEquals("", child.biome());
        assertEquals(1, child.rarity());
        assertEquals("SIMPLEX", child.footprintStyle().style());
        assertEquals(0.18, child.edgeTaperVariationStyle().zoom(), 0);
        assertEquals(0.5, child.footprintThreshold(), 0);
        assertEquals(10, child.edgeTaperWidth());
        assertEquals(160, child.minHeightAboveSurface());
        assertEquals(210, child.maxHeightAboveSurface());
        assertEquals(TopShapeMode.BIOME, child.topShapeMode());
        assertEquals(FloatingBottomPaletteMode.DEPTH, child.bottomPaletteMode());
        assertTrue(child.wallWarpStyle().isEmpty());
        assertTrue(child.carveStyle().isEmpty());
        assertTrue(child.minAbsoluteY().isEmpty());
        assertTrue(child.localFluidHeight().isEmpty());
        assertEquals("minecraft:water", child.fluidBlock());
        assertTrue(child.inheritDecorators() && child.inheritObjects());
        assertEquals(OverrideMode.INHERIT_ONLY, child.topObjectMode());
        assertEquals(96, child.maxThickness());
        assertEquals(1.0, child.carveThreshold(), 0);
        assertTrue(child.topObjectOverrides().isEmpty());
        assertNull(child.color().isEmpty() ? null : child.color());
    }

    @Test
    public void aBiomeHoldsItsChildrenAsSettings() {
        IrisBiome biome = gson.fromJson("{\"floatingChildBiomes\": [" + DUNES_CHILD + ", {}]}", IrisBiome.class);

        assertEquals(2, biome.getFloatingChildBiomes().size());
        assertEquals("magnetics/dunes", biome.getFloatingChildBiomes().get(0).biome());
        assertNotNull(biome.getFloatingChildBiomes().get(1));
    }

    @Test
    public void theEdgeTaperProfileKeepsTheSettingsWidthExponentAndVariation() {
        FloatingChildBiome child = gson.fromJson(DUNES_CHILD, FloatingChildBiome.class);
        ResolvedFloatingChild resolved = new ResolvedFloatingChild(child);

        FloatingIslandEdgeProfile profile = resolved.getEdgeTaperProfile(77L, mock(IrisData.class));

        assertEquals(14, profile.width());
        assertEquals(1.45, profile.exponent(), 0);
        assertEquals(4, profile.variationAmplitude(), 0);
        assertEquals(2, resolved.getRarity() + 1);
    }
}
