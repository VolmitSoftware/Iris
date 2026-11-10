/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.modded;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.core.content.ContentEntity;
import art.arcane.iris.core.content.ContentItem;
import art.arcane.iris.core.content.ContentSpawns;
import art.arcane.iris.modded.api.ModdedCustomContentRegistry;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.math.RNG;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class ModdedEntitySpawner {
    private static final int PASSENGER_RNG_BASE = 234858;
    private static final int LEASH_RNG_SEED = 234548;
    private static final int PLAYER_EFFECT_RADIUS = 32;
    private static final int RISE_MAX_MOVES = 101;
    private static final String COLOR_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";
    private static final Set<String> WARNED_TYPES = ConcurrentHashMap.newKeySet();
    private static final Set<String> WARNED_ATTRIBUTES = ConcurrentHashMap.newKeySet();
    private static final Set<String> WARNED_EFFECT_SOUNDS = ConcurrentHashMap.newKeySet();
    private static final Set<String> WARNED_EFFECT_PARTICLES = ConcurrentHashMap.newKeySet();
    private static final Set<String> WARNED_PERSISTENCE_TYPES = ConcurrentHashMap.newKeySet();

    private ModdedEntitySpawner() {
    }

    /** Spawns {@code entity}, whose every random choice a module made, at the block; null when it cannot appear. */
    public static Entity spawn(Engine engine, ContentEntity entity, ServerLevel level, int blockX, int blockY, int blockZ) {
        if (engine == null || entity == null || level == null) {
            return null;
        }
        if (!chunksSafe(level, blockX >> 4, blockZ >> 4)) {
            return null;
        }

        double x = blockX + 0.5;
        double y = blockY + 0.5;
        double z = blockZ + 0.5;
        boolean riseEffectActive = entity.riseOutOfGround() && hasPlayersNearby(level, x, y, z);
        int spawnBlockY = riseEffectActive ? blockY - 5 : blockY;
        double spawnY = spawnBlockY + 0.5;

        Entity created = create(entity, level, x, spawnY, z);
        if (created == null) {
            return null;
        }

        if (entity.isSpecial() && !entity.applySettingsToSpecial()) {
            return created;
        }

        applyConfig(engine, entity, created, level, blockX, spawnBlockY, blockZ, riseEffectActive);
        return created;
    }

    private static Entity create(ContentEntity entity, ServerLevel level, double x, double y, double z) {
        if (entity.isSpecial()) {
            return ModdedCustomContentRegistry.spawnMob(level, x, y, z, entity.specialType());
        }

        EntityType<?> type = resolveType(entity.type());
        if (type == null) {
            return null;
        }

        Entity created = type.spawn(level, BlockPos.containing(x, y, z), reasonFor(entity.reason()));
        if (created != null) {
            created.snapTo(x, y, z, 0F, 0F);
        }
        return created;
    }

    private static void applyConfig(Engine engine, ContentEntity spec, Entity entity, ServerLevel level, int blockX, int blockY, int blockZ, boolean riseEffectActive) {
        ContentEntity.Flags flags = spec.flags();
        entity.setCustomName(Component.literal(colorize(spec.customName())));
        entity.setCustomNameVisible(flags.customNameVisible());
        entity.setGlowingTag(flags.glowing());
        entity.setNoGravity(!flags.gravity());
        entity.setInvulnerable(flags.invulnerable());
        entity.setSilent(flags.silent());

        boolean persistent = flags.keepEntity() || forcePersist();
        applyPersistence(entity, persistent);

        applyPassengers(engine, spec, entity, level, blockX, blockY, blockZ);

        if (entity instanceof LivingEntity living) {
            applyAttributes(spec, living, level);
        }

        if (!spec.lootTables().isEmpty()) {
            ModdedDeathLoot.bind(engine, entity, spec.lootTables(), blockX, blockY, blockZ, spec.lootShuffleSeed());
        }

        if (entity instanceof Mob mob) {
            mob.setNoAi(shouldDisableAi(flags.ai()));
            ModdedEntityAwareness.configure(mob, flags.aware());
            mob.setCanPickUpLoot(flags.pickupItems());
            if (!flags.removable()) {
                mob.setPersistenceRequired();
            }
            if (spec.leashHolder() != null) {
                Entity holder = spawn(engine, spec.leashHolder(), level, blockX, blockY, blockZ);
                if (holder != null) {
                    mob.setLeashedTo(holder, true);
                }
            }
        }

        if (entity instanceof LivingEntity equippable) {
            applyEquipment(spec, equippable, level);
        }

        if (flags.baby()) {
            applyBaby(entity);
        }

        if (entity instanceof Panda panda) {
            panda.setMainGene(gene(spec.pandaMainGene()));
            panda.setHiddenGene(gene(spec.pandaHiddenGene()));
        }

        if (entity instanceof Villager villager) {
            ModdedEntityPersistence.configure(villager, true);
        }

        applySpawnEffect(spec.spawnEffect(), entity, level);
        ModdedEntityCommandRunner.run(spec.commands(), level, blockX, blockY, blockZ);
        if (riseEffectActive && entity instanceof LivingEntity living) {
            startRiseEffect(engine, level, entity, living);
        }
    }

    private static void applyPassengers(Engine engine, ContentEntity spec, Entity entity, ServerLevel level, int blockX, int blockY, int blockZ) {
        for (ContentEntity passengerSpec : spec.passengers()) {
            Entity passenger = spawn(engine, passengerSpec, level, blockX, blockY, blockZ);
            if (passenger != null) {
                passenger.startRiding(entity);
            }
        }
    }

    private static void applyAttributes(ContentEntity spec, LivingEntity living, ServerLevel level) {
        if (spec.attributes().isEmpty()) {
            return;
        }

        Registry<Attribute> registry = level.registryAccess().lookupOrThrow(Registries.ATTRIBUTE);
        int index = 0;
        for (ContentItem.Attribute modifier : spec.attributes()) {
            index++;
            Holder<Attribute> holder = ModdedItemTranslator.resolveAttribute(registry, modifier.attribute());
            if (holder == null) {
                if (WARNED_ATTRIBUTES.add(modifier.attribute())) {
                    IrisLogging.warn("Iris entity: unknown attribute '" + modifier.attribute() + "'");
                }
                continue;
            }
            AttributeInstance instance = living.getAttributes().getInstance(holder);
            if (instance == null) {
                continue;
            }
            Identifier id = Identifier.tryParse("iris:" + normalizeName(modifier.name()) + "_" + index);
            if (id == null) {
                continue;
            }
            instance.addOrReplacePermanentModifier(new AttributeModifier(id, modifier.amount(), ModdedItemTranslator.operationFor(modifier.operation())));
        }
    }

    private static void applyEquipment(ContentEntity spec, LivingEntity living, ServerLevel level) {
        ContentEntity.Equipment equipment = spec.equipment();
        setSlot(living, EquipmentSlot.HEAD, equipment.helmet(), level);
        setSlot(living, EquipmentSlot.CHEST, equipment.chestplate(), level);
        setSlot(living, EquipmentSlot.LEGS, equipment.leggings(), level);
        setSlot(living, EquipmentSlot.FEET, equipment.boots(), level);
        setSlot(living, EquipmentSlot.MAINHAND, equipment.mainHand(), level);
        setSlot(living, EquipmentSlot.OFFHAND, equipment.offHand(), level);
    }

    private static void setSlot(LivingEntity living, EquipmentSlot slot, ContentItem item, ServerLevel level) {
        if (item == null) {
            return;
        }
        ItemStack stack = ModdedItemTranslator.stack(item, level);
        if (stack != null && !stack.isEmpty()) {
            living.setItemSlot(slot, stack);
        }
    }

    private static void applyBaby(Entity entity) {
        if (entity instanceof Mob mob) {
            mob.setBaby(true);
        }
    }

    static boolean isAreaClearForSpawn(ServerLevel level, ContentEntity entity, ContentSpawns.Surface surface,
                                       int blockX, int blockY, int blockZ) {
        if (entity.isSpecial()) {
            return true;
        }
        EntityType<?> type = resolveType(entity.type());
        if (type == null) {
            return true;
        }
        if (surface.isFluid()) {
            return isFluidAreaClearForSpawn(blockX, blockY, blockZ, type.getWidth(), type.getHeight(),
                    position -> ModdedWorldManager.matchesSurface(surface, level.getBlockState(position)));
        }
        return isAreaClearForSpawn(blockX, blockY, blockZ, type.getWidth(), type.getHeight(),
                position -> level.getBlockState(position).is(Blocks.AIR));
    }

    static boolean isFluidAreaClearForSpawn(int blockX, int blockY, int blockZ,
                                           float width, float height, Predicate<BlockPos> isFluid) {
        int startX = (int) Math.floor(blockX + 0.5 - width / 2D);
        int endX = (int) Math.floor(Math.nextDown(blockX + 0.5 + width / 2D));
        int endY = (int) Math.floor(Math.nextDown(blockY + 0.5 + height));
        int startZ = (int) Math.floor(blockZ + 0.5 - width / 2D);
        int endZ = (int) Math.floor(Math.nextDown(blockZ + 0.5 + width / 2D));
        for (int x = startX; x <= endX; x++) {
            for (int y = blockY; y <= endY; y++) {
                for (int z = startZ; z <= endZ; z++) {
                    if (!isFluid.test(new BlockPos(x, y, z))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    static boolean isAreaClearForSpawn(int blockX, int blockY, int blockZ, float width, float height, Predicate<BlockPos> isAir) {
        int radius = (int) (width / 2F);
        int endY = blockY + (int) height;
        for (int x = blockX - radius; x <= blockX + radius; x++) {
            for (int y = blockY; y <= endY; y++) {
                for (int z = blockZ - radius; z <= blockZ + radius; z++) {
                    if (!isAir.test(new BlockPos(x, y, z))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    static boolean chunksSafe(ServerLevel level, int chunkX, int chunkZ) {
        return allNeighborChunksLoaded(chunkX, chunkZ,
                (x, z) -> level.getChunkSource().getChunkNow(x, z) != null);
    }

    static boolean allNeighborChunksLoaded(int chunkX, int chunkZ, ChunkLoadedLookup lookup) {
        for (int x = chunkX - 1; x <= chunkX + 1; x++) {
            for (int z = chunkZ - 1; z <= chunkZ + 1; z++) {
                if (!lookup.loaded(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    static boolean shouldDisableAi(boolean ai) {
        return !ai;
    }

    private static boolean hasPlayersNearby(ServerLevel level, double x, double y, double z) {
        AABB bounds = new AABB(
                x - PLAYER_EFFECT_RADIUS,
                y - PLAYER_EFFECT_RADIUS,
                z - PLAYER_EFFECT_RADIUS,
                x + PLAYER_EFFECT_RADIUS,
                y + PLAYER_EFFECT_RADIUS,
                z + PLAYER_EFFECT_RADIUS);
        for (ServerPlayer player : level.players()) {
            if (player.getBoundingBox().intersects(bounds)) {
                return true;
            }
        }
        return false;
    }

    private static void applySpawnEffect(ContentEntity.SpawnEffect effect, Entity entity, ServerLevel level) {
        if (effect == null) {
            return;
        }

        SoundEvent sound = resolveSound(effect.sound());
        if (sound != null) {
            double soundX = entity.getX() + RNG.r.i(-effect.soundDistance(), effect.soundDistance());
            double soundY = entity.getY() + RNG.r.i(-effect.soundDistance(), effect.soundDistance());
            double soundZ = entity.getZ() + RNG.r.i(-effect.soundDistance(), effect.soundDistance());
            level.playSound(null, soundX, soundY, soundZ, sound, SoundSource.MASTER,
                    (float) effect.volume(), (float) RNG.r.d(effect.minPitch(), effect.maxPitch()));
        }

        ParticleOptions particle = resolveParticle(effect.particle());
        if (particle == null) {
            return;
        }

        double addition = RNG.r.d();
        double subtraction = RNG.r.d();
        double particleX = entity.getX() + addition - subtraction + RNG.r.d();
        double particleY = entity.getY() + 0.25 + addition - subtraction + RNG.r.i(effect.particleOffset());
        double particleZ = entity.getZ() + addition - subtraction + RNG.r.d();
        double altX = effect.randomAltX() ? RNG.r.d(-effect.altX(), effect.altX()) : effect.altX();
        double altY = effect.randomAltY() ? RNG.r.d(-effect.altY(), effect.altY()) : effect.altY();
        double altZ = effect.randomAltZ() ? RNG.r.d(-effect.altZ(), effect.altZ()) : effect.altZ();
        level.sendParticles(particle, particleX, particleY, particleZ, effect.particleCount(),
                altX, altY, altZ, effect.extra());
    }

    private static SoundEvent resolveSound(String key) {
        Identifier id = identifierFor(key);
        if (id == null || !BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
            if (key != null && WARNED_EFFECT_SOUNDS.add(key)) {
                IrisLogging.warn("Iris entity effect: unknown sound '" + key + "'");
            }
            return null;
        }
        return BuiltInRegistries.SOUND_EVENT.getValue(id);
    }

    private static ParticleOptions resolveParticle(String key) {
        Identifier id = identifierFor(key);
        if (id == null || !BuiltInRegistries.PARTICLE_TYPE.containsKey(id)) {
            if (key != null && WARNED_EFFECT_PARTICLES.add(key)) {
                IrisLogging.warn("Iris entity effect: unknown particle '" + key + "'");
            }
            return null;
        }
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.getValue(id);
        if (type instanceof SimpleParticleType simple) {
            return simple;
        }
        if (WARNED_EFFECT_PARTICLES.add(key)) {
            IrisLogging.warn("Iris entity effect: particle '" + key + "' requires data that the effect does not define");
        }
        return null;
    }

    private static Identifier identifierFor(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return Identifier.tryParse(normalized.indexOf(':') >= 0 ? normalized : "minecraft:" + normalized);
    }

    private static void startRiseEffect(Engine engine, ServerLevel level, Entity entity, LivingEntity living) {
        RiseTask task = new RiseTask(engine, level, entity, living);
        task.start();
    }

    static EntityType<?> resolveType(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        Identifier id = identifierFor(normalized);
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            if (WARNED_TYPES.add(normalized)) {
                IrisLogging.warn("Iris entity: unknown entity type '" + key + "'; skipping spawn");
            }
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.getValue(id);
    }

    static EntitySpawnReason reasonFor(String reason) {
        if (reason == null || reason.isBlank()) {
            return EntitySpawnReason.NATURAL;
        }
        String value = reason.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "NATURAL", "DEFAULT" -> EntitySpawnReason.NATURAL;
            case "JOCKEY", "MOUNT" -> EntitySpawnReason.JOCKEY;
            case "CHUNK_GEN" -> EntitySpawnReason.CHUNK_GENERATION;
            case "SPAWNER" -> EntitySpawnReason.SPAWNER;
            case "TRIAL_SPAWNER" -> EntitySpawnReason.TRIAL_SPAWNER;
            case "EGG", "BUILD_SNOWMAN", "BUILD_IRONGOLEM", "BUILD_COPPERGOLEM", "BUILD_WITHER",
                    "SLIME_SPLIT", "SILVERFISH_BLOCK", "TRAP", "ENDER_PEARL", "EXPLOSION",
                    "ENCHANTMENT", "OMINOUS_ITEM_SPAWNER", "POTION_EFFECT", "REANIMATE" -> EntitySpawnReason.TRIGGERED;
            case "SPAWNER_EGG" -> EntitySpawnReason.SPAWN_ITEM_USE;
            case "LIGHTNING", "VILLAGE_INVASION", "RAID", "CUSTOM" -> EntitySpawnReason.EVENT;
            case "VILLAGE_DEFENSE", "SPELL" -> EntitySpawnReason.MOB_SUMMONED;
            case "BREEDING", "OCELOT_BABY", "DUPLICATION", "REHYDRATION" -> EntitySpawnReason.BREEDING;
            case "REINFORCEMENTS" -> EntitySpawnReason.REINFORCEMENT;
            case "NETHER_PORTAL" -> EntitySpawnReason.STRUCTURE;
            case "DISPENSE_EGG" -> EntitySpawnReason.DISPENSER;
            case "INFECTION", "CURED", "DROWNED", "SHEARED", "PIGLIN_ZOMBIFIED", "FROZEN",
                    "METAMORPHOSIS" -> EntitySpawnReason.CONVERSION;
            case "SHOULDER_ENTITY", "BEEHIVE" -> EntitySpawnReason.LOAD;
            case "PATROL" -> EntitySpawnReason.PATROL;
            case "COMMAND" -> EntitySpawnReason.COMMAND;
            case "BUCKET" -> EntitySpawnReason.BUCKET;
            default -> EntitySpawnReason.NATURAL;
        };
    }

    private static Panda.Gene gene(String value) {
        if (value == null || value.isBlank()) {
            return Panda.Gene.NORMAL;
        }
        try {
            return Panda.Gene.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Panda.Gene.NORMAL;
        }
    }

    private static boolean forcePersist() {
        return IrisSettings.get().getWorld().isForcePersistEntities();
    }

    private static void applyPersistence(Entity entity, boolean persistent) {
        ModdedEntityPersistence.configure(entity, persistent);
        if (persistent && (!entity.getType().canSerialize() || !entity.shouldBeSaved())) {
            String type = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
            if (WARNED_PERSISTENCE_TYPES.add(type)) {
                IrisLogging.warn("Iris entity: vanilla cannot persist non-serializable entity type '" + type + "'");
            }
        }
    }

    private static String normalizeName(String name) {
        String source = name == null || name.isBlank() ? "modifier" : name;
        String normalized = source.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
        return normalized.isBlank() ? "modifier" : normalized;
    }

    private static String colorize(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && COLOR_CODES.indexOf(chars[i + 1]) > -1) {
                chars[i] = '§';
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    @FunctionalInterface
    interface ChunkLoadedLookup {
        boolean loaded(int chunkX, int chunkZ);
    }

    private static final class RiseTask implements Runnable {
        private final Engine engine;
        private final ServerLevel level;
        private final Entity entity;
        private final LivingEntity living;
        private final double x;
        private final double z;
        private final boolean originalInvulnerable;
        private final boolean originalNoPhysics;
        private final int originalInvulnerableTime;
        private final boolean originalNoAi;
        private double y;
        private int moves;
        private boolean restored;

        private RiseTask(Engine engine, ServerLevel level, Entity entity, LivingEntity living) {
            this.engine = engine;
            this.level = level;
            this.entity = entity;
            this.living = living;
            this.x = entity.getX();
            this.y = entity.getY();
            this.z = entity.getZ();
            this.originalInvulnerable = entity.isInvulnerable();
            this.originalNoPhysics = entity.noPhysics;
            this.originalInvulnerableTime = entity.invulnerableTime;
            this.originalNoAi = entity instanceof Mob mob && mob.isNoAi();
        }

        private void start() {
            entity.setInvulnerable(true);
            entity.noPhysics = true;
            entity.invulnerableTime = 100_000;
            if (entity instanceof Mob mob) {
                mob.setNoAi(true);
            }
            run();
        }

        @Override
        public void run() {
            if (moves >= RISE_MAX_MOVES || !entity.isAlive() || engine.isClosed() || entity.level() != level) {
                restore();
                return;
            }
            BlockPos current = entity.blockPosition();
            if (level.getChunkSource().getChunkNow(current.getX() >> 4, current.getZ() >> 4) == null) {
                restore();
                return;
            }
            BlockPos eye = BlockPos.containing(entity.getX(), living.getEyeY(), entity.getZ());
            if (!ModdedBlockResolution.isSolid(level.getBlockState(current))
                    && !ModdedBlockResolution.isSolid(level.getBlockState(eye))) {
                restore();
                return;
            }

            moves++;
            y += 0.1;
            entity.setPos(x, y, z);
            emitEffects();

            ModdedScheduler scheduler = ModdedEngineBootstrap.schedulerOrNull();
            if (scheduler == null) {
                IrisLogging.error("Iris could not continue an entity rise effect because the modded scheduler is unavailable.");
                restore();
                return;
            }
            scheduler.laterGlobal(this, 1);
        }

        private void emitEffects() {
            BlockPos source = BlockPos.containing(entity.getX(), living.getEyeY() - 2, entity.getZ());
            BlockState state = level.getBlockState(source);
            ItemParticleOption item = new ItemParticleOption(ParticleTypes.ITEM, state.getBlock().asItem());
            level.sendParticles(item, entity.getX(), living.getEyeY(), entity.getZ(), 6,
                    0.2, 0.4, 0.2, 0.06);
            if (RNG.r.nextDouble() < 0.2) {
                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.CHORUS_FLOWER_GROW,
                        SoundSource.MASTER, 0.8F, 0.1F);
            }
        }

        private void restore() {
            if (restored) {
                return;
            }
            restored = true;
            entity.invulnerableTime = originalInvulnerableTime;
            entity.noPhysics = originalNoPhysics;
            entity.setInvulnerable(originalInvulnerable);
            if (entity instanceof Mob mob) {
                mob.setNoAi(originalNoAi);
            }
        }
    }
}
