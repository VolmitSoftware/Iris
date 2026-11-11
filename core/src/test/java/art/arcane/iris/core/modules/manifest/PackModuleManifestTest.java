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

package art.arcane.iris.core.modules.manifest;

import art.arcane.iris.core.modules.manifest.PackModuleManifest.ManifestFormatException;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PackModuleManifestTest {
    @Test
    public void readsTheModulesInTheFilesOrderAndThePinnedOnes() {
        PackModuleManifest manifest = PackModuleManifest.parse("""
                {
                  "modules": {"iris:terrain": "2.3.1", "iris:carve": "1.3.0", "acme:rivers": "0.9.0"},
                  "pinned": ["iris:carve"]
                }
                """);

        assertEquals(List.of("iris:terrain", "iris:carve", "acme:rivers"), List.copyOf(manifest.modules().keySet()));
        assertEquals("1.3.0", manifest.modules().get("iris:carve"));
        assertTrue(manifest.pins("iris:carve"));
        assertFalse(manifest.pins("iris:terrain"));
        assertFalse(manifest.lists("iris:deposit"));
    }

    @Test
    public void pinnedMayBeLeftOut() {
        assertEquals(Set.of(), PackModuleManifest.parse("{\"modules\": {}}").pinned());
    }

    @Test
    public void whatItWritesReadsBackTheSame() {
        PackModuleManifest manifest = new PackModuleManifest(Map.of("iris:terrain", "2.3.1"), Set.of("iris:terrain"));

        assertEquals(manifest, PackModuleManifest.parse(manifest.toJson()));
    }

    @Test
    public void aFileThatIsNotAManifestSaysWhatToFix() {
        assertRejected("{\"modules\": ", "modules.json is not valid JSON");
        assertRejected("[]", "modules.json must be a JSON object");
        assertRejected("{}", "modules.json needs a \"modules\" object");
        assertRejected("{\"modules\": {\"iris:terrain\": 2}}", "modules.json gives iris:terrain the version 2");
        assertRejected("{\"modules\": {}, \"pinned\": \"iris:carve\"}", "modules.json has a \"pinned\" that is not a list");
    }

    @Test
    public void aVersionIsOneToThreeNumbers() {
        assertEquals(Optional.of(new ModuleVersion(2, 3, 1)), ModuleVersion.parse("2.3.1"));
        assertEquals(Optional.of(new ModuleVersion(2, 0, 0)), ModuleVersion.parse("2"));
        assertEquals(Optional.empty(), ModuleVersion.parse("4.0.1-26.2"));
        assertEquals(Optional.empty(), ModuleVersion.parse("1.0-SNAPSHOT"));
        assertTrue(new ModuleVersion(1, 10, 0).isNewerThan(new ModuleVersion(1, 9, 9)));
        assertTrue(new ModuleVersion(1, 4, 0).placesTheSameBlocksAs(new ModuleVersion(1, 3, 7)));
        assertFalse(new ModuleVersion(2, 0, 0).placesTheSameBlocksAs(new ModuleVersion(1, 9, 0)));
    }

    @Test
    public void anUnreadableLockRecordsNothing() {
        assertEquals(Map.of(), PackModuleLock.parse("not json {").installed());
        assertEquals(Map.of("iris:carve", "1.3.0"), PackModuleLock.parse("{\"iris:carve\": \"1.3.0\"}").installed());
    }

    private static void assertRejected(String json, String messageStart) {
        ManifestFormatException rejected = assertThrows(ManifestFormatException.class, () -> PackModuleManifest.parse(json));
        assertTrue(rejected.getMessage(), rejected.getMessage().startsWith(messageStart));
    }
}
