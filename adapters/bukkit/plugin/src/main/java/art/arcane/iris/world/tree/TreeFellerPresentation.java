package art.arcane.iris.world.tree;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.task.J;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class TreeFellerPresentation {
    private static final int MIN_BLOCKS_PER_PULSE = 4;
    private static final int MAX_BLOCKS_PER_PULSE = 64;
    private static final int TARGET_EROSION_PULSES = 60;
    private static final int MAX_EFFECT_ORIGINS_PER_PULSE = 16;

    private final Player player;
    private final World sourceWorld;
    private final Queue<PendingDrop> pendingDrops = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean flushScheduled = new AtomicBoolean();
    private final AtomicBoolean fallbackScheduled = new AtomicBoolean();
    private final AtomicBoolean completed = new AtomicBoolean();
    private final AtomicBoolean effectFailureReported = new AtomicBoolean();
    private final AtomicBoolean deliveryFailureReported = new AtomicBoolean();
    private final AtomicInteger deliveryPulses = new AtomicInteger();
    private volatile Location fallbackLocation;

    TreeFellerPresentation(Player player, World sourceWorld, Location fallbackLocation) {
        this.player = player;
        this.sourceWorld = sourceWorld;
        this.fallbackLocation = fallbackLocation.clone();
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

    static List<ItemStack> consolidateDrops(Collection<ItemStack> drops) {
        List<ItemStack> consolidated = new ArrayList<>();
        for (ItemStack drop : drops) {
            mergeDrop(consolidated, drop);
        }
        return List.copyOf(consolidated);
    }

    void activate(Block block) {
        try {
            Location center = block.getLocation().clone().add(0.5D, 0.5D, 0.5D);
            World world = block.getWorld();
            world.spawnParticle(Particle.ENCHANT, center, 24, 0.45D, 0.45D, 0.45D, 0.18D);
            world.spawnParticle(Particle.END_ROD, center, 8, 0.25D, 0.25D, 0.25D, 0.035D);
            world.playSound(center, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.55F, 1.35F);
            world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 0.8F);
        } catch (Throwable error) {
            reportEffectFailure(error);
        }
    }

    void erode(
            Location source,
            BlockData visualData,
            int erosionOrder,
            int processed,
            int blocksPerPulse,
            int effectStride,
            int totalBlocks
    ) {
        if (processed % effectStride != 0 || source.getWorld() == null) {
            return;
        }
        try {
            World world = source.getWorld();
            world.spawnParticle(Particle.BLOCK, source, 5, 0.3D, 0.3D, 0.3D, 0.04D, visualData);
            world.spawnParticle(Particle.ENCHANT, source, 3, 0.28D, 0.28D, 0.28D, 0.12D);
            if (processed % blocksPerPulse == 0) {
                double progress = totalBlocks <= 1 ? 1D : (double) erosionOrder / (double) (totalBlocks - 1);
                float pitch = (float) Math.min(1.95D, 0.65D + (progress * 1.25D));
                world.playSound(source, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.22F, pitch);
            }
        } catch (Throwable error) {
            reportEffectFailure(error);
        }
    }

    boolean routeDrop(Object drop) {
        if (!(drop instanceof ItemStack item)) {
            return false;
        }
        if (item.getType() == Material.AIR || item.getAmount() <= 0) {
            return true;
        }
        return enqueue(new PendingDrop(item.clone(), 0));
    }

    boolean routeExperience(int experience) {
        return experience <= 0 || enqueue(new PendingDrop(null, experience));
    }

    void finish() {
        completed.set(true);
        if (!pendingDrops.isEmpty()) {
            scheduleFlush();
        }
    }

    private boolean enqueue(PendingDrop drop) {
        pendingDrops.add(drop);
        if (scheduleFlush()) {
            return true;
        }
        return !pendingDrops.remove(drop);
    }

    private boolean scheduleFlush() {
        if (pendingDrops.isEmpty() || !flushScheduled.compareAndSet(false, true)) {
            return true;
        }
        fallbackScheduled.set(false);
        Runnable retired = () -> {
            if (!scheduleFallbackFlush()) {
                flushScheduled.set(false);
                IrisLogging.error("Unable to deliver queued Iris tree-feller drops after the player retired.");
            }
        };
        if (J.runEntity(player, this::flushAtPlayer, 1, retired)) {
            return true;
        }
        return scheduleFallbackFlush();
    }

    private boolean scheduleFallbackFlush() {
        if (!fallbackScheduled.compareAndSet(false, true)) {
            return true;
        }
        Location fallback = fallbackLocation.clone();
        if (J.runAt(fallback, () -> flushAtFallback(fallback), 1)) {
            return true;
        }
        fallbackScheduled.set(false);
        flushScheduled.set(false);
        return false;
    }

    private void flushAtPlayer() {
        if (!player.isOnline() || !player.getWorld().equals(sourceWorld)) {
            if (!scheduleFallbackFlush()) {
                IrisLogging.error("Unable to deliver queued Iris tree-feller drops at their fallback location.");
            }
            return;
        }
        Location target = player.getLocation().clone().add(0D, 0.15D, 0D);
        fallbackLocation = target.clone();
        deliverBatch(target, drainPendingDrops(), true);
        completeFlush();
    }

    private void flushAtFallback(Location fallback) {
        deliverBatch(fallback, drainPendingDrops(), false);
        completeFlush();
    }

    private DropBatch drainPendingDrops() {
        List<ItemStack> items = new ArrayList<>();
        long experience = 0L;
        PendingDrop pending;
        while ((pending = pendingDrops.poll()) != null) {
            if (pending.item() != null) {
                items.add(pending.item());
            }
            experience = Math.min(Integer.MAX_VALUE, experience + pending.experience());
        }
        return new DropBatch(consolidateDrops(items), (int) experience);
    }

    private void deliverBatch(Location target, DropBatch batch, boolean atPlayer) {
        World world = target.getWorld();
        if (world == null) {
            requeueBatch(batch);
            return;
        }
        int deliveredItems = 0;
        for (int index = 0; index < batch.items().size(); index++) {
            ItemStack item = batch.items().get(index);
            Item dropped;
            try {
                dropped = world.dropItem(target, item);
            } catch (Throwable error) {
                requeueUndelivered(batch, index);
                reportDeliveryFailure(error);
                return;
            }
            deliveredItems++;
            try {
                dropped.setVelocity(new Vector(0D, 0.08D, 0D));
            } catch (Throwable error) {
                reportEffectFailure(error);
            }
        }
        if (batch.experience() > 0) {
            try {
                ExperienceOrb orb = world.spawn(target.clone().add(0D, 0.35D, 0D), ExperienceOrb.class);
                orb.setExperience(batch.experience());
            } catch (Throwable error) {
                pendingDrops.add(new PendingDrop(null, batch.experience()));
                reportDeliveryFailure(error);
            }
        }
        try {
            int enchantParticles = Math.min(32, 6 + (deliveredItems * 2));
            Location effectLocation = target.clone().add(0D, 0.35D, 0D);
            world.spawnParticle(Particle.ENCHANT, effectLocation, enchantParticles, 0.35D, 0.25D, 0.35D, 0.15D);
            world.spawnParticle(Particle.END_ROD, effectLocation, Math.min(8, 2 + deliveredItems), 0.2D, 0.2D, 0.2D, 0.025D);
            int deliveryPulse = deliveryPulses.incrementAndGet();
            if (atPlayer && (deliveryPulse == 1 || deliveryPulse % 4 == 0 || completed.get())) {
                world.playSound(target, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.2F, 1.65F);
            }
        } catch (Throwable error) {
            reportEffectFailure(error);
        }
    }

    private void completeFlush() {
        fallbackScheduled.set(false);
        flushScheduled.set(false);
        if (!pendingDrops.isEmpty() && !scheduleFlush()) {
            IrisLogging.error("Unable to schedule the remaining Iris tree-feller drops.");
        }
    }

    private void requeueBatch(DropBatch batch) {
        requeueUndelivered(batch, 0);
    }

    private void requeueUndelivered(DropBatch batch, int firstUndeliveredItem) {
        for (int index = firstUndeliveredItem; index < batch.items().size(); index++) {
            pendingDrops.add(new PendingDrop(batch.items().get(index), 0));
        }
        if (batch.experience() > 0) {
            pendingDrops.add(new PendingDrop(null, batch.experience()));
        }
    }

    private void reportEffectFailure(Throwable error) {
        if (effectFailureReported.compareAndSet(false, true)) {
            IrisLogging.reportError("Failed to render an Iris tree-feller effect.", error);
        }
    }

    private void reportDeliveryFailure(Throwable error) {
        if (deliveryFailureReported.compareAndSet(false, true)) {
            IrisLogging.reportError("Failed to deliver an Iris tree-feller drop; it remains queued for retry.", error);
        }
    }

    private static void mergeDrop(List<ItemStack> consolidated, ItemStack source) {
        if (source == null || source.getType() == Material.AIR || source.getAmount() <= 0) {
            return;
        }
        int remaining = source.getAmount();
        for (ItemStack existing : consolidated) {
            if (!existing.isSimilar(source) || existing.getAmount() >= existing.getMaxStackSize()) {
                continue;
            }
            int transferable = Math.min(remaining, existing.getMaxStackSize() - existing.getAmount());
            existing.setAmount(existing.getAmount() + transferable);
            remaining -= transferable;
            if (remaining == 0) {
                return;
            }
        }
        int maximumStackSize = Math.max(1, source.getMaxStackSize());
        while (remaining > 0) {
            ItemStack stack = source.clone();
            stack.setAmount(Math.min(remaining, maximumStackSize));
            consolidated.add(stack);
            remaining -= stack.getAmount();
        }
    }

    private record PendingDrop(ItemStack item, int experience) {
    }

    private record DropBatch(List<ItemStack> items, int experience) {
    }
}
