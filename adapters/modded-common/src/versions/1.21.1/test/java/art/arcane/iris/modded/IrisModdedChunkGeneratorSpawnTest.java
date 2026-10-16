package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeSpawnTableMerger;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.EntityType;
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
        MobSpawnSettings.SpawnerData zombie = new MobSpawnSettings.SpawnerData(EntityType.ZOMBIE, 100, 1, 4);
        MobSpawnSettings.SpawnerData vanillaSlime = new MobSpawnSettings.SpawnerData(EntityType.SLIME, 1, 1, 1);
        MobSpawnSettings.SpawnerData explicitSlime = new MobSpawnSettings.SpawnerData(EntityType.SLIME, 7, 2, 5);
        MobSpawnSettings.SpawnerData explicitCow = new MobSpawnSettings.SpawnerData(EntityType.COW, 3, 2, 4);
        WeightedRandomList<MobSpawnSettings.SpawnerData> vanilla = WeightedRandomList.create(List.of(
                zombie,
                vanillaSlime));
        WeightedRandomList<MobSpawnSettings.SpawnerData> explicit = WeightedRandomList.create(List.of(
                explicitSlime,
                explicitCow));

        WeightedRandomList<MobSpawnSettings.SpawnerData> merged = NativeSpawnTableMerger.merge(vanilla, explicit);
        List<MobSpawnSettings.SpawnerData> entries = merged.unwrap();

        assertEquals(3, entries.size());
        assertEquals(EntityType.ZOMBIE, entries.get(0).type);
        assertEquals(100, entries.get(0).getWeight().asInt());
        assertEquals(explicitSlime, entries.get(1));
        assertEquals(7, entries.get(1).getWeight().asInt());
        assertEquals(explicitCow, entries.get(2));
        assertEquals(3, entries.get(2).getWeight().asInt());
    }
}
