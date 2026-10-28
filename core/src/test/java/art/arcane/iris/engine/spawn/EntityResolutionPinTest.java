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

package art.arcane.iris.engine.spawn;

import art.arcane.iris.engine.loot.LootItems;
import art.arcane.iris.builtin.spawn.EntityDefinition;
import com.volmit.world.RunningWorld.Sky;
import art.arcane.volmlib.util.math.RNG;
import com.google.gson.Gson;
import com.volmit.ModuleLog;
import com.volmit.entity.EntitySpec;
import com.volmit.host.ModuleLogs;
import com.volmit.item.ItemRegistry;
import com.volmit.resource.ContentKey;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import org.junit.Test;

import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What an entity of the pack becomes when it is made from a fixed stream, hashed. The number was taken before the entity
 * classes became settings of the spawn module; a change in it changes the equipment, attributes and passengers worlds spawn.
 * It was retaken once when absent values became {@code Optional}s and loot tables ids: the spec printed the old way (empty
 * texts as {@code ""}, other absent values as {@code null}, tables by key) still hashed to the old number, -819231818.
 * It was retaken again when the spec was grouped, its keys became ids and content keys and the Iris-only knobs an
 * extension: the spec printed the old way (flat fields, keys as written, operations by Bukkit name) still hashed to 1441930785.
 * It was retaken a third time when the five Iris-only knobs left the extension for the spec (the panda genes in the
 * appearance, the reason and rising out of the ground in the arrival, applying to a provided mob on the spec): the spec
 * printed the old way (those fields in an {@code IrisEntityExtras} extension, no new fields) still hashed to -847596166.
 */
public class EntityResolutionPinTest {
    private static final long STREAM_SEED = 4321L;
    private static final String ENTITY = """
            {"type": "minecraft:zombie", "customName": "&cPin", "aware": false, "glowing": true, "baby": true, "removable": true,
             "helmet": {"type": "iron_helmet", "rarity": 2, "minDurability": 0.2, "maxDurability": 0.9,
                        "enchantments": [{"enchantment": "protection", "minLevel": 1, "maxLevel": 4}]},
             "mainHand": {"type": "diamond_sword", "minAmount": 1, "maxAmount": 1,
                          "attributes": [{"attribute": "attack_damage", "name": "pin", "minAmount": 1, "maxAmount": 9}]},
             "offHand": {"type": "bread", "minAmount": 1, "maxAmount": 6},
             "attributes": [{"attribute": "max_health", "name": "tough", "minAmount": 10, "maxAmount": 40, "chance": 0.8},
                            {"attribute": "movement_speed", "name": "quick", "operation": "ADD_SCALAR", "minAmount": 0.1, "maxAmount": 0.3}],
             "loot": {"tables": ["zombie/drops", "zombie/rare"]},
             "passengers": [{"type": "minecraft:chicken", "baby": true, "helmet": {"type": "bread"}}],
             "leashHolder": {"type": "minecraft:bat", "attributes": [{"attribute": "max_health", "name": "tiny", "minAmount": 1, "maxAmount": 3}]},
             "spawnEffect": {"sound": "entity.zombie.ambient", "particleEffect": "SMOKE", "particleCount": 3, "interval": 1, "chance": 1},
             "spawnEffectRiseOutOfGround": true,
             "rawCommands": [{"commands": ["say hello {x}"], "delay": 5, "repeat": true, "repeatDelay": 40}]}
            """;

    @Test
    public void anEntityResolvesToThePinnedSpec() {
        ItemRegistry registry = mock(ItemRegistry.class);
        when(registry.maxDurability(any(ContentKey.class))).thenReturn(OptionalInt.of(0));
        when(registry.maxDurability(platformItem("iron_helmet"))).thenReturn(OptionalInt.of(165));
        when(registry.maxDurability(platformItem("diamond_sword"))).thenReturn(OptionalInt.of(1561));
        Optional<Sky> sky = Optional.of(new Sky(0L, false, false));
        EntityDefinition entity = new Gson().fromJson(ENTITY, EntityDefinition.class);

        PackResources resources = mock(PackResources.class);
        when(resources.idOf(anyString())).thenAnswer(invocation -> new ResourceId("test", invocation.getArgument(0)));

        ModuleLog log = ModuleLogs.discarding();
        EntitySpec spec = new EntityResolution(resources, new LootItems(registry, false, log), sky, new EffectTimers(), log)
                .resolve(entity, new RNG(STREAM_SEED)).orElseThrow();

        assertEquals(383685705, spec.toString().hashCode());
    }

    private static ContentKey platformItem(String name) {
        return new ContentKey.Registered(ResourceId.ofPlatformKey(name));
    }
}
