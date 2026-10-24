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

package art.arcane.iris.engine.loot;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.BuiltInPack;
import art.arcane.iris.core.modules.ModuleResourceFixture;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineGeneration;
import art.arcane.iris.builtin.loot.LootSettings;
import art.arcane.iris.builtin.loot.LootTable;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.volmlib.util.math.RNG;
import com.google.gson.Gson;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Which loot tables a spot draws from, in which order and how often, for the loot references of its dimension, region and
 * biomes, as written in pack JSON. Taken before the references became settings of the loot module.
 */
public class LootEnvironmentPinTest {
    private static final int X = 10;
    private static final int Z = 20;
    private static final int SURFACE_Y = 70;
    private static final int CAVE_Y = 40;
    private static final int WORLD_HEIGHT = 256;

    private Engine engine;
    private IrisDimension dimension;
    private IrisRegion region;
    private IrisBiome surface;
    private IrisBiome cave;

    @Before
    public void buildTheWorld() {
        engine = mock(Engine.class, RETURNS_DEEP_STUBS);
        IrisData data = mock(IrisData.class);
        ModuleResourceFixture fixture = ModuleResourceFixture.serve(data);
        ModuleResourceFixture.serveColumnSettings(data);
        for (String key : List.of("a", "b", "c", "d", "e")) {
            fixture.resource(LootResources.TABLES, key, table(key));
        }
        IrisComplex complex = engine.getComplex();
        when(engine.getData()).thenReturn(data);
        when(engine.getHeight()).thenReturn(WORLD_HEIGHT);
        dimension = mock(IrisDimension.class);
        region = mock(IrisRegion.class);
        surface = biome("surface");
        cave = biome("cave");
        fixture.biome(cave);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getRegion(X, Z)).thenReturn(region);
        when(engine.getSurfaceBiome(X, Z)).thenReturn(surface);
        when(complex.getHeightStream().get(X, Z)).thenReturn(64D);
        when(engine.getCaveBiome(X, CAVE_Y, Z)).thenReturn(cave);
        when(engine.getCaveBiome(X, SURFACE_Y, Z)).thenReturn(surface);
    }

    @Test
    public void everyLevelAddsItsTablesInOrderAndTheMultipliersScaleTheCount() {
        references("{\"tables\": [\"a\"]}", "{\"tables\": [\"b\", \"c\"], \"multiplier\": 2}", "{\"tables\": [\"d\"], \"multiplier\": 1.5}", "{}");

        assertEquals(List.of("a", "b", "c", "d", "c", "d", "a", "b", "d", "c", "a", "b"), identities(false, SURFACE_Y));
    }

    @Test
    public void fallbackReferencesStepAsideForTablesThatAnObjectDefined() {
        references("{\"mode\": \"FALLBACK\", \"tables\": [\"a\"]}", "{\"tables\": [\"b\"]}", "{\"mode\": \"FALLBACK\", \"tables\": [\"c\"]}", "{}");

        assertEquals(List.of("b"), identities(true, SURFACE_Y));
        assertEquals(List.of("a", "b", "c"), identities(false, SURFACE_Y));
    }

    @Test
    public void replaceAndClearDropWhatCameBefore() {
        references("{\"tables\": [\"a\"]}", "{\"mode\": \"REPLACE\", \"tables\": [\"b\"]}", "{\"tables\": [\"d\"]}", "{}");
        assertEquals(List.of("b", "d"), identities(false, SURFACE_Y));

        references("{\"tables\": [\"a\"]}", "{\"tables\": [\"b\"]}", "{\"mode\": \"CLEAR\", \"tables\": [\"d\"]}", "{}");
        assertEquals(List.of(), identities(false, SURFACE_Y));
    }

    @Test
    public void belowTheSurfaceTheCaveBiomeAddsItsTablesToo() {
        references("{\"tables\": [\"a\"]}", "{}", "{\"tables\": [\"b\"]}", "{\"tables\": [\"e\"], \"multiplier\": 3}");

        assertEquals(List.of("a", "b", "e", "e", "b", "a", "e", "a", "b"), identities(false, CAVE_Y));
    }

    private List<String> identities(boolean objectLootDefined, int y) {
        List<String> sources = new ArrayList<>();
        LootTables.resolveEnvironmentSources(sources, new EngineGeneration(engine).world(), new RNG(19L), X, y, Z, objectLootDefined, key -> key);
        return sources;
    }

    private void references(String dimensionJson, String regionJson, String surfaceJson, String caveJson) {
        when(dimension.getModuleSettings()).thenReturn(BuiltInPack.settings(loot(dimensionJson)));
        when(region.getModuleSettings()).thenReturn(BuiltInPack.settings(loot(regionJson)));
        when(surface.getModuleSettings()).thenReturn(BuiltInPack.settings(loot(surfaceJson)));
        when(cave.getModuleSettings()).thenReturn(BuiltInPack.settings(loot(caveJson)));
    }

    private static LootSettings loot(String json) {
        return new Gson().fromJson(json, LootSettings.class);
    }

    private static IrisBiome biome(String key) {
        IrisBiome biome = mock(IrisBiome.class);
        when(biome.getLoadKey()).thenReturn(key);
        return biome;
    }

    private static LootTable table(String key) {
        return new Gson().fromJson("{\"name\": \"" + key + "\"}", LootTable.class);
    }
}
