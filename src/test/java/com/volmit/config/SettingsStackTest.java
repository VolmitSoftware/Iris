package com.volmit.config;

import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.resource.ResourceId;
import com.volmit.support.ColumnSettings;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingsStackTest {
    private static final ResourceId DIMENSION = ResourceId.parse("pack:dimension/overworld");
    private static final ResourceId REGION = ResourceId.parse("pack:region/temperate");
    private static final ResourceId PLAINS = ResourceId.parse("pack:biome/plains");
    private static final ResourceId SWAMP = ResourceId.parse("pack:biome/swamp");
    private static final ResourceId TREE = ResourceId.parse("pack:object/tree");

    @Feature(id = "test:depth", name = "Depth", key = "depth", targets = {Level.DIMENSION, Level.REGION, Level.BIOME, Level.PLACEMENT})
    record Depth(int value) implements IrisFeature {}

    private static Entry<Depth> entry(Level level, ResourceId source, int depth) {
        return new Entry<>(level, source, new Depth(depth));
    }

    @Test
    void sortsByLevelAndKeepsTheOrderWithinALevel() {
        var swamp = entry(Level.BIOME, SWAMP, 3);
        var plains = entry(Level.BIOME, PLAINS, 4);
        var region = entry(Level.REGION, REGION, 2);
        var dimension = entry(Level.DIMENSION, DIMENSION, 1);

        var stack = SettingsStack.of(List.of(swamp, region, plains, dimension));

        assertEquals(List.of(dimension, region, swamp, plains), stack.entries());
        assertEquals(List.of(1, 2, 3, 4), stack.values().stream().map(Depth::value).toList());
    }

    @Test
    void picksTheDimensionALevelOrTheMostSpecificValue() {
        var stack = SettingsStack.of(List.of(entry(Level.PLACEMENT, TREE, 9), entry(Level.BIOME, SWAMP, 3),
                entry(Level.BIOME, PLAINS, 4), entry(Level.DIMENSION, DIMENSION, 1)));

        assertEquals(Optional.of(new Depth(1)), stack.dimension());
        assertEquals(Optional.of(new Depth(3)), stack.level(Level.BIOME), "the first entry of the level");
        assertEquals(Optional.empty(), stack.level(Level.REGION));
        assertEquals(Optional.of(new Depth(9)), stack.mostSpecific());
    }

    @Test
    void anEmptyStackHasNothingToPick() {
        var empty = SettingsStack.<Depth>of(List.of());

        assertSame(SettingsStack.empty(), empty);
        assertTrue(empty.isEmpty());
        assertEquals(Optional.empty(), empty.dimension());
        assertEquals(Optional.empty(), empty.mostSpecific());
        assertEquals(List.of(), empty.values());
    }

    @Test
    void stacksOfEqualEntriesAreEqualAndImmutable() {
        var first = SettingsStack.of(List.of(entry(Level.BIOME, PLAINS, 4)));
        var second = SettingsStack.of(List.of(entry(Level.BIOME, PLAINS, 4)));

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertThrows(UnsupportedOperationException.class, () -> first.entries().clear());
    }

    @Test
    void aChunkDeclaresEveryDistinctEntryOfItsColumnsInLevelOrder() {
        var dimension = entry(Level.DIMENSION, DIMENSION, 1);
        var swamp = entry(Level.BIOME, SWAMP, 3);
        var plains = entry(Level.BIOME, PLAINS, 4);
        var settings = new ColumnSettings((x, z) -> List.of(Math.floorMod(x, 16) < 8 ? swamp : plains, dimension));

        assertEquals(List.of(dimension, swamp, plains), settings.declaredInChunk(Depth.class, -1, 2).entries());
        assertTrue(ColumnSettings.NONE.declaredInChunk(Depth.class, 0, 0).isEmpty());
    }

    @Test
    void aChunkReadsItsOwnColumnsOnly() {
        var plains = entry(Level.BIOME, PLAINS, 4);
        var onlyChunkOneZero = new ColumnSettings((x, z) -> x >= 16 && x < 32 && z >= 0 && z < 16 ? List.of(plains) : List.of());

        assertEquals(List.of(plains), onlyChunkOneZero.declaredInChunk(Depth.class, 1, 0).entries());
        assertTrue(onlyChunkOneZero.declaredInChunk(Depth.class, 0, 0).isEmpty());
        assertTrue(onlyChunkOneZero.declaredInChunk(Depth.class, 2, 0).isEmpty());
    }
}
