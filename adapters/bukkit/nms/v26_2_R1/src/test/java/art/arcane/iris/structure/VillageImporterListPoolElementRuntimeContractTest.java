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
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class VillageImporterListPoolElementRuntimeContractTest {
    @BeforeClass
    public static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void pillagerOutpostTowerCompositeProducesOnePhysicalTowerChoice() throws Exception {
        StructurePoolElement tower = StructurePoolElement.legacy("minecraft:pillager_outpost/watchtower")
                .apply(StructureTemplatePool.Projection.RIGID);
        StructurePoolElement overgrown = StructurePoolElement.legacy("minecraft:pillager_outpost/watchtower_overgrown")
                .apply(StructureTemplatePool.Projection.RIGID);
        ListPoolElement towers = new ListPoolElement(
                List.of(tower, overgrown),
                StructureTemplatePool.Projection.RIGID
        );

        VillageImporter.PoolElementResolution resolution = VillageImporter.resolvePoolElement(towers);
        Map<String, Object> entry = VillageImporter.piecePoolEntry(
                "qa/pillager_outpost/piece/minecraft/pillager_outpost/watchtower",
                1
        );
        StructureLoss loss = VillageImporter.listElementFallbackLoss(
                resolution,
                "minecraft:pillager_outpost/towers"
        );
        Map<String, Object> basePlatePiece = VillageImporter.pieceJson(
                "qa/pillager_outpost/piece/minecraft/pillager_outpost/base_plate",
                List.of(Map.of("name", "minecraft:bottom")),
                0
        );
        Map<String, Object> watchtowerPiece = VillageImporter.pieceJson(
                "qa/pillager_outpost/piece/minecraft/pillager_outpost/watchtower",
                List.of(),
                1155
        );
        VillageImporter.PoolMemberNormalization connectorScaffold = VillageImporter.normalizePoolMember(
                "minecraft:pillager_outpost/features",
                "qa/pillager_outpost/pool/minecraft/pillager_outpost/features",
                false,
                2,
                "minecraft:empty",
                "minecraft:pillager_outpost/feature_plate",
                "qa/pillager_outpost/piece/minecraft/pillager_outpost/feature_plate",
                1,
                0,
                List.of(Map.of("name", "minecraft:bottom")));

        assertSame(tower, resolution.physicalElement());
        assertEquals("minecraft:pillager_outpost/watchtower", resolution.templateLocation());
        assertEquals(1, resolution.omittedElements());
        assertEquals("qa/pillager_outpost/piece/minecraft/pillager_outpost/watchtower", entry.get("piece"));
        assertEquals(1, entry.get("weight"));
        assertEquals(StructureCapability.LIST_ELEMENTS, loss.capability());
        assertEquals("list_pool_overlays_not_imported", loss.code());
        assertEquals(false, basePlatePiece.get("collidable"));
        assertFalse(watchtowerPiece.containsKey("collidable"));
        assertEquals(VillageImporter.PoolMemberDisposition.PHYSICAL, connectorScaffold.disposition());
        assertTrue(connectorScaffold.losses().isEmpty());
    }

    @Test
    public void actualWaystoneLikeAllAirMemberNormalizesToEmpty() throws Exception {
        StructurePoolElement waystone = StructurePoolElement
                .legacy("dungeons_and_taverns:waystones/waystone_scaffold")
                .apply(StructureTemplatePool.Projection.RIGID);
        VillageImporter.PoolElementResolution resolution = VillageImporter.resolvePoolElement(waystone);
        VillageImporter.PoolMemberNormalization normalization = VillageImporter.normalizePoolMember(
                "dungeons_and_taverns:waystones",
                "qa/pool/dungeons_and_taverns/waystones",
                false,
                1,
                "dungeons_and_taverns:waystones",
                resolution.templateLocation(),
                "qa/piece/dungeons_and_taverns/waystones/waystone_scaffold",
                2,
                0,
                List.of());

        assertSame(waystone, resolution.physicalElement());
        assertEquals("dungeons_and_taverns:waystones/waystone_scaffold", resolution.templateLocation());
        assertEquals(VillageImporter.PoolMemberDisposition.EMPTY, normalization.disposition());
        assertEquals(true, normalization.poolEntry().get("empty"));
        assertEquals(2, normalization.poolEntry().get("weight"));
        assertEquals("connectorless_all_air_member_normalized_empty",
                normalization.losses().getFirst().code());
    }

    @Test
    public void actualNonAirOrphanMemberIsOmitted() throws Exception {
        StructurePoolElement orphan = StructurePoolElement
                .legacy("test:orphan_house")
                .apply(StructureTemplatePool.Projection.RIGID);
        VillageImporter.PoolElementResolution resolution = VillageImporter.resolvePoolElement(orphan);
        VillageImporter.PoolMemberNormalization normalization = VillageImporter.normalizePoolMember(
                "test:orphan_pool",
                "qa/pool/test/orphan_pool",
                false,
                1,
                null,
                resolution.templateLocation(),
                "qa/piece/test/orphan_house",
                4,
                37,
                List.of());

        assertSame(orphan, resolution.physicalElement());
        assertEquals("test:orphan_house", resolution.templateLocation());
        assertEquals(VillageImporter.PoolMemberDisposition.OMITTED, normalization.disposition());
        assertTrue(normalization.poolEntry().isEmpty());
        assertEquals("connectorless_non_air_member_omitted",
                normalization.losses().getFirst().code());
        assertTrue(normalization.losses().getFirst().detail().contains("37 non-air block(s)"));
        assertTrue(normalization.losses().getFirst().detail().contains("no source fallback"));
        assertTrue(normalization.losses().getFirst().detail().contains("selection weights"));
        assertTrue(normalization.losses().getFirst().detail().contains("RNG consumption"));
    }

    @Test
    public void actualConnectorlessStartMemberRemainsPhysical() throws Exception {
        StructurePoolElement start = StructurePoolElement
                .legacy("test:air_start")
                .apply(StructureTemplatePool.Projection.RIGID);
        VillageImporter.PoolElementResolution resolution = VillageImporter.resolvePoolElement(start);
        VillageImporter.PoolMemberNormalization normalization = VillageImporter.normalizePoolMember(
                "test:start",
                "qa/pool/test/start",
                true,
                2,
                "test:fallback",
                resolution.templateLocation(),
                "qa/piece/test/air_start",
                6,
                0,
                List.of());

        assertSame(start, resolution.physicalElement());
        assertEquals(VillageImporter.PoolMemberDisposition.PHYSICAL, normalization.disposition());
        assertEquals("qa/piece/test/air_start", normalization.poolEntry().get("piece"));
        assertTrue(normalization.losses().isEmpty());
    }

    @Test
    public void actualMixedPoolAllAirMemberIsOmittedWithoutBecomingEmpty() throws Exception {
        StructurePoolElement inert = StructurePoolElement
                .legacy("test:mixed_air_inert")
                .apply(StructureTemplatePool.Projection.RIGID);
        VillageImporter.PoolElementResolution resolution = VillageImporter.resolvePoolElement(inert);
        VillageImporter.PoolMemberNormalization normalization = VillageImporter.normalizePoolMember(
                "test:mixed",
                "qa/pool/test/mixed",
                false,
                4,
                "test:mixed",
                resolution.templateLocation(),
                "qa/piece/test/mixed_air_inert",
                9,
                0,
                List.of());

        assertSame(inert, resolution.physicalElement());
        assertEquals(VillageImporter.PoolMemberDisposition.OMITTED, normalization.disposition());
        assertTrue(normalization.poolEntry().isEmpty());
        assertEquals("connectorless_all_air_mixed_member_omitted",
                normalization.losses().getFirst().code());
        assertTrue(normalization.losses().getFirst().detail().contains("selection weights"));
        assertTrue(normalization.losses().getFirst().detail().contains("RNG consumption"));
    }

    @Test
    public void actualMixedDistinctFallbackRetainsAllConnectorlessMembers() throws Exception {
        StructurePoolElement allAirElement = StructurePoolElement
                .legacy("test:mixed_all_air_primary")
                .apply(StructureTemplatePool.Projection.RIGID);
        StructurePoolElement nonAirElement = StructurePoolElement
                .legacy("test:mixed_non_air_primary")
                .apply(StructureTemplatePool.Projection.RIGID);
        VillageImporter.PoolElementResolution allAirResolution = VillageImporter.resolvePoolElement(allAirElement);
        VillageImporter.PoolElementResolution nonAirResolution = VillageImporter.resolvePoolElement(nonAirElement);
        VillageImporter.PoolMemberNormalization allAir = VillageImporter.normalizePoolMember(
                "test:mixed",
                "qa/pool/test/mixed",
                false,
                4,
                "test:fallback",
                allAirResolution.templateLocation(),
                "qa/piece/test/mixed_all_air_primary",
                9,
                0,
                List.of());
        VillageImporter.PoolMemberNormalization nonAir = VillageImporter.normalizePoolMember(
                "test:mixed",
                "qa/pool/test/mixed",
                false,
                4,
                "test:fallback",
                nonAirResolution.templateLocation(),
                "qa/piece/test/mixed_non_air_primary",
                12,
                41,
                List.of());

        assertSame(allAirElement, allAirResolution.physicalElement());
        assertSame(nonAirElement, nonAirResolution.physicalElement());
        assertEquals(VillageImporter.PoolMemberDisposition.PHYSICAL, allAir.disposition());
        assertEquals(VillageImporter.PoolMemberDisposition.PHYSICAL, nonAir.disposition());
        assertEquals("qa/piece/test/mixed_all_air_primary", allAir.poolEntry().get("piece"));
        assertEquals("qa/piece/test/mixed_non_air_primary", nonAir.poolEntry().get("piece"));
        assertTrue(allAir.losses().isEmpty());
        assertTrue(nonAir.losses().isEmpty());
    }

    @Test
    public void actualSingletonDistinctFallbackRetainsAllAirAndNonAirMembers() throws Exception {
        StructurePoolElement allAirElement = StructurePoolElement
                .legacy("test:all_air_primary")
                .apply(StructureTemplatePool.Projection.RIGID);
        StructurePoolElement nonAirElement = StructurePoolElement
                .legacy("test:non_air_primary")
                .apply(StructureTemplatePool.Projection.RIGID);
        VillageImporter.PoolElementResolution allAirResolution = VillageImporter.resolvePoolElement(allAirElement);
        VillageImporter.PoolElementResolution nonAirResolution = VillageImporter.resolvePoolElement(nonAirElement);
        VillageImporter.PoolMemberNormalization allAir = VillageImporter.normalizePoolMember(
                "test:primary",
                "qa/pool/test/primary",
                false,
                1,
                "test:fallback",
                allAirResolution.templateLocation(),
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
                nonAirResolution.templateLocation(),
                "qa/piece/test/non_air_primary",
                7,
                29,
                List.of());

        assertSame(allAirElement, allAirResolution.physicalElement());
        assertSame(nonAirElement, nonAirResolution.physicalElement());
        assertEquals(VillageImporter.PoolMemberDisposition.PHYSICAL, allAir.disposition());
        assertEquals(VillageImporter.PoolMemberDisposition.PHYSICAL, nonAir.disposition());
        assertEquals("qa/piece/test/all_air_primary", allAir.poolEntry().get("piece"));
        assertEquals("qa/piece/test/non_air_primary", nonAir.poolEntry().get("piece"));
        assertTrue(allAir.losses().isEmpty());
        assertTrue(nonAir.losses().isEmpty());
    }
}
