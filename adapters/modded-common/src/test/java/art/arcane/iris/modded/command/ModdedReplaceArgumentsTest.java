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

import org.junit.Test;

import java.util.List;
import java.util.OptionalLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ModdedReplaceArgumentsTest {
    @Test
    public void targetAloneUsesTheDefaultTypeAndPreservesTheSeed() {
        ModdedReplaceArguments arguments = ModdedReplaceArguments.parse("minecraft:overworld");

        assertEquals("minecraft:overworld", arguments.target());
        assertEquals("default", arguments.type());
        assertEquals(OptionalLong.empty(), arguments.seed());
        assertFalse(arguments.off());
    }

    @Test
    public void positionalArgumentsFillTargetTypeAndSeedInOrder() {
        ModdedReplaceArguments arguments = ModdedReplaceArguments.parse("main overworld 123456789");

        assertEquals("main", arguments.target());
        assertEquals("overworld", arguments.type());
        assertEquals(OptionalLong.of(123456789L), arguments.seed());
    }

    @Test
    public void keyedArgumentsAcceptThePaperNamesAndAliases() {
        ModdedReplaceArguments named = ModdedReplaceArguments.parse("target=examplemod:skylands type=skypack:sky seed=-5");
        ModdedReplaceArguments aliased = ModdedReplaceArguments.parse("world-name=myworld dimension=underworld s=7");
        ModdedReplaceArguments pack = ModdedReplaceArguments.parse("seed=1 pack=overworld:floating/sky target=nether");

        assertEquals("examplemod:skylands", named.target());
        assertEquals("skypack:sky", named.type());
        assertEquals(OptionalLong.of(-5L), named.seed());
        assertEquals("myworld", aliased.target());
        assertEquals("underworld", aliased.type());
        assertEquals(OptionalLong.of(7L), aliased.seed());
        assertEquals("nether", pack.target());
        assertEquals("overworld:floating/sky", pack.type());
        assertEquals(OptionalLong.of(1L), pack.seed());
    }

    @Test
    public void positionalTargetMixesWithKeyedTypeAndSeedLikeThePaperCommand() {
        ModdedReplaceArguments arguments = ModdedReplaceArguments.parse("minecraft:the_nether type=underworld seed=-987654321");

        assertEquals("minecraft:the_nether", arguments.target());
        assertEquals("underworld", arguments.type());
        assertEquals(OptionalLong.of(-987654321L), arguments.seed());
    }

    @Test
    public void preserveKeepsTheSavedSeedAndTheFullSignedRangeIsAccepted() {
        assertEquals(OptionalLong.empty(), ModdedReplaceArguments.parse("main seed=preserve").seed());
        assertEquals(OptionalLong.empty(), ModdedReplaceArguments.parse("main overworld PRESERVE").seed());
        assertEquals(OptionalLong.of(Long.MAX_VALUE),
                ModdedReplaceArguments.parse("main seed=" + Long.MAX_VALUE).seed());
        assertEquals(OptionalLong.of(Long.MIN_VALUE + 1L),
                ModdedReplaceArguments.parse("main seed=" + (Long.MIN_VALUE + 1L)).seed());
    }

    @Test
    public void invalidSeedsAreRejected() {
        IllegalArgumentException text = assertThrows(IllegalArgumentException.class,
                () -> ModdedReplaceArguments.parse("main seed=random"));
        IllegalArgumentException reserved = assertThrows(IllegalArgumentException.class,
                () -> ModdedReplaceArguments.parse("main seed=" + Long.MIN_VALUE));

        assertEquals("Seed must be a signed 64-bit integer", text.getMessage());
        assertTrue(reserved.getMessage(), reserved.getMessage().contains(Long.toString(Long.MIN_VALUE)));
    }

    @Test
    public void offTurnsAReplacementOffAndTakesNoSeed() {
        assertTrue(ModdedReplaceArguments.parse("examplemod:skylands off").off());
        assertTrue(ModdedReplaceArguments.parse("target=examplemod:skylands type=OFF").off());
        assertThrows(IllegalArgumentException.class, () -> ModdedReplaceArguments.parse("examplemod:skylands off seed=1"));
    }

    @Test
    public void malformedArgumentListsAreRejected() {
        for (String raw : List.of("", "   ", "type=overworld", "main foo=1", "main type=a pack=b",
                "main overworld 1 extra", "main type=", "target=main world-name=other")) {
            assertThrows(raw, IllegalArgumentException.class, () -> ModdedReplaceArguments.parse(raw));
        }
    }
}
