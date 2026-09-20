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
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.BiomeEnvironment;
import art.arcane.iris.world.history.SavedBiomeUnavailableException;
import art.arcane.iris.generation.runtime.EngineAssignedComponent;
import art.arcane.iris.generation.runtime.EngineEffects;
import art.arcane.iris.command.IrisCommand;
import art.arcane.iris.command.IrisCommandRegistry;
import art.arcane.iris.world.entity.IrisEffect;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolPlayer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativePlayerEffects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModdedEngineEffects extends EngineAssignedComponent implements EngineEffects {
    private static final long EFFECT_BUDGET_NANOS = 1_500_000L;
    private static final long SAMPLE_INTERVAL_MILLIS = 55L;
    private static final double SAMPLE_DISTANCE_SQUARED = 81.0D;
    private static final double PLAYER_REFRESH_CHANCE = 0.02D;
    private static final Set<String> UNKNOWN_POTIONS = ConcurrentHashMap.newKeySet();
    private static final Set<String> UNKNOWN_SOUNDS = ConcurrentHashMap.newKeySet();
    private static final Set<String> UNKNOWN_PARTICLES = ConcurrentHashMap.newKeySet();
    private static final Set<String> UNSUPPORTED_PARTICLES = ConcurrentHashMap.newKeySet();

    private final Map<UUID, PlayerState> players;
    private final AtomicBoolean updateRequested;
    private final AtomicBoolean tickRequested;
    private final AtomicBoolean passQueued;

    public ModdedEngineEffects(Engine engine) {
        super(engine, "FX");
        players = new HashMap<>();
        updateRequested = new AtomicBoolean(false);
        tickRequested = new AtomicBoolean(false);
        passQueued = new AtomicBoolean(false);
    }

    @Override
    public void updatePlayerMap() {
        updateRequested.set(true);
        queueMainPass();
    }

    @Override
    public void tickRandomPlayer() {
        tickRequested.set(true);
        queueMainPass();
    }

    private void queueMainPass() {
        ModdedScheduler scheduler = ModdedEngineBootstrap.schedulerOrNull();
        if (scheduler == null || !passQueued.compareAndSet(false, true)) {
            return;
        }
        scheduler.global(this::runQueuedPass);
    }

    private void runQueuedPass() {
        try {
            boolean shouldUpdate = updateRequested.getAndSet(false);
            boolean shouldTick = tickRequested.getAndSet(false);
            if (!shouldUpdate && !shouldTick) {
                return;
            }

            NativeWorld level = resolveLevel();
            if (level == null) {
                players.clear();
                return;
            }

            if (shouldUpdate) {
                syncPlayers(level);
            }
            if (shouldTick) {
                tickPlayers(level);
            }
        } catch (Throwable error) {
            IrisLogging.reportError(error);
        } finally {
            passQueued.set(false);
            if (updateRequested.get() || tickRequested.get()) {
                queueMainPass();
            }
        }
    }

    private NativeWorld resolveLevel() {
        Engine engine = getEngine();
        if (engine.isClosed() || !engine.getWorld().hasPlatformWorld()) {
            return null;
        }
        return engine.getWorld().platformWorld();
    }

    private void syncPlayers(NativeWorld level) {
        List<NativeProtocolPlayer> activePlayers = NativePlayerEffects.players(level);
        Set<UUID> activeIds = new HashSet<>(Math.max(16, activePlayers.size() * 2));
        for (NativeProtocolPlayer player : activePlayers) {
            UUID playerId = player.id();
            activeIds.add(playerId);
            PlayerState state = players.get(playerId);
            if (state == null) {
                players.put(playerId, new PlayerState(player));
            } else {
                state.player(player);
            }
        }

        Iterator<Map.Entry<UUID, PlayerState>> iterator = players.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PlayerState> entry = iterator.next();
            if (!activeIds.contains(entry.getKey())) {
                iterator.remove();
            }
        }
    }

    private void tickPlayers(NativeWorld level) {
        if (players.isEmpty()) {
            syncPlayers(level);
        } else if (RNG.r.d() < PLAYER_REFRESH_CHANCE) {
            syncPlayers(level);
            return;
        }
        if (players.isEmpty()) {
            return;
        }

        List<PlayerState> snapshot = new ArrayList<>(players.values());
        long started = System.nanoTime();
        int remaining = snapshot.size();
        while (remaining-- > 0 && System.nanoTime() - started < EFFECT_BUDGET_NANOS) {
            PlayerState state = snapshot.get(RNG.r.nextInt(snapshot.size()));
            try {
                tickPlayer(level, state);
            } catch (Throwable error) {
                IrisLogging.reportError(error);
            }
        }
    }

    private void tickPlayer(NativeWorld level, PlayerState state) {
        NativeProtocolPlayer player = state.player();
        if (!NativePlayerEffects.alive(player) || player.removed() || !player.inWorld(level)) {
            return;
        }

        try {
            samplePlayer(state);
        } catch (SavedBiomeUnavailableException unavailable) {
            state.environment = null;
            state.sampled(false);
            return;
        }
        if (!IrisSettings.get().getWorld().isEffectSystem()) {
            return;
        }

        BiomeEnvironment environment = state.environment;
        try (BiomeEnvironment.Scope ignored = getEngine().openBiomeEnvironmentScope(environment)) {
            applyEffects(level, player, environment.region().getEffects());
            applyEffects(level, player, environment.biome().getEffects());
        }
    }

    private void samplePlayer(PlayerState state) {
        NativeProtocolPlayer player = state.player();
        double deltaX = player.x() - state.lastX();
        double deltaY = player.y() - state.lastY();
        double deltaZ = player.z() - state.lastZ();
        double distanceSquared = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
        long now = System.currentTimeMillis();
        if (!needsSample(state.sampled(), now - state.lastSample(), distanceSquared)) {
            return;
        }

        int blockX = floor(player.x());
        int blockY = floor(player.y()) - getEngine().getWorld().minHeight();
        int blockZ = floor(player.z());
        BiomeEnvironment environment = getEngine().getBiomeEnvironment(blockX, blockY, blockZ);
        state.environment = environment;
        state.sampled(true);
        state.lastPosition(player.x(), player.y(), player.z());
        state.lastSample(now);
    }

    private void applyEffects(NativeWorld level, NativeProtocolPlayer player, Iterable<IrisEffect> effects) {
        for (IrisEffect effect : effects) {
            try {
                applyEffect(level, player, effect);
            } catch (Throwable error) {
                IrisLogging.reportError(error);
            }
        }
    }

    private void applyEffect(NativeWorld level, NativeProtocolPlayer player, IrisEffect effect) {
        if (!effect.shouldApplyNow()) {
            return;
        }

        applySound(player, effect);
        applyParticles(level, player, effect);
        applyCommands(level, player, effect.getCommandRegistry());
        applyPotion(player, effect);
    }

    private void applySound(NativeProtocolPlayer player, IrisEffect effect) {
        String key = normalizeRegistryKey(effect.getSoundKey());
        if (key == null) {
            return;
        }
        NativePlayerEffects.Sound sound = NativePlayerEffects.sound(key);
        if (sound == null) {
            warnUnknown(UNKNOWN_SOUNDS, "sound", key);
            return;
        }

        int distance = effect.getSoundDistance();
        double x = player.x() + RNG.r.i(-distance, distance);
        double y = player.y() + RNG.r.i(-distance, distance);
        double z = player.z() + RNG.r.i(-distance, distance);
        float volume = (float) effect.getVolume();
        float pitch = (float) RNG.r.d(effect.getMinPitch(), effect.getMaxPitch());
        NativePlayerEffects.sound(player, sound, new NativePlayerEffects.SoundEmission(
                x, y, z, volume, pitch, ThreadLocalRandom.current().nextLong()));
    }

    private void applyParticles(NativeWorld level, NativeProtocolPlayer player, IrisEffect effect) {
        String key = normalizeRegistryKey(effect.getParticleEffectKey());
        if (key == null) {
            return;
        }
        NativePlayerEffects.Particle particleType = NativePlayerEffects.particle(key);
        if (particleType == null) {
            warnUnknown(UNKNOWN_PARTICLES, "particle", key);
            return;
        }
        if (!particleType.simple()) {
            if (UNSUPPORTED_PARTICLES.add(key)) {
                IrisLogging.warn("Particle type \"" + key + "\" requires particle data and cannot be used by an Iris effect without data.");
            }
            return;
        }

        NativePlayerEffects.Direction direction = NativePlayerEffects.look(player);
        double forward = RNG.r.i(effect.getParticleDistance()) + effect.getParticleAway();
        double sideways = RNG.r.d(-effect.getParticleDistanceWidth(), effect.getParticleDistanceWidth());
        double surfaceX = player.x() + direction.x() * forward + direction.z() * sideways;
        double surfaceZ = player.z() + direction.z() * forward - direction.x() * sideways;
        Integer surfaceY = NativePlayerEffects.loadedSurface(level, floor(surfaceX), floor(surfaceZ));
        if (surfaceY == null) {
            return;
        }
        double x = surfaceX + RNG.r.d();
        double y = surfaceY + RNG.r.i(effect.getParticleOffset());
        double z = surfaceZ + RNG.r.d();
        double altX = randomized(effect.getParticleAltX(), effect.isRandomAltX());
        double altY = randomized(effect.getParticleAltY(), effect.isRandomAltY());
        double altZ = randomized(effect.getParticleAltZ(), effect.isRandomAltZ());
        NativePlayerEffects.particles(player, particleType, new NativePlayerEffects.ParticleEmission(
                x, y, z, effect.getParticleCount(), altX, altY, altZ, effect.getExtra()));
    }

    private void applyCommands(NativeWorld level, NativeProtocolPlayer player, IrisCommandRegistry registry) {
        if (registry == null || registry.getRawCommands() == null || registry.getRawCommands().isEmpty()) {
            return;
        }

        double x = commandCoordinate(player.x(), registry.getCommandOffsetX(), registry.isCommandRandomAltX());
        double y = commandCoordinate(player.y(), registry.getCommandOffsetY(), registry.isCommandRandomAltY());
        double z = commandCoordinate(player.z(), registry.getCommandOffsetZ(), registry.isCommandRandomAltZ());
        for (IrisCommand command : registry.getRawCommands()) {
            command.run(level, floor(x), floor(y), floor(z));
            if (registry.isCommandAllRandomLocations()) {
                x = commandCoordinate(player.x(), registry.getCommandOffsetX(), registry.isCommandRandomAltX());
                y = commandCoordinate(player.y(), registry.getCommandOffsetY(), registry.isCommandRandomAltY());
                z = commandCoordinate(player.z(), registry.getCommandOffsetZ(), registry.isCommandRandomAltZ());
            }
        }
    }

    private void applyPotion(NativeProtocolPlayer player, IrisEffect effect) {
        int strength = effect.getPotionStrength();
        if (strength < 0) {
            return;
        }

        NativePlayerEffects.Potion type = resolvePotion(effect.getPotionEffect());
        Integer current = NativePlayerEffects.amplifier(player, type);
        if (current != null && !shouldReplacePotionEffect(current, strength)) {
            return;
        }
        if (current != null) {
            NativePlayerEffects.removePotion(player, type);
        }

        int minimum = Math.min(effect.getPotionTicksMin(), effect.getPotionTicksMax());
        int maximum = Math.max(effect.getPotionTicksMin(), effect.getPotionTicksMax());
        int duration = RNG.r.i(minimum, maximum);
        NativePlayerEffects.potion(player, type, new NativePlayerEffects.PotionEmission(duration, strength, true, false, false));
    }

    private NativePlayerEffects.Potion resolvePotion(String rawKey) {
        String key = normalizePotionEffectKey(rawKey);
        NativePlayerEffects.Potion potion = NativePlayerEffects.potion(key);
        if (potion != null) {
            return potion;
        }
        if (UNKNOWN_POTIONS.add(key)) {
            IrisLogging.warn("Unknown Potion Effect Type: \"" + rawKey + "\". Using LUCK instead.");
        }
        return NativePlayerEffects.potion("minecraft:luck");
    }

    static String normalizeRegistryKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }
        String normalized = rawKey.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        return normalized.contains(":") ? normalized : "minecraft:" + normalized;
    }

    static String normalizePotionEffectKey(String rawKey) {
        String normalized = normalizeRegistryKey(rawKey == null || rawKey.isBlank() ? "luck" : rawKey);
        int separator = normalized.indexOf(':');
        String namespace = normalized.substring(0, separator);
        String path = normalized.substring(separator + 1);
        String aliased = switch (path) {
            case "slow" -> "slowness";
            case "fast_digging" -> "haste";
            case "slow_digging" -> "mining_fatigue";
            case "increase_damage" -> "strength";
            case "heal" -> "instant_health";
            case "harm" -> "instant_damage";
            case "jump" -> "jump_boost";
            case "confusion" -> "nausea";
            case "damage_resistance" -> "resistance";
            default -> path;
        };
        return namespace + ":" + aliased;
    }

    static boolean needsSample(boolean sampled, long elapsedMillis, double distanceSquared) {
        return !sampled || elapsedMillis > SAMPLE_INTERVAL_MILLIS && distanceSquared > SAMPLE_DISTANCE_SQUARED;
    }

    static boolean shouldReplacePotionEffect(int existingAmplifier, int configuredAmplifier) {
        return existingAmplifier <= configuredAmplifier;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static double randomized(double value, boolean random) {
        return random ? RNG.r.d(-value, value) : value;
    }

    private static double commandCoordinate(double base, double offset, boolean random) {
        return base + randomized(offset, random);
    }

    private static void warnUnknown(Set<String> warned, String type, String key) {
        if (warned.add(key)) {
            IrisLogging.warn("Unknown " + type + " type: \"" + key + "\".");
        }
    }

    private static final class PlayerState {
        private NativeProtocolPlayer player;
        private BiomeEnvironment environment;
        private double lastX;
        private double lastY;
        private double lastZ;
        private long lastSample;
        private boolean sampled;

        private PlayerState(NativeProtocolPlayer player) {
            this.player = player;
            lastX = player.x();
            lastY = player.y();
            lastZ = player.z();
            lastSample = -1L;
            sampled = false;
        }

        private NativeProtocolPlayer player() {
            return player;
        }

        private void player(NativeProtocolPlayer player) {
            this.player = player;
        }

        private double lastX() {
            return lastX;
        }

        private double lastY() {
            return lastY;
        }

        private double lastZ() {
            return lastZ;
        }

        private void lastPosition(double x, double y, double z) {
            lastX = x;
            lastY = y;
            lastZ = z;
        }

        private long lastSample() {
            return lastSample;
        }

        private void lastSample(long lastSample) {
            this.lastSample = lastSample;
        }

        private boolean sampled() {
            return sampled;
        }

        private void sampled(boolean sampled) {
            this.sampled = sampled;
        }
    }
}
