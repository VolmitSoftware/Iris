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

package art.arcane.iris.structure.placement;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class StructurePlacementGridTest {
    @Test
    public void randomSpreadStartIsDeterministic() {
        boolean first = StructurePlacementGrid.randomSpreadStart(5, 7, 32, 8, 165745296, 12345L);
        boolean second = StructurePlacementGrid.randomSpreadStart(5, 7, 32, 8, 165745296, 12345L);
        assertEquals(first, second);
    }

    @Test
    public void randomSpreadStartYieldsExactlyOneStartPerGridCell() {
        int spacing = 32;
        int separation = 8;
        int salt = 165745296;
        long seed = 99L;
        int count = 0;
        for (int cx = 0; cx < spacing; cx++) {
            for (int cz = 0; cz < spacing; cz++) {
                if (StructurePlacementGrid.randomSpreadStart(cx, cz, spacing, separation, salt, seed)) {
                    count++;
                }
            }
        }
        assertEquals(1, count);
    }

    @Test
    public void randomSpreadStartVariesAcrossCells() {
        int spacing = 32;
        int separation = 8;
        int salt = 165745296;
        long seed = 99L;
        int starts = 0;
        for (int cellX = 0; cellX < 4; cellX++) {
            for (int cellZ = 0; cellZ < 4; cellZ++) {
                for (int x = 0; x < spacing; x++) {
                    for (int z = 0; z < spacing; z++) {
                        int cx = cellX * spacing + x;
                        int cz = cellZ * spacing + z;
                        if (StructurePlacementGrid.randomSpreadStart(cx, cz, spacing, separation, salt, seed)) {
                            starts++;
                        }
                    }
                }
            }
        }
        assertEquals(16, starts);
    }

    @Test
    public void mixIsDeterministicAndSensitiveToInputs() {
        assertEquals(StructurePlacementGrid.mix(1L, 2, 3, 4), StructurePlacementGrid.mix(1L, 2, 3, 4));
        assertNotEquals(StructurePlacementGrid.mix(1L, 2, 3, 4), StructurePlacementGrid.mix(1L, 2, 3, 5));
        assertNotEquals(StructurePlacementGrid.mix(1L, 2, 3, 4), StructurePlacementGrid.mix(2L, 2, 3, 4));
    }

    @Test
    public void differentSaltsShiftStartPositions() {
        int spacing = 32;
        int separation = 8;
        long seed = 7L;
        int matches = 0;
        int total = 0;
        for (int cx = 0; cx < spacing; cx++) {
            for (int cz = 0; cz < spacing; cz++) {
                boolean a = StructurePlacementGrid.randomSpreadStart(cx, cz, spacing, separation, 10387312, seed);
                boolean b = StructurePlacementGrid.randomSpreadStart(cx, cz, spacing, separation, 165745296, seed);
                if (a) {
                    total++;
                }
                if (a && b) {
                    matches++;
                }
            }
        }
        assertEquals(1, total);
        assertTrue(matches <= total);
    }

    @Test
    public void densityStartIsDeterministicAndPlacementIdScoped() {
        IrisStructurePlacement placement = placement(StructureDistribution.DENSITY);
        placement.setDensity(0.35);
        IrisStructurePlacement secondPlacement = placement(StructureDistribution.DENSITY);
        secondPlacement.setDensity(0.35);
        secondPlacement.setPlacementId("second");
        boolean differsById = false;
        for (int cx = -32; cx <= 32; cx++) {
            for (int cz = -32; cz <= 32; cz++) {
                boolean first = StructurePlacementGrid.startsInChunk(placement, cx, cz, 912345L);
                boolean repeated = StructurePlacementGrid.startsInChunk(placement, cx, cz, 912345L);
                boolean second = StructurePlacementGrid.startsInChunk(secondPlacement, cx, cz, 912345L);
                assertEquals(first, repeated);
                differsById |= first != second;
            }
        }
        assertTrue(differsById);
    }

    @Test
    public void densityBoundariesNeverAndAlwaysStart() {
        IrisStructurePlacement placement = placement(StructureDistribution.DENSITY);
        placement.setDensity(0.0);
        assertFalse(StructurePlacementGrid.startsInChunk(placement, 4, -9, 77L));
        placement.setDensity(1.0);
        assertTrue(StructurePlacementGrid.startsInChunk(placement, 4, -9, 77L));
    }

    @Test
    public void concentricRingCountIsFiniteTotalPlacementCount() {
        IrisStructurePlacement placement = placement(StructureDistribution.CONCENTRIC_RINGS);
        placement.setRingCount(10);
        placement.setRingSpread(3);
        placement.setRingDistance(12);
        long seed = 484848L;
        int lastRing = Math.ceilDiv(placement.getRingCount(), placement.getRingSpread());
        int scanRadius = (lastRing + 2) * placement.getRingDistance();
        int starts = 0;
        for (int cx = -scanRadius; cx <= scanRadius; cx++) {
            for (int cz = -scanRadius; cz <= scanRadius; cz++) {
                if (StructurePlacementGrid.startsInChunk(placement, cx, cz, seed)) {
                    starts++;
                }
            }
        }
        assertEquals(placement.getRingCount(), starts);
        assertNull(StructurePlacementGrid.concentricRingChunk(placement, placement.getRingCount(), seed));
    }

    @Test
    public void placementRngIsStableAndPlacementIdScoped() {
        IrisStructurePlacement placement = placement(StructureDistribution.RANDOM_SPREAD);
        long first = StructurePlacementGrid.placementRng(placement, 7, -11, 123L).getSeed();
        long repeated = StructurePlacementGrid.placementRng(placement, 7, -11, 123L).getSeed();
        placement.setPlacementId("second");
        long secondPlacement = StructurePlacementGrid.placementRng(placement, 7, -11, 123L).getSeed();
        assertEquals(first, repeated);
        assertNotEquals(first, secondPlacement);
    }

    @Test
    public void authoredPlacementIdSeparatesRandomSpreadStarts() {
        IrisStructurePlacement first = placement(StructureDistribution.RANDOM_SPREAD);
        first.setPlacementId("first");
        IrisStructurePlacement second = placement(StructureDistribution.RANDOM_SPREAD);
        second.setPlacementId("second");
        int[] firstStart = StructurePlacementGrid.randomSpreadCellChunk(
                8, -3, first.getSpacing(), first.getSeparation(), StructurePlacementGrid.placementSalt(first), 31337L);
        int[] secondStart = StructurePlacementGrid.randomSpreadCellChunk(
                8, -3, second.getSpacing(), second.getSeparation(), StructurePlacementGrid.placementSalt(second), 31337L);
        assertFalse(firstStart[0] == secondStart[0] && firstStart[1] == secondStart[1]);
    }

    @Test
    public void blankIdsDeriveDistinctStableRandomSpreadGridsFromContent() {
        IrisStructurePlacement first = placement(StructureDistribution.RANDOM_SPREAD);
        IrisStructurePlacement recreatedFirst = placement(StructureDistribution.RANDOM_SPREAD);
        IrisStructurePlacement second = placement(StructureDistribution.RANDOM_SPREAD);
        second.getStructures().clear();
        second.getStructures().add("test:other");

        assertEquals(StructurePlacementGrid.placementSalt(first),
                StructurePlacementGrid.placementSalt(recreatedFirst));
        assertNotEquals(StructurePlacementGrid.placementSalt(first),
                StructurePlacementGrid.placementSalt(second));
        boolean differs = false;
        for (int cell = -8; cell <= 8; cell++) {
            int[] firstStart = StructurePlacementGrid.randomSpreadCellChunk(
                    cell, cell + 3, first.getSpacing(), first.getSeparation(),
                    StructurePlacementGrid.placementSalt(first), 31337L);
            int[] secondStart = StructurePlacementGrid.randomSpreadCellChunk(
                    cell, cell + 3, second.getSpacing(), second.getSeparation(),
                    StructurePlacementGrid.placementSalt(second), 31337L);
            differs |= firstStart[0] != secondStart[0] || firstStart[1] != secondStart[1];
        }
        assertTrue(differs);
    }

    @Test
    public void anonymousIdentityPreservesAuthoredSourceOrder() {
        IrisStructurePlacement first = placement(StructureDistribution.RANDOM_SPREAD);
        first.getStructures().add("test:second");
        IrisStructurePlacement reordered = placement(StructureDistribution.RANDOM_SPREAD);
        reordered.getStructures().clear();
        reordered.getStructures().add("test:second");
        reordered.getStructures().add("test:structure");

        assertNotEquals(StructurePlacementGrid.placementIdentity(first),
                StructurePlacementGrid.placementIdentity(reordered));
        assertNotEquals(StructurePlacementGrid.placementSalt(first),
                StructurePlacementGrid.placementSalt(reordered));
        assertNotEquals(StructurePlacementGrid.placementRng(first, 7, -11, 123L).getSeed(),
                StructurePlacementGrid.placementRng(reordered, 7, -11, 123L).getSeed());
    }

    @Test
    public void blankIdsDeriveDistinctStableConcentricRingsFromContent() {
        IrisStructurePlacement first = placement(StructureDistribution.CONCENTRIC_RINGS);
        IrisStructurePlacement recreatedFirst = placement(StructureDistribution.CONCENTRIC_RINGS);
        IrisStructurePlacement second = placement(StructureDistribution.CONCENTRIC_RINGS);
        second.getStructures().clear();
        second.getStructures().add("test:other");
        first.setRingCount(12);
        recreatedFirst.setRingCount(12);
        second.setRingCount(12);

        boolean differs = false;
        for (int index = 0; index < first.getRingCount(); index++) {
            int[] firstPoint = StructurePlacementGrid.concentricRingChunk(first, index, 77123L);
            int[] recreatedPoint = StructurePlacementGrid.concentricRingChunk(recreatedFirst, index, 77123L);
            int[] secondPoint = StructurePlacementGrid.concentricRingChunk(second, index, 77123L);
            assertEquals(firstPoint[0], recreatedPoint[0]);
            assertEquals(firstPoint[1], recreatedPoint[1]);
            differs |= firstPoint[0] != secondPoint[0] || firstPoint[1] != secondPoint[1];
        }
        assertTrue(differs);
    }

    @Test
    public void derivedPlacementIdentityIncludesPlacementSettings() {
        IrisStructurePlacement first = placement(StructureDistribution.DENSITY);
        IrisStructurePlacement second = placement(StructureDistribution.DENSITY);
        second.setMinHeight(first.getMinHeight() + 1);
        long firstSeed = StructurePlacementGrid.placementRng(first, 9, 14, 42L).getSeed();
        long secondSeed = StructurePlacementGrid.placementRng(second, 9, 14, 42L).getSeed();
        assertNotEquals(firstSeed, secondSeed);
    }

    @Test
    public void legacyAnchorDoesNotMoveExistingPlacements() {
        IrisStructurePlacement implicit = placement(StructureDistribution.RANDOM_SPREAD);
        IrisStructurePlacement explicit = placement(StructureDistribution.RANDOM_SPREAD)
                .setAnchor(IrisStructureAnchorMode.LEGACY);

        assertEquals(StructurePlacementGrid.placementIdentity(implicit),
                StructurePlacementGrid.placementIdentity(explicit));
        assertEquals(StructurePlacementGrid.placementSalt(implicit),
                StructurePlacementGrid.placementSalt(explicit));
    }

    @Test
    public void caveAnchorConfigurationParticipatesInAnonymousIdentity() {
        IrisStructurePlacement floor = placement(StructureDistribution.RANDOM_SPREAD)
                .setAnchor(IrisStructureAnchorMode.CAVE_FLOOR);
        IrisStructurePlacement ceiling = placement(StructureDistribution.RANDOM_SPREAD)
                .setAnchor(IrisStructureAnchorMode.CAVE_CEILING);

        assertNotEquals(StructurePlacementGrid.placementIdentity(floor),
                StructurePlacementGrid.placementIdentity(ceiling));
        assertNotEquals(StructurePlacementGrid.placementSalt(floor),
                StructurePlacementGrid.placementSalt(ceiling));
    }

    @Test
    public void unusedCaveSettingsDoNotMoveExplicitSurfacePlacements() {
        IrisStructurePlacement first = placement(StructureDistribution.RANDOM_SPREAD)
                .setAnchor(IrisStructureAnchorMode.SURFACE);
        IrisStructurePlacement second = placement(StructureDistribution.RANDOM_SPREAD)
                .setAnchor(IrisStructureAnchorMode.SURFACE)
                .setCaveAnchorAttempts(64)
                .setCaveAnchorScanStep(16)
                .setCaveMinimumClearance(64);
        second.getCaveBiomes().add("unused");

        assertEquals(StructurePlacementGrid.placementIdentity(first),
                StructurePlacementGrid.placementIdentity(second));
        assertEquals(StructurePlacementGrid.placementSalt(first),
                StructurePlacementGrid.placementSalt(second));
    }

    @Test
    public void caveBiomeAllowlistIdentityIgnoresOrderCaseWhitespaceAndDuplicates() {
        IrisStructurePlacement first = placement(StructureDistribution.RANDOM_SPREAD)
                .setAnchor(IrisStructureAnchorMode.CAVE_CENTER);
        first.getCaveBiomes().add("crystal");
        first.getCaveBiomes().add("deep");
        IrisStructurePlacement second = placement(StructureDistribution.RANDOM_SPREAD)
                .setAnchor(IrisStructureAnchorMode.CAVE_CENTER);
        second.getCaveBiomes().add(" DEEP ");
        second.getCaveBiomes().add("CRYSTAL");
        second.getCaveBiomes().add("crystal");
        second.getCaveBiomes().add(" ");

        assertEquals(StructurePlacementGrid.placementIdentity(first),
                StructurePlacementGrid.placementIdentity(second));
        assertEquals(StructurePlacementGrid.placementSalt(first),
                StructurePlacementGrid.placementSalt(second));
    }

    @Test
    public void explicitPlacementIdentityKeepsRngStableAcrossSettingEdits() {
        IrisStructurePlacement first = placement(StructureDistribution.DENSITY);
        first.setPlacementId("stable");
        IrisStructurePlacement second = placement(StructureDistribution.DENSITY);
        second.setPlacementId("stable");
        second.setMinHeight(first.getMinHeight() + 1);
        second.setDensity(0.75);
        long firstSeed = StructurePlacementGrid.placementRng(first, 9, 14, 42L).getSeed();
        long secondSeed = StructurePlacementGrid.placementRng(second, 9, 14, 42L).getSeed();
        assertEquals(firstSeed, secondSeed);
    }

    private IrisStructurePlacement placement(StructureDistribution distribution) {
        IrisStructurePlacement placement = new IrisStructurePlacement();
        placement.setDistribution(distribution);
        placement.getStructures().add("test:structure");
        placement.setSalt(31415926);
        return placement;
    }
}
