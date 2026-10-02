package art.arcane.iris.generation.block;

import art.arcane.iris.generation.geometry.IrisBlockVector;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class VectorMapDeterminismTest {
    @Test
    public void collisionBucketsAndChunkBoundariesHaveCanonicalOrder() {
        List<IrisBlockVector> positions = new ArrayList<>();
        for (int chunk = -2; chunk <= 2; chunk++) {
            for (int index = 0; index < 16; index++) {
                positions.add(new IrisBlockVector((chunk * 1024) + (index * 64), chunk - 1, index & 1));
            }
        }
        List<String> expected = null;
        for (int fixture = 0; fixture < 64; fixture++) {
            Collections.shuffle(positions, new Random(fixture));
            VectorMap<Integer> map = new VectorMap<>();
            for (IrisBlockVector position : positions) {
                map.put(position, position.getBlockX());
            }
            List<String> observed = walk(map);
            if (expected == null) {
                expected = observed;
            }
            assertEquals("fixture " + fixture, expected, observed);
            assertEquals(observed, walk(map));
        }
    }

    @Test
    public void cachedCursorReflectsReplacementInsertionRemovalAndClear() {
        VectorMap<String> map = new VectorMap<>();
        IrisBlockVector origin = new IrisBlockVector(0, 0, 0);
        map.put(origin, "before");
        assertEquals(List.of("0:0:0:before"), walk(map));
        map.put(origin, "after");
        assertEquals(List.of("0:0:0:after"), walk(map));
        map.computeIfAbsent(new IrisBlockVector(-1, 0, 0), position -> "added");
        assertEquals(List.of("-1:0:0:added", "0:0:0:after"), walk(map));
        map.remove(origin);
        assertEquals(List.of("-1:0:0:added"), walk(map));
        map.clear();
        assertTrue(walk(map).isEmpty());
        map.put(origin, "fresh");
        assertEquals(List.of("0:0:0:fresh"), walk(map));
    }

    @Test
    public void returnedMutablePositionsCannotCorruptCacheOrIteratorRemoval() {
        VectorMap<String> map = new VectorMap<>();
        map.put(new IrisBlockVector(0, 0, 0), "first");
        map.put(new IrisBlockVector(1, 0, 0), "second");
        Iterator<IrisBlockVector> keys = map.keys();
        keys.next().setX(200);
        keys.remove();
        assertFalse(map.containsKey(new IrisBlockVector(0, 0, 0)));
        assertEquals(List.of("1:0:0:second"), walk(map));
        Iterator<Map.Entry<IrisBlockVector, String>> entries = map.iterator();
        entries.next().getKey().setX(300);
        entries.remove();
        assertTrue(map.isEmpty());
    }

    @Test
    public void warmedCursorReusesItsPositionAndDoesNotExposeSnapshotKeys() {
        VectorMap<String> map = new VectorMap<>();
        map.put(new IrisBlockVector(0, 0, 0), "first");
        map.put(new IrisBlockVector(1, 0, 0), "second");
        VectorMap<String>.Cursor cursor = map.cursor();
        assertTrue(cursor.next());
        IrisBlockVector first = cursor.key();
        first.setX(100);
        assertTrue(cursor.next());
        assertSame(first, cursor.key());
        assertEquals(1, cursor.key().getBlockX());
        assertEquals(List.of("0:0:0:first", "1:0:0:second"), walk(map));
    }

    private static <T> List<String> walk(VectorMap<T> map) {
        List<String> output = new ArrayList<>();
        VectorMap<T>.Cursor cursor = map.cursor();
        while (cursor.next()) {
            IrisBlockVector position = cursor.key();
            output.add(position.getBlockX() + ":" + position.getBlockY() + ":" + position.getBlockZ()
                    + ":" + cursor.value());
        }
        return output;
    }
}
