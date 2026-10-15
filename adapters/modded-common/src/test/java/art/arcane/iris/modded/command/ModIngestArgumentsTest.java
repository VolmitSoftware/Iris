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

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ModIngestArgumentsTest {
    @Test
    public void requiredPacksBindPositionallyAndOptionalsDefault() {
        ModIngestArguments arguments = ModIngestArguments.parse("overworld modded_overworld");

        assertEquals("overworld", arguments.sourcePack());
        assertEquals("modded_overworld", arguments.targetPack());
        assertEquals("overworld", arguments.dimension());
        assertEquals(Set.of(), arguments.namespaces());
        assertEquals(Set.of(), arguments.excludedRegions());
        assertFalse(arguments.replace());
    }

    @Test
    public void keyedOptionalsAreParsedInAnyOrder() {
        ModIngestArguments arguments = ModIngestArguments.parse(
                "  overworld   modded  replace=true namespaces=biomesoplenty,terralith excludeRegions=prismatics,estranged dimension=overworld_alt ");

        assertEquals("overworld_alt", arguments.dimension());
        assertEquals(Set.of("prismatics", "estranged"), arguments.excludedRegions());
        assertEquals(Set.of("biomesoplenty", "terralith"), arguments.namespaces());
        assertTrue(arguments.replace());
    }

    @Test
    public void missingTargetIsRejected() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> ModIngestArguments.parse("overworld"));

        assertEquals("missing target pack", failure.getMessage());
    }

    @Test
    public void strayPositionalsAndUnknownKeysAreRejected() {
        assertEquals("unexpected argument 'extra'", reason("overworld modded extra"));
        assertEquals("unknown option 'seed'", reason("overworld modded seed=1"));
        assertEquals("option 'replace' given twice", reason("overworld modded replace=true replace=false"));
    }

    @Test
    public void valuesAreValidated() {
        assertEquals("replace must be true or false", reason("overworld modded replace=yes"));
        assertEquals("invalid pack name '../evil'", reason("../evil modded"));
        assertEquals("invalid target pack name 'modded.v2'", reason("overworld modded.v2"));
        assertEquals("invalid dimension key 'a..b'", reason("overworld modded dimension=a..b"));
        assertEquals("invalid namespace 'Bad'", reason("overworld modded namespaces=ok,Bad"));
        assertEquals("namespaces must not be empty", reason("overworld modded namespaces=,"));
        assertEquals("invalid region 'a/../b'", reason("overworld modded excludeRegions=a/../b"));
        assertEquals("excludeRegions must not be empty", reason("overworld modded excludeRegions=,"));
        assertEquals("source and target must be different packs", reason("overworld overworld"));
    }

    private static String reason(String raw) {
        return assertThrows(IllegalArgumentException.class, () -> ModIngestArguments.parse(raw)).getMessage();
    }
}
