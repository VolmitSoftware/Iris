package art.arcane.iris.core.service;

import art.arcane.iris.core.runtime.jigsaw.JigsawStudioToolPayload;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioTripleSneakTracker;
import art.arcane.iris.core.service.JigsawStudioService.ActiveStudio;
import art.arcane.iris.spi.IrisLogging;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.BrewingStartEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.BrewingStandFuelEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static art.arcane.iris.core.service.JigsawStudioPlayerContext.boardService;
import static art.arcane.iris.core.service.JigsawStudioProtection.authorizeOwner;
import static art.arcane.iris.core.service.JigsawStudioProtection.blocksMutatingCommand;
import static art.arcane.iris.core.service.JigsawStudioProtection.blocksNonEditableWorkcellMutation;
import static art.arcane.iris.core.service.JigsawStudioProtection.hasNonEditableWorkcell;
import static art.arcane.iris.core.service.JigsawStudioProtection.isMutatingCommand;
import static art.arcane.iris.core.service.JigsawStudioProtection.isSafeNonOwnerCommand;
import static art.arcane.iris.core.service.JigsawStudioProtection.ownerMatches;
import static art.arcane.iris.core.service.JigsawStudioService.isNaturalStudioSpawn;
import static art.arcane.iris.core.service.JigsawStudioService.message;
import static art.arcane.iris.core.service.JigsawStudioService.sameBlockPosition;

final class JigsawStudioProtectionListener implements Listener {
    private final JigsawStudioService service;

