package art.arcane.iris.modded.service;

import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolPlayer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeItemStack;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeDropEffects;

import art.arcane.iris.modded.ModdedIrisLog;
import art.arcane.iris.modded.ModdedEngineBootstrap;
import art.arcane.iris.modded.ModdedScheduler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class ModdedTreeFellerPresentation {
    private static final int MIN_BLOCKS_PER_PULSE = 4;
    private static final int MAX_BLOCKS_PER_PULSE = 64;
    private static final int TARGET_EROSION_PULSES = 60;
    private static final int MAX_EFFECT_ORIGINS_PER_PULSE = 16;

    private final NativeProtocolPlayer player;
    private final NativeWorld sourceLevel;
    private final NativeDropEffects effects;
    private final List<NativeItemStack> pendingDrops = new ArrayList<>();
    private final AtomicBoolean effectFailureReported = new AtomicBoolean();
    private final AtomicBoolean deliveryFailureReported = new AtomicBoolean();
    private boolean flushScheduled;
    private double fallbackX;
    private double fallbackY;
    private double fallbackZ;

    ModdedTreeFellerPresentation(NativeProtocolPlayer player, NativeWorld sourceLevel) {
        this.player = player;
        this.sourceLevel = sourceLevel;
        this.effects = new NativeDropEffects(sourceLevel);
        this.fallbackX = player.x();
        this.fallbackY = player.y() + 0.15D;
        this.fallbackZ = player.z();
    }

    static int blocksPerPulse(int blockCount) {
        int requested = Math.max(1, (blockCount + TARGET_EROSION_PULSES - 1) / TARGET_EROSION_PULSES);
        return Math.max(MIN_BLOCKS_PER_PULSE, Math.min(requested, MAX_BLOCKS_PER_PULSE));
    }

    static int effectStride(int blocksPerPulse) {
        return Math.max(
                1,
                (blocksPerPulse + MAX_EFFECT_ORIGINS_PER_PULSE - 1) / MAX_EFFECT_ORIGINS_PER_PULSE
        );
    }

    static List<NativeItemStack> consolidateDrops(Collection<NativeItemStack> drops) {
        List<NativeItemStack> consolidated = new ArrayList<>();
        for (NativeItemStack drop : drops) {
            mergeDrop(consolidated, drop);
        }
        return List.copyOf(consolidated);
    }

    void activate(NativeBlockPoint position, NativeBlockState state) {
        try {
            double x = position.x() + 0.5D;
            double y = position.y() + 0.5D;
            double z = position.z() + 0.5D;
            effects.particle(NativeDropEffects.Particle.ENCHANT,
                    new NativeDropEffects.Emission(x, y, z, 24, 0.45D, 0.45D, 0.45D, 0.18D), null);
            effects.particle(NativeDropEffects.Particle.END_ROD,
                    new NativeDropEffects.Emission(x, y, z, 8, 0.25D, 0.25D, 0.25D, 0.035D), null);
            effects.particle(NativeDropEffects.Particle.BLOCK,
                    new NativeDropEffects.Emission(x, y, z, 8, 0.25D, 0.25D, 0.25D, 0.04D), state);
            effects.sound("minecraft:block.enchantment_table.use",
                    new NativeDropEffects.SoundEmission(x, y, z, 0.55F, 1.35F));
            effects.sound("minecraft:block.amethyst_block.chime",
                    new NativeDropEffects.SoundEmission(x, y, z, 0.4F, 0.8F));
        } catch (Throwable error) {
            reportEffectFailure(error);
        }
    }

    void erode(NativeBlockPoint position, NativeBlockState state, int processed, int effectStride, float pitch) {
        if (processed % effectStride != 0) {
            return;
        }
        try {
            double x = position.x() + 0.5D;
            double y = position.y() + 0.5D;
            double z = position.z() + 0.5D;
            effects.particle(NativeDropEffects.Particle.BLOCK,
                    new NativeDropEffects.Emission(x, y, z, 5, 0.3D, 0.3D, 0.3D, 0.04D), state);
            effects.particle(NativeDropEffects.Particle.ENCHANT,
                    new NativeDropEffects.Emission(x, y, z, 3, 0.28D, 0.28D, 0.28D, 0.12D), null);
            effects.sound("minecraft:block.amethyst_block.chime",
                    new NativeDropEffects.SoundEmission(x, y, z, 0.22F, pitch));
        } catch (Throwable error) {
            reportEffectFailure(error);
        }
    }

    synchronized boolean route(Iterable<NativeItemStack> drops) {
        for (NativeItemStack drop : drops) {
            if (drop != null && !drop.isEmpty()) {
                pendingDrops.add(drop.copy());
            }
        }
        scheduleFlush();
        return true;
    }

    synchronized void flush() {
        flushScheduled = false;
        if (pendingDrops.isEmpty()) {
            return;
        }
        List<NativeItemStack> drops = consolidateDrops(pendingDrops);
        pendingDrops.clear();
        boolean atPlayer = !player.removed() && player.inWorld(sourceLevel);
        double x = atPlayer ? player.x() : fallbackX;
        double y = atPlayer ? player.y() + 0.15D : fallbackY;
        double z = atPlayer ? player.z() : fallbackZ;
        if (atPlayer) {
            fallbackX = x;
            fallbackY = y;
            fallbackZ = z;
        }
        int delivered = 0;
        for (NativeItemStack drop : drops) {
            try {
                if (effects.drop(drop, new NativeDropEffects.DropPosition(x, y, z, 0D, 0.08D, 0D))) {
                    delivered++;
                } else {
                    pendingDrops.add(drop.copy());
                }
            } catch (Throwable error) {
                pendingDrops.add(drop.copy());
                reportDeliveryFailure(error);
            }
        }
        try {
            int particles = Math.min(32, 6 + (delivered * 2));
            effects.particle(NativeDropEffects.Particle.ENCHANT,
                    new NativeDropEffects.Emission(x, y + 0.35D, z, particles, 0.3D, 0.25D, 0.3D, 0.1D), null);
            effects.sound("minecraft:block.amethyst_block.chime", new NativeDropEffects.SoundEmission(x, y, z, 0.28F, 1.75F));
        } catch (Throwable error) {
            reportEffectFailure(error);
        }
        if (!pendingDrops.isEmpty()) {
            scheduleFlush();
        }
    }

    synchronized void finish() {
        flush();
    }

    private synchronized void scheduleFlush() {
        if (pendingDrops.isEmpty() || flushScheduled) {
            return;
        }
        ModdedScheduler scheduler = ModdedEngineBootstrap.schedulerOrNull();
        if (scheduler == null) {
            flush();
            return;
        }
        flushScheduled = true;
        scheduler.laterGlobal(this::flush, 1);
    }

    private static void mergeDrop(List<NativeItemStack> consolidated, NativeItemStack drop) {
        if (drop == null || drop.isEmpty()) {
            return;
        }
        NativeItemStack remaining = drop.copy();
        for (NativeItemStack existing : consolidated) {
            if (!NativeItemStack.sameItemAndComponents(existing, remaining)) {
                continue;
            }
            int capacity = existing.maxStackSize() - existing.count();
            if (capacity <= 0) {
                continue;
            }
            int moved = Math.min(capacity, remaining.count());
            existing.grow(moved);
            remaining.shrink(moved);
            if (remaining.isEmpty()) {
                return;
            }
        }
        while (!remaining.isEmpty()) {
            int amount = Math.min(remaining.count(), remaining.maxStackSize());
            consolidated.add(remaining.copyWithCount(amount));
            remaining.shrink(amount);
        }
    }

    private void reportEffectFailure(Throwable error) {
        if (effectFailureReported.compareAndSet(false, true)) {
            ModdedIrisLog.error("Iris modded tree-feller presentation failed", error);
        }
    }

    private void reportDeliveryFailure(Throwable error) {
        if (deliveryFailureReported.compareAndSet(false, true)) {
            ModdedIrisLog.error("Iris modded tree-feller drop delivery failed", error);
        }
    }
}
