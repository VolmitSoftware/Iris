package art.arcane.iris.modded;

import art.arcane.iris.world.entity.IrisEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import org.junit.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModdedEntitySpawnerParityTest {
    @Test
    public void collisionCheckUsesPaperInclusiveIntegerDimensions() {
        Set<BlockPos> checked = new HashSet<>();

        boolean clear = ModdedEntitySpawner.isAreaClearForSpawn(
                10, 64, 20, 0.6F, 1.95F, position -> {
                    checked.add(position);
                    return true;
                });

        assertTrue(clear);
        assertEquals(Set.of(new BlockPos(10, 64, 20), new BlockPos(10, 65, 20)), checked);
    }

    @Test
    public void collisionCheckCoversWideEntityVolumeAndRejectsOneBlockedCell() {
        AtomicInteger checked = new AtomicInteger();
        boolean fullyClear = ModdedEntitySpawner.isAreaClearForSpawn(
                0, 10, 0, 4F, 3.2F, position -> {
                    checked.incrementAndGet();
                    return true;
                });

        assertTrue(fullyClear);
        assertEquals(100, checked.get());

        BlockPos obstruction = new BlockPos(2, 12, -2);

        boolean clear = ModdedEntitySpawner.isAreaClearForSpawn(
                0, 10, 0, 4F, 3.2F, position -> !position.equals(obstruction));

        assertFalse(clear);
    }

    @Test
    public void spawnSafetyRequiresTheTargetChunkAndAllEightNeighbors() {
        Set<Long> loaded = new HashSet<>();
        for (int x = 4; x <= 6; x++) {
            for (int z = -3; z <= -1; z++) {
                loaded.add(pack(x, z));
            }
        }

        assertTrue(ModdedEntitySpawner.allNeighborChunksLoaded(5, -2,
                (x, z) -> loaded.contains(pack(x, z))));

        loaded.remove(pack(4, -3));

        assertFalse(ModdedEntitySpawner.allNeighborChunksLoaded(5, -2,
                (x, z) -> loaded.contains(pack(x, z))));
    }

    @Test
    public void onlyAiFalseUsesVanillaNoAi() {
        assertFalse(ModdedEntitySpawner.shouldDisableAi(true));
        assertTrue(ModdedEntitySpawner.shouldDisableAi(false));
    }

    @Test
    public void piglinAndZoglinUseTheVirtualMobBabyContract() throws NoSuchMethodException {
        assertTrue(Mob.class.isAssignableFrom(Piglin.class));
        assertTrue(Mob.class.isAssignableFrom(Zoglin.class));
        assertEquals(Piglin.class, Piglin.class.getMethod("setBaby", boolean.class).getDeclaringClass());
        assertEquals(Zoglin.class, Zoglin.class.getMethod("setBaby", boolean.class).getDeclaringClass());
    }

    @Test
    public void everyAcceptedBukkitSpawnReasonHasAnExplicitNmsMapping() {
        Map<String, EntitySpawnReason> expected = Map.ofEntries(
                Map.entry("NATURAL", EntitySpawnReason.NATURAL),
                Map.entry("JOCKEY", EntitySpawnReason.JOCKEY),
                Map.entry("CHUNK_GEN", EntitySpawnReason.CHUNK_GENERATION),
                Map.entry("SPAWNER", EntitySpawnReason.SPAWNER),
                Map.entry("TRIAL_SPAWNER", EntitySpawnReason.TRIAL_SPAWNER),
                Map.entry("EGG", EntitySpawnReason.TRIGGERED),
                Map.entry("SPAWNER_EGG", EntitySpawnReason.SPAWN_ITEM_USE),
                Map.entry("LIGHTNING", EntitySpawnReason.EVENT),
                Map.entry("BUILD_SNOWMAN", EntitySpawnReason.TRIGGERED),
                Map.entry("BUILD_IRONGOLEM", EntitySpawnReason.TRIGGERED),
                Map.entry("BUILD_COPPERGOLEM", EntitySpawnReason.TRIGGERED),
                Map.entry("BUILD_WITHER", EntitySpawnReason.TRIGGERED),
                Map.entry("VILLAGE_DEFENSE", EntitySpawnReason.MOB_SUMMONED),
                Map.entry("VILLAGE_INVASION", EntitySpawnReason.EVENT),
                Map.entry("BREEDING", EntitySpawnReason.BREEDING),
                Map.entry("SLIME_SPLIT", EntitySpawnReason.TRIGGERED),
                Map.entry("REINFORCEMENTS", EntitySpawnReason.REINFORCEMENT),
                Map.entry("NETHER_PORTAL", EntitySpawnReason.STRUCTURE),
                Map.entry("DISPENSE_EGG", EntitySpawnReason.DISPENSER),
                Map.entry("INFECTION", EntitySpawnReason.CONVERSION),
                Map.entry("CURED", EntitySpawnReason.CONVERSION),
                Map.entry("OCELOT_BABY", EntitySpawnReason.BREEDING),
                Map.entry("SILVERFISH_BLOCK", EntitySpawnReason.TRIGGERED),
                Map.entry("MOUNT", EntitySpawnReason.JOCKEY),
                Map.entry("TRAP", EntitySpawnReason.TRIGGERED),
                Map.entry("ENDER_PEARL", EntitySpawnReason.TRIGGERED),
                Map.entry("SHOULDER_ENTITY", EntitySpawnReason.LOAD),
                Map.entry("DROWNED", EntitySpawnReason.CONVERSION),
                Map.entry("SHEARED", EntitySpawnReason.CONVERSION),
                Map.entry("EXPLOSION", EntitySpawnReason.TRIGGERED),
                Map.entry("RAID", EntitySpawnReason.EVENT),
                Map.entry("PATROL", EntitySpawnReason.PATROL),
                Map.entry("BEEHIVE", EntitySpawnReason.LOAD),
                Map.entry("PIGLIN_ZOMBIFIED", EntitySpawnReason.CONVERSION),
                Map.entry("SPELL", EntitySpawnReason.MOB_SUMMONED),
                Map.entry("FROZEN", EntitySpawnReason.CONVERSION),
                Map.entry("METAMORPHOSIS", EntitySpawnReason.CONVERSION),
                Map.entry("DUPLICATION", EntitySpawnReason.BREEDING),
                Map.entry("COMMAND", EntitySpawnReason.COMMAND),
                Map.entry("ENCHANTMENT", EntitySpawnReason.TRIGGERED),
                Map.entry("OMINOUS_ITEM_SPAWNER", EntitySpawnReason.TRIGGERED),
                Map.entry("BUCKET", EntitySpawnReason.BUCKET),
                Map.entry("POTION_EFFECT", EntitySpawnReason.TRIGGERED),
                Map.entry("REANIMATE", EntitySpawnReason.TRIGGERED),
                Map.entry("REHYDRATION", EntitySpawnReason.BREEDING),
                Map.entry("CUSTOM", EntitySpawnReason.EVENT),
                Map.entry("DEFAULT", EntitySpawnReason.NATURAL));

        assertEquals(47, expected.size());
        for (Map.Entry<String, EntitySpawnReason> entry : expected.entrySet()) {
            assertEquals(entry.getKey(), entry.getValue(), ModdedEntitySpawner.reasonFor(entry.getKey()));
            assertEquals(entry.getKey(), entry.getValue(), ModdedEntitySpawner.reasonFor(entry.getKey().toLowerCase()));
        }
    }

    @Test
    public void invalidConfiguredSpawnReasonIntentionallyDefaultsToNatural() {
        assertEquals(EntitySpawnReason.NATURAL, ModdedEntitySpawner.reasonFor(null));
        assertEquals(EntitySpawnReason.NATURAL, ModdedEntitySpawner.reasonFor(""));
        assertEquals(EntitySpawnReason.NATURAL, ModdedEntitySpawner.reasonFor("not_a_spawn_reason"));
    }

    @Test
    public void entityEffectExposesNeutralRegistryKeys() {
        IrisEffect effect = new IrisEffect()
                .setSound("minecraft:block.amethyst_block.chime")
                .setParticleEffect("minecraft:flame");

        assertEquals("minecraft:block.amethyst_block.chime", effect.getSoundKey());
        assertEquals("minecraft:flame", effect.getParticleEffectKey());
    }

    private static long pack(int x, int z) {
        return (((long) x) & 0xFFFFFFFFL) | ((((long) z) & 0xFFFFFFFFL) << 32);
    }
}
