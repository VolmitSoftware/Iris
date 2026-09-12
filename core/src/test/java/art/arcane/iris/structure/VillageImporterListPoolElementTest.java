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

import art.arcane.iris.structure.authoring.StructureCapability;
import art.arcane.iris.structure.authoring.StructureLoss;
import art.arcane.iris.structure.object.IrisObject;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class VillageImporterListPoolElementTest {
    @Test
    public void plainTemplateElementRemainsUnchanged() throws Exception {
        FakeSinglePoolElement single = new FakeSinglePoolElement("minecraft:village/plains/houses/plains_small_house_1");

        VillageImporter.PoolElementResolution resolution = VillageImporter.resolvePoolElement(single);

        assertSame(single, resolution.physicalElement());
        assertEquals("minecraft:village/plains/houses/plains_small_house_1", resolution.templateLocation());
        assertEquals(0, resolution.listLevels());
        assertEquals(0, resolution.omittedElements());
    }

    @Test
    public void nestedListsRetainOnlyTheRecursivePrimaryTemplate() throws Exception {
        FakeSinglePoolElement primary = new FakeSinglePoolElement("minecraft:pillager_outpost/watchtower");
        FakeSinglePoolElement innerOverlay = new FakeSinglePoolElement("test:inner_overlay");
        FakeSinglePoolElement outerOverlay = new FakeSinglePoolElement("test:outer_overlay");
        FakeListPoolElement inner = new FakeListPoolElement(List.of(primary, innerOverlay));
        FakeListPoolElement outer = new FakeListPoolElement(List.of(inner, outerOverlay));

        VillageImporter.PoolElementResolution resolution = VillageImporter.resolvePoolElement(outer);

        assertSame(primary, resolution.physicalElement());
        assertEquals("minecraft:pillager_outpost/watchtower", resolution.templateLocation());
        assertEquals(2, resolution.listLevels());
        assertEquals(2, resolution.omittedElements());
    }

    @Test
    public void unsupportedPrimaryDoesNotFallThroughToLaterChildren() throws Exception {
        FakeFeaturePoolElement feature = new FakeFeaturePoolElement();
        FakeSinglePoolElement fallback = new FakeSinglePoolElement("test:must_not_be_selected");
        FakeListPoolElement list = new FakeListPoolElement(List.of(feature, fallback));

        VillageImporter.PoolElementResolution resolution = VillageImporter.resolvePoolElement(list);

        assertSame(feature, resolution.physicalElement());
        assertNull(resolution.templateLocation());
        assertEquals(1, resolution.omittedElements());
    }

    @Test
    public void emptyListResolvesWithoutIndexingAChild() throws Exception {
        VillageImporter.PoolElementResolution resolution = VillageImporter.resolvePoolElement(
                new FakeListPoolElement(List.of())
        );

        assertNull(resolution.physicalElement());
        assertNull(resolution.templateLocation());
        assertEquals(1, resolution.listLevels());
        assertEquals(0, resolution.omittedElements());
    }

    @Test
    public void outerChoiceProducesOneEntryWithItsOriginalWeight() throws Exception {
        FakeListPoolElement towers = new FakeListPoolElement(List.of(
                new FakeSinglePoolElement("minecraft:pillager_outpost/watchtower"),
                new FakeSinglePoolElement("minecraft:pillager_outpost/watchtower_overgrown")
        ));
        VillageImporter.PoolElementResolution resolution = VillageImporter.resolvePoolElement(towers);

        List<Map<String, Object>> entries = List.of(VillageImporter.piecePoolEntry(
                "qa/pillager_outpost/piece/minecraft/pillager_outpost/watchtower",
                7
        ));

        assertEquals(1, entries.size());
        assertEquals("minecraft:pillager_outpost/watchtower", resolution.templateLocation());
        assertEquals(7, entries.getFirst().get("weight"));
        assertEquals(1, resolution.omittedElements());
    }

    @Test
    public void omittedCompositeChildrenProduceExplicitFidelityLoss() throws Exception {
        FakeListPoolElement towers = new FakeListPoolElement(List.of(
                new FakeSinglePoolElement("minecraft:pillager_outpost/watchtower"),
                new FakeSinglePoolElement("minecraft:pillager_outpost/watchtower_overgrown")
        ));
        VillageImporter.PoolElementResolution resolution = VillageImporter.resolvePoolElement(towers);

        StructureLoss loss = VillageImporter.listElementFallbackLoss(
                resolution,
                "minecraft:pillager_outpost/towers"
        );

        assertEquals(StructureCapability.LIST_ELEMENTS, loss.capability());
        assertEquals("list_pool_overlays_not_imported", loss.code());
        assertTrue(loss.detail().contains("minecraft:pillager_outpost/towers"));
        assertTrue(loss.detail().contains("omitted 1 colocated element"));
    }

    @Test
    public void allAirTemplatesWithConnectorsRemainNonCollidablePhysicalScaffolds() {
        List<Map<String, Object>> connectors = List.of(Map.of("name", "test:connector"));
        VillageImporter.PoolMemberNormalization normalization = VillageImporter.normalizePoolMember(
                "test:pool",
                "test/pool",
                false,
                2,
                "test:fallback",
                "test:scaffold",
                "test:scaffold",
                3,
                0,
                connectors);
        Map<String, Object> scaffold = VillageImporter.pieceJson("test:scaffold", connectors, 0);
        Map<String, Object> physical = VillageImporter.pieceJson("test:physical", connectors, 1);

        assertEquals(VillageImporter.PoolMemberDisposition.PHYSICAL, normalization.disposition());
        assertEquals("test:scaffold", normalization.poolEntry().get("piece"));
        assertTrue(normalization.losses().isEmpty());
        assertEquals(false, scaffold.get("collidable"));
        assertFalse(physical.containsKey("collidable"));
    }

    @Test
    public void connectorlessAllAirMemberNormalizesToExplicitEmptyWithFidelityLoss() {
        VillageImporter.PoolMemberNormalization normalization = VillageImporter.normalizePoolMember(
                "dungeons_and_taverns:waystones",
                "qa/pool/dungeons_and_taverns/waystones",
                false,
                1,
                "dungeons_and_taverns:waystones",
                "dungeons_and_taverns:waystones/waystone_scaffold",
                "qa/piece/dungeons_and_taverns/waystones/waystone_scaffold",
                5,
                0,
                List.of());

        assertEquals(VillageImporter.PoolMemberDisposition.EMPTY, normalization.disposition());
        assertEquals(true, normalization.poolEntry().get("empty"));
        assertEquals(5, normalization.poolEntry().get("weight"));
        assertEquals(1, normalization.losses().size());
        StructureLoss loss = normalization.losses().getFirst();
        assertEquals(StructureCapability.IRIS_PLACEMENT, loss.capability());
        assertEquals("connectorless_all_air_member_normalized_empty", loss.code());
        assertEquals("jigsaw-pools/qa/pool/dungeons_and_taverns/waystones.json", loss.affectedResource());
        assertTrue(loss.detail().contains("source self-fallback"));
    }

    @Test
    public void connectorlessNonAirMemberIsOmittedAsUnattachableWithFidelityLoss() {
        VillageImporter.PoolMemberNormalization normalization = VillageImporter.normalizePoolMember(
                "test:orphan_pool",
                "qa/pool/test/orphan_pool",
                false,
                1,
                null,
                "test:orphan_house",
                "qa/piece/test/orphan_house",
                7,
                42,
                List.of());

        assertEquals(VillageImporter.PoolMemberDisposition.OMITTED, normalization.disposition());
        assertTrue(normalization.poolEntry().isEmpty());
        assertEquals(1, normalization.losses().size());
        StructureLoss loss = normalization.losses().getFirst();
        assertEquals(StructureCapability.BLOCKS, loss.capability());
        assertEquals("connectorless_non_air_member_omitted", loss.code());
        assertTrue(loss.detail().contains("42 non-air block(s)"));
        assertTrue(loss.detail().contains("no source fallback"));
        assertTrue(loss.detail().contains("selection weights"));
        assertTrue(loss.detail().contains("RNG consumption"));
        assertEquals("jigsaw-pools/qa/pool/test/orphan_pool.json", loss.affectedResource());
    }

    @Test
    public void connectorlessStartMemberRemainsPhysicalRegardlessOfAirContent() {
        VillageImporter.PoolMemberNormalization allAir = VillageImporter.normalizePoolMember(
                "test:start",
                "qa/pool/test/start",
                true,
                2,
                "test:fallback",
                "test:all_air_start",
                "qa/piece/test/all_air_start",
                2,
                0,
                List.of());
        VillageImporter.PoolMemberNormalization nonAir = VillageImporter.normalizePoolMember(
                "test:start",
                "qa/pool/test/start",
                true,
                2,
                "test:fallback",
                "test:non_air_start",
                "qa/piece/test/non_air_start",
                3,
                17,
                List.of());

        assertEquals(VillageImporter.PoolMemberDisposition.PHYSICAL, allAir.disposition());
        assertEquals("qa/piece/test/all_air_start", allAir.poolEntry().get("piece"));
        assertTrue(allAir.losses().isEmpty());
        assertEquals(VillageImporter.PoolMemberDisposition.PHYSICAL, nonAir.disposition());
        assertEquals("qa/piece/test/non_air_start", nonAir.poolEntry().get("piece"));
        assertTrue(nonAir.losses().isEmpty());
    }

    @Test
    public void mixedPoolConnectorlessAllAirMemberIsOmittedWithWeightAndRngDriftLoss() {
        VillageImporter.PoolMemberNormalization normalization = VillageImporter.normalizePoolMember(
                "test:mixed",
                "qa/pool/test/mixed",
                false,
                3,
                "test:mixed",
                "test:air_inert",
                "qa/piece/test/air_inert",
                11,
                0,
                List.of());

        assertEquals(VillageImporter.PoolMemberDisposition.OMITTED, normalization.disposition());
        assertTrue(normalization.poolEntry().isEmpty());
        assertEquals(1, normalization.losses().size());
        StructureLoss loss = normalization.losses().getFirst();
        assertEquals(StructureCapability.IRIS_PLACEMENT, loss.capability());
        assertEquals("connectorless_all_air_mixed_member_omitted", loss.code());
        assertTrue(loss.detail().contains("mixed 3-member pool"));
        assertTrue(loss.detail().contains("selection weights"));
        assertTrue(loss.detail().contains("RNG consumption"));
    }

    @Test
    public void mixedDistinctFallbackRetainsEveryConnectorlessMemberAsPhysical() {
        VillageImporter.PoolMemberNormalization allAir = VillageImporter.normalizePoolMember(
                "test:mixed",
                "qa/pool/test/mixed",
                false,
                3,
                "test:fallback",
                "test:all_air_inert",
                "qa/piece/test/all_air_inert",
                11,
                0,
                List.of());
        VillageImporter.PoolMemberNormalization nonAir = VillageImporter.normalizePoolMember(
                "test:mixed",
                "qa/pool/test/mixed",
                false,
                3,
                "test:fallback",
                "test:non_air_inert",
                "qa/piece/test/non_air_inert",
                13,
                31,
                List.of());

        assertEquals(VillageImporter.PoolMemberDisposition.PHYSICAL, allAir.disposition());
        assertEquals(VillageImporter.PoolMemberDisposition.PHYSICAL, nonAir.disposition());
        assertEquals("qa/piece/test/all_air_inert", allAir.poolEntry().get("piece"));
        assertEquals("qa/piece/test/non_air_inert", nonAir.poolEntry().get("piece"));
        assertTrue(allAir.losses().isEmpty());
        assertTrue(nonAir.losses().isEmpty());
    }

    @Test
    public void singletonDistinctFallbackRetainsConnectorlessMembersAsPhysical() {
        VillageImporter.PoolMemberNormalization allAir = VillageImporter.normalizePoolMember(
                "test:primary",
                "qa/pool/test/primary",
                false,
                1,
                "test:fallback",
                "test:all_air_primary",
                "qa/piece/test/all_air_primary",
                5,
                0,
                List.of());
        VillageImporter.PoolMemberNormalization nonAir = VillageImporter.normalizePoolMember(
                "test:primary",
                "qa/pool/test/primary",
                false,
                1,
                "test:fallback",
                "test:non_air_primary",
                "qa/piece/test/non_air_primary",
                7,
                23,
                List.of());
        VillageImporter.PoolMemberNormalization noFallbackAllAir = VillageImporter.normalizePoolMember(
                "test:terminal",
                "qa/pool/test/terminal",
                false,
                1,
                null,
                "test:all_air_terminal",
                "qa/piece/test/all_air_terminal",
                3,
                0,
                List.of());
        VillageImporter.PoolMemberNormalization selfFallbackNonAir = VillageImporter.normalizePoolMember(
                "test:self",
                "qa/pool/test/self",
                false,
                1,
                "test:self",
                "test:non_air_self",
                "qa/piece/test/non_air_self",
                2,
                19,
                List.of());

        assertEquals(VillageImporter.PoolMemberDisposition.PHYSICAL, allAir.disposition());
        assertEquals("qa/piece/test/all_air_primary", allAir.poolEntry().get("piece"));
        assertTrue(allAir.losses().isEmpty());
        assertEquals(VillageImporter.PoolMemberDisposition.PHYSICAL, nonAir.disposition());
        assertEquals("qa/piece/test/non_air_primary", nonAir.poolEntry().get("piece"));
        assertTrue(nonAir.losses().isEmpty());
        assertEquals(VillageImporter.PoolMemberDisposition.EMPTY, noFallbackAllAir.disposition());
        assertEquals(VillageImporter.PoolMemberDisposition.OMITTED, selfFallbackNonAir.disposition());
    }

    @Test
    public void omittedTemplateContributesNoBlocksOrCapturedCapabilities() {
        VillageImporter.ImportedTemplate imported = new VillageImporter.ImportedTemplate(
                new IrisObject(3, 3, 3),
                27,
                4,
                List.of(),
                List.of(StructureCapability.BLOCKS, StructureCapability.BLOCK_ENTITIES));
        VillageImporter.PoolMemberNormalization omitted = VillageImporter.normalizePoolMember(
                "test:self",
                "qa/pool/test/self",
                false,
                2,
                "test:self",
                "test:omitted_tile",
                "qa/piece/test/omitted_tile",
                1,
                4,
                List.of());

        assertEquals(VillageImporter.PoolMemberDisposition.OMITTED, omitted.disposition());
        assertEquals(0, imported.emittedBlocks(omitted));
        assertTrue(imported.emittedCapabilities(omitted).isEmpty());
    }

    private static final class FakeListPoolElement {
        private final List<Object> elements;

        private FakeListPoolElement(List<?> elements) {
            this.elements = List.copyOf(elements);
        }

        public List<Object> getElements() {
            return elements;
        }
    }

    private static final class FakeSinglePoolElement {
        private final FakeResourceLocation location;

        private FakeSinglePoolElement(String location) {
            String[] parts = location.split(":", 2);
            this.location = new FakeResourceLocation(parts[0], parts[1]);
        }

        public FakeResourceLocation getTemplateLocation() {
            return location;
        }
    }

    private static final class FakeFeaturePoolElement {
    }

    private static final class FakeResourceLocation {
        private final String namespace;
        private final String path;

        private FakeResourceLocation(String namespace, String path) {
            this.namespace = namespace;
            this.path = path;
        }

        public String getNamespace() {
            return namespace;
        }

        public String getPath() {
            return path;
        }
    }
}
