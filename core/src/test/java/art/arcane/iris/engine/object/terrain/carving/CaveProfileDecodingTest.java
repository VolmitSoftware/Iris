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

package art.arcane.iris.engine.object.terrain.carving;

import art.arcane.iris.builtin.anchor.ObjectPlaceMode;
import art.arcane.iris.builtin.carve.CaveAnchorMode;
import art.arcane.iris.builtin.carve.CaveProfile;
import art.arcane.iris.builtin.carve.CarvingMode;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * The cave profile of the overworld pack's dimension, read into {@link CaveProfile} (IrisApi types only) with the values
 * Iris's own cave profile class read from the same JSON, and what the carvers read it as ({@link ResolvedCaveProfile}).
 */
public class CaveProfileDecodingTest {
    private static final String OVERWORLD_CAVE_PROFILE = """
            {"enabled": true, "verticalRange": {"min": 6, "max": 700},
             "baseDensityStyle": {"style": "PERLIN_IRIS", "zoom": 0.72}, "detailDensityStyle": {"style": "SIMPLEX", "zoom": 0.54},
             "warpStyle": {"style": "FRACTAL_WATER", "zoom": 0.5}, "baseWeight": 0.9, "detailWeight": 0.11, "warpStrength": 0.24,
             "densityThreshold": {"min": -0.14, "max": -0.06, "style": {"style": "SIMPLEX", "zoom": 0.74}},
             "thresholdBias": 0.14, "sampleStep": 3, "surfaceClearance": 5, "allowSurfaceBreak": true,
             "surfaceBreakStyle": {"style": "SIMPLEX", "zoom": 0.88}, "surfaceBreakNoiseThreshold": 0.6, "surfaceBreakDepth": 16,
             "surfaceBreakThresholdBoost": 0.1, "objectMinDepthBelowSurface": 14, "defaultObjectAnchor": "FLOOR",
             "defaultObjectPlaceMode": "ORGANIC_STILT", "anchorScanStep": 1, "anchorSearchAttempts": 12, "allowFluid": true,
             "fluidMinDepthBelowSurface": 20, "fluidRequiresFloor": true, "allowLava": true,
             "modules": [{"style": {"style": "SIMPLEX_VASCULAR", "zoom": 1.08}, "weight": 0.08, "threshold": 0.03,
                          "verticalRange": {"min": 24, "max": 660}, "invert": false},
                         {"style": {"style": "FRACTAL_FBM_SIMPLEX", "zoom": 0.24}, "weight": 0.04, "threshold": 0.08,
                          "verticalRange": {"min": 6, "max": 240}, "invert": true}],
             "verticalEdgeFade": 24, "verticalEdgeFadeStrength": 0.18, "minCarveCells": 24, "recoveryThresholdBoost": 0.07}
            """;
    private static final String DARK_DEPTHS_OBJECT = """
            {"place": ["rocks/a"], "chance": 0.235719, "density": 4, "carvingSupport": "CARVING_ONLY",
             "caveAnchorMode": "PROFILE_DEFAULT", "translate": {"y": -1}, "bottom": true, "force": true}
            """;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Gson gson;

    @Before
    public void decodeLikeIrisDoes() throws IOException {
        File snippets = folder.newFolder("snippet", "cave-profile");
        Files.writeString(new File(snippets, "lush.json").toPath(), "{\"enabled\": true, \"sampleStep\": 2}");
        IrisData data = mock(IrisData.class, CALLS_REAL_METHODS);
        doReturn(folder.getRoot()).when(data).getDataFolder();
        gson = new GsonBuilder().registerTypeAdapterFactory(data).create();
    }

