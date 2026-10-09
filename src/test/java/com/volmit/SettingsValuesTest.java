package com.volmit;

import com.volmit.block.BlockSpec;
import com.volmit.config.Range;
import com.volmit.noise.NoiseFactory;
import com.volmit.noise.NoiseSampler;
import com.volmit.noise.NoiseStyle;
import com.volmit.noise.StyledRange;
import com.volmit.random.WorldRandom;
import com.volmit.vanilla.VanillaStructureRule;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static com.volmit.vanilla.VanillaStructureRule.*;
import static org.junit.jupiter.api.Assertions.*;

/** The value types settings hold and the rules of vanilla structure hooks. */
class SettingsValuesTest {
    @Test
    void blockSpecsAreValuesWithTheDefaultsPacksGetWhenTheyLeaveOptionsOut() {
        var spec = BlockSpec.of("iron_ore").withProperty("waterlogged", "true").withWeight(3)
                .withBackup(BlockSpec.of("stone")).withTileData(Map.of("Delay", 20.0));

        assertEquals("iron_ore", spec.block());
        assertEquals(Map.of("waterlogged", "true"), spec.data());
        assertEquals(3, spec.weight());
        assertEquals(Optional.of(BlockSpec.of("stone")), spec.backup());
        assertEquals(Map.of("Delay", 20.0), spec.tileData());
        assertEquals(spec, BlockSpec.of("iron_ore").withProperty("waterlogged", "true").withWeight(3)
                .withBackup(BlockSpec.of("stone")).withTileData(Map.of("Delay", 20.0)));
        assertNotEquals(spec, spec.withWeight(1));
        assertEquals(1, BlockSpec.of("stone").weight());
        assertEquals(Optional.empty(), BlockSpec.of("stone").backup());
        assertThrows(UnsupportedOperationException.class, () -> spec.data().clear());
        assertThrows(UnsupportedOperationException.class, () -> spec.tileData().clear());
    }

    @Test
    void noiseStylesNameTheHostsStyleAndAreFlatOnlyWithoutExpressionOrImageMap() {
        var simplex = NoiseStyle.of("SIMPLEX").withZoom(0.08);

        assertEquals("SIMPLEX", simplex.style());
        assertEquals(0.08, simplex.zoom());
        assertEquals(1, NoiseStyle.of("SIMPLEX").zoom());
        assertEquals(simplex, NoiseStyle.of("SIMPLEX").withZoom(0.08));
        assertEquals(simplex.hashCode(), NoiseStyle.of("SIMPLEX").withZoom(0.08).hashCode());
        assertNotEquals(simplex, NoiseStyle.of("SIMPLEX"));
        assertTrue(NoiseStyle.of(NoiseStyle.FLAT).isFlat());
        assertFalse(simplex.isFlat());
        assertEquals(Optional.empty(), simplex.expression());
        assertEquals(Optional.empty(), simplex.fracture());
    }

    @Test
    void styledRangesPickBetweenTheirBoundsWithTheStylesNoise() {
        var asked = new ArrayList<String>();
        NoiseFactory noise = (style, seed) -> {
            asked.add(style.style() + "@" + seed);
            return fixed(0.25);
        };
        var styled = StyledRange.of(10, 30, NoiseStyle.of("SIMPLEX"));

        assertEquals(15, styled.at(noise, 7, 100, 200));
        assertEquals(List.of("SIMPLEX@7"), asked);
        assertEquals(20, StyledRange.of(10, 30, NoiseStyle.of(NoiseStyle.FLAT)).at(noise, 7, 0, 0), "flat: halfway");
        assertEquals(10, StyledRange.of(10, 10, NoiseStyle.of("SIMPLEX")).at(noise, 7, 0, 0), "equal bounds");
        assertEquals(1, asked.size(), "neither flat nor equal bounds build noise");
        assertTrue(StyledRange.of(10, 10, NoiseStyle.of("SIMPLEX")).isFlat());
        assertFalse(styled.isFlat());
    }

    @Test
    void rangesIncludeBothBoundsAndPickWithOneDraw() {
        var range = Range.of(2, 6);

        assertTrue(range.contains(2));
        assertTrue(range.contains(6));
        assertFalse(range.contains(6.5));
        var picks = new WorldRandom(42);
        var expected = new WorldRandom(42);
        assertEquals(2 + expected.nextDouble() * 4, range.pick(picks));
        assertEquals(expected.nextLong(), picks.nextLong(), "one draw per pick");
        assertEquals(3, Range.of(3, 3).pick(picks));
        assertEquals(expected.nextLong(), picks.nextLong(), "no draw when the bounds are equal");
    }

    @Test
    void vanillaStructureRulesCombineBlockingAndPlacingWhateverTheOrder() {
        var expected = Map.of(
                List.of(GENERATE, GENERATE), GENERATE,
                List.of(GENERATE, ALSO_PLACE), ALSO_PLACE,
                List.of(GENERATE, BLOCK), BLOCK,
                List.of(ALSO_PLACE, BLOCK), REPLACE,
                List.of(GENERATE, REPLACE), REPLACE,
                List.of(BLOCK, BLOCK), BLOCK);
        expected.forEach((rules, combined) -> {
            assertEquals(combined, rules.get(0).and(rules.get(1)), rules.toString());
            assertEquals(combined, rules.get(1).and(rules.get(0)), rules.toString());
        });
        for (VanillaStructureRule rule : VanillaStructureRule.values()) {
            assertEquals(rule, rule.and(GENERATE), "GENERATE changes nothing");
        }
        assertTrue(REPLACE.blocksPlatform() && REPLACE.placedByModule());
        assertTrue(BLOCK.blocksPlatform() && !BLOCK.placedByModule());
        assertTrue(!ALSO_PLACE.blocksPlatform() && ALSO_PLACE.placedByModule());
    }

    private static NoiseSampler fixed(double value) {
        return new NoiseSampler() {
            public double sample(double worldX, double worldZ) { return value; }
            public double sample(double worldX, double y, double worldZ) { return value; }
        };
    }
}
