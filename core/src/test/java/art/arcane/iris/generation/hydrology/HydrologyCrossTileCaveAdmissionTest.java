package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.cave.CavePosition;
import art.arcane.iris.generation.hydrology.cave.CaveVoxel;
import art.arcane.iris.generation.hydrology.cave.CaveVoxelPrecondition;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveMode;
import art.arcane.iris.generation.hydrology.cave.HydrologyCavePlan;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveRejection;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveSource;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HydrologyCrossTileCaveAdmissionTest {
    @Test
    public void compatibleLowerRankClaimRetainsTheCurrentClaim() {
        CavePosition shared = position(4, 20, 7);
        HydrologyCrossTileCaveAdmission.Claim current = claim(
                11L,
                30,
                "water",
                Map.of(shared, HydrologyCaveAction.WET_SOURCE)
        );
        HydrologyCrossTileCaveAdmission.Claim blocker = claim(
                12L,
                10,
                "water",
                Map.of(shared, HydrologyCaveAction.WET_SOURCE)
        );

        HydrologyCrossTileCaveAdmission.Result result = HydrologyCrossTileCaveAdmission.admit(
                4,
                List.of(current),
                List.of(ranked(0, 0, 1, blocker))
        );

        assertTrue(result.rejections().isEmpty());
    }

    @Test
    public void actionConflictYieldsToTheLowerOwnerColorRank() {
        CavePosition shared = position(8, 24, 9);
        HydrologyCrossTileCaveAdmission.Claim current = claim(
                21L,
                40,
                "water",
                Map.of(shared, HydrologyCaveAction.WET_SOURCE)
        );
        HydrologyCrossTileCaveAdmission.Claim blocker = claim(
                22L,
                20,
                "water",
                Map.of(shared, HydrologyCaveAction.FALLING_FLUID)
        );

        HydrologyCrossTileCaveAdmission.Result result = HydrologyCrossTileCaveAdmission.admit(
                4,
                List.of(current),
                List.of(ranked(0, 1, 1, blocker))
        );

        assertEquals(Map.of(21L, 22L), result.loserCourseIdsToWinnerSourceIds());
    }

    @Test
    public void profileConflictYieldsToTheLowerOwnerColorRank() {
        CavePosition shared = position(12, 18, 13);
        HydrologyCrossTileCaveAdmission.Claim current = claim(
                31L,
                40,
                "water",
                Map.of(shared, HydrologyCaveAction.WET_SOURCE)
        );
        HydrologyCrossTileCaveAdmission.Claim blocker = claim(
                32L,
                20,
                "lava",
                Map.of(shared, HydrologyCaveAction.WET_SOURCE)
        );

        HydrologyCrossTileCaveAdmission.Result result = HydrologyCrossTileCaveAdmission.admit(
                5,
                List.of(current),
                List.of(ranked(1, 0, 2, blocker))
        );

        assertEquals(Map.of(31L, 32L), result.loserCourseIdsToWinnerSourceIds());
    }

    @Test
    public void ownerColorRankPrecedesCaveSourceHeadPriority() {
        CavePosition shared = position(16, 28, 17);
        HydrologyCrossTileCaveAdmission.Claim higherHeadCurrent = claim(
                41L,
                90,
                "water",
                Map.of(shared, HydrologyCaveAction.WET_SOURCE)
        );
        HydrologyCrossTileCaveAdmission.Claim lowerHeadBlocker = claim(
                42L,
                10,
                "water",
                Map.of(shared, HydrologyCaveAction.DRY_AIR)
        );

        HydrologyCrossTileCaveAdmission.Result result = HydrologyCrossTileCaveAdmission.admit(
                8,
                List.of(higherHeadCurrent),
                List.of(ranked(-1, -1, 7, lowerHeadBlocker))
        );

        assertEquals(Map.of(41L, 42L), result.loserCourseIdsToWinnerSourceIds());
    }

    @Test
    public void sourcePriorityBreaksTiesWithinOneBlockerRank() {
        CavePosition firstOverlap = position(20, 32, 21);
        CavePosition secondOverlap = position(24, 32, 25);
        HydrologyCrossTileCaveAdmission.Claim current = claim(
                51L,
                30,
                "water",
                Map.of(
                        firstOverlap, HydrologyCaveAction.DRY_AIR,
                        secondOverlap, HydrologyCaveAction.DRY_AIR
                )
        );
        HydrologyCrossTileCaveAdmission.Claim higherHead = claim(
                52L,
                40,
                "water",
                Map.of(firstOverlap, HydrologyCaveAction.WET_SOURCE)
        );
        HydrologyCrossTileCaveAdmission.Claim lowerHead = claim(
                53L,
                20,
                "water",
                Map.of(secondOverlap, HydrologyCaveAction.WET_SOURCE)
        );

        HydrologyCrossTileCaveAdmission.Result result = HydrologyCrossTileCaveAdmission.admit(
                6,
                List.of(current),
                List.of(
                        ranked(1, 1, 2, lowerHead),
                        ranked(1, 1, 2, higherHead)
                )
        );

        assertEquals(Map.of(51L, 52L), result.loserCourseIdsToWinnerSourceIds());
    }

    @Test
    public void negativeOwnersAndInputOrderProduceTheSameDeterministicResult() {
        CavePosition first = position(-257, 22, -513);
        CavePosition second = position(-300, 22, -520);
        HydrologyCrossTileCaveAdmission.Claim currentOne = claim(
                61L,
                30,
                "water",
                Map.of(first, HydrologyCaveAction.WET_SOURCE)
        );
        HydrologyCrossTileCaveAdmission.Claim currentTwo = claim(
                62L,
                20,
                "water",
                Map.of(second, HydrologyCaveAction.WET_SOURCE)
        );
        HydrologyCrossTileCaveAdmission.RankedClaim firstBlocker = ranked(
                -2,
                -3,
                1,
                claim(63L, 10, "water", Map.of(first, HydrologyCaveAction.DRY_AIR))
        );
        HydrologyCrossTileCaveAdmission.RankedClaim secondBlocker = ranked(
                -1,
                -2,
                2,
                claim(64L, 10, "water", Map.of(second, HydrologyCaveAction.DRY_AIR))
        );
        ArrayList<HydrologyCrossTileCaveAdmission.Claim> reverseClaims = new ArrayList<>(List.of(
                currentOne,
                currentTwo
        ));
        Collections.reverse(reverseClaims);
        ArrayList<HydrologyCrossTileCaveAdmission.RankedClaim> reverseBlockers = new ArrayList<>(List.of(
                firstBlocker,
                secondBlocker
        ));
        Collections.reverse(reverseBlockers);

        HydrologyCrossTileCaveAdmission.Result forward = HydrologyCrossTileCaveAdmission.admit(
                5,
                List.of(currentOne, currentTwo),
                List.of(firstBlocker, secondBlocker)
        );
        HydrologyCrossTileCaveAdmission.Result reverse = HydrologyCrossTileCaveAdmission.admit(
                5,
                reverseClaims,
                reverseBlockers
        );

        assertEquals(Map.of(61L, 63L, 62L, 64L), forward.loserCourseIdsToWinnerSourceIds());
        assertEquals(forward, reverse);
    }

    @Test
    public void sameOrHigherRankClaimsCannotBeUsedAsBlockers() {
        HydrologyCrossTileCaveAdmission.Claim current = claim(
                71L,
                30,
                "water",
                Map.of(position(1, 20, 1), HydrologyCaveAction.WET_SOURCE)
        );
        HydrologyCrossTileCaveAdmission.RankedClaim blocker = ranked(
                1,
                1,
                3,
                claim(72L, 20, "water", Map.of(position(1, 20, 1), HydrologyCaveAction.DRY_AIR))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> HydrologyCrossTileCaveAdmission.admit(3, List.of(current), List.of(blocker))
        );
    }

    private HydrologyCrossTileCaveAdmission.RankedClaim ranked(
            int tileX,
            int tileZ,
            int rank,
            HydrologyCrossTileCaveAdmission.Claim claim
    ) {
        return new HydrologyCrossTileCaveAdmission.RankedClaim(
                new HydrologyTileKey(tileX, tileZ),
                rank,
                claim
        );
    }

    private HydrologyCrossTileCaveAdmission.Claim claim(
            long sourceId,
            int waterHeadY,
            String profileKey,
            Map<CavePosition, HydrologyCaveAction> actions
    ) {
        CavePosition entry = position((int) sourceId, waterHeadY + 4, 0);
        CavePosition target = position((int) sourceId, waterHeadY, 0);
        HydrologyCaveSource source = new HydrologyCaveSource(
                sourceId,
                entry,
                target,
                waterHeadY,
                HydrologyCaveMode.CLOSED_COMPONENT
        );
        LinkedHashMap<CavePosition, CaveVoxelPrecondition> preconditions = new LinkedHashMap<>();
        for (CavePosition position : actions.keySet()) {
            preconditions.put(position, new CaveVoxelPrecondition(CaveVoxel.SOLID, false));
        }
        HydrologyCavePlan plan = new HydrologyCavePlan(
                source,
                HydrologyCaveRejection.NONE,
                actions,
                preconditions,
                OptionalLong.empty()
        );
        return new HydrologyCrossTileCaveAdmission.Claim(profileKey, plan);
    }

    private CavePosition position(int x, int y, int z) {
        return new CavePosition(x, y, z);
    }
}
