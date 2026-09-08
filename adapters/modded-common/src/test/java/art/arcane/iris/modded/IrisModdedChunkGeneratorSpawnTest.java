package art.arcane.iris.modded;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.junit.Test;
import org.junit.BeforeClass;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class IrisModdedChunkGeneratorSpawnTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void explicitSpawnsReplaceMatchingVanillaTypesAndPreserveOthers() {
        MobSpawnSettings.SpawnerData zombie = new MobSpawnSettings.SpawnerData(EntityTypes.ZOMBIE, 1, 4);
        MobSpawnSettings.SpawnerData vanillaSlime = new MobSpawnSettings.SpawnerData(EntityTypes.SLIME, 1, 1);
        MobSpawnSettings.SpawnerData explicitSlime = new MobSpawnSettings.SpawnerData(EntityTypes.SLIME, 2, 5);
        MobSpawnSettings.SpawnerData explicitCow = new MobSpawnSettings.SpawnerData(EntityTypes.COW, 2, 4);
        WeightedList<MobSpawnSettings.SpawnerData> vanilla = WeightedList.of(List.of(
                new Weighted<>(zombie, 100),
                new Weighted<>(vanillaSlime, 1)));
        WeightedList<MobSpawnSettings.SpawnerData> explicit = WeightedList.of(List.of(
                new Weighted<>(explicitSlime, 7),
                new Weighted<>(explicitCow, 3)));

        WeightedList<MobSpawnSettings.SpawnerData> merged = NativeSpawnTableMerger.merge(vanilla, explicit);
        List<Weighted<MobSpawnSettings.SpawnerData>> entries = merged.unwrap();

        assertEquals(3, entries.size());
        assertEquals(EntityTypes.ZOMBIE, entries.get(0).value().type());
        assertEquals(100, entries.get(0).weight());
        assertEquals(explicitSlime, entries.get(1).value());
        assertEquals(7, entries.get(1).weight());
        assertEquals(explicitCow, entries.get(2).value());
        assertEquals(3, entries.get(2).weight());
    }
}