    JigsawStudioProtectionListener(JigsawStudioService service) {
        this.service = Objects.requireNonNull(service, "Jigsaw Studio service");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        ActiveStudio studio = service.studios.get(event.getWorld().getUID());
        if (studio != null) {
            UUID requestId = studio.generator().getRequest().requestId();
            service.tileWatcher.finalizeJigsawTileWatches(requestId);
            if (service.saveLifecycle.requiresLifecycleDrain(studio)
                    && !service.saveLifecycle.discardingRequest(requestId)) {
                event.setCancelled(true);
                service.autosaveScheduler.expediteAutosaves(requestId);
                IrisLogging.warn("Jigsaw Studio kept world %s loaded while pending edits finish autosaving",
                        event.getWorld().getName());
                return;
            }
        }
        service.registry.unregister(event.getWorld());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        ActiveStudio studio = service.studios.get(event.getWorld().getUID());
        if (studio != null) {
            service.registry.markChunkAvailable(studio, event.getChunk().getX(), event.getChunk().getZ());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        service.visualization.ensureVisualizationLoop(event.getPlayer());
        service.playerContext.reconcilePlayerContext(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        service.toolbelt.closeMenu(event.getPlayer());
        service.tileWatcher.finalizeJigsawTileWatchesForPlayer(event.getPlayer().getUniqueId());
        service.tripleSneakTracker.clearPlayer(event.getPlayer().getUniqueId());
        service.visualization.visualizationLoops.remove(event.getPlayer().getUniqueId());
        service.visualization.assemblyPreviews.remove(event.getPlayer().getUniqueId());
        service.playerContext.playerWorkcells.remove(event.getPlayer().getUniqueId());
        service.visualization.ensureVisualizationLoop(event.getPlayer());
        service.playerContext.reconcilePlayerContext(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        service.toolbelt.closeMenu(event.getPlayer());
        service.tileWatcher.finalizeJigsawTileWatchesForPlayer(playerId);
        service.tripleSneakTracker.clearPlayer(playerId);
        service.toolbelt.toolConfirmations.remove(playerId);
        service.visualization.visualizationLoops.remove(playerId);
        service.particlesDisabled.remove(playerId);
        service.visualization.assemblyPreviews.remove(playerId);
        service.playerContext.playerWorkcells.remove(playerId);
        service.toolbelt.deferredDuplications.entrySet().removeIf(
                entry -> entry.getValue().playerId().equals(playerId));
        boardService().clearJigsawContext(event.getPlayer());
    }

    @EventHandler
    public void onTripleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio == null || !ownerMatches(player, studio)) {
            service.tripleSneakTracker.clearPlayer(player.getUniqueId());
            return;
        }
        JigsawStudioTripleSneakTracker.Progress progress = service.tripleSneakTracker.recordSneak(
                player.getUniqueId(),
                studio.worldId(),
                studio.generator().getRequest().requestId(),
                System.nanoTime());
        if (progress == JigsawStudioTripleSneakTracker.Progress.TRIGGERED) {
            service.toolbelt.openControlMenu(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onUseTool(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Optional<JigsawStudioToolPayload> decoded = service.toolbelt.toolCodec.decode(event.getItem());
        if (decoded.isEmpty()) {
            return;
        }
        service.tileWatcher.finalizeJigsawTileWatchesForPlayer(event.getPlayer().getUniqueId());
        event.setCancelled(true);
        service.toolbelt.useTool(event.getPlayer(), decoded.get(), event.getItem(), event.getPlayer().isSneaking());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (sameBlockPosition(event.getFrom(), event.getTo())) {
            return;
        }
        service.playerContext.reconcilePlayerContext(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        service.tileWatcher.finalizeJigsawTileWatchesForPlayer(event.getPlayer().getUniqueId());
        service.playerContext.reconcilePlayerContext(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        service.tileWatcher.finalizeJigsawTileWatchesForPlayer(event.getPlayer().getUniqueId());
        service.playerContext.reconcilePlayerContext(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        service.tileWatcher.finalizeJigsawTileWatchesForPlayer(event.getPlayer().getUniqueId());
        service.playerContext.reconcilePlayerContext(event.getPlayer(), event.getRespawnLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNaturalCreatureSpawn(CreatureSpawnEvent event) {
        if (service.studios.containsKey(event.getLocation().getWorld().getUID())
                && isNaturalStudioSpawn(event.getSpawnReason())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUnauthorizedBlockPlace(BlockPlaceEvent event) {
        if (service.protection.isUnauthorizedStudioEdit(event.getPlayer(), event.getBlockPlaced())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUnauthorizedBlockMultiPlace(BlockMultiPlaceEvent event) {
        for (BlockState state : event.getReplacedBlockStates()) {
            if (service.protection.isUnauthorizedStudioEdit(event.getPlayer(), state.getBlock())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedBlockBreak(BlockBreakEvent event) {
        if (service.protection.isControlChest(event.getBlock())) {
            event.setCancelled(true);
            message(event.getPlayer(), "The Jigsaw Studio control chest cannot be removed.");
            return;
        }
        if (service.protection.isUnauthorizedStudioEdit(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUnauthorizedBucketEmpty(PlayerBucketEmptyEvent event) {
        Block target = event.getBlockClicked().getRelative(event.getBlockFace());
        if (service.protection.isUnauthorizedStudioEdit(event.getPlayer(), target)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUnauthorizedBucketFill(PlayerBucketFillEvent event) {
        if (service.protection.isUnauthorizedStudioEdit(event.getPlayer(), event.getBlockClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUnauthorizedInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && !service.protection.isControlChest(event.getClickedBlock())
                && service.protection.isUnauthorizedStudioEdit(event.getPlayer(), event.getClickedBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        service.dirtyTracker.markDirty(event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockMultiPlace(BlockMultiPlaceEvent event) {
        for (BlockState state : event.getReplacedBlockStates()) {
            service.dirtyTracker.markDirty(state.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        service.dirtyTracker.markDirty(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        service.dirtyTracker.markDirty(event.getBlockClicked().getRelative(event.getBlockFace()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        service.dirtyTracker.markDirty(event.getBlockClicked());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onControlChest(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getHand() != EquipmentSlot.HAND
                || !service.protection.isControlChest(event.getClickedBlock())) {
            return;
        }
        event.setCancelled(true);
        ActiveStudio studio = service.studios.get(event.getPlayer().getWorld().getUID());
        if (!authorizeOwner(event.getPlayer(), studio)) {
            return;
        }
        service.toolbelt.openControlMenu(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedBlockExplode(BlockExplodeEvent event) {
        if (service.protection.materializationInProgress(service.studios.get(event.getBlock().getWorld().getUID()))) {
            event.blockList().clear();
            return;
        }
        event.blockList().removeIf(service.protection::isImmutableStudioBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedEntityExplode(EntityExplodeEvent event) {
        if (service.protection.materializationInProgress(service.studios.get(event.getEntity().getWorld().getUID()))) {
            event.blockList().clear();
            return;
        }
        event.blockList().removeIf(service.protection::isImmutableStudioBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedPistonExtend(BlockPistonExtendEvent event) {
        if (service.protection.materializationInProgress(service.studios.get(event.getBlock().getWorld().getUID()))
                || service.protection.movesProtectedBlocks(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedPistonRetract(BlockPistonRetractEvent event) {
        if (service.protection.materializationInProgress(service.studios.get(event.getBlock().getWorld().getUID()))
                || service.protection.movesProtectedBlocks(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedEntityChangeBlock(EntityChangeBlockEvent event) {
        if (service.protection.materializationInProgress(service.studios.get(event.getBlock().getWorld().getUID()))
                || service.protection.isImmutableStudioBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedBlockFromTo(BlockFromToEvent event) {
        if (service.protection.materializationInProgress(service.studios.get(event.getBlock().getWorld().getUID()))
                || service.protection.isImmutableStudioBlock(event.getBlock())
                || service.protection.isImmutableStudioBlock(event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedBlockForm(BlockFormEvent event) {
        if (service.protection.materializationInProgress(service.studios.get(event.getBlock().getWorld().getUID()))
                || service.protection.isImmutableStudioBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedBlockGrow(BlockGrowEvent event) {
        if (service.protection.materializationInProgress(service.studios.get(event.getBlock().getWorld().getUID()))
                || service.protection.isImmutableStudioBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedBlockSpread(BlockSpreadEvent event) {
        if (service.protection.materializationInProgress(service.studios.get(event.getBlock().getWorld().getUID()))
                || service.protection.isImmutableStudioBlock(event.getBlock())
                || service.protection.isImmutableStudioBlock(event.getSource())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedBlockFade(BlockFadeEvent event) {
        if (service.protection.materializationInProgress(service.studios.get(event.getBlock().getWorld().getUID()))
                || service.protection.isImmutableStudioBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedBlockBurn(BlockBurnEvent event) {
        if (service.protection.materializationInProgress(service.studios.get(event.getBlock().getWorld().getUID()))
                || service.protection.isImmutableStudioBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProtectedBlockRedstone(BlockRedstoneEvent event) {
        if (service.protection.materializationInProgress(service.studios.get(event.getBlock().getWorld().getUID()))
                || service.protection.isImmutableStudioBlock(event.getBlock())) {
            event.setNewCurrent(event.getOldCurrent());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedStructureGrow(StructureGrowEvent event) {
        if (event.getLocation().getWorld() != null
                && (service.protection.materializationInProgress(
                        service.studios.get(event.getLocation().getWorld().getUID()))
                || service.protection.containsImmutableStudioBlock(event.getBlocks()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedInventoryClick(InventoryClickEvent event) {
        if (!service.protection.blocksInventoryMutation(
                event.getView().getTopInventory(),
                event.getWhoClicked().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) {
            message(player, "This Jigsaw Studio container is read-only during the current operation or owner session.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedInventoryDrag(InventoryDragEvent event) {
        if (!service.protection.blocksInventoryMutation(
                event.getView().getTopInventory(),
                event.getWhoClicked().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) {
            message(player, "This Jigsaw Studio container is read-only during the current operation or owner session.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedInventoryMove(InventoryMoveItemEvent event) {
        if (service.protection.blocksInventoryMutation(event.getSource(), null)
                || service.protection.blocksInventoryMutation(event.getDestination(), null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedInventoryPickup(InventoryPickupItemEvent event) {
        if (service.protection.blocksInventoryMutation(event.getInventory(), null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedBlockCook(BlockCookEvent event) {
        if (service.protection.blocksMachineMutation(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedFurnaceBurn(FurnaceBurnEvent event) {
        if (service.protection.blocksMachineMutation(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedBrew(BrewEvent event) {
        if (service.protection.blocksMachineMutation(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedBrewingStandFuel(BrewingStandFuelEvent event) {
        if (service.protection.blocksMachineMutation(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedBlockDispense(BlockDispenseEvent event) {
        if (service.protection.blocksMachineMutation(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedCrafterCraft(CrafterCraftEvent event) {
        if (service.protection.blocksMachineMutation(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.PHYSICAL) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked != null) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                service.tileWatcher.finalizeJigsawTileWatchesForPlayer(event.getPlayer().getUniqueId());
                if (clicked.getType() == Material.JIGSAW) {
                    service.tileWatcher.startJigsawTileWatch(event.getPlayer(), clicked);
                }
            }
            service.dirtyTracker.markDirty(clicked);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        service.dirtyTracker.markDirty(event.getView().getTopInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        service.dirtyTracker.markDirty(event.getView().getTopInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        service.dirtyTracker.markDirty(event.getView().getTopInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        service.dirtyTracker.markDirty(event.getSource());
        service.dirtyTracker.markDirty(event.getDestination());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        service.dirtyTracker.markDirty(event.getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockCook(BlockCookEvent event) {
        service.dirtyTracker.markDirty(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        service.dirtyTracker.markDirty(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFurnaceStartSmelt(FurnaceStartSmeltEvent event) {
        service.dirtyTracker.markDirty(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        service.dirtyTracker.markDirty(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBrewingStart(BrewingStartEvent event) {
        service.dirtyTracker.markDirty(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrewingStandFuel(BrewingStandFuelEvent event) {
        service.dirtyTracker.markDirty(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDispense(BlockDispenseEvent event) {
        service.dirtyTracker.markDirty(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrafterCraft(CrafterCraftEvent event) {
        service.dirtyTracker.markDirty(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        service.dirtyTracker.markDirty(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        service.dirtyTracker.markDirty(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        service.dirtyTracker.markDirty(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        service.dirtyTracker.markDirty(event.getToBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        service.dirtyTracker.markDirty(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        service.dirtyTracker.markDirty(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        service.dirtyTracker.markDirty(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        service.dirtyTracker.markDirty(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        service.dirtyTracker.markDirty(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockRedstone(BlockRedstoneEvent event) {
        service.dirtyTracker.markDirty(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        service.dirtyTracker.markDirty(event.getBlock());
        service.dirtyTracker.markDirty(event.getBlock().getRelative(event.getDirection()));
        for (Block block : event.getBlocks()) {
            service.dirtyTracker.markDirty(block);
            service.dirtyTracker.markDirty(block.getRelative(event.getDirection()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        service.dirtyTracker.markDirty(event.getBlock());
        service.dirtyTracker.markDirty(event.getBlock().getRelative(event.getDirection()));
        for (Block block : event.getBlocks()) {
            service.dirtyTracker.markDirty(block);
            service.dirtyTracker.markDirty(block.getRelative(event.getDirection()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        for (BlockState state : event.getBlocks()) {
            service.dirtyTracker.markDirty(state.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUnauthorizedPlayerCommand(PlayerCommandPreprocessEvent event) {
        service.tileWatcher.finalizeJigsawTileWatchesForPlayer(event.getPlayer().getUniqueId());
        ActiveStudio studio = service.studios.get(event.getPlayer().getWorld().getUID());
        if (studio != null
                && service.protection.materializationInProgress(studio)
                && !isSafeNonOwnerCommand(event.getMessage())) {
            event.setCancelled(true);
            message(event.getPlayer(), "Wait for the current Jigsaw Studio variant load or rollback to finish.");
            return;
        }
        if (studio != null && blocksNonEditableWorkcellMutation(
                hasNonEditableWorkcell(studio), event.getMessage())) {
            event.setCancelled(true);
            message(event.getPlayer(), "Mutating commands are disabled while this Studio has an empty or read-only workcell.");
            return;
        }
        UUID ownerId = studio == null ? null : studio.generator().getRequest().ownerId();
        if (!blocksMutatingCommand(ownerId, event.getPlayer().getUniqueId(), event.getMessage())) {
            return;
        }
        event.setCancelled(true);
        message(event.getPlayer(), "This Jigsaw Studio is owned by another player session.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (isMutatingCommand(event.getMessage())) {
            service.dirtyTracker.markAllDirty(event.getPlayer().getWorld());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (!isSafeNonOwnerCommand(event.getCommand()) && service.protection.anyMaterializationInProgress()) {
            event.setCancelled(true);
            return;
        }
        if (isMutatingCommand(event.getCommand()) && service.protection.anyNonEditableWorkcell()) {
            event.setCancelled(true);
            return;
        }
        if (isMutatingCommand(event.getCommand())) {
            service.dirtyTracker.markAllDirty();
        }
    }
}
