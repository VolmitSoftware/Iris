package art.arcane.iris.platform.bukkit.nms.v26_3_R1;

import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.generation.biome.IrisBiomeCustomSpawn;
import art.arcane.iris.generation.biome.IrisBiomeCustomSpawnType;
import art.arcane.iris.pack.datapack.v263.DataFixerV263;
import art.arcane.volmlib.util.collection.KList;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CustomBiomeCodecTest {
    private static HolderLookup.Provider registries;

    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = VanillaRegistries.createWorldLookup();
    }

    @Test
    public void nativeCodecAcceptsCustomColorsAndSpawnCounts() {
        IrisBiomeCustom custom = new IrisBiomeCustom();
        custom.setId("codec_test");
        custom.setWaterColor("#102030");
        custom.setFogColor("#405060");
        custom.setSpawnRarity(2);
        IrisBiomeCustomSpawn spawn = new IrisBiomeCustomSpawn();
        spawn.setType("minecraft:zombie");
        spawn.setGroup(IrisBiomeCustomSpawnType.MONSTER);
        spawn.setMinCount(2);
        spawn.setMaxCount(4);
        spawn.setWeight(7);
        custom.setSpawns(new KList<>(spawn));
        Biome biome = Biome.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, registries),
                JsonParser.parseString(custom.generateJson(new DataFixerV263()))).getOrThrow();
        MobSpawnSettings settings = biome.getAttributes().applyModifier(EnvironmentAttributes.NATURAL_MOB_SPAWNS,
                EnvironmentAttributes.NATURAL_MOB_SPAWNS.defaultValue());
        MobSpawnSettings.SpawnerData decoded = settings.getMobsToSpawn(MobCategory.MONSTER).unwrap().getFirst().value();
        assertEquals(2, decoded.count().minInclusive());
        assertEquals(4, decoded.count().maxInclusive());
        assertEquals(0x102030, biome.getWaterColor() & 0xFFFFFF);
    }
}
