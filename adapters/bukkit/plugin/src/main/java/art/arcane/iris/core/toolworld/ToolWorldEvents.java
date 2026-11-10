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

package art.arcane.iris.core.toolworld;

import com.volmit.block.BlockState;
import com.volmit.geometry.BlockPosition;
import com.volmit.tooling.world.BlockChange;
import com.volmit.tooling.world.BlockChange.Cause;
import com.volmit.tooling.world.ToolPlayer;
import com.volmit.tooling.world.ToolUse;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
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
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The one listener that maps Bukkit's events onto {@link com.volmit.tooling.world.ToolWorldListener}, for tool worlds
 * only. Every handler first asks whether the event happens in a tool world and does nothing when it does not. The
 * priorities and {@code ignoreCancelled} flags are those of the jigsaw studio's own handlers: decisions at HIGHEST so
 * that other plugins have had their say, observations at MONITOR.
 */
public final class ToolWorldEvents implements Listener {
    private static final String COMMAND_PREFIX = "/";

    private final OpenToolWorlds worlds;
    private final ToolPlayers players;

    ToolWorldEvents(OpenToolWorlds worlds, ToolPlayers players) {
        this.worlds = worlds;
        this.players = players;
    }

    // Block changes: the tool decides, `false` cancels.

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        allowOrCancel(event, Cause.PLACE, placed.getWorld(), List.of(positionOf(placed)), Optional.of(event.getPlayer()),
                Optional.of(BukkitToolBlocks.stateOf(placed.getBlockData())));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockMultiPlace(BlockMultiPlaceEvent event) {
        List<BlockPosition> replaced = event.getReplacedBlockStates().stream()
                .map(state -> positionOf(state.getBlock())).toList();
        allowOrCancel(event, Cause.PLACE, event.getBlock().getWorld(), replaced, Optional.of(event.getPlayer()), Optional.empty());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        allowOrCancel(event, Cause.BREAK, event.getBlock(), Optional.of(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        allowOrCancel(event, Cause.BUCKET, event.getBlockClicked().getRelative(event.getBlockFace()), Optional.of(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        allowOrCancel(event, Cause.BUCKET, event.getBlockClicked(), Optional.of(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        allowOrCancel(event, Cause.PHYSICS, event.getBlock(), Optional.empty());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        allowOrCancel(event, Cause.PHYSICS, event.getBlock(), Optional.empty());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        allowOrCancel(event, Cause.PHYSICS, event.getBlock().getWorld(),
                List.of(positionOf(event.getBlock()), positionOf(event.getSource())), Optional.empty(), Optional.empty());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        allowOrCancel(event, Cause.FLUID, event.getBlock().getWorld(),
                List.of(positionOf(event.getBlock()), positionOf(event.getToBlock())), Optional.empty(), Optional.empty());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        allowOrCancel(event, Cause.GROWTH, event.getBlock(), Optional.empty());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        World world = event.getLocation().getWorld();
        List<BlockPosition> grown = event.getBlocks().stream().map(state -> positionOf(state.getBlock())).toList();
        if (!grown.isEmpty()) {
            allowOrCancel(event, Cause.GROWTH, world, grown, Optional.ofNullable(event.getPlayer()), Optional.empty());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        allowOrCancel(event, Cause.FIRE, event.getBlock(), Optional.empty());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockRedstone(BlockRedstoneEvent event) {
        Block block = event.getBlock();
        if (!isAllowed(Cause.REDSTONE, block.getWorld(), List.of(positionOf(block)), Optional.empty(), Optional.empty())) {
            event.setNewCurrent(event.getOldCurrent());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        allowPistonOrCancel(event, event.getBlock(), event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        allowPistonOrCancel(event, event.getBlock(), event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        keepAllowedBlocks(event.getBlock().getWorld(), event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        keepAllowedBlocks(event.getEntity().getWorld(), event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Optional<Player> actor = event.getEntity() instanceof Player player ? Optional.of(player) : Optional.empty();
        allowOrCancel(event, Cause.ENTITY, event.getBlock(), actor);
    }

    // Containers and machines.

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        allowContainerOrCancel(event, event.getView().getTopInventory(), event.getWhoClicked() instanceof Player player ? player : null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        allowContainerOrCancel(event, event.getView().getTopInventory(), event.getWhoClicked() instanceof Player player ? player : null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        allowContainerOrCancel(event, event.getSource(), null);
        if (!event.isCancelled()) {
            allowContainerOrCancel(event, event.getDestination(), null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        allowContainerOrCancel(event, event.getInventory(), null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockCook(BlockCookEvent event) {
        allowOrCancel(event, Cause.MACHINE, event.getBlock(), Optional.empty());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        allowOrCancel(event, Cause.MACHINE, event.getBlock(), Optional.empty());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        allowOrCancel(event, Cause.MACHINE, event.getBlock(), Optional.empty());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        allowOrCancel(event, Cause.MACHINE, event.getBlock(), Optional.empty());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBrewingStandFuel(BrewingStandFuelEvent event) {
        allowOrCancel(event, Cause.MACHINE, event.getBlock(), Optional.empty());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDispense(BlockDispenseEvent event) {
        allowOrCancel(event, Cause.MACHINE, event.getBlock(), Optional.empty());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafterCraft(CrafterCraftEvent event) {
        allowOrCancel(event, Cause.MACHINE, event.getBlock(), Optional.empty());
    }

    // What changed inside containers and machines, told after the fact.

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = event.getPlayer() instanceof Player closer ? closer : null;
        tellContainerChanged(containerBlocks(event.getView().getTopInventory()), Optional.ofNullable(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFurnaceStartSmelt(FurnaceStartSmeltEvent event) {
        tellContainerChanged(List.of(event.getBlock()), Optional.empty());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBrewingStart(BrewingStartEvent event) {
        tellContainerChanged(List.of(event.getBlock()), Optional.empty());
    }

    private void tellContainerChanged(List<Block> blocks, Optional<Player> player) {
        for (Block block : blocks) {
            Optional<ToolPlayer> toolPlayer = player.map(players::of).map(ToolPlayer.class::cast);
            BlockPosition at = positionOf(block);
            worlds.forWorld(block.getWorld()).ifPresent(world ->
                    world.tell(listener -> listener.containerChanged(toolPlayer, at)));
        }
    }

    // What players do.

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Optional<BukkitToolWorld> toolWorld = worlds.forWorld(player.getWorld());
        if (toolWorld.isEmpty()) {
            return;
        }
        ToolUse use = new ToolUse(
                event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND ? ToolUse.Hand.OFF : ToolUse.Hand.MAIN,
                ToolUse.Action.valueOf(event.getAction().name()),
                BukkitToolItems.read(event.getItem()),
                Optional.ofNullable(event.getClickedBlock()).map(ToolWorldEvents::positionOf),
                BukkitToolBlocks.faceOf(event.getBlockFace()));
        ToolPlayer toolPlayer = players.of(player);
        if (!toolWorld.get().ask(listener -> listener.used(toolPlayer, use), false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        worlds.forWorld(player.getWorld()).ifPresent(world ->
                world.tell(listener -> listener.sneakToggled(players.of(player), event.isSneaking())));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        Optional<BukkitToolWorld> toolWorld = worlds.forWorld(player.getWorld());
        if (toolWorld.isEmpty()) {
            return;
        }
        String line = lineOf(event.getMessage());
        ToolPlayer toolPlayer = players.of(player);
        if (!toolWorld.get().ask(listener -> listener.command(Optional.of(toolPlayer), line), true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        String line = lineOf(event.getCommand());
        for (BukkitToolWorld toolWorld : worlds.all()) {
            if (!toolWorld.ask(listener -> listener.command(Optional.empty(), line), true)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // Arriving, leaving and moving.

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        worlds.forWorld(player.getWorld()).ifPresent(world -> enter(world, player, player.getLocation()));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        worlds.forWorld(player.getWorld()).ifPresent(world -> leave(world, player));
        players.forget(player);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        worlds.forWorld(event.getFrom()).ifPresent(world -> leave(world, player));
        worlds.forWorld(player.getWorld()).ifPresent(world -> enter(world, player, player.getLocation()));
    }

    /**
     * Every teleport of any player by anyone, portals included, is told to the tool worlds it touches at the time it
     * happens, with the position it goes to: the world it leaves hears {@code left}, the one it enters {@code entered},
     * and the one it arrives in {@code teleported}.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location destination = event.getTo();
        Optional<BukkitToolWorld> from = worlds.forWorld(event.getFrom().getWorld());
        Optional<BukkitToolWorld> to = destination == null ? Optional.empty() : worlds.forWorld(destination.getWorld());
        if (from.isEmpty() && to.isPresent()) {
            players.rememberOrigin(player, event.getFrom());
        }
        if (from.isPresent() && to.isEmpty()) {
            players.restoreGameMode(player);
            players.takeOrigin(player);
        }
        if (!from.equals(to)) {
            from.ifPresent(world -> leave(world, player));
        }
        to.ifPresent(world -> enter(world, player, destination));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Location respawn = event.getRespawnLocation();
        worlds.forWorld(respawn.getWorld()).ifPresent(world -> enter(world, event.getPlayer(), respawn));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || sameBlock(event.getFrom(), to)) {
            return;
        }
        worlds.forWorld(to.getWorld()).ifPresent(world ->
                world.tell(listener -> listener.moved(players.of(event.getPlayer()), BukkitToolBlocks.positionOf(to))));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onChunkLoad(ChunkLoadEvent event) {
        worlds.forWorld(event.getWorld()).ifPresent(world -> world.chunkLoaded(event.getChunk()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        worlds.forWorld(event.getWorld()).ifPresent(BukkitToolWorld::unloaded);
    }

    // The host's own rules.

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNaturalSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL
                && worlds.forWorld(event.getLocation().getWorld()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (worlds.forWorld(event.getFrom().getWorld()).isPresent()) {
            event.setCancelled(true);
        }
    }

    /** The player is in the world at {@code at}: told as having entered on the first of their arrivals, and as having arrived on each. */
    private void enter(BukkitToolWorld world, Player player, Location at) {
        if (world.presence().enter(player.getUniqueId())) {
            world.tell(listener -> listener.entered(players.of(player)));
        }
        BlockPosition arrival = BukkitToolBlocks.positionOf(at);
        world.tell(listener -> listener.teleported(players.of(player), arrival));
    }

    private void leave(BukkitToolWorld world, Player player) {
        if (world.presence().leave(player.getUniqueId())) {
            world.tell(listener -> listener.left(players.of(player)));
        }
    }

    private void allowOrCancel(Cancellable event, Cause cause, Block block, Optional<Player> player) {
        allowOrCancel(event, cause, block.getWorld(), List.of(positionOf(block)), player, Optional.empty());
    }

    private void allowOrCancel(
            Cancellable event,
            Cause cause,
            World world,
            List<BlockPosition> positions,
            Optional<Player> player,
            Optional<BlockState> placed
    ) {
        if (!isAllowed(cause, world, positions, player, placed)) {
            event.setCancelled(true);
        }
    }

    private void allowPistonOrCancel(Cancellable event, Block piston, List<Block> moved, BlockFace direction) {
        boolean allowed = isAllowed(
                Cause.PISTON,
                piston.getWorld(),
                pistonChecked(moved, direction),
                pistonChanged(piston, moved, direction),
                Optional.empty(),
                Optional.empty());
        if (!allowed) {
            event.setCancelled(true);
        }
    }

    private void allowContainerOrCancel(Cancellable event, Inventory inventory, Player player) {
        List<Block> blocks = containerBlocks(inventory);
        if (blocks.isEmpty()) {
            return;
        }
        List<BlockPosition> positions = blocks.stream().map(ToolWorldEvents::positionOf).toList();
        allowOrCancel(event, Cause.CONTAINER, blocks.getFirst().getWorld(), positions, Optional.ofNullable(player), Optional.empty());
    }

    /** Whether the tool lets the change happen; changes outside tool worlds are none of the host's business. */
    private boolean isAllowed(
            Cause cause,
            World world,
            List<BlockPosition> positions,
            Optional<Player> player,
            Optional<BlockState> placed
    ) {
        return isAllowed(cause, world, positions, positions, player, placed);
    }

    private boolean isAllowed(
            Cause cause,
            World world,
            List<BlockPosition> positions,
            List<BlockPosition> changed,
            Optional<Player> player,
            Optional<BlockState> placed
    ) {
        Optional<BukkitToolWorld> toolWorld = worlds.forWorld(world);
        if (toolWorld.isEmpty()) {
            return true;
        }
        BlockChange change = new BlockChange(
                cause, positions, changed, player.map(players::of).map(ToolPlayer.class::cast), placed);
        return toolWorld.get().ask(listener -> listener.blockChanging(change), false);
    }

    private void keepAllowedBlocks(World world, List<Block> exploded) {
        if (worlds.forWorld(world).isEmpty()) {
            return;
        }
        exploded.removeIf(block -> !isAllowed(Cause.EXPLOSION, world, List.of(positionOf(block)), Optional.empty(), Optional.empty()));
    }

    /** Each block a piston moves and the block each of them enters and leaves, which a protected area is checked against; not the piston. */
    static List<BlockPosition> pistonChecked(List<Block> moved, BlockFace direction) {
        List<BlockPosition> positions = new ArrayList<>();
        for (Block block : moved) {
            positions.add(positionOf(block));
            positions.add(positionOf(block.getRelative(direction)));
            positions.add(positionOf(block.getRelative(direction.getOppositeFace())));
        }
        return positions.stream().distinct().toList();
    }

    /** The piston and the block ahead of it, then each block it moves and the block each enters: what a piston changes. */
    static List<BlockPosition> pistonChanged(Block piston, List<Block> moved, BlockFace direction) {
        List<BlockPosition> positions = new ArrayList<>();
        positions.add(positionOf(piston));
        positions.add(positionOf(piston.getRelative(direction)));
        for (Block block : moved) {
            positions.add(positionOf(block));
            positions.add(positionOf(block.getRelative(direction)));
        }
        return positions.stream().distinct().toList();
    }

    /** The blocks a container inventory is made of: none for a player's or a menu, two for a double chest. */
    static List<Block> containerBlocks(Inventory inventory) {
        if (inventory == null) {
            return List.of();
        }
        List<Block> blocks = new ArrayList<>(2);
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof BlockInventoryHolder blockHolder) {
            blocks.add(blockHolder.getBlock());
        } else if (holder instanceof DoubleChest doubleChest) {
            addBlockOf(blocks, doubleChest.getLeftSide());
            addBlockOf(blocks, doubleChest.getRightSide());
        }
        return blocks;
    }

    private static void addBlockOf(List<Block> blocks, InventoryHolder holder) {
        if (holder instanceof BlockInventoryHolder blockHolder && !blocks.contains(blockHolder.getBlock())) {
            blocks.add(blockHolder.getBlock());
        }
    }

    /** Whether two locations are in one block of one world. */
    static boolean sameBlock(Location from, Location to) {
        return from.getWorld() == to.getWorld()
                && from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ();
    }

    /** What a command line is for the tool: without the slash a player types before it. */
    static String lineOf(String typed) {
        return typed.startsWith(COMMAND_PREFIX) ? typed.substring(COMMAND_PREFIX.length()) : typed;
    }

    private static BlockPosition positionOf(Block block) {
        return BukkitToolBlocks.positionOf(block);
    }
}
