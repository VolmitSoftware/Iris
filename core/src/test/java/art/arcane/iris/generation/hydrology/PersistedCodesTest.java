package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.query.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.water.PondKind;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import org.junit.Test;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PersistedCodesTest {
    private static final Map<HydrologyFeatureType, Integer> FEATURE_TYPE_CODES = Map.ofEntries(
            Map.entry(HydrologyFeatureType.STREAM, 0),
            Map.entry(HydrologyFeatureType.RIVER, 1),
            Map.entry(HydrologyFeatureType.MAJOR_RIVER, 2),
            Map.entry(HydrologyFeatureType.CASCADE, 3),
            Map.entry(HydrologyFeatureType.WATERFALL, 4),
            Map.entry(HydrologyFeatureType.MOUTH, 5),
            Map.entry(HydrologyFeatureType.POND, 6),
            Map.entry(HydrologyFeatureType.LAKE, 7),
            Map.entry(HydrologyFeatureType.DAM, 8),
            Map.entry(HydrologyFeatureType.SEA_INLET, 9),
            Map.entry(HydrologyFeatureType.CAVE_POOL, 10),
            Map.entry(HydrologyFeatureType.GROTTO, 11));
    private static final Map<ReachStyle, Integer> REACH_STYLE_CODES = Map.of(
            ReachStyle.RUN, 0,
            ReachStyle.RIFFLE, 1,
            ReachStyle.CASCADE, 2,
            ReachStyle.WATERFALL, 3,
            ReachStyle.POND_OUTLET, 4,
            ReachStyle.MOUTH, 5,
            ReachStyle.TIDAL, 6);
    private static final Map<PondKind, Integer> POND_KIND_CODES = Map.of(
            PondKind.NATURAL, 0,
            PondKind.SEEDED, 1,
            PondKind.CHAINED, 2,
            PondKind.LAKE, 3);

    @Test
    public void codesAreUniqueAndPinned() {
        assertPinned(HydrologyFeatureType.class, FEATURE_TYPE_CODES, HydrologyFeatureType::code, HydrologyFeatureType::fromCode);
        assertPinned(ReachStyle.class, REACH_STYLE_CODES, ReachStyle::code, ReachStyle::fromCode);
        assertPinned(PondKind.class, POND_KIND_CODES, PondKind::code, PondKind::fromCode);
    }

    @Test
    public void unknownCodesFailClosed() {
        assertUnknown("HydrologyFeatureType", FEATURE_TYPE_CODES, HydrologyFeatureType::fromCode);
        assertUnknown("ReachStyle", REACH_STYLE_CODES, ReachStyle::fromCode);
        assertUnknown("PondKind", POND_KIND_CODES, PondKind::fromCode);
    }

    @Test
    public void conflictingCodesAreRejected() {
        try {
            PersistedCodes.of(SharedCode.class, SharedCode::code);
            fail("two constants share code 7");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("SharedCode") && expected.getMessage().contains("7"));
        }
        try {
            PersistedCodes.of(WideCode.class, WideCode::code);
            fail("code 256 does not fit one byte");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("WideCode") && expected.getMessage().contains("256"));
        }
    }

    private static <E extends Enum<E>> void assertPinned(Class<E> type, Map<E, Integer> pins, ToIntFunction<E> code,
                                                         IntFunction<E> fromCode) {
        assertEquals(type.getSimpleName() + " constants need a pinned code", EnumSet.allOf(type), pins.keySet());
        Set<Integer> seen = new HashSet<>();
        for (E constant : type.getEnumConstants()) {
            int pinned = pins.get(constant);
            assertEquals(type.getSimpleName() + "." + constant.name(), pinned, code.applyAsInt(constant));
            assertTrue(type.getSimpleName() + " reuses code " + pinned, seen.add(pinned));
            assertSame(constant, fromCode.apply(pinned));
        }
    }

    private static <E extends Enum<E>> void assertUnknown(String name, Map<E, Integer> pins, IntFunction<E> fromCode) {
        int unused = Collections.max(pins.values()) + 1;
        for (int code : new int[]{-1, unused, 256}) {
            try {
                fromCode.apply(code);
                fail(name + " decoded unknown code " + code);
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains(name) && expected.getMessage().contains(Integer.toString(code)));
            }
        }
    }

    private enum SharedCode {
        FIRST(7),
        SECOND(7);

        private final int code;

        SharedCode(int code) {
            this.code = code;
        }

        private int code() {
            return code;
        }
    }

    private enum WideCode {
        ONLY(256);

        private final int code;

        WideCode(int code) {
            this.code = code;
        }

        private int code() {
            return code;
        }
    }
}
