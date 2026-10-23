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
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.builtin.loot.ObjectLoot;
import art.arcane.iris.builtin.loot.ObjectLootEntry;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.host.ModuleLogs;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockSpec;
import com.volmit.block.BlockState;
import com.volmit.resource.PackResources;
import com.volmit.service.Loot;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
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

    private PackResources resources;
    private PlacementLoot placement;

    @Before
    public void servePackTables() {
        IrisData data = mock(IrisData.class);
        ModuleResourceFixture fixture = ModuleResourceFixture.serve(data);
        for (String key : List.of("everywhere", "chests", "this-chest", "other")) {
            fixture.resource(LootResources.TABLES, key, TestSettings.of(LootTable.class, "{\"name\": \"" + key + "\"}"));
        }
        resources = data.getPackResources();
        BlockRegistry blocks = mock(BlockRegistry.class);
        when(resources.blocks()).thenReturn(blocks);
        when(blocks.resolve(any(BlockState.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(blocks.resolve(any(BlockSpec.class))).thenAnswer(invocation -> BlockState.parse(invocation.<BlockSpec>getArgument(0).block()));
        placement = new PlacementLoot(resources, () -> Set.of("minecraft:chests/simple_dungeon"), LOOT_SEED, ModuleLogs.discarding());
    }

    private Loot.Table table(String key) {
        return new Loot.Table(resources.idOf(key));
    }

    @Test
    public void aTableThatNamesTheExactStateBeatsOneThatNamesTheKindOfBlock() {
        List<ObjectLootEntry> pack = List.of(entry("everywhere", 1, false), entry("chests", 1, false, CHEST), entry("this-chest", 1, true, CHEST));

        assertEquals(table("this-chest"), pick(List.of(), pack, CHEST));
        assertEquals(table("chests"), pick(List.of(), pack, EAST_CHEST));
    }

    @Test
    public void aTableThatNamesNoBlockServesChestsNothingElseNames() {
        List<ObjectLootEntry> pack = List.of(entry("everywhere", 1, false), entry("other", 1, false, "minecraft:barrel"));

        assertEquals(table("everywhere"), pick(List.of(), pack, CHEST));
    }

    @Test
    public void exactEntriesDoNotApplyToOtherStatesAndNothingMatchingGivesNothing() {
        List<ObjectLootEntry> pack = List.of(entry("this-chest", 1, true, CHEST), entry("other", 1, false, "minecraft:barrel"));

        assertNull(pick(List.of(), pack, EAST_CHEST));
    }

    @Test
    public void weightsPickAmongEqualsAndZeroWeightsAreIgnored() {
        List<ObjectLootEntry> pack = List.of(entry("everywhere", 3, false), entry("chests", 1, false), entry("other", 0, false));
        int everywhere = 0;
        RNG rng = new RNG(5L);

        for (int i = 0; i < 4000; i++) {
            Loot.Source picked = placement.pick(List.of(), pack, BlockState.parse(CHEST), rng, 0, 64, 0);
            if (picked.equals(table("everywhere"))) {
                everywhere++;
            } else {
                assertEquals(table("chests"), picked);
            }
        }

        assertEquals(3000, everywhere, 150);
    }

    @Test
    public void aPlatformTableWithAnythingThePlatformHasIsASourceWithTheSeedOfItsIdentity() {
        List<ObjectLootEntry> platform = List.of(entry("chests/simple_dungeon", 1, false), entry("chests/missing", 1, false));

        Loot.Source picked = pick(platform, List.of(), CHEST);

        assertEquals(new Loot.PlatformTable("minecraft:chests/simple_dungeon",
                LootTables.seed(LOOT_SEED, "Vanilla minecraft:chests/simple_dungeon", 0, 64, 0)), picked);
    }

    @Test
    public void tablesTheWorldLacksAreSkipped() {
        assertNull(pick(List.of(entry("chests/missing", 1, false)), List.of(entry("not-a-table", 1, false)), CHEST));
    }

    private Loot.Source pick(List<ObjectLootEntry> platform, List<ObjectLootEntry> pack, String container) {
        return placement.pick(platform, pack, BlockState.parse(container), new RNG(1L), 0, 64, 0);
    }

    private static ObjectLootEntry entry(String name, int weight, boolean exact, String... filter) {
        String blocks = Arrays.stream(filter).map(key -> "{\"block\": \"" + key + "\"}").collect(Collectors.joining(", "));
        return TestSettings.of(ObjectLoot.class, "{\"name\": \"" + name + "\", \"weight\": " + weight + ", \"exact\": " + exact
                + ", \"filter\": [" + blocks + "]}");
    }
}
