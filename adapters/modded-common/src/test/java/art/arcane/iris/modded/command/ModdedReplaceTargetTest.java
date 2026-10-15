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

package art.arcane.iris.modded.command;

import art.arcane.iris.world.IrisEnvironment;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class ModdedReplaceTargetTest {
    private static final String LEVEL_NAME = "world";

    @Test
    public void friendlyNamesResolveToTheVanillaSlots() {
        for (String overworld : List.of("main", "overworld", "MAIN", " Overworld ")) {
            assertTarget("minecraft:overworld", ModdedReplaceTarget.Kind.VANILLA_OVERWORLD, overworld);
        }
        assertTarget("minecraft:the_nether", ModdedReplaceTarget.Kind.VANILLA_NETHER, "nether");
        assertTarget("minecraft:the_nether", ModdedReplaceTarget.Kind.VANILLA_NETHER, "the_nether");
        assertTarget("minecraft:the_end", ModdedReplaceTarget.Kind.VANILLA_END, "end");
        assertTarget("minecraft:the_end", ModdedReplaceTarget.Kind.VANILLA_END, "the_end");
    }

    @Test
    public void configuredLevelNamesResolveToTheVanillaSlotsFirst() {
        assertTarget("minecraft:overworld", ModdedReplaceTarget.Kind.VANILLA_OVERWORLD, "world");
        assertTarget("minecraft:the_nether", ModdedReplaceTarget.Kind.VANILLA_NETHER, "world_nether");
        assertTarget("minecraft:the_end", ModdedReplaceTarget.Kind.VANILLA_END, "WORLD_the_end");
        assertEquals("minecraft:overworld", ModdedReplaceTarget.resolve("nether", "nether").levelKey());
    }

    @Test
    public void exactKeysKeepTheirNamespace() {
        assertTarget("minecraft:overworld", ModdedReplaceTarget.Kind.VANILLA_OVERWORLD, "minecraft:overworld");
        assertTarget("minecraft:the_end", ModdedReplaceTarget.Kind.VANILLA_END, "MINECRAFT:THE_END");
        assertTarget("examplemod:skylands", ModdedReplaceTarget.Kind.DIMENSION, "examplemod:skylands");
        assertTarget("examplemod:realm/upper", ModdedReplaceTarget.Kind.DIMENSION, "examplemod:realm/upper");
    }

    @Test
    public void otherBareNamesResolveToIrisWorlds() {
        assertTarget("irisworldgen:myworld", ModdedReplaceTarget.Kind.IRIS_WORLD, "myworld");
        assertTarget("irisworldgen:my_world", ModdedReplaceTarget.Kind.IRIS_WORLD, "My World");
        assertTarget("irisworldgen:myworld", ModdedReplaceTarget.Kind.IRIS_WORLD, "iris:myworld");
        assertTarget("irisworldgen:myworld", ModdedReplaceTarget.Kind.IRIS_WORLD, "irisworldgen:myworld");
    }

    @Test
    public void vanillaSlotsRequireTheirEnvironment() {
        assertEquals(IrisEnvironment.NORMAL, ModdedReplaceTarget.Kind.VANILLA_OVERWORLD.environment());
        assertEquals(IrisEnvironment.NETHER, ModdedReplaceTarget.Kind.VANILLA_NETHER.environment());
        assertEquals(IrisEnvironment.THE_END, ModdedReplaceTarget.Kind.VANILLA_END.environment());
        assertNull(ModdedReplaceTarget.Kind.IRIS_WORLD.environment());
        assertNull(ModdedReplaceTarget.Kind.DIMENSION.environment());

        ModdedReplaceTarget.requireCompatibleEnvironment(ModdedReplaceTarget.Kind.VANILLA_NETHER, IrisEnvironment.NETHER);
        ModdedReplaceTarget.requireCompatibleEnvironment(ModdedReplaceTarget.Kind.DIMENSION, IrisEnvironment.THE_END);
        IllegalArgumentException mismatch = assertThrows(IllegalArgumentException.class,
                () -> ModdedReplaceTarget.requireCompatibleEnvironment(
                        ModdedReplaceTarget.Kind.VANILLA_NETHER, IrisEnvironment.NORMAL));
        assertEquals("The vanilla_nether slot requires a pack environment of NETHER.", mismatch.getMessage());
    }

    @Test
    public void unsafeOrMalformedTargetsAreRejected() {
        for (String target : List.of("", "  ", "../world", "a/b", "a\\b", "ns:", ":x", "a:b:c", "bad key:x",
                "examplemod:sky/../x", "examplemod:/sky")) {
            assertThrows(target, IllegalArgumentException.class, () -> ModdedReplaceTarget.resolve(target, LEVEL_NAME));
        }
    }

    private static void assertTarget(String levelKey, ModdedReplaceTarget.Kind kind, String requested) {
        ModdedReplaceTarget target = ModdedReplaceTarget.resolve(requested, LEVEL_NAME);
        assertEquals(requested, levelKey, target.levelKey());
        assertEquals(requested, kind, target.kind());
    }
}
