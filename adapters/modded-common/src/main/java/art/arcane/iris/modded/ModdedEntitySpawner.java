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

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.decoration.IrisSurface;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.modded.api.ModdedCustomContentRegistry;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.structure.placement.LootResolver;
import art.arcane.iris.world.entity.IrisAttributeModifier;
import art.arcane.iris.world.entity.IrisEffect;
import art.arcane.iris.world.entity.IrisEntity;
import art.arcane.iris.world.loot.IrisLoot;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeEntityRuntime;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeItemStack;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeSpawnedEntity;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    public static NativeSpawnedEntity spawn(Engine engine, IrisEntity irisEntity, NativeEntityRuntime level, int blockX, int blockY, int blockZ, RNG rng) {
        if (engine == null || irisEntity == null || level == null) {
            return null;
        }
        if (!chunksSafe(level, blockX >> 4, blockZ >> 4)) {
            return null;
        }

        double x = blockX + 0.5;
        double y = blockY + 0.5;
        double z = blockZ + 0.5;
        boolean riseEffectActive = irisEntity.isSpawnEffectRiseOutOfGround() && level.hasPlayersNearby(new NativeEntityRuntime.Position(x, y, z), PLAYER_EFFECT_RADIUS);
        int spawnBlockY = riseEffectActive ? blockY - 5 : blockY;
        double spawnY = spawnBlockY + 0.5;

        NativeSpawnedEntity created = create(irisEntity, level, x, spawnY, z);
        if (created == null) {
            return null;
        }

        if (irisEntity.isSpecialType() && !irisEntity.isApplySettingsToCustomMobAnyways()) {
            return created;
        }

        applyConfig(engine, irisEntity, created, level, blockX, spawnBlockY, blockZ, rng, riseEffectActive);
        return created;
    }

    private static NativeSpawnedEntity create(IrisEntity irisEntity, NativeEntityRuntime level, double x, double y, double z) {
        if (irisEntity.isSpecialType()) {
            return level.spawnCustom(irisEntity.getSpecialType(), new NativeEntityRuntime.Position(x, y, z),
                    ModdedCustomContentRegistry::spawnMob);
        }

        NativeEntityRuntime.Type type = resolveType(irisEntity.getType());
        if (type == null) {
            return null;
        }

        return level.spawn(type, new NativeEntityRuntime.Position(x, y, z), irisEntity.getReason());
    }

    private static void applyConfig(Engine engine, IrisEntity irisEntity, NativeSpawnedEntity entity, NativeEntityRuntime level, int blockX, int blockY, int blockZ, RNG rng, boolean riseEffectActive) {
        String customName = irisEntity.getCustomName();
        entity.applyBase(irisEntity, customName == null ? null : colorize(customName));

        boolean persistent = irisEntity.isKeepEntity() || forcePersist();
        applyPersistence(entity, persistent);

        applyPassengers(engine, irisEntity, entity, level, blockX, blockY, blockZ, rng);

        if (entity.living()) {
            applyAttributes(irisEntity, entity, level, rng);
        }

        if (!irisEntity.getLoot().getTables().isEmpty()) {
            ModdedDeathLoot.bind(engine, entity, irisEntity.getLoot().getTables(), blockX, blockY, blockZ, rng);
        }

        if (entity.mob()) {
            entity.configureMob(irisEntity);
            if (irisEntity.getLeashHolder() != null) {
                NativeSpawnedEntity holder = spawn(engine, irisEntity.getLeashHolder(), level, blockX, blockY, blockZ, rng.nextParallelRNG(LEASH_RNG_SEED));
                if (holder != null) {
                    entity.leashTo(holder);
                }
            }
        }

        if (entity.living()) {
            applyEquipment(irisEntity, entity, level, rng);
        }

        entity.configureAnimal(irisEntity);

        applySpawnEffect(irisEntity.getSpawnEffect(), entity, level);
        ModdedEntityCommandRunner.run(irisEntity.getRawCommands(), level.world(), blockX, blockY, blockZ);
        if (riseEffectActive && entity.living()) {
            startRiseEffect(engine, level, entity);
        }
    }

    private static void applyPassengers(Engine engine, IrisEntity irisEntity, NativeSpawnedEntity entity, NativeEntityRuntime level, int blockX, int blockY, int blockZ, RNG rng) {
        int index = 0;
        for (IrisEntity passengerEntity : irisEntity.getPassengers()) {
            NativeSpawnedEntity passenger = spawn(engine, passengerEntity, level, blockX, blockY, blockZ, rng.nextParallelRNG(PASSENGER_RNG_BASE + index++));
            if (passenger != null) {
                passenger.ride(entity);
            }
        }
    }

    private static void applyAttributes(IrisEntity irisEntity, NativeSpawnedEntity living, NativeEntityRuntime level, RNG rng) {
        KList<IrisAttributeModifier> modifiers = irisEntity.getAttributes();
        if (modifiers.isEmpty()) {
            return;
        }

        int index = 0;
        for (IrisAttributeModifier modifier : modifiers) {
            index++;
            if (rng.nextDouble() >= modifier.getChance()) {
                continue;
            }
            String id = "iris:" + normalizeName(modifier.getName()) + "_" + index;
            if (!living.applyAttribute(level, modifier.getAttribute(), id, modifier.getOperation(), () -> modifier.getAmount(rng))
                    && WARNED_ATTRIBUTES.add(modifier.getAttribute())) {
                IrisLogging.warn("Iris entity: unknown attribute '" + modifier.getAttribute() + "'");
            }
        }
    }

    private static void applyEquipment(IrisEntity irisEntity, NativeSpawnedEntity living, NativeEntityRuntime level, RNG rng) {
        setSlot(living, NativeSpawnedEntity.Equipment.HEAD, irisEntity.getHelmet(), level, rng);
        setSlot(living, NativeSpawnedEntity.Equipment.CHEST, irisEntity.getChestplate(), level, rng);
        setSlot(living, NativeSpawnedEntity.Equipment.LEGS, irisEntity.getLeggings(), level, rng);
        setSlot(living, NativeSpawnedEntity.Equipment.FEET, irisEntity.getBoots(), level, rng);
        setSlot(living, NativeSpawnedEntity.Equipment.MAINHAND, irisEntity.getMainHand(), level, rng);
        setSlot(living, NativeSpawnedEntity.Equipment.OFFHAND, irisEntity.getOffHand(), level, rng);
    }

    private static void setSlot(NativeSpawnedEntity living, NativeSpawnedEntity.Equipment slot, IrisLoot loot, NativeEntityRuntime level, RNG rng) {
        if (loot == null || !LootResolver.oneIn(rng, loot.getRarity())) {
            return;
        }
        NativeItemStack nativeStack = ModdedItemTranslator.stack(loot, rng, ModdedItemTranslator.context(level.world()));
        living.equip(slot, nativeStack);
    }

    static boolean isAreaClearForSpawn(NativeEntityRuntime level, IrisEntity irisEntity, int blockX, int blockY, int blockZ) {
        if (irisEntity.isSpecialType()) {
            return true;
        }
        NativeEntityRuntime.Type type = resolveType(irisEntity.getType());
        if (type == null) {
            return true;
        }
        if (irisEntity.getSurface().isFluid()) {
            return NativeEntityRuntime.isFluidAreaClearForSpawn(blockX, blockY, blockZ, type.width(), type.height(),
                    (x, y, z) -> level.fluid(x, y, z, irisEntity.getSurface() == IrisSurface.LAVA));
        }
        return NativeEntityRuntime.isAreaClearForSpawn(blockX, blockY, blockZ, type.width(), type.height(), level::air);
    }

    static boolean chunksSafe(NativeEntityRuntime level, int chunkX, int chunkZ) {
        return level.chunksSafe(chunkX, chunkZ);
    }

    private static void applySpawnEffect(IrisEffect effect, NativeSpawnedEntity entity, NativeEntityRuntime level) {
        if (effect == null || !effect.shouldApplyNow()) {
            return;
        }

        NativeEntityRuntime.Sound sound = resolveSound(effect.getSoundKey());
        if (sound != null) {
            double soundX = entity.x() + RNG.r.i(-effect.getSoundDistance(), effect.getSoundDistance());
            double soundY = entity.y() + RNG.r.i(-effect.getSoundDistance(), effect.getSoundDistance());
            double soundZ = entity.z() + RNG.r.i(-effect.getSoundDistance(), effect.getSoundDistance());
            level.sound(sound, new NativeEntityRuntime.SoundEmission(soundX, soundY, soundZ,
                    (float) effect.getVolume(), (float) RNG.r.d(effect.getMinPitch(), effect.getMaxPitch())));
        }

        NativeEntityRuntime.Particle particle = resolveParticle(effect.getParticleEffectKey());
        if (particle == null) {
            return;
        }

        double addition = RNG.r.d();
        double subtraction = RNG.r.d();
        double particleX = entity.x() + addition - subtraction + RNG.r.d();
        double particleY = entity.y() + 0.25 + addition - subtraction + RNG.r.i(effect.getParticleOffset());
        double particleZ = entity.z() + addition - subtraction + RNG.r.d();
        double altX = effect.isRandomAltX() ? RNG.r.d(-effect.getParticleAltX(), effect.getParticleAltX()) : effect.getParticleAltX();
        double altY = effect.isRandomAltY() ? RNG.r.d(-effect.getParticleAltY(), effect.getParticleAltY()) : effect.getParticleAltY();
        double altZ = effect.isRandomAltZ() ? RNG.r.d(-effect.getParticleAltZ(), effect.getParticleAltZ()) : effect.getParticleAltZ();
        level.particle(particle, new NativeEntityRuntime.ParticleEmission(particleX, particleY, particleZ,
                effect.getParticleCount(), altX, altY, altZ, effect.getExtra()));
    }

    private static NativeEntityRuntime.Sound resolveSound(String key) {
        NativeEntityRuntime.Sound sound = NativeEntityRuntime.resolveSound(key);
        if (sound == null && key != null && WARNED_EFFECT_SOUNDS.add(key)) {
            IrisLogging.warn("Iris entity effect: unknown sound '" + key + "'");
        }
        return sound;
    }

    private static NativeEntityRuntime.Particle resolveParticle(String key) {
        NativeEntityRuntime.Particle particle = NativeEntityRuntime.resolveParticle(key);
        if (particle == null) {
            if (key != null && WARNED_EFFECT_PARTICLES.add(key)) {
                IrisLogging.warn("Iris entity effect: unknown particle '" + key + "'");
            }
            return null;
        }
        if (particle.simple()) {
            return particle;
        }
        if (WARNED_EFFECT_PARTICLES.add(key)) {
            IrisLogging.warn("Iris entity effect: particle '" + key + "' requires data that the effect does not define");
        }
        return null;
    }

    private static void startRiseEffect(Engine engine, NativeEntityRuntime level, NativeSpawnedEntity entity) {
        RiseTask task = new RiseTask(new RiseContext(engine, level, entity));
        task.start();
    }

    private static NativeEntityRuntime.Type resolveType(String key) {
        NativeEntityRuntime.Type type = NativeEntityRuntime.resolveType(key);
        if (type == null && key != null && !key.isBlank() && WARNED_TYPES.add(key.trim().toLowerCase(Locale.ROOT))) {
            IrisLogging.warn("Iris entity: unknown entity type '" + key + "'; skipping spawn");
        }
        return type;
    }

    private static boolean forcePersist() {
        return IrisSettings.get().getWorld().isForcePersistEntities();
    }

    private static void applyPersistence(NativeSpawnedEntity entity, boolean persistent) {
        if (!entity.persistence(persistent)) {
            String type = entity.typeKey();
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

    private record RiseContext(Engine engine, NativeEntityRuntime level, NativeSpawnedEntity entity) {
    }

    private static final class RiseTask implements Runnable {
        private final Engine engine;
        private final NativeEntityRuntime level;
        private final NativeSpawnedEntity entity;
        private final NativeEntityRuntime.Sound riseSound;
        private final double x;
        private final double z;
        private NativeSpawnedEntity.MotionState original;
        private double y;
        private int moves;
        private boolean restored;

        private RiseTask(RiseContext context) {
            engine = context.engine();
            level = context.level();
            entity = context.entity();
            riseSound = NativeEntityRuntime.resolveSound("minecraft:block.chorus_flower.grow");
            x = entity.x();
            y = entity.y();
            z = entity.z();
        }

        private void start() {
            original = entity.pausePhysics(100_000);
            run();
        }

        @Override
        public void run() {
            if (moves >= RISE_MAX_MOVES || !entity.alive() || engine.isClosed() || !entity.inWorld(level)) {
                restore();
                return;
            }
            int blockX = entity.blockX();
            int blockY = entity.blockY();
            int blockZ = entity.blockZ();
            if (!level.chunkLoaded(blockX >> 4, blockZ >> 4)) {
                restore();
                return;
            }
            if (!level.solid(blockX, blockY, blockZ)
                    && !level.solid(blockX, (int) Math.floor(entity.eyeY()), blockZ)) {
                restore();
                return;
            }
            moves++;
            y += 0.1;
            entity.position(x, y, z);
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
            level.blockItemParticles(new NativeEntityRuntime.Position(entity.x(), entity.eyeY() - 2, entity.z()),
                    new NativeEntityRuntime.ParticleEmission(entity.x(), entity.eyeY(), entity.z(), 6, 0.2, 0.4, 0.2, 0.06));
            if (RNG.r.nextDouble() < 0.2) {
                level.sound(riseSound,
                        new NativeEntityRuntime.SoundEmission(entity.x(), entity.y(), entity.z(), 0.8F, 0.1F));
            }
        }

        private void restore() {
            if (!restored) {
                restored = true;
                entity.restoreMotion(original);
            }
        }
    }
}
