package art.arcane.iris.modded;

import art.arcane.iris.engine.object.IRare;
import art.arcane.iris.engine.object.IrisEntitySpawn;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class ModdedSpawnerRarityParityTest {
    private static IrisEntitySpawn spawn(int rarity) {
        IrisEntitySpawn spawn = new IrisEntitySpawn();
        spawn.setRarity(rarity);
        return spawn;
    }

    @Test
    public void rarityIsAppliedOnceAsPoolWeightOnly() {
        IrisEntitySpawn common = spawn(1);
        IrisEntitySpawn rare = spawn(4);

        KList<IrisEntitySpawn> expanded = IRare.expandWeighted(List.of(common, rare));

        // totalRarity 5 -> common appears 5/1 = 5 times, rare 5/4 = 1 time.
        assertEquals(6, expanded.size());
        assertEquals(5, expanded.stream().filter(entry -> entry == common).count());
        assertEquals(1, expanded.stream().filter(entry -> entry == rare).count());
    }

    @Test
    public void rarityZeroAndNegativeAreClampedToOne() {
        assertEquals(1, IRare.get(spawn(0)));
        assertEquals(1, IRare.get(spawn(-5)));
        assertEquals(3, IRare.get(spawn(3)));
    }
}
