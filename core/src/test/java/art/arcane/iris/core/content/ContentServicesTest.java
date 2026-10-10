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

package art.arcane.iris.core.content;

import art.arcane.iris.builtin.loot.LootTable;
import art.arcane.iris.builtin.spawn.EntityDefinition;
import art.arcane.iris.builtin.spawn.SpawnMarker;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.ModuleResourceFixture;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineGeneration;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.loot.LootModule;
import art.arcane.iris.engine.loot.LootResources;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.engine.object.world.IrisWorld;
import art.arcane.iris.engine.spawn.SpawnModule;
import art.arcane.iris.engine.spawn.SpawnResources;
import art.arcane.iris.spi.PlatformWorld;
import com.volmit.item.ItemRegistry;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The answers of the loot and spawn modules reach adapters that cannot name IrisApi as host-typed values. */
public class ContentServicesTest {
    private Engine engine;

    @BeforeClass
    public static void bindPlatform() {
        UsableBlockPlatform.bind();
    }

    @AfterClass
    public static void unbindPlatform() {
        UsableBlockPlatform.unbind();
    }

    @Before
    public void buildEngine() {
        engine = mock(Engine.class);
        IrisData data = mock(IrisData.class);
        ModuleResourceFixture fixture = ModuleResourceFixture.serve(data);
        ModuleResourceFixture.serveModules(data, new SpawnModule(), new LootModule());
        ItemRegistry items = mock(ItemRegistry.class);
        when(items.maxDurability(anyString())).thenReturn(OptionalInt.of(0));
        when(data.getPackResources().items()).thenReturn(items);
        when(engine.getData()).thenReturn(data);
        IrisWorld engineWorld = mock(IrisWorld.class);
        when(engineWorld.platformWorld()).thenReturn(mock(PlatformWorld.class));
        when(engine.getWorld()).thenReturn(engineWorld);
        SeedManager seeds = mock(SeedManager.class);
        when(seeds.getEntity()).thenReturn(11L);
        when(seeds.getLoot()).thenReturn(5L);
        when(engine.getSeedManager()).thenReturn(seeds);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        fixture.resource(SpawnResources.ENTITIES, "guard", TestSettings.of(EntityDefinition.class, """
                {"type": "minecraft:zombie", "customName": "Guard", "baby": true, "glowing": true,
                 "helmet": {"type": "iron_helmet", "enchantments": [{"enchantment": "protection", "minLevel": 3, "maxLevel": 3}]},
                 "loot": {"tables": ["guard/drops"]},
                 "rawCommands": [{"commands": ["say hi {x}"], "delay": 4, "repeat": true, "repeatDelay": 20}]}"""));
        fixture.resource(SpawnResources.MARKERS, "camp", TestSettings.of(SpawnMarker.class,
                "{\"spawners\": [], \"emptyAbove\": false, \"removeOnChange\": false}"));
        fixture.resource(LootResources.TABLES, "guard/drops", TestSettings.of(LootTable.class,
                "{\"name\": \"Drops\", \"minPicked\": 1, \"maxPicked\": 1, \"loot\": [{\"type\": \"bread\", \"minAmount\": 3, \"maxAmount\": 3}]}"));
    }

    @Test
    public void anEntityKeepsEveryDecisionTheModuleMade() {
        ContentEntity guard = ContentServices.entity(engine, "guard").orElseThrow();

        assertEquals("minecraft:zombie", guard.type());
        assertEquals("Guard", guard.customName());
        assertTrue(guard.flags().baby());
        assertTrue(guard.flags().glowing());
        assertFalse(guard.isSpecial());
        assertEquals("iron_helmet", guard.equipment().helmet().item());
        assertEquals(List.of(new ContentItem.Enchantment("protection", 3)), guard.equipment().helmet().enchantments());
        assertEquals(List.of("guard/drops"), guard.lootTables());
        assertEquals(List.of(new ContentEntity.Command(List.of("say hi {x}"), 4, true, 20)), guard.commands());
        assertTrue(ContentServices.entity(engine, "nobody").isEmpty());
    }

    @Test
    public void aMarkerRuleIsTheModulesAnswer() {
        ContentSpawns.MarkerRule rule = ContentServices.markerRule(engine, "camp").orElseThrow();

        assertEquals(List.of(), rule.spawners());
        assertFalse(rule.emptyAbove());
        assertFalse(rule.removeOnChange());
        assertTrue(ContentServices.markerRule(engine, "unknown").isEmpty());
    }

    @Test
    public void aTablesItemsAreDrawnByTheModuleAtTheSpot() {
        List<ContentItem> items = ContentServices.tableItems(engine, 10, 64, -3, "guard/drops");

        assertEquals(1, items.size());
        assertEquals("bread", items.getFirst().item());
        assertEquals(3, items.getFirst().amount());
        assertEquals(items, ContentServices.tableItems(engine, 10, 64, -3, "guard/drops"));
        assertTrue(ContentServices.tableItems(engine, 10, 64, -3, "missing").isEmpty());
    }
}
