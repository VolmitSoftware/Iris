package art.arcane.iris.engine.object.entity;

import art.arcane.iris.engine.object.entity.IrisEntitySpawn;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static org.junit.Assert.assertNull;

public class IrisEntitySpawnTest {
    @Test
    public void caveSpawnWithoutSafeMarkerIsSkipped() {
        assertNull(IrisEntitySpawn.selectCaveSpawnLocation(new KList<>(), null, new RNG(1L)));
    }
}
