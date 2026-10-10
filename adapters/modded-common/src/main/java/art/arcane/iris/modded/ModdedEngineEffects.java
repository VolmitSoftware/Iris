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
import art.arcane.iris.core.content.ContentEffect;
import art.arcane.iris.core.content.ContentServices;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineAssignedComponent;
import art.arcane.iris.engine.framework.EngineEffects;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.math.RNG;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

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

            ServerLevel level = resolveLevel();
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

    private ServerLevel resolveLevel() {
        Engine engine = getEngine();
        if (engine.isClosed() || !engine.getWorld().hasPlatformWorld()) {
            return null;
        }
        Object nativeWorld = engine.getWorld().platformWorld().nativeHandle();
        return nativeWorld instanceof ServerLevel ? (ServerLevel) nativeWorld : null;
    }

    private void syncPlayers(ServerLevel level) {
        List<ServerPlayer> activePlayers = level.players();
        Set<UUID> activeIds = new HashSet<>(Math.max(16, activePlayers.size() * 2));
        for (ServerPlayer player : activePlayers) {
            UUID playerId = player.getUUID();
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

    private void tickPlayers(ServerLevel level) {
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

    private void tickPlayer(ServerLevel level, PlayerState state) {
        ServerPlayer player = state.player();
        if (!player.isAlive() || player.isRemoved() || player.level() != level) {
            return;
        }

        samplePlayer(state);
        if (!IrisSettings.get().getWorld().isEffectSystem() || !state.sampled()) {
            return;
        }

        for (ContentEffect effect : ContentServices.ambientEffects(getEngine(), state.sampledX(), state.sampledY(), state.sampledZ())) {
            try {
                applyEffect(level, player, effect);
            } catch (Throwable error) {
                IrisLogging.reportError(error);
            }
        }
    }

    private void samplePlayer(PlayerState state) {
        ServerPlayer player = state.player();
        double deltaX = player.getX() - state.lastX();
        double deltaY = player.getY() - state.lastY();
        double deltaZ = player.getZ() - state.lastZ();
        double distanceSquared = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
        long now = System.currentTimeMillis();
        if (!needsSample(state.sampled(), now - state.lastSample(), distanceSquared)) {
            return;
        }

        state.sampled(true);
        state.lastPosition(player.getX(), player.getY(), player.getZ());
        state.lastSample(now);
        state.sampledAt(floor(player.getX()), floor(player.getY()), floor(player.getZ()));
    }

    private void applyEffect(ServerLevel level, ServerPlayer player, ContentEffect effect) {
        if (effect.sound() != null) {
            applySound(player, effect.sound());
        }
        if (effect.particles() != null) {
            applyParticles(level, player, effect.particles());
        }
        applyCommands(level, player, effect.commands());
        if (effect.potion() != null) {
            applyPotion(player, effect.potion());
        }
    }

    private void applySound(ServerPlayer player, ContentEffect.Sound effect) {
        String key = normalizeRegistryKey(effect.key());
        if (key == null) {
            return;
        }
        Identifier identifier = Identifier.tryParse(key);
        Optional<Holder.Reference<SoundEvent>> sound = identifier == null
                ? Optional.empty()
                : BuiltInRegistries.SOUND_EVENT.get(identifier);
        if (sound.isEmpty()) {
            warnUnknown(UNKNOWN_SOUNDS, "sound", key);
            return;
        }

        player.connection.send(new ClientboundSoundPacket(
                sound.get(), SoundSource.MASTER, player.getX() + effect.offsetX(), player.getY() + effect.offsetY(),
                player.getZ() + effect.offsetZ(), effect.volume(), effect.pitch(), ThreadLocalRandom.current().nextLong()));
    }

    private void applyParticles(ServerLevel level, ServerPlayer player, ContentEffect.Particles effect) {
        String key = normalizeRegistryKey(effect.key());
        if (key == null) {
            return;
        }
        Identifier identifier = Identifier.tryParse(key);
        ParticleType<?> particleType = identifier == null
                ? null
                : BuiltInRegistries.PARTICLE_TYPE.getValue(identifier);
        if (particleType == null) {
            warnUnknown(UNKNOWN_PARTICLES, "particle", key);
            return;
        }
        if (!(particleType instanceof SimpleParticleType simpleParticle)) {
            if (UNSUPPORTED_PARTICLES.add(key)) {
                IrisLogging.warn("Particle type \"" + key + "\" requires particle data and cannot be used by an Iris effect without data.");
            }
            return;
        }

        Vec3 direction = player.getLookAngle();
        double surfaceX = player.getX() + direction.x * effect.forward() + direction.z * effect.sideways();
        double surfaceZ = player.getZ() + direction.z * effect.forward() - direction.x * effect.sideways();
        int surfaceY = getEngine().getHeight(floor(surfaceX), floor(surfaceZ));
        double x = surfaceX + effect.jitterX();
        double y = surfaceY + 1.0D + level.getMinY() + effect.lift();
        double z = surfaceZ + effect.jitterZ();
        level.sendParticles(player, simpleParticle, false, false,
                x, y, z, effect.count(), effect.altX(), effect.altY(), effect.altZ(), effect.extra());
    }

    private void applyCommands(ServerLevel level, ServerPlayer player, List<ContentEffect.Command> commands) {
        for (ContentEffect.Command command : commands) {
            ModdedEntityCommandRunner.run(List.of(command.command()), level, floor(player.getX() + command.offsetX()),
                    floor(player.getY() + command.offsetY()), floor(player.getZ() + command.offsetZ()));
        }
    }

    private void applyPotion(ServerPlayer player, ContentEffect.Potion effect) {
        Holder<MobEffect> type = resolvePotion(effect.key());
        MobEffectInstance current = player.getEffect(type);
        if (current != null && !shouldReplacePotionEffect(current.getAmplifier(), effect.strength())) {
            return;
        }
        if (current != null) {
            player.removeEffect(type);
        }

        player.addEffect(new MobEffectInstance(type, effect.ticks(), effect.strength(), true, false, false));
    }

    private Holder<MobEffect> resolvePotion(String rawKey) {
        String key = normalizePotionEffectKey(rawKey);
        Identifier identifier = Identifier.tryParse(key);
        if (identifier != null) {
            Optional<Holder.Reference<MobEffect>> effect = BuiltInRegistries.MOB_EFFECT.get(identifier);
            if (effect.isPresent()) {
                return effect.get();
            }
        }
        if (UNKNOWN_POTIONS.add(key)) {
            IrisLogging.warn("Unknown Potion Effect Type: \"" + rawKey + "\". Using LUCK instead.");
        }
        return MobEffects.LUCK;
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

    private static void warnUnknown(Set<String> warned, String type, String key) {
        if (warned.add(key)) {
            IrisLogging.warn("Unknown " + type + " type: \"" + key + "\".");
        }
    }

    private static final class PlayerState {
        private ServerPlayer player;
        private int sampledX;
        private int sampledY;
        private int sampledZ;
        private double lastX;
        private double lastY;
        private double lastZ;
        private long lastSample;
        private boolean sampled;

        private PlayerState(ServerPlayer player) {
            this.player = player;
            lastX = player.getX();
            lastY = player.getY();
            lastZ = player.getZ();
            lastSample = -1L;
            sampled = false;
        }

        private ServerPlayer player() {
            return player;
        }

        private void player(ServerPlayer player) {
            this.player = player;
        }

        private int sampledX() {
            return sampledX;
        }

        private int sampledY() {
            return sampledY;
        }

        private int sampledZ() {
            return sampledZ;
        }

        private void sampledAt(int x, int y, int z) {
            sampledX = x;
            sampledY = y;
            sampledZ = z;
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
