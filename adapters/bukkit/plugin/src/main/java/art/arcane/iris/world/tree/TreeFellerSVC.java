package art.arcane.iris.world.tree;

import art.arcane.iris.api.tree.IrisTreeFellerService;
import art.arcane.iris.api.tree.TreeFellerAccess;
import art.arcane.iris.api.tree.TreeFellerOptions;
import art.arcane.iris.api.tree.TreeFellerRunHooks;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.world.tree.TreeFellerModel.PendingFell;
import art.arcane.iris.world.tree.TreeFellerModel.ProvenanceSnapshot;
import art.arcane.iris.world.tree.TreeFellerModel.TreeCandidate;
import art.arcane.iris.world.tree.TreeFellerModel.TreeClaim;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.platform.bukkit.plugin.IrisService;
import art.arcane.iris.world.task.J;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class TreeFellerSVC implements IrisService, IrisTreeFellerService {
    private static final String PERMISSION = "iris.treefeller";

    private final AtomicBoolean serviceEnabled = new AtomicBoolean();
    private final Map<BlockBreakEvent, PendingFell> pending = Collections.synchronizedMap(new IdentityHashMap<>());
    final Set<BlockBreakEvent> managedEvents = Collections.synchronizedSet(
            Collections.newSetFromMap(new IdentityHashMap<>())
    );
    final Set<TreeClaim> activeClaims = ConcurrentHashMap.newKeySet();
    final Map<UUID, Set<FellingRun>> activeRuns = new ConcurrentHashMap<>();
    private final Map<Engine, TreeDefinitionIndex> definitions = Collections.synchronizedMap(new WeakHashMap<>());
    private final TreeProvenance provenance = new TreeProvenance(definitions);
    private final TreeFellingRunner runner = new TreeFellingRunner(this, provenance);

    @Override
    public void onEnable() {
        serviceEnabled.set(true);
        Bukkit.getServicesManager().register(
                IrisTreeFellerService.class,
                this,
                BukkitPlatform.plugin(),
                ServicePriority.Normal
        );
        IrisServices.register(IrisTreeFellerService.class, this);
    }

    @Override
    public void onDisable() {
        serviceEnabled.set(false);
        Bukkit.getServicesManager().unregister(IrisTreeFellerService.class, this);
        IrisServices.remove(IrisTreeFellerService.class);
        pending.clear();
        managedEvents.clear();
        for (Set<FellingRun> runs : activeRuns.values()) {
            for (FellingRun run : runs) {
                runner.finish(run);
            }
        }
        activeRuns.clear();
        activeClaims.clear();
        definitions.clear();
    }

    @Override
    public boolean tryFell(BlockBreakEvent event, TreeFellerOptions options) {
        if (!serviceEnabled.get()
                || event == null
                || options == null
                || event.isCancelled()) {
            return false;
        }
        // An event that already carries a pending fell may still be upgraded to
        // INTEGRATION_OVERRIDE; only internal probes are refused outright.
        if (!pending.containsKey(event) && isManagedBreak(event)) {
            return false;
        }
        if (!canUse(event.getPlayer(), options.access())) {
            return false;
        }

        TreeCandidate candidate;
        try {
            candidate = provenance.resolveCandidate(event.getBlock(), event.getPlayer());
        } catch (Throwable error) {
            IrisLogging.reportError("Failed to resolve an Iris tree-feller request.", error);
            return false;
        }
        if (candidate == null) {
            return false;
        }

        PendingFell requested = new PendingFell(
                candidate,
                resolvePreservationChance(options),
                options.runHooks()
        );
        synchronized (pending) {
            PendingFell existing = pending.get(event);
            if (existing != null && existing.candidate().access() == TreeFellerAccess.INTEGRATION_OVERRIDE) {
                return options.access() == TreeFellerAccess.INTEGRATION_OVERRIDE;
            }
            if (existing == null || options.access() == TreeFellerAccess.INTEGRATION_OVERRIDE) {
                pending.put(event, requested.withAccess(options.access()));
            }
        }
        managedEvents.add(event);
        return true;
    }

    @Override
    public boolean isManagedBreak(BlockBreakEvent event) {
        return event != null && managedEvents.contains(event);
    }

    @Override
    public boolean isTreeBlock(Block block) {
        if (block == null) {
            return false;
        }
        try {
            return provenance.resolveTreeContext(block) != null;
        } catch (Throwable error) {
            IrisLogging.reportError("Failed to inspect Iris tree provenance.", error);
            return false;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void requestStandalone(BlockBreakEvent event) {
        tryFell(event, TreeFellerOptions.standalone());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void finalizeBreak(BlockBreakEvent event) {
        PendingFell requested;
        synchronized (pending) {
            requested = pending.remove(event);
        }

        if (requested == null) {
            if (!event.isCancelled() && !isManagedBreak(event)) {
                deferSuccessfulBreakCleanup(event);
            }
            return;
        }

        if (event.isCancelled()) {
            releaseManagedLater(event);
            return;
        }

        TreeCandidate current;
        try {
            current = provenance.resolveCandidate(event.getBlock(), event.getPlayer());
        } catch (Throwable error) {
            IrisLogging.reportError("Failed to finalize an Iris tree-feller request.", error);
            deferSuccessfulBreakCleanup(event);
            releaseManagedLater(event);
            return;
        }
        if (current == null || !current.context().marker().equals(requested.candidate().context().marker())) {
            deferSuccessfulBreakCleanup(event);
            releaseManagedLater(event);
            return;
        }

        TreeClaim claim = new TreeClaim(current.world().getUID(), current.context().marker());
        if (!activeClaims.add(claim)) {
            event.setCancelled(true);
            event.setDropItems(false);
            event.setExpToDrop(0);
            releaseManagedLater(event);
            return;
        }

        event.setCancelled(true);
        event.setDropItems(false);
        event.setExpToDrop(0);
        releaseManagedLater(event);

        FellingRun run = new FellingRun(
                claim,
                current,
                requested.preservationChance(),
                requested.runHooks(),
                event.getPlayer().getInventory().getHeldItemSlot(),
                event.getPlayer().getLocation().clone().add(0D, 0.15D, 0D)
        );
        activeRuns.computeIfAbsent(event.getPlayer().getUniqueId(), ignored -> ConcurrentHashMap.newKeySet()).add(run);
        notifyActivationAccepted(run.runHooks);
        run.presentation.activate(event.getBlock());
        runner.discover(run);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void haltWhenSneakingStops(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            runner.finishRuns(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void haltWhenHeldSlotChanges(PlayerItemHeldEvent event) {
        if (event.getNewSlot() != event.getPreviousSlot()) {
            runner.finishRuns(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void haltWhenHandsSwap(PlayerSwapHandItemsEvent event) {
        runner.finishRuns(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void clearPlacedProvenance(BlockPlaceEvent event) {
        ProvenanceSnapshot snapshot = provenance.captureProvenance(event.getBlockPlaced());
        if (snapshot == null) {
            return;
        }
        Location location = event.getBlockPlaced().getLocation();
        Runnable cleanup = () -> {
            if (!event.isCancelled()) {
                provenance.clearProvenanceIfMatching(snapshot);
            }
        };
        if (!J.runAt(location, cleanup, 1) && !J.isFolia()) {
            J.s(cleanup, 1);
        }
    }

    boolean isServiceEnabled() {
        return serviceEnabled.get();
    }

    private boolean canUse(Player player, TreeFellerAccess access) {
        if (access == TreeFellerAccess.INTEGRATION_OVERRIDE) {
            return true;
        }
        IrisSettings.IrisSettingsTreeFeller settings = IrisSettings.get().getTreeFeller();
        return settings != null && settings.isEnabled() && player.hasPermission(PERMISSION);
    }

    private int resolvePreservationChance(TreeFellerOptions options) {
        if (options.access() == TreeFellerAccess.INTEGRATION_OVERRIDE) {
            return options.durabilityPreservationChance();
        }
        IrisSettings.IrisSettingsTreeFeller settings = IrisSettings.get().getTreeFeller();
        return settings == null ? 0 : settings.getDurabilityPreservationChance();
    }

    private void notifyActivationAccepted(TreeFellerRunHooks runHooks) {
        try {
            runHooks.onActivationAccepted();
        } catch (Throwable error) {
            IrisLogging.reportError("An Iris tree-feller integration activation callback failed.", error);
        }
    }

    private void releaseManagedLater(BlockBreakEvent event) {
        J.s(() -> managedEvents.remove(event), 1);
    }

    private void deferSuccessfulBreakCleanup(BlockBreakEvent event) {
        ProvenanceSnapshot snapshot = provenance.captureProvenance(event.getBlock());
        if (snapshot == null) {
            return;
        }
        Location location = event.getBlock().getLocation();
        Runnable cleanup = () -> {
            if (!event.isCancelled()) {
                provenance.clearProvenanceIfMatching(snapshot);
            }
        };
        if (!J.runAt(location, cleanup, 1) && !J.isFolia()) {
            J.s(cleanup, 1);
        }
    }

    static boolean isAxe(ItemStack item) {
        return item != null && item.getType() != Material.AIR && item.getType().name().endsWith("_AXE");
    }

    void routeDrops(FellingRun run, List<ItemStack> drops, Location source) {
        World world = source.getWorld();
        if (world == null) {
            return;
        }
        for (ItemStack drop : drops) {
            if (!run.presentation.routeDrop(drop)) {
                world.dropItem(source, drop);
            }
        }
    }

    void dropExperience(Location location, int experience) {
        if (experience <= 0 || location.getWorld() == null) {
            return;
        }
        ExperienceOrb orb = location.getWorld().spawn(
                location,
                ExperienceOrb.class
        );
        orb.setExperience(experience);
    }
}
