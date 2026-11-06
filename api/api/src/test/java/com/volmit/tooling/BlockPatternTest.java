package com.volmit.tooling;

import com.volmit.block.BlockState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

class BlockPatternTest {
    private static final BlockState GRASS = BlockState.parse("minecraft:grass_block");
    private static final BlockState DIRT = BlockState.parse("minecraft:dirt");

    @Test
    void keepsEachBlockOnceAndWhichColumnHoldsIt() {
        BlockPattern pattern = BlockPattern.of(2, new BlockState[]{GRASS, DIRT, null, BlockState.parse("minecraft:grass_block")});
        assertEquals(List.of(GRASS, DIRT), pattern.blocks());
        assertEquals(OptionalInt.of(0), pattern.pickAt(0, 0));
        assertEquals(OptionalInt.of(1), pattern.pickAt(1, 0));
        assertEquals(OptionalInt.empty(), pattern.pickAt(0, 1));
        assertEquals(OptionalInt.of(0), pattern.pickAt(1, 1));
    }

    @Test
    void needsAColumnForEverySpotOfTheSquare() {
        var short_ = assertThrows(IllegalArgumentException.class, () -> BlockPattern.of(2, new BlockState[3]));
        assertTrue(short_.getMessage().contains("holds 4 columns, not 3"), short_.getMessage());
    }

    @Test
    void placesAreaColumnsFromItsCornerAtItsSpacing() {
        PatternArea area = new PatternArea(-64, 32, 16, 4);
        assertEquals(-56, area.blockX(2));
        assertEquals(44, area.blockZ(3));
        assertThrows(IllegalArgumentException.class, () -> new PatternArea(0, 0, 16, 0));
    }

    @Test
    void anEntryReachesAtLeastOneBlockDeep() {
        assertThrows(IllegalArgumentException.class, () -> new PatternEntry("plains#layer/0", "Layer 1", Optional.of("Plains"), 0, List.of()));
    }

    @Test
    void anEntryWithoutAGroupLeavesItEmptyRatherThanBlank() {
        assertThrows(IllegalArgumentException.class, () -> new PatternEntry("plains#layer/0", "Layer 1", Optional.of(" "), 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SceneEntry("oak", "oak", Optional.of(""), 1));
        assertTrue(new SceneEntry("oak", "oak", Optional.empty(), 1).group().isEmpty());
    }
}
