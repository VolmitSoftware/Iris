package art.arcane.iris.modded;

import art.arcane.volmlib.util.math.Rarity;
import art.arcane.iris.world.entity.IrisEntitySpawn;
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

        KList<IrisEntitySpawn> expanded = Rarity.expandWeighted(List.of(common, rare));

        // totalRarity 5 -> common appears 5/1 = 5 times, rare 5/4 = 1 time.
        assertEquals(6, expanded.size());
        assertEquals(5, expanded.stream().filter(entry -> entry == common).count());
        assertEquals(1, expanded.stream().filter(entry -> entry == rare).count());
    }

    @Test
    public void rarityZeroAndNegativeAreClampedToOne() {
        assertEquals(1, Rarity.get(spawn(0)));
        assertEquals(1, Rarity.get(spawn(-5)));
        assertEquals(3, Rarity.get(spawn(3)));
    }
}
