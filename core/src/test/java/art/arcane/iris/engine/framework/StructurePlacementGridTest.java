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

package art.arcane.iris.engine.framework;

import art.arcane.iris.builtin.structure.StructurePlacement;
import art.arcane.iris.builtin.structure.StructureAnchorMode;
import art.arcane.iris.builtin.structure.StructureDistribution;
import org.junit.Test;
import art.arcane.iris.engine.object.TestSettings;

import static art.arcane.iris.engine.framework.StructurePlacementFixtures.withAdded;
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
        StructurePlacement placement = placement(StructureDistribution.DENSITY);
        TestSettings.with(placement, "density", 0.35);
        StructurePlacement secondPlacement = placement(StructureDistribution.DENSITY);
        TestSettings.with(secondPlacement, "density", 0.35);
        TestSettings.with(secondPlacement, "placementId", "second");
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
        StructurePlacement placement = placement(StructureDistribution.DENSITY);
        TestSettings.with(placement, "density", 0.0);
        assertFalse(StructurePlacementGrid.startsInChunk(placement, 4, -9, 77L));
        TestSettings.with(placement, "density", 1.0);
        assertTrue(StructurePlacementGrid.startsInChunk(placement, 4, -9, 77L));
    }

    @Test
    public void concentricRingCountIsFiniteTotalPlacementCount() {
        StructurePlacement placement = placement(StructureDistribution.CONCENTRIC_RINGS);
        TestSettings.with(placement, "ringCount", 10);
        TestSettings.with(placement, "ringSpread", 3);
        TestSettings.with(placement, "ringDistance", 12);
        long seed = 484848L;
        int lastRing = Math.ceilDiv(placement.ringCount(), placement.ringSpread());
        int scanRadius = (lastRing + 2) * placement.ringDistance();
        int starts = 0;
        for (int cx = -scanRadius; cx <= scanRadius; cx++) {
            for (int cz = -scanRadius; cz <= scanRadius; cz++) {
                if (StructurePlacementGrid.startsInChunk(placement, cx, cz, seed)) {
                    starts++;
                }
            }
        }
        assertEquals(placement.ringCount(), starts);
        assertNull(StructurePlacementGrid.concentricRingChunk(placement, placement.ringCount(), seed));
    }

    @Test
    public void placementRngIsStableAndPlacementIdScoped() {
        StructurePlacement placement = placement(StructureDistribution.RANDOM_SPREAD);
        long first = StructurePlacementGrid.placementRng(placement, 7, -11, 123L).getSeed();
        long repeated = StructurePlacementGrid.placementRng(placement, 7, -11, 123L).getSeed();
        TestSettings.with(placement, "placementId", "second");
        long secondPlacement = StructurePlacementGrid.placementRng(placement, 7, -11, 123L).getSeed();
        assertEquals(first, repeated);
        assertNotEquals(first, secondPlacement);
    }

    @Test
    public void authoredPlacementIdSeparatesRandomSpreadStarts() {
        StructurePlacement first = placement(StructureDistribution.RANDOM_SPREAD);
        TestSettings.with(first, "placementId", "first");
        StructurePlacement second = placement(StructureDistribution.RANDOM_SPREAD);
        TestSettings.with(second, "placementId", "second");
        int[] firstStart = StructurePlacementGrid.randomSpreadCellChunk(
                8, -3, first.spacing(), first.separation(), StructurePlacementGrid.placementSalt(first), 31337L);
        int[] secondStart = StructurePlacementGrid.randomSpreadCellChunk(
                8, -3, second.spacing(), second.separation(), StructurePlacementGrid.placementSalt(second), 31337L);
        assertFalse(firstStart[0] == secondStart[0] && firstStart[1] == secondStart[1]);
    }

    @Test
    public void blankIdsDeriveDistinctStableRandomSpreadGridsFromContent() {
        StructurePlacement first = placement(StructureDistribution.RANDOM_SPREAD);
        StructurePlacement recreatedFirst = placement(StructureDistribution.RANDOM_SPREAD);
        StructurePlacement second = placement(StructureDistribution.RANDOM_SPREAD);
        TestSettings.with(second, "structures", new java.util.ArrayList<>());
        withAdded(second, "structures", "test:other");

        assertEquals(StructurePlacementGrid.placementSalt(first),
                StructurePlacementGrid.placementSalt(recreatedFirst));
        assertNotEquals(StructurePlacementGrid.placementSalt(first),
                StructurePlacementGrid.placementSalt(second));
        boolean differs = false;
        for (int cell = -8; cell <= 8; cell++) {
            int[] firstStart = StructurePlacementGrid.randomSpreadCellChunk(
                    cell, cell + 3, first.spacing(), first.separation(),
                    StructurePlacementGrid.placementSalt(first), 31337L);
            int[] secondStart = StructurePlacementGrid.randomSpreadCellChunk(
                    cell, cell + 3, second.spacing(), second.separation(),
                    StructurePlacementGrid.placementSalt(second), 31337L);
            differs |= firstStart[0] != secondStart[0] || firstStart[1] != secondStart[1];
        }
        assertTrue(differs);
    }

    @Test
    public void anonymousIdentityPreservesAuthoredSourceOrder() {
        StructurePlacement first = placement(StructureDistribution.RANDOM_SPREAD);
        withAdded(first, "structures", "test:second");
        StructurePlacement reordered = placement(StructureDistribution.RANDOM_SPREAD);
        TestSettings.with(reordered, "structures", new java.util.ArrayList<>());
        withAdded(reordered, "structures", "test:second");
        withAdded(reordered, "structures", "test:structure");

        assertNotEquals(StructurePlacementGrid.placementIdentity(first),
                StructurePlacementGrid.placementIdentity(reordered));
        assertNotEquals(StructurePlacementGrid.placementSalt(first),
                StructurePlacementGrid.placementSalt(reordered));
        assertNotEquals(StructurePlacementGrid.placementRng(first, 7, -11, 123L).getSeed(),
                StructurePlacementGrid.placementRng(reordered, 7, -11, 123L).getSeed());
    }

    @Test
    public void blankIdsDeriveDistinctStableConcentricRingsFromContent() {
        StructurePlacement first = placement(StructureDistribution.CONCENTRIC_RINGS);
        StructurePlacement recreatedFirst = placement(StructureDistribution.CONCENTRIC_RINGS);
        StructurePlacement second = placement(StructureDistribution.CONCENTRIC_RINGS);
        TestSettings.with(second, "structures", new java.util.ArrayList<>());
        withAdded(second, "structures", "test:other");
        TestSettings.with(first, "ringCount", 12);
        TestSettings.with(recreatedFirst, "ringCount", 12);
        TestSettings.with(second, "ringCount", 12);

        boolean differs = false;
        for (int index = 0; index < first.ringCount(); index++) {
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
        StructurePlacement first = placement(StructureDistribution.DENSITY);
        StructurePlacement second = placement(StructureDistribution.DENSITY);
        TestSettings.with(second, "minHeight", first.minHeight() + 1);
        long firstSeed = StructurePlacementGrid.placementRng(first, 9, 14, 42L).getSeed();
        long secondSeed = StructurePlacementGrid.placementRng(second, 9, 14, 42L).getSeed();
        assertNotEquals(firstSeed, secondSeed);
    }

    @Test
    public void legacyAnchorDoesNotMoveExistingPlacements() {
        StructurePlacement implicit = placement(StructureDistribution.RANDOM_SPREAD);
        StructurePlacement explicit = TestSettings.with(placement(StructureDistribution.RANDOM_SPREAD), "anchor", StructureAnchorMode.LEGACY);

        assertEquals(StructurePlacementGrid.placementIdentity(implicit),
                StructurePlacementGrid.placementIdentity(explicit));
        assertEquals(StructurePlacementGrid.placementSalt(implicit),
                StructurePlacementGrid.placementSalt(explicit));
    }

    @Test
    public void caveAnchorConfigurationParticipatesInAnonymousIdentity() {
        StructurePlacement floor = TestSettings.with(placement(StructureDistribution.RANDOM_SPREAD), "anchor", StructureAnchorMode.CAVE_FLOOR);
        StructurePlacement ceiling = TestSettings.with(placement(StructureDistribution.RANDOM_SPREAD), "anchor", StructureAnchorMode.CAVE_CEILING);

        assertNotEquals(StructurePlacementGrid.placementIdentity(floor),
                StructurePlacementGrid.placementIdentity(ceiling));
        assertNotEquals(StructurePlacementGrid.placementSalt(floor),
                StructurePlacementGrid.placementSalt(ceiling));
    }

    @Test
    public void unusedCaveSettingsDoNotMoveExplicitSurfacePlacements() {
        StructurePlacement first = TestSettings.with(placement(StructureDistribution.RANDOM_SPREAD), "anchor", StructureAnchorMode.SURFACE);
        StructurePlacement second = TestSettings.with(placement(StructureDistribution.RANDOM_SPREAD), "anchor", StructureAnchorMode.SURFACE, "caveAnchorAttempts", 64, "caveAnchorScanStep", 16, "caveMinimumClearance", 64);
        withAdded(second, "caveBiomes", "unused");

        assertEquals(StructurePlacementGrid.placementIdentity(first),
                StructurePlacementGrid.placementIdentity(second));
        assertEquals(StructurePlacementGrid.placementSalt(first),
                StructurePlacementGrid.placementSalt(second));
    }

    @Test
    public void caveBiomeAllowlistIdentityIgnoresOrderCaseWhitespaceAndDuplicates() {
        StructurePlacement first = TestSettings.with(placement(StructureDistribution.RANDOM_SPREAD), "anchor", StructureAnchorMode.CAVE_CENTER);
        withAdded(first, "caveBiomes", "crystal");
        withAdded(first, "caveBiomes", "deep");
        StructurePlacement second = TestSettings.with(placement(StructureDistribution.RANDOM_SPREAD), "anchor", StructureAnchorMode.CAVE_CENTER);
        withAdded(second, "caveBiomes", " DEEP ");
        withAdded(second, "caveBiomes", "CRYSTAL");
        withAdded(second, "caveBiomes", "crystal");
        withAdded(second, "caveBiomes", " ");

        assertEquals(StructurePlacementGrid.placementIdentity(first),
                StructurePlacementGrid.placementIdentity(second));
        assertEquals(StructurePlacementGrid.placementSalt(first),
                StructurePlacementGrid.placementSalt(second));
    }

    @Test
    public void explicitPlacementIdentityKeepsRngStableAcrossSettingEdits() {
        StructurePlacement first = placement(StructureDistribution.DENSITY);
        TestSettings.with(first, "placementId", "stable");
        StructurePlacement second = placement(StructureDistribution.DENSITY);
        TestSettings.with(second, "placementId", "stable");
        TestSettings.with(second, "minHeight", first.minHeight() + 1);
        TestSettings.with(second, "density", 0.75);
        long firstSeed = StructurePlacementGrid.placementRng(first, 9, 14, 42L).getSeed();
        long secondSeed = StructurePlacementGrid.placementRng(second, 9, 14, 42L).getSeed();
        assertEquals(firstSeed, secondSeed);
    }

    private StructurePlacement placement(StructureDistribution distribution) {
        StructurePlacement placement = new StructurePlacement();
        TestSettings.with(placement, "distribution", distribution);
        withAdded(placement, "structures", "test:structure");
        TestSettings.with(placement, "salt", 31415926);
        return placement;
    }
}