    @Test
    public void theOverworldProfileReadsEveryFieldIrisReadFromIt() {
        CaveProfile profile = gson.fromJson(OVERWORLD_CAVE_PROFILE, CaveProfile.class);

        assertTrue(profile.enabled());
        assertEquals(6, profile.verticalRange().min(), 0);
        assertEquals(700, profile.verticalRange().max(), 0);
        assertEquals("PERLIN_IRIS", profile.baseDensityStyle().style());
        assertEquals(0.72, profile.baseDensityStyle().zoom(), 0);
        assertEquals("FRACTAL_WATER", profile.warpStyle().style());
        assertEquals(0.9, profile.baseWeight(), 0);
        assertEquals(0.24, profile.warpStrength(), 0);
        assertEquals(-0.14, profile.densityThreshold().min(), 0);
        assertEquals("SIMPLEX", profile.densityThreshold().style().style());
        assertEquals(3, profile.sampleStep());
        assertEquals(CaveAnchorMode.FLOOR, profile.defaultObjectAnchor());
        assertEquals(ObjectPlaceMode.ORGANIC_STILT, profile.defaultObjectPlaceMode().orElseThrow());
        assertEquals(12, profile.anchorSearchAttempts());
        assertEquals(24, profile.verticalEdgeFade());
        assertEquals(2, profile.modules().size());
        assertEquals("SIMPLEX_VASCULAR", profile.modules().get(0).style().style());
        assertFalse(profile.modules().get(0).invert());
        assertTrue(profile.modules().get(1).invert());
        assertEquals(240, profile.modules().get(1).verticalRange().max(), 0);
    }

    @Test
    public void anEmptyProfileHasIrisDefaults() {
        CaveProfile profile = gson.fromJson("{}", CaveProfile.class);

        assertFalse(profile.enabled());
        assertEquals(0, profile.verticalRange().min(), 0);
        assertEquals(384, profile.verticalRange().max(), 0);
        assertEquals("CELLULAR_IRIS_DOUBLE", profile.baseDensityStyle().style());
        assertEquals("SIMPLEX", profile.detailDensityStyle().style());
        assertEquals("FLAT", profile.warpStyle().style());
        assertEquals(0.08, profile.surfaceBreakStyle().zoom(), 0);
        assertEquals(-0.2, profile.densityThreshold().min(), 0);
        assertEquals(0.2, profile.densityThreshold().max(), 0);
        assertEquals(0.35, profile.detailWeight(), 0);
        assertEquals(0.16, profile.thresholdBias(), 0);
        assertEquals(20, profile.verticalEdgeFade());
        assertEquals(CaveAnchorMode.FLOOR, profile.defaultObjectAnchor());
        assertTrue(profile.defaultObjectPlaceMode().isEmpty());
        assertTrue(profile.modules().isEmpty());
        assertTrue(profile.allowFluid() && profile.fluidRequiresFloor() && profile.allowLava() && profile.adaptiveSampling());
        assertEquals(12, profile.fluidMinDepthBelowSurface());
        assertEquals(6, profile.anchorSearchAttempts());
    }

    @Test
    public void profilesMayBeSnippets() {
        CaveProfile profile = gson.fromJson("\"snippet/cave-profile/lush\"", CaveProfile.class);

        assertTrue(profile.enabled());
        assertEquals(2, profile.sampleStep());
    }

    @Test
    public void placementsReadTheirCarvingSupportAndAnchorMode() {
        ObjectPlacement placement = gson.fromJson(DARK_DEPTHS_OBJECT, ObjectPlacement.class);

        assertEquals(CarvingMode.CARVING_ONLY, placement.carvingSupport());
        assertTrue(placement.carvingSupport().supportsCarving());
        assertFalse(placement.carvingSupport().supportsSurface());
        assertEquals(CaveAnchorMode.PROFILE_DEFAULT, placement.caveAnchorMode());
        assertEquals(CarvingMode.SURFACE_ONLY, gson.fromJson("{}", ObjectPlacement.class).carvingSupport());
    }

    @Test
    public void carversReadTheProfileThroughItsApiValues() {
        CaveProfile profile = gson.fromJson(OVERWORLD_CAVE_PROFILE, CaveProfile.class);

        assertEquals(6, profile.verticalRange().min(), 0);
        assertEquals(700, profile.verticalRange().max(), 0);
        assertEquals("PERLIN_IRIS", profile.baseDensityStyle().style());
        assertEquals(0.72, profile.baseDensityStyle().zoom(), 0);
        assertEquals("FRACTAL_WATER", profile.warpStyle().style());
        assertEquals(0.88, profile.surfaceBreakStyle().zoom(), 0);
        assertEquals(-0.06, profile.densityThreshold().max(), 0);
        assertEquals("SIMPLEX", profile.densityThreshold().style().style());
        assertEquals(2, profile.modules().size());
        assertEquals(0.08, profile.modules().get(0).weight(), 0);
        assertEquals(24, profile.modules().get(0).verticalRange().min(), 0);
        assertTrue(profile.modules().get(1).invert());
    }
}
