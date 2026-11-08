package art.arcane.iris.core.toolworld;

import com.volmit.geometry.BlockPosition;
import com.volmit.tooling.world.BlockChange;
import com.volmit.tooling.world.BlockChange.Cause;
import com.volmit.tooling.world.ToolPlayer;
import com.volmit.tooling.world.ToolUse;
import com.volmit.tooling.world.ToolWorldListener;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The events of a tool world reach its listener as the api says, and the events of other worlds reach nobody. */
public class ToolWorldEventsTest {
    private static final UUID TOOL_WORLD_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OTHER_WORLD_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    private final List<BlockChange> changes = new ArrayList<>();
    private final List<String> heard = new ArrayList<>();
    private boolean allowChanges;
    private boolean allowUses = true;
    private boolean allowCommands = true;
    private World toolBukkitWorld;
    private World otherBukkitWorld;
    private Player bukkitPlayer;
    private ToolWorldEvents events;

    @Before
    public void aToolWorldAndAnotherWorld() {
        ToolWorldListener listener = new ToolWorldListener() {
            @Override
            public void entered(ToolPlayer player) {
                heard.add("entered " + player.name());
            }

            @Override
            public void left(ToolPlayer player) {
                heard.add("left " + player.name());
            }

            @Override
            public boolean used(ToolPlayer player, ToolUse use) {
                heard.add("used " + use.action() + " " + use.hand());
                return allowUses;
            }

            @Override
            public boolean blockChanging(BlockChange change) {
                changes.add(change);
                return allowChanges;
            }

            @Override
            public boolean command(Optional<ToolPlayer> sender, String line) {
                heard.add("command " + sender.map(ToolPlayer::name).orElse("console") + " " + line);
                return allowCommands;
            }
        };
        OpenToolWorlds worlds = new OpenToolWorlds();
        ToolPlayers players = new ToolPlayers(worlds);
        BukkitToolWorld toolWorld = new BukkitToolWorld("pack", "scratch", "iris-test", listener, null,
                new PendingBlockEntities(), worlds, players);
        toolBukkitWorld = world(TOOL_WORLD_ID);
        otherBukkitWorld = world(OTHER_WORLD_ID);
        assertTrue(worlds.register(toolWorld));
        toolWorld.bind(toolBukkitWorld);
        bukkitPlayer = mock(Player.class);
        when(bukkitPlayer.getUniqueId()).thenReturn(PLAYER_ID);
        when(bukkitPlayer.getName()).thenReturn("Tester");
        when(bukkitPlayer.getWorld()).thenReturn(toolBukkitWorld);
        events = new ToolWorldEvents(worlds, players);
    }

    private static World world(UUID id) {
        World world = mock(World.class);
        when(world.getUID()).thenReturn(id);
        return world;
    }

    private static Block block(World world, int x, int y, int z) {
        Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        BlockData data = mock(BlockData.class);
        when(data.getAsString()).thenReturn("minecraft:stone");
        when(block.getBlockData()).thenReturn(data);
        return block;
    }

    private BlockChange onlyChange() {
        assertEquals(1, changes.size());
        return changes.getFirst();
    }

    @Test
    public void aPlacedBlockIsAPlaceChangeWithThePlacedStateAndThePlayer() {
        Block placed = block(toolBukkitWorld, 3, 70, -4);
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getBlockPlaced()).thenReturn(placed);
        when(event.getPlayer()).thenReturn(bukkitPlayer);

        events.onBlockPlace(event);

