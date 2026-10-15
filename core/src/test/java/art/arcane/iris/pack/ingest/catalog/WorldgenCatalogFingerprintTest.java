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

package art.arcane.iris.pack.ingest.catalog;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class WorldgenCatalogFingerprintTest {
    private static final List<CatalogNamespace> NAMESPACES = List.of(
            new CatalogNamespace("minecraft", "Minecraft", "26.3"),
            new CatalogNamespace("examplemod", "Example Mod", "1.0.0"));

    @Test
    public void fingerprintIsLowercaseSha256Hex() {
        String fingerprint = WorldgenCatalogFingerprint.of(NAMESPACES, Map.of("biome", List.of("minecraft:plains")));

        assertEquals(64, fingerprint.length());
        assertTrue(fingerprint.matches("[0-9a-f]{64}"));
    }

    @Test
    public void fingerprintIgnoresNamespaceAndKeyOrder() {
        String ordered = WorldgenCatalogFingerprint.of(NAMESPACES, Map.of(
                "biome", List.of("examplemod:glade", "minecraft:plains"),
                "placed_feature", List.of("examplemod:ore_glimmer", "minecraft:ore_iron")));
        String shuffled = WorldgenCatalogFingerprint.of(List.of(NAMESPACES.get(1), NAMESPACES.get(0)), Map.of(
                "placed_feature", List.of("minecraft:ore_iron", "examplemod:ore_glimmer"),
                "biome", List.of("minecraft:plains", "examplemod:glade")));

        assertEquals(ordered, shuffled);
    }

    @Test
    public void fingerprintChangesWithModVersion() {
        Map<String, List<String>> keys = Map.of("biome", List.of("examplemod:glade"));
        String original = WorldgenCatalogFingerprint.of(NAMESPACES, keys);
        String updated = WorldgenCatalogFingerprint.of(List.of(NAMESPACES.get(0),
                new CatalogNamespace("examplemod", "Example Mod", "1.0.1")), keys);

        assertNotEquals(original, updated);
    }

    @Test
    public void fingerprintChangesWhenAKeyIsAddedOrMovesRegistry() {
        String original = WorldgenCatalogFingerprint.of(NAMESPACES, Map.of(
                "biome", List.of("examplemod:glade"), "structure", List.of()));
        String added = WorldgenCatalogFingerprint.of(NAMESPACES, Map.of(
                "biome", List.of("examplemod:glade", "examplemod:marsh"), "structure", List.of()));
        String moved = WorldgenCatalogFingerprint.of(NAMESPACES, Map.of(
                "biome", List.of(), "structure", List.of("examplemod:glade")));

        assertNotEquals(original, added);
        assertNotEquals(original, moved);
    }
}
