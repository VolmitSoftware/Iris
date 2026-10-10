package art.arcane.iris.builtin.anchor;

import org.junit.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/** The data each place mode carries reproduces what the stages decided by comparing modes. */
public class ObjectPlaceModeDataTest {
    @Test
    public void floatingIslandsDropGroundStiltsAndRawStamps() {
        Map<ObjectPlaceMode, ObjectPlaceMode> expected = new EnumMap<>(ObjectPlaceMode.class);
        for (ObjectPlaceMode mode : ObjectPlaceMode.values()) {
            expected.put(mode, mode);
        }
        expected.put(ObjectPlaceMode.STILT, ObjectPlaceMode.MAX_HEIGHT);
        expected.put(ObjectPlaceMode.FAST_STILT, ObjectPlaceMode.FAST_MAX_HEIGHT);
        expected.put(ObjectPlaceMode.MIN_STILT, ObjectPlaceMode.MIN_HEIGHT);
        expected.put(ObjectPlaceMode.FAST_MIN_STILT, ObjectPlaceMode.FAST_MIN_HEIGHT);
        expected.put(ObjectPlaceMode.CENTER_STILT, ObjectPlaceMode.CENTER_HEIGHT);
        expected.put(ObjectPlaceMode.ERODE_STILT, ObjectPlaceMode.MAX_HEIGHT);
        expected.put(ObjectPlaceMode.STRUCTURE_PIECE, ObjectPlaceMode.CENTER_HEIGHT);

        for (ObjectPlaceMode mode : ObjectPlaceMode.values()) {
            assertEquals(mode.name(), expected.get(mode), mode.onFloatingIsland());
        }
    }

    @Test
    public void traitsBelongToTheModesTheStagesSingledOut() {
        assertEquals(EnumSet.of(ObjectPlaceMode.STRUCTURE_PIECE), modesWith(PlaceTrait.RAW_STAMP));
        assertEquals(EnumSet.of(ObjectPlaceMode.STRUCTURE_PIECE, ObjectPlaceMode.FLOATING), modesWith(PlaceTrait.FREE_HEIGHT));
        assertEquals(EnumSet.of(ObjectPlaceMode.CEILING_HANG), modesWith(PlaceTrait.UPSIDE_DOWN));
        assertEquals(EnumSet.of(ObjectPlaceMode.ORGANIC_STILT, ObjectPlaceMode.CEILING_HANG), modesWith(PlaceTrait.CAVE_ANCHORED));
    }

    @Test
    public void vacuumModesBendTheTerrainWithTheirOwnProfile() {
        Set<ObjectPlaceMode> bending = EnumSet.noneOf(ObjectPlaceMode.class);
        for (ObjectPlaceMode mode : ObjectPlaceMode.values()) {
            if (mode.placeMode().bendsTerrain()) {
                bending.add(mode);
            }
        }

        assertEquals(EnumSet.of(ObjectPlaceMode.VACUUM, ObjectPlaceMode.VACUUM_HIGH, ObjectPlaceMode.VACUUM_FAST,
                ObjectPlaceMode.VACUUM_ORGANIC, ObjectPlaceMode.VACUUM_WAVY), bending);
        assertEquals(VacuumProfile.Edge.JITTERED, ObjectPlaceMode.VACUUM_ORGANIC.placeMode().vacuum().edge());
        assertEquals(VacuumProfile.Edge.WAVY, ObjectPlaceMode.VACUUM_WAVY.placeMode().vacuum().edge());
        assertEquals(AnchorSampling.SURFACE, ObjectPlaceMode.PAINT.placeMode().anchor());
    }

    private static Set<ObjectPlaceMode> modesWith(PlaceTrait trait) {
        Set<ObjectPlaceMode> modes = EnumSet.noneOf(ObjectPlaceMode.class);
        for (ObjectPlaceMode mode : ObjectPlaceMode.values()) {
            if (mode.placeMode().has(trait)) {
                modes.add(mode);
            }
        }
        return modes;
    }
}