        BlockChange change = onlyChange();
        assertEquals(Cause.PLACE, change.cause());
        assertEquals(List.of(new BlockPosition(3, 70, -4)), change.positions());
        assertEquals("minecraft:stone", change.placed().orElseThrow().key());
        assertEquals("Tester", change.player().orElseThrow().name());
        verify(event).setCancelled(true);
    }

    @Test
    public void aListenerThatAllowsTheChangeLeavesTheEventAlone() {
        allowChanges = true;
        Block broken = block(toolBukkitWorld, 1, 2, 3);
        BlockBreakEvent event = mock(BlockBreakEvent.class);
        when(event.getBlock()).thenReturn(broken);
        when(event.getPlayer()).thenReturn(bukkitPlayer);

        events.onBlockBreak(event);

        assertEquals(Cause.BREAK, onlyChange().cause());
        verify(event, never()).setCancelled(true);
    }

    @Test
    public void aBucketEmptiesIntoTheBlockNextToTheOneClicked() {
        Block clicked = block(toolBukkitWorld, 0, 64, 0);
        Block target = block(toolBukkitWorld, 0, 65, 0);
        when(clicked.getRelative(BlockFace.UP)).thenReturn(target);
        PlayerBucketEmptyEvent event = mock(PlayerBucketEmptyEvent.class);
        when(event.getBlockClicked()).thenReturn(clicked);
        when(event.getBlockFace()).thenReturn(BlockFace.UP);
        when(event.getPlayer()).thenReturn(bukkitPlayer);

        events.onBucketEmpty(event);

        BlockChange change = onlyChange();
        assertEquals(Cause.BUCKET, change.cause());
        assertEquals(List.of(new BlockPosition(0, 65, 0)), change.positions());
    }

    @Test
    public void flowingFluidNamesTheSourceAndTheTargetBlock() {
        Block source = block(toolBukkitWorld, 0, 64, 0);
        Block target = block(toolBukkitWorld, 1, 64, 0);
        BlockFromToEvent event = mock(BlockFromToEvent.class);
        when(event.getBlock()).thenReturn(source);
        when(event.getToBlock()).thenReturn(target);

        events.onBlockFromTo(event);

        BlockChange change = onlyChange();
        assertEquals(Cause.FLUID, change.cause());
        assertEquals(List.of(new BlockPosition(0, 64, 0), new BlockPosition(1, 64, 0)), change.positions());
        assertTrue(change.player().isEmpty());
        verify(event).setCancelled(true);
    }

    @Test
    public void aPistonNamesItselfAndEveryBlockItMovesAndTheirNeighboursInTheDirectionOfTheMove() {
        Block piston = block(toolBukkitWorld, 0, 64, 0);
        Block moved = block(toolBukkitWorld, 1, 64, 0);
        Block ahead = block(toolBukkitWorld, 2, 64, 0);
        when(moved.getRelative(BlockFace.EAST)).thenReturn(ahead);
        when(moved.getRelative(BlockFace.WEST)).thenReturn(piston);

        List<BlockPosition> positions = ToolWorldEvents.pistonPositions(piston, List.of(moved), BlockFace.EAST);

        assertEquals(List.of(new BlockPosition(0, 64, 0), new BlockPosition(1, 64, 0), new BlockPosition(2, 64, 0)), positions);
    }

    @Test
    public void aPistonExtendingInAToolWorldIsAPistonChange() {
        Block piston = block(toolBukkitWorld, 0, 64, 0);
        BlockPistonExtendEvent event = mock(BlockPistonExtendEvent.class);
        when(event.getBlock()).thenReturn(piston);
        when(event.getBlocks()).thenReturn(List.of());
        when(event.getDirection()).thenReturn(BlockFace.EAST);

        events.onPistonExtend(event);

        assertEquals(Cause.PISTON, onlyChange().cause());
        verify(event).setCancelled(true);
    }

    @Test
    public void anExplosionAsksForEachBlockAndKeepsOnlyTheOnesTheToolAllows() {
        allowChanges = false;
        Block first = block(toolBukkitWorld, 0, 64, 0);
        Block second = block(toolBukkitWorld, 5, 64, 5);
        List<Block> blockList = new ArrayList<>(List.of(first, second));
        Block source = block(toolBukkitWorld, 0, 70, 0);
        BlockExplodeEvent event = mock(BlockExplodeEvent.class);
        when(event.getBlock()).thenReturn(source);
        when(event.blockList()).thenReturn(blockList);

        events.onBlockExplode(event);

        assertEquals(2, changes.size());
        assertEquals(Cause.EXPLOSION, changes.getFirst().cause());
        assertTrue("nothing was allowed to blow up", blockList.isEmpty());
    }

    @Test
    public void refusedRedstoneKeepsTheOldCurrent() {
        Block wire = block(toolBukkitWorld, 0, 64, 0);
        BlockRedstoneEvent event = mock(BlockRedstoneEvent.class);
        when(event.getBlock()).thenReturn(wire);
        when(event.getOldCurrent()).thenReturn(7);

        events.onBlockRedstone(event);

        assertEquals(Cause.REDSTONE, onlyChange().cause());
        verify(event).setNewCurrent(7);
    }

    @Test
    public void aCrafterIsAMachine() {
        Block crafter = block(toolBukkitWorld, 0, 64, 0);
        CrafterCraftEvent event = mock(CrafterCraftEvent.class);
        when(event.getBlock()).thenReturn(crafter);

        events.onCrafterCraft(event);

        assertEquals(Cause.MACHINE, onlyChange().cause());
    }

    @Test
    public void nothingThatHappensInAnotherWorldReachesTheToolOrIsCancelled() {
        Block brokenElsewhere = block(otherBukkitWorld, 1, 2, 3);
        Block wireElsewhere = block(otherBukkitWorld, 1, 2, 3);
        BlockBreakEvent breaking = mock(BlockBreakEvent.class);
        when(breaking.getBlock()).thenReturn(brokenElsewhere);
        when(breaking.getPlayer()).thenReturn(bukkitPlayer);
        BlockRedstoneEvent redstone = mock(BlockRedstoneEvent.class);
        when(redstone.getBlock()).thenReturn(wireElsewhere);

        events.onBlockBreak(breaking);
        events.onBlockRedstone(redstone);

        assertTrue(changes.isEmpty());
        verify(breaking, never()).setCancelled(true);
        verify(redstone, never()).setNewCurrent(0);
    }

    @Test
    public void usingTheHandIsAUseWithTheHandAndActionAndAFalseCancelsIt() {
        allowUses = false;
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getPlayer()).thenReturn(bukkitPlayer);
        when(event.getHand()).thenReturn(EquipmentSlot.OFF_HAND);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(event.getBlockFace()).thenReturn(BlockFace.SELF);

        events.onInteract(event);

        assertEquals(List.of("used RIGHT_CLICK_AIR OFF"), heard);
        verify(event).setCancelled(true);
    }

    @Test
    public void aCommandReachesTheToolWithoutItsSlashAndTheConsoleHasNoPlayer() {
        allowCommands = false;
        PlayerCommandPreprocessEvent typed = mock(PlayerCommandPreprocessEvent.class);
        when(typed.getPlayer()).thenReturn(bukkitPlayer);
        when(typed.getMessage()).thenReturn("/gamemode survival");
        ServerCommandEvent console = mock(ServerCommandEvent.class);
        when(console.getCommand()).thenReturn("stop");

        events.onPlayerCommand(typed);
        events.onServerCommand(console);

        assertEquals(List.of("command Tester gamemode survival", "command console stop"), heard);
        verify(typed).setCancelled(true);
        verify(console).setCancelled(true);
    }

    @Test
    public void naturalSpawnsInAToolWorldAreCancelledButOtherSpawnsAreNot() {
        CreatureSpawnEvent natural = mock(CreatureSpawnEvent.class);
        when(natural.getSpawnReason()).thenReturn(CreatureSpawnEvent.SpawnReason.NATURAL);
        when(natural.getLocation()).thenReturn(new Location(toolBukkitWorld, 0, 64, 0));
        CreatureSpawnEvent egg = mock(CreatureSpawnEvent.class);
        when(egg.getSpawnReason()).thenReturn(CreatureSpawnEvent.SpawnReason.SPAWNER_EGG);
        when(egg.getLocation()).thenReturn(new Location(toolBukkitWorld, 0, 64, 0));
        CreatureSpawnEvent elsewhere = mock(CreatureSpawnEvent.class);
        when(elsewhere.getSpawnReason()).thenReturn(CreatureSpawnEvent.SpawnReason.NATURAL);
        when(elsewhere.getLocation()).thenReturn(new Location(otherBukkitWorld, 0, 64, 0));

        events.onNaturalSpawn(natural);
        events.onNaturalSpawn(egg);
        events.onNaturalSpawn(elsewhere);

        verify(natural).setCancelled(true);
        verify(egg, never()).setCancelled(true);
        verify(elsewhere, never()).setCancelled(true);
    }

    @Test
    public void enteringIsToldOnceHoweverManyEventsAnnounceTheArrival() {
        PlayerTeleportEvent teleport = mock(PlayerTeleportEvent.class);
        when(teleport.getPlayer()).thenReturn(bukkitPlayer);
        when(teleport.getFrom()).thenReturn(new Location(otherBukkitWorld, 0, 64, 0));
        when(teleport.getTo()).thenReturn(new Location(toolBukkitWorld, 0, 64, 0));

        events.onTeleport(teleport);
        events.onTeleport(teleport);

        assertEquals(List.of("entered Tester"), heard);
    }

    @Test
    public void leavingByQuittingIsToldOnceAfterTheStay() {
        PlayerTeleportEvent teleport = mock(PlayerTeleportEvent.class);
        when(teleport.getPlayer()).thenReturn(bukkitPlayer);
        when(teleport.getFrom()).thenReturn(new Location(otherBukkitWorld, 0, 64, 0));
        when(teleport.getTo()).thenReturn(new Location(toolBukkitWorld, 0, 64, 0));
        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(bukkitPlayer);

        events.onTeleport(teleport);
        events.onQuit(quit);
        events.onQuit(quit);

        assertEquals(List.of("entered Tester", "left Tester"), heard);
    }

    @Test
    public void movingInsideOneBlockIsNotAMove() {
        Location from = new Location(toolBukkitWorld, 1.1, 64.0, 2.2);

        assertTrue(ToolWorldEvents.sameBlock(from, new Location(toolBukkitWorld, 1.9, 64.5, 2.9)));
        assertFalse(ToolWorldEvents.sameBlock(from, new Location(toolBukkitWorld, 2.1, 64.0, 2.2)));
        assertFalse(ToolWorldEvents.sameBlock(from, new Location(otherBukkitWorld, 1.1, 64.0, 2.2)));
    }

    @Test
    public void aCommandLineHasNoLeadingSlash() {
        assertEquals("tp a b", ToolWorldEvents.lineOf("/tp a b"));
        assertEquals("tp a b", ToolWorldEvents.lineOf("tp a b"));
    }

    @Test
    public void everyHandlerKeepsThePriorityAndCancelFlagOfTheJigsawStudioHandlerItReplaces() {
        Map<String, EventHandler> byMethod = new HashMap<>();
        for (Method method : ToolWorldEvents.class.getDeclaredMethods()) {
            EventHandler handler = method.getAnnotation(EventHandler.class);
            if (handler != null) {
                byMethod.put(method.getName(), handler);
            }
        }
        Set<String> checked = new HashSet<>();

        for (String decision : List.of("onBlockPlace", "onBlockMultiPlace", "onBlockBreak", "onBucketEmpty", "onBucketFill",
                "onBlockForm", "onBlockFade", "onBlockSpread", "onBlockFromTo", "onBlockGrow", "onStructureGrow", "onBlockBurn",
                "onPistonExtend", "onPistonRetract", "onBlockExplode", "onEntityExplode", "onEntityChangeBlock",
                "onInventoryClick", "onInventoryDrag", "onInventoryMove", "onInventoryPickup", "onBlockCook", "onFurnaceBurn",
                "onFurnaceSmelt", "onBrew", "onBrewingStandFuel", "onBlockDispense", "onCrafterCraft", "onPlayerCommand",
                "onServerCommand", "onNaturalSpawn", "onPortal")) {
            assertPriority(byMethod, checked, decision, EventPriority.HIGHEST, true);
        }
        assertPriority(byMethod, checked, "onBlockRedstone", EventPriority.HIGHEST, false);
        assertPriority(byMethod, checked, "onInteract", EventPriority.LOWEST, true);
        assertPriority(byMethod, checked, "onToggleSneak", EventPriority.NORMAL, false);
        for (String arrival : List.of("onJoin", "onQuit", "onChangedWorld", "onChunkLoad")) {
            assertPriority(byMethod, checked, arrival, EventPriority.NORMAL, false);
        }
        assertPriority(byMethod, checked, "onTeleport", EventPriority.MONITOR, true);
        assertPriority(byMethod, checked, "onMove", EventPriority.MONITOR, true);
        assertPriority(byMethod, checked, "onRespawn", EventPriority.MONITOR, false);
        assertPriority(byMethod, checked, "onWorldUnload", EventPriority.MONITOR, true);
        assertEquals("every handler of the class was checked", byMethod.keySet(), checked);
    }

    private static void assertPriority(
            Map<String, EventHandler> byMethod,
            Set<String> checked,
            String method,
            EventPriority priority,
            boolean ignoreCancelled
    ) {
        checked.add(method);
        EventHandler handler = byMethod.get(method);
        assertTrue(method + " is a handler", handler != null);
        assertEquals(method + " priority", priority, handler.priority());
        assertEquals(method + " ignoreCancelled", ignoreCancelled, handler.ignoreCancelled());
    }
}
