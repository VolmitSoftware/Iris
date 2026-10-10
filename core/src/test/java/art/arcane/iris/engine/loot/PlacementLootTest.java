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

import art.arcane.iris.builtin.loot.LootTable;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.ModuleResourceFixture;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.engine.object.loot.IObjectLoot;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.host.ModuleLogs;
import com.volmit.service.Loot;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The loot table an object placement gives a chest: a table naming the chest's exact state beats one naming its kind of
 * block, which beats one naming no block; the weights pick among equals; platform tables count when the platform has them.
 */
public class PlacementLootTest {
    private static final String CHEST = "minecraft:chest[facing=north]";
    private static final String EAST_CHEST = "minecraft:chest[facing=east]";
    private static final long LOOT_SEED = 99L;

    private PlacementLoot placement;

    @BeforeClass
    public static void bindPlatform() {
        UsableBlockPlatform.bind();
    }

    @AfterClass
    public static void unbindPlatform() {
        UsableBlockPlatform.unbind();
    }

    @Before
    public void servePackTables() {
        IrisData data = mock(IrisData.class);
        ModuleResourceFixture fixture = ModuleResourceFixture.serve(data);
        for (String key : List.of("everywhere", "chests", "this-chest", "other")) {
            fixture.resource(LootResources.TABLES, key, TestSettings.of(LootTable.class, "{\"name\": \"" + key + "\"}"));
        }
        placement = new PlacementLoot(data, () -> Set.of("minecraft:chests/simple_dungeon"), LOOT_SEED, ModuleLogs.discarding());
    }

    @Test
    public void aTableThatNamesTheExactStateBeatsOneThatNamesTheKindOfBlock() {
        List<IObjectLoot> pack = List.of(entry("everywhere", 1, false), entry("chests", 1, false, CHEST), entry("this-chest", 1, true, CHEST));

        assertEquals(new Loot.Table("this-chest"), pick(List.of(), pack, CHEST));
        assertEquals(new Loot.Table("chests"), pick(List.of(), pack, EAST_CHEST));
    }

    @Test
    public void aTableThatNamesNoBlockServesChestsNothingElseNames() {
        List<IObjectLoot> pack = List.of(entry("everywhere", 1, false), entry("other", 1, false, "minecraft:barrel"));

        assertEquals(new Loot.Table("everywhere"), pick(List.of(), pack, CHEST));
    }

    @Test
    public void exactEntriesDoNotApplyToOtherStatesAndNothingMatchingGivesNothing() {
        List<IObjectLoot> pack = List.of(entry("this-chest", 1, true, CHEST), entry("other", 1, false, "minecraft:barrel"));

        assertNull(pick(List.of(), pack, EAST_CHEST));
    }

    @Test
    public void weightsPickAmongEqualsAndZeroWeightsAreIgnored() {
        List<IObjectLoot> pack = List.of(entry("everywhere", 3, false), entry("chests", 1, false), entry("other", 0, false));
        int everywhere = 0;
        RNG rng = new RNG(5L);

        for (int i = 0; i < 4000; i++) {
            Loot.Source picked = placement.pick(List.of(), pack, block(CHEST), rng, 0, 64, 0);
            if (picked.equals(new Loot.Table("everywhere"))) {
                everywhere++;
            } else {
                assertEquals(new Loot.Table("chests"), picked);
            }
        }

        assertEquals(3000, everywhere, 150);
    }

    @Test
    public void aPlatformTableWithAnythingThePlatformHasIsASourceWithTheSeedOfItsIdentity() {
        List<IObjectLoot> platform = List.of(entry("chests/simple_dungeon", 1, false), entry("chests/missing", 1, false));

        Loot.Source picked = pick(platform, List.of(), CHEST);

        assertEquals(new Loot.PlatformTable("minecraft:chests/simple_dungeon",
                LootTables.seed(LOOT_SEED, "Vanilla minecraft:chests/simple_dungeon", 0, 64, 0)), picked);
    }

    @Test
    public void tablesTheWorldLacksAreSkipped() {
        assertNull(pick(List.of(entry("chests/missing", 1, false)), List.of(entry("not-a-table", 1, false)), CHEST));
    }

    private Loot.Source pick(List<IObjectLoot> platform, List<IObjectLoot> pack, String container) {
        return placement.pick(platform, pack, block(container), new RNG(1L), 0, 64, 0);
    }

    private static PlatformBlockState block(String key) {
        return UsableBlockPlatform.block(key);
    }

    private static IObjectLoot entry(String name, int weight, boolean exact, String... filter) {
        IObjectLoot loot = mock(IObjectLoot.class);
        when(loot.getName()).thenReturn(name);
        when(loot.getWeight()).thenReturn(weight);
        when(loot.isExact()).thenReturn(exact);
        KList<PlatformBlockState> filterStates = new KList<>();
        for (String key : filter) {
            filterStates.add(block(key));
        }
        when(loot.getFilter(org.mockito.ArgumentMatchers.any())).thenReturn(filterStates);
        return loot;
    }
}
