/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.structure;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BulkStructureImporterTemplateNameTest {
    @Test
    public void minecraftKeysDropTheNamespacePrefix() {
        assertEquals("village/plains", BulkStructureImporter.templateNameFor("minecraft:village/plains"));
        assertEquals("igloo/top", BulkStructureImporter.templateNameFor("minecraft:igloo/top"));
    }

    @Test
    public void datapackKeysKeepNamespaceAsDirectory() {
        assertEquals("nova_structures/temple/large", BulkStructureImporter.templateNameFor("nova_structures:temple/large"));
        assertEquals("aquaculture/treasure", BulkStructureImporter.templateNameFor("aquaculture:treasure"));
    }

    @Test
    public void keyWithoutNamespaceDefaultsToMinecraft() {
        assertEquals("village", BulkStructureImporter.templateNameFor("village"));
    }

    @Test
    public void pathIsLowercasedAndSanitized() {
        assertEquals("nova/foo_bar", BulkStructureImporter.templateNameFor("nova:Foo Bar"));
        assertEquals("nova/weird_name", BulkStructureImporter.templateNameFor("nova:weird@name"));
    }

    @Test
    public void duplicateSlashesAreCollapsedAndLeadingSlashStripped() {
        assertEquals("nova/a/b", BulkStructureImporter.templateNameFor("nova:a//b"));
        assertEquals("nova/leading", BulkStructureImporter.templateNameFor("nova:/leading"));
    }

    @Test
    public void explicitSourceScopeIncludesOnlyOwnedRegistryKeys() {
        Set<String> allowed = Set.of("nova:tavern", "minecraft:village_plains");

        assertTrue(BulkStructureImporter.isAllowedDatapackKey("nova:tavern", allowed));
        assertTrue(BulkStructureImporter.isAllowedDatapackKey("minecraft:village_plains", allowed));
        assertFalse(BulkStructureImporter.isAllowedDatapackKey("other:castle", allowed));
    }

    @Test
    public void explicitSourceSelectionReportsEveryMissingAllowedKey() {
        BulkStructureImporter.KeySelection selection = BulkStructureImporter.selectDatapackKeys(
                List.of("NOVA:TAVERN", "other:castle", "nova:tavern"),
                Set.of("nova:tavern", "nova:missing", "minecraft:village_plains")
        );

        assertEquals(List.of("nova:tavern"), selection.present());
        assertEquals(List.of("minecraft:village_plains", "nova:missing"), selection.missing());
        assertEquals(3, selection.total());
    }

    @Test
    public void defaultSourceSelectionExcludesMinecraftAndDeduplicatesKeys() {
        BulkStructureImporter.KeySelection selection = BulkStructureImporter.selectDatapackKeys(
                List.of("minecraft:village", "Nova:Tavern", "nova:tavern", "other:castle"),
                null
        );

        assertEquals(List.of("nova:tavern", "other:castle"), selection.present());
        assertTrue(selection.missing().isEmpty());
        assertEquals(2, selection.total());
    }

    @Test
    public void datapackReportIncludesStructureAndTemplateAttempts() {
        BulkStructureImporter.Report report = BulkStructureImporter.datapackReport(
                2,
                3,
                3,
                1,
                1,
                Map.of("iris:nova_tavern", "nova:tavern")
        );

        assertEquals(5, report.total());
        assertEquals(3, report.imported());
        assertEquals(1, report.skipped());
        assertEquals(1, report.failed());
        assertFalse(report.retryRequired());
    }

    @Test
    public void datapackReportRetainsRetryClassification() {
        BulkStructureImporter.Report report = BulkStructureImporter.datapackReport(
                1,
                0,
                0,
                0,
                1,
                Map.of(),
                true
        );

        assertTrue(report.retryRequired());
    }

    @Test
    public void enumerationFailureIsOneFailedAttempt() {
        BulkStructureImporter.Report report = BulkStructureImporter.enumerationFailureReport();

        assertEquals(1, report.total());
        assertEquals(0, report.imported());
        assertEquals(0, report.skipped());
        assertEquals(1, report.failed());
        assertTrue(report.retryRequired());
    }

    @Test
    public void successfulBundleEvidenceIsDefensiveAndImmutable() {
        Map<String, String> successfulBundles = new HashMap<>();
        successfulBundles.put("iris:nova_tavern", "nova:tavern");
        BulkStructureImporter.Report report = new BulkStructureImporter.Report(1, 1, 0, 0, successfulBundles);

        successfulBundles.put("iris:other_castle", "other:castle");

        assertEquals(Map.of("iris:nova_tavern", "nova:tavern"), report.successfulBundles());
        assertThrows(UnsupportedOperationException.class,
                () -> report.successfulBundles().put("iris:third", "third:structure"));
    }
}
