/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.generation.runtime;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.world.tree.BlockDropRouter;
import art.arcane.iris.world.loot.IrisBlockDrops;
import art.arcane.iris.world.entity.IrisMarker;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.platform.bukkit.BukkitBlockState;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.matter.MatterMarker;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Item;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Biome, region and dimension block drops for a Bukkit Iris world. Native loot is retained while
 * saved biome definitions load, then finalized on the block's owning thread after the break.
 */
final class WorldBlockDropRouter {
    private final IrisWorldManager manager;
    private final Set<DeferredDrops> pending = ConcurrentHashMap.newKeySet();
    private boolean closed;

    WorldBlockDropRouter(IrisWorldManager manager) {
        this.manager = manager;
    }

    void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled() || !isManagedWorld(event.getBlock().getWorld())) {
            return;
        }
        Location location = event.getBlock().getLocation();
        int mantleY = toMantleY(location.getBlockY(), manager.getEngine().getWorld().minHeight());
        Runnable finalizedBreak = manager.managedTask("bukkit_world_manager_block_break_finalize", () -> {
            if (event.isCancelled()) {
                return;
            }
            J.a(manager.managedTask("bukkit_world_manager_block_break_marker", () -> {
                MatterMarker marker = manager.getMantle().get(location.getBlockX(), mantleY, location.getBlockZ(), MatterMarker.class);
                if (marker == null || marker.getTag().equals("cave_floor") || marker.getTag().equals("cave_ceiling")) {
                    return;
                }
                IrisMarker mark = manager.getData().getMarkerLoader().load(marker.getTag());
                if (mark == null || mark.isRemoveOnChange()) {
                    manager.getMantle().remove(location.getBlockX(), mantleY, location.getBlockZ(), MatterMarker.class);
                }
            }));
        });
        schedule(location, finalizedBreak);
        if (!(event instanceof BlockDropRouter router) || !event.isDropItems()) {
            return;
        }
        synchronized (pending) {
            if (closed) {
                return;
            }
            BlockData state = event.getBlock().getBlockData().clone();
            if (!mayHaveBlockDropRules(state)) {
                return;
            }
            Location dropLocation = location.clone().add(.5, .5, .5);
            List<NativeDrop> originals = new ArrayList<>();
            for (Object nativeDrop : router.captureNativeDrops()) {
                if (nativeDrop instanceof ItemStack stack) {
                    originals.add(new NativeDrop(dropLocation, stack.clone()));
                }
            }
            CompletableFuture<BiomeEnvironment> environment = environment(location);
            if (defer(new DeferredDrops(location, state, router, () -> !event.isCancelled(), originals, environment, new AtomicBoolean()))) {
                event.setDropItems(false);
            }
        }
    }

    void onBlockDropItems(BlockDropItemEvent event) {
        if (event.isCancelled() || !isManagedWorld(event.getBlock().getWorld())) {
            return;
        }
        synchronized (pending) {
            if (closed) {
                return;
            }
            BlockData state = event.getBlockState().getBlockData().clone();
            if (!mayHaveBlockDropRules(state)) {
                return;
            }
            Location location = event.getBlockState().getLocation();
            CompletableFuture<BiomeEnvironment> environment = environment(location);
            List<NativeDrop> originals = new ArrayList<>(event.getItems().size());
            for (Item item : event.getItems()) {
                originals.add(new NativeDrop(location.clone().add(.5, .5, .5), item.getItemStack().clone()));
            }
            if (defer(new DeferredDrops(location, state, null, () -> !event.isCancelled(), originals, environment, new AtomicBoolean()))) {
                event.getItems().clear();
            }
        }
    }

    void onBlockPlace(BlockPlaceEvent event) {

    }

    CompletableFuture<Void> flushAsync() {
        List<DeferredDrops> outstanding;
        synchronized (pending) {
            outstanding = List.copyOf(pending);
        }
        List<CompletableFuture<Void>> restores = new ArrayList<>(outstanding.size());
        for (DeferredDrops drops : outstanding) {
            Location location = drops.location;
            restores.add(J.runRegionFuture(location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4,
                    () -> finish(drops, null, null)));
        }
        return CompletableFuture.allOf(restores.toArray(CompletableFuture<?>[]::new));
    }

    void close() {
        synchronized (pending) {
            closed = true;
        }
        try {
            flushAsync().get(5L, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while preserving pending Iris block loot.", interrupted);
        } catch (ExecutionException | TimeoutException failure) {
            throw new IllegalStateException("Unable to preserve pending Iris block loot before world shutdown.", failure);
        }
    }

    static int toMantleY(int worldY, int minHeight) {
        return worldY - minHeight;
    }

    static <T> void routeDrops(Iterable<T> drops, BlockDropRouter router, Consumer<T> fallback) {
        for (T drop : drops) {
            boolean routed = false;
            if (router != null) {
                try {
                    routed = router.routeDrop(drop);
                } catch (Throwable error) {
                    IrisLogging.reportError("Failed to route a deferred Iris block drop.", error);
                }
            }
            if (!routed) {
                fallback.accept(drop);
            }
        }
    }

    static int toWorldY(int mantleY, int minHeight) {
        return mantleY + minHeight;
    }

    private boolean isManagedWorld(World world) {
        return world.equals(BukkitWorldBinding.world(manager.getTarget().getWorld()));
    }

    private CompletableFuture<BiomeEnvironment> environment(Location location) {
        Engine engine = manager.getEngine();
        int relativeY = toMantleY(location.getBlockY(), engine.getWorld().minHeight());
        if (engine instanceof IrisEngine iris) {
            return iris.getBiomeEnvironmentAsync(location.getBlockX(), relativeY, location.getBlockZ());
        }
        try {
            return CompletableFuture.completedFuture(engine.getBiomeEnvironment(location.getBlockX(), relativeY, location.getBlockZ()));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private boolean defer(DeferredDrops drops) {
        pending.add(drops);
        if (J.runAt(drops.location, () -> drops.environment.orTimeout(5L, TimeUnit.SECONDS).whenComplete((environment, failure) -> {
            if (drops.finished.get()) {
                return;
            }
            J.runRegionFuture(drops.location.getWorld(), drops.location.getBlockX() >> 4, drops.location.getBlockZ() >> 4,
                    () -> finish(drops, environment, failure)).whenComplete((ignored, schedulingFailure) -> {
                if (schedulingFailure != null) {
                    IrisLogging.reportError("Unable to finalize pending Iris block loot on its owning thread.", schedulingFailure);
                }
            });
        }), 1)) {
            return true;
        }
        pending.remove(drops);
        IrisLogging.reportError("Unable to schedule Iris block loot. Preserving normal block loot.",
                new IllegalStateException("The owning region did not accept the block-drop task."));
        return false;
    }

    private void schedule(Location location, Runnable task) {
        if (!J.runAt(location, task, 1) && !J.isFolia()) {
            J.s(task, 1);
        }
    }

    private void finish(DeferredDrops drops, BiomeEnvironment environment, Throwable failure) {
        if (!drops.finished.compareAndSet(false, true)) {
            return;
        }
        pending.remove(drops);
        if (!drops.allowed.getAsBoolean()) {
            return;
        }
        KList<ItemStack> custom = new KList<>();
        boolean replaceVanilla = false;
        if (failure == null && environment != null) {
            try {
                DropResult result = EngineLifecycleTasks.call(manager.getEngine(), "bukkit_world_manager_block_drop_roll",
                        () -> rollDrops(drops.state, environment), null);
                if (result != null) {
                    custom = result.items();
                    replaceVanilla = result.replaceVanilla();
                }
            } catch (Throwable error) {
                failure = error;
            }
        }
        if (failure != null) {
            Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
            String context = "Unable to resolve Iris block drops at " + drops.location + ". Preserving normal block loot.";
            if (IrisLogging.warnOnce("block-drops:" + drops.location.getWorld().getUID() + ":" + cause.getMessage(), context)) {
                IrisLogging.reportError(context, cause);
            }
        }
        if (!replaceVanilla) {
            for (NativeDrop original : drops.originals) {
                routeDrops(List.of(original.stack()), drops.router, stack -> original.location().getWorld().dropItemNaturally(original.location(), stack));
            }
        }
        Location location = drops.location.clone().add(.5, .5, .5);
        routeDrops(custom, drops.router, stack -> location.getWorld().dropItemNaturally(location, stack));
    }

    private DropResult rollDrops(BlockData state, BiomeEnvironment environment) {
        try (BiomeEnvironment.Scope ignored = manager.getEngine().openBiomeEnvironmentScope(environment)) {
            KList<ItemStack> items = new KList<>();
            boolean replaceVanilla = false;
            for (IrisBlockDrops provider : resolveDropProviders(state, environment)) {
                provider.fillDrops(false, items);
                replaceVanilla |= provider.isReplaceVanillaDrops();
            }
            return new DropResult(items, replaceVanilla);
        }
    }

    private List<IrisBlockDrops> resolveDropProviders(BlockData state, BiomeEnvironment environment) {
        List<IrisBlockDrops> providers = new ArrayList<>();
        addMatching(providers, environment.biome().getBlockDrops(), state, environment.data());
        if (providers.stream().noneMatch(IrisBlockDrops::isSkipParents)) {
            addMatching(providers, environment.region().getBlockDrops(), state, environment.data());
            addMatching(providers, environment.dimension().getBlockDrops(), state, environment.data());
        }
        return providers;
    }

    private void addMatching(List<IrisBlockDrops> providers, KList<IrisBlockDrops> candidates, BlockData state, IrisData data) {
        for (IrisBlockDrops candidate : candidates) {
            if (candidate.shouldDropFor(state, data)) {
                providers.add(candidate);
            }
        }
    }

    private boolean mayHaveBlockDropRules(BlockData state) {
        String material = BukkitBlockState.of(state).materialKey();
        if (manager.getData().hasBlockDropRules(material)) {
            return true;
        }
        if (manager.getEngine() instanceof IrisEngine engine) {
            return engine.getGenerationHistoryRuntimeRouter().map(router -> router.biomes().mayHaveBlockDropRules(material)).orElse(false);
        }
        return false;
    }

    private record DropResult(KList<ItemStack> items, boolean replaceVanilla) {
    }

    private record NativeDrop(Location location, ItemStack stack) {
    }

    private record DeferredDrops(Location location, BlockData state, BlockDropRouter router, BooleanSupplier allowed,
                                 List<NativeDrop> originals, CompletableFuture<BiomeEnvironment> environment,
                                 AtomicBoolean finished) {
    }
}
