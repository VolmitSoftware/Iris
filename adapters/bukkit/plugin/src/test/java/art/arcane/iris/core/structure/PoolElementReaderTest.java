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

package art.arcane.iris.core.structure;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class PoolElementReaderTest {
    private static final String FINAL_STATE =
            "minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]";

    @Test
    public void plainTemplateElementRemainsUnchanged() throws Exception {
        FakeSinglePoolElement single = new FakeSinglePoolElement("minecraft:village/plains/houses/plains_small_house_1");

        PoolElementReader.PoolElementResolution resolution = PoolElementReader.resolvePoolElement(single);

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

        PoolElementReader.PoolElementResolution resolution = PoolElementReader.resolvePoolElement(outer);

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

        PoolElementReader.PoolElementResolution resolution = PoolElementReader.resolvePoolElement(list);

        assertSame(feature, resolution.physicalElement());
        assertNull(resolution.templateLocation());
        assertEquals(1, resolution.omittedElements());
    }

    @Test
    public void emptyListResolvesWithoutIndexingAChild() throws Exception {
        PoolElementReader.PoolElementResolution resolution = PoolElementReader.resolvePoolElement(
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
        PoolElementReader.PoolElementResolution resolution = PoolElementReader.resolvePoolElement(towers);

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
    public void vanillaNbtMetadataIsReadFromTheJigsawBlockInfo() throws Exception {
        FakeJigsawBlockInfo jigsaw = new FakeJigsawBlockInfo(-29, -17);
        FakeStructureBlockInfo info = new FakeStructureBlockInfo(new FakeCompoundTag(FINAL_STATE));

        PoolElementReader.ConnectorMetadata metadata = PoolElementReader.readConnectorMetadata(jigsaw, info);

        assertEquals(Optional.of(FINAL_STATE), metadata.finalState());
        assertEquals(-17, metadata.selectionPriority());
        assertEquals(-29, metadata.placementPriority());
    }

    private record FakeJigsawBlockInfo(int placementPriority, int selectionPriority) {
    }

    private record FakeStructureBlockInfo(FakeCompoundTag nbt) {
    }

    private record FakeCompoundTag(String finalState) {
        public Optional<String> getString(String key) {
            return "final_state".equals(key) ? Optional.of(finalState) : Optional.empty();
        }
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
