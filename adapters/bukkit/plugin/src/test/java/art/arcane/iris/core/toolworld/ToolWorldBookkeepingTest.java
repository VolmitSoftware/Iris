package art.arcane.iris.core.toolworld;

import art.arcane.iris.core.runtime.StudioPainterSlots.PaintedBlockEntity;
import art.arcane.iris.core.runtime.StudioPainterSlots.PaintedChunk;
import com.volmit.object.BlockEntityData;
import com.volmit.resource.ResourceId;
import org.bukkit.World;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The small pieces of state the Bukkit host keeps for its tool worlds. */
public class ToolWorldBookkeepingTest {
    private static final BlockEntityData DATA = new BlockEntityData(ResourceId.parse("iris:tile"), 1, new byte[] {1});

    @Test
    public void aPlayerEntersAndLeavesOncePerStay() {
        ToolWorldPresence presence = new ToolWorldPresence();
        UUID player = UUID.randomUUID();

        assertFalse(presence.leave(player));
        assertTrue(presence.enter(player));
        assertFalse(presence.enter(player));
        assertTrue(presence.isPresent(player));
        assertTrue(presence.leave(player));
        assertFalse(presence.leave(player));
        assertTrue("a second stay is told again", presence.enter(player));
    }

    @Test
    public void blockEntitiesWaitForTheirChunkAndAreHandedOverOnce() {
        PendingBlockEntities pending = new PendingBlockEntities();
        PaintedBlockEntity chest = new PaintedBlockEntity(1, 64, 2, DATA);
        pending.add(new PaintedChunk(-3, 7, List.of(chest)));

        assertEquals(List.of(), pending.take(3, 7));
        assertEquals(List.of(), pending.take(-3, -7));
        assertEquals(List.of(chest), pending.take(-3, 7));
        assertEquals(List.of(), pending.take(-3, 7));
    }

    @Test
    public void aLaterPaintOfAChunkReplacesWhatWaitedForIt() {
        PendingBlockEntities pending = new PendingBlockEntities();
        PaintedBlockEntity first = new PaintedBlockEntity(1, 64, 2, DATA);
        PaintedBlockEntity second = new PaintedBlockEntity(3, 64, 4, DATA);
        pending.add(new PaintedChunk(0, 0, List.of(first)));
        pending.add(new PaintedChunk(0, 0, List.of(second)));

        assertEquals(List.of(second), pending.take(0, 0));
    }

    @Test
    public void aToolNameIsTakenWithinAPackAndFreeAgainOnceTheWorldIsUnregistered() {
        OpenToolWorlds worlds = new OpenToolWorlds();
        BukkitToolWorld first = toolWorld(worlds, "pack-a", "scratch", "iris-1");
        BukkitToolWorld sameNameOtherPack = toolWorld(worlds, "pack-b", "scratch", "iris-2");
        BukkitToolWorld sameNameSamePack = toolWorld(worlds, "pack-a", "scratch", "iris-3");

        assertTrue(worlds.register(first));
        assertTrue(worlds.register(sameNameOtherPack));
        assertFalse(worlds.register(sameNameSamePack));
        assertEquals(first, worlds.forName("pack-a", "scratch").orElseThrow());

        worlds.unregister(first);

        assertTrue(worlds.forName("pack-a", "scratch").isEmpty());
        assertTrue(worlds.register(sameNameSamePack));
    }

    @Test
    public void aBoundWorldIsFoundByItsBukkitWorldAndNotByAnotherWorld() {
        OpenToolWorlds worlds = new OpenToolWorlds();
        BukkitToolWorld toolWorld = toolWorld(worlds, "pack", "scratch", "iris-1");
        World bukkit = world(UUID.randomUUID());
        World other = world(UUID.randomUUID());
        assertTrue(worlds.register(toolWorld));

        toolWorld.bind(bukkit);

        assertEquals(toolWorld, worlds.forWorld(bukkit).orElseThrow());
        assertTrue(worlds.forWorld(other).isEmpty());
        assertTrue(worlds.forWorld(null).isEmpty());

        worlds.unregister(toolWorld);

        assertTrue(worlds.forWorld(bukkit).isEmpty());
    }

    @Test
    public void aMenuSlotCountsFromTheMiddleColumnOfTheWindow() {
        assertEquals(-4, BukkitToolMenus.positionOf(0));
        assertEquals(0, BukkitToolMenus.positionOf(4));
        assertEquals(4, BukkitToolMenus.positionOf(8));
        assertEquals(-4, BukkitToolMenus.positionOf(9));
        assertEquals(4, BukkitToolMenus.positionOf(53));
    }

    @Test
    public void aMenuTitleIsShortenedToWhatTheWindowCanShow() {
        assertEquals("short", BukkitToolMenus.shortened("short"));
        assertEquals(32, BukkitToolMenus.shortened("x".repeat(50)).length());
    }

    private static BukkitToolWorld toolWorld(OpenToolWorlds worlds, String pack, String name, String storageName) {
        return new BukkitToolWorld(pack, name, storageName, new com.volmit.tooling.world.ToolWorldListener() { }, null,
                new PendingBlockEntities(), worlds, new ToolPlayers(worlds));
    }

    private static World world(UUID id) {
        World world = mock(World.class);
        when(world.getUID()).thenReturn(id);
        return world;
    }
}
