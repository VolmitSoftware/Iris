package art.arcane.iris.integration;

import art.arcane.iris.api.pregen.IrisPregenPhase;
import art.arcane.iris.api.pregen.IrisPregenProgress;
import art.arcane.iris.api.pregen.IrisPregenerationEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class IrisPapiListenerTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000d4");
    private static final String DASH = "---";

    private static IrisPregenProgress progress(double percent) {
        return new IrisPregenProgress(
                "sandbox",
                "sandbox:identity",
                percent,
                1234L,
                4096L,
                2862L,
                0L,
                12.5D,
                125_000L,
                60_000L,
                "async",
                false);
    }

    private static final class Harness {
        private final FakeIrisTerrainService terrain = new FakeIrisTerrainService();
        private final IrisPapiTestSupport.Clock clock = new IrisPapiTestSupport.Clock();
        private final IrisPapiState state = new IrisPapiState(() -> terrain, clock);
        private final IrisPapiListener listener = new IrisPapiListener(state);
        private final World sandbox = IrisPapiTestSupport.world("sandbox");
        private final World hub = IrisPapiTestSupport.world("hub");
        private final IrisPapiTestSupport.StandingPlayer player;

        private Harness() {
            terrain.addIrisWorld(sandbox);
            player = new IrisPapiTestSupport.StandingPlayer(PLAYER, at(sandbox, 10, 10));
        }

        private static Location at(World world, int blockX, int blockZ) {
            return new Location(world, blockX + 0.5D, 71.0D, blockZ + 0.5D);
        }
    }

    @Test
    public void theListenerIsABukkitListener() {
        assertTrue(Listener.class.isAssignableFrom(IrisPapiListener.class));
    }

    @Test
    public void everyPositionSourceIsHandledAtMonitorPriority() {
        Set<Class<?>> handled = new HashSet<>();

        for (Method method : IrisPapiListener.class.getDeclaredMethods()) {
            EventHandler handler = method.getAnnotation(EventHandler.class);

            if (handler == null) {
                continue;
            }

            assertEquals("placeholder bookkeeping must never influence an event: " + method.getName(),
                    EventPriority.MONITOR, handler.priority());
            assertEquals("a handler takes exactly one event: " + method.getName(),
                    1, method.getParameterCount());
            handled.add(method.getParameterTypes()[0]);
        }

        assertEquals(Set.of(
                        PlayerMoveEvent.class,
                        PlayerTeleportEvent.class,
                        PlayerPortalEvent.class,
                        PlayerRespawnEvent.class,
                        PlayerJoinEvent.class,
                        PlayerChangedWorldEvent.class,
                        PlayerQuitEvent.class,
                        IrisPregenerationEvent.class),
                handled);
    }

    @Test
    public void everyStepOfTheMoveHierarchyOwnsItsHandlerListSoNoneIsCoveredByItsParent() throws Exception {
        List<Class<? extends Event>> hierarchy =
                List.of(PlayerMoveEvent.class, PlayerTeleportEvent.class, PlayerPortalEvent.class);
        Set<Class<?>> handled = handledEventTypes();

        for (int index = 1; index < hierarchy.size(); index++) {
            Class<? extends Event> child = hierarchy.get(index);
            Class<? extends Event> parent = hierarchy.get(index - 1);

            assertSame(child.getName() + " no longer extends " + parent.getName(),
                    parent, child.getSuperclass());
            assertNotSame(child.getName() + " has its own HandlerList, so a fired " + child.getSimpleName()
                            + " never reaches a " + parent.getSimpleName() + " handler",
                    handlerList(parent), handlerList(child));
            assertTrue(child.getName() + " is a position source that no other handler can cover",
                    handled.contains(child));
        }

        assertTrue(PlayerMoveEvent.class.getName() + " must still be handled",
                handled.contains(PlayerMoveEvent.class));
    }

    private static HandlerList handlerList(Class<? extends Event> type) throws Exception {
        return (HandlerList) type.getDeclaredMethod("getHandlerList").invoke(null);
    }

    private static Set<Class<?>> handledEventTypes() {
        Set<Class<?>> handled = new HashSet<>();

        for (Method method : IrisPapiListener.class.getDeclaredMethods()) {
            if (method.getAnnotation(EventHandler.class) != null) {
                handled.add(method.getParameterTypes()[0]);
            }
        }

        return handled;
    }

    @Test
    public void joiningPublishesTheColumnThePlayerLandsOn() {
        Harness harness = new Harness();

        assertEquals("false", harness.state.worldAvailable(PLAYER));

        harness.listener.onPlayerJoin(new PlayerJoinEvent(harness.player.handle(), Component.empty()));

        assertEquals("true", harness.state.worldAvailable(PLAYER));
        assertEquals("Hot Desert Dunes", harness.state.biome(PLAYER));
        assertEquals("10,10", harness.terrain.sampledColumn());
    }

    @Test
    public void movingPublishesTheDestinationColumn() {
        Harness harness = new Harness();
        Location from = harness.player.standing();
        Location to = Harness.at(harness.sandbox, 40, -80);

        harness.listener.onPlayerMove(new PlayerMoveEvent(harness.player.handle(), from, to));

        assertEquals("true", harness.state.worldAvailable(PLAYER));
        assertEquals("Hot Desert Dunes", harness.state.biome(PLAYER));
        assertEquals("40,-80", harness.terrain.sampledColumn());
    }

    @Test
    public void aSameWorldTeleportUpdatesTheBoardWithoutAnyFurtherMovement() {
        Harness harness = new Harness();

        harness.listener.onPlayerJoin(new PlayerJoinEvent(harness.player.handle(), Component.empty()));
        assertEquals("Hot Desert Dunes", harness.state.biome(PLAYER));
        assertEquals("10,10", harness.terrain.sampledColumn());

        harness.terrain.describe("overworld", "Frozen Shelf", "cold/shelf", "Glacier", "glacier");

        Location from = harness.player.standing();
        Location to = Harness.at(harness.sandbox, 2000, -2000);
        harness.player.standAt(to);
        harness.listener.onPlayerTeleport(new PlayerTeleportEvent(
                harness.player.handle(), from, to, PlayerTeleportEvent.TeleportCause.COMMAND));

        assertEquals("a same world teleport must republish the position immediately,"
                        + " with no move, no world change and no clock advance",
                "Frozen Shelf", harness.state.biome(PLAYER));
        assertEquals("2000,-2000", harness.terrain.sampledColumn());
        assertEquals("cold/shelf", harness.state.biomeKey(PLAYER));
        assertEquals("Glacier", harness.state.region(PLAYER));
    }

    @Test
    public void aSameWorldPortalUpdatesTheBoardWithoutAnyFurtherMovement() {
        Harness harness = new Harness();

        harness.listener.onPlayerJoin(new PlayerJoinEvent(harness.player.handle(), Component.empty()));
        assertEquals("Hot Desert Dunes", harness.state.biome(PLAYER));

        harness.terrain.describe("overworld", "Frozen Shelf", "cold/shelf", "Glacier", "glacier");

        Location from = harness.player.standing();
        Location to = Harness.at(harness.sandbox, 512, 512);
        harness.player.standAt(to);
        harness.listener.onPlayerPortal(new PlayerPortalEvent(
                harness.player.handle(), from, to, PlayerTeleportEvent.TeleportCause.NETHER_PORTAL));

        assertEquals("Frozen Shelf", harness.state.biome(PLAYER));
        assertEquals("512,512", harness.terrain.sampledColumn());
    }

    @Test
    public void aSameWorldRespawnUpdatesTheBoardWithoutAnyFurtherMovement() {
        Harness harness = new Harness();

        harness.listener.onPlayerJoin(new PlayerJoinEvent(harness.player.handle(), Component.empty()));
        assertEquals("Hot Desert Dunes", harness.state.biome(PLAYER));

        harness.terrain.describe("overworld", "Frozen Shelf", "cold/shelf", "Glacier", "glacier");

        Location bed = Harness.at(harness.sandbox, -333, 777);
        harness.player.standAt(bed);
        harness.listener.onPlayerRespawn(new PlayerRespawnEvent(
                harness.player.handle(), bed, true, false, false, PlayerRespawnEvent.RespawnReason.DEATH));

        assertEquals("Frozen Shelf", harness.state.biome(PLAYER));
        assertEquals("-333,777", harness.terrain.sampledColumn());
    }

    @Test
    public void changingWorldsRepublishesAgainstTheNewWorld() {
        Harness harness = new Harness();

        harness.listener.onPlayerJoin(new PlayerJoinEvent(harness.player.handle(), Component.empty()));
        assertEquals("true", harness.state.worldAvailable(PLAYER));

        harness.player.standAt(Harness.at(harness.hub, 0, 0));
        harness.listener.onPlayerChangedWorld(new PlayerChangedWorldEvent(harness.player.handle(), harness.sandbox));

        assertEquals("false", harness.state.worldAvailable(PLAYER));
        assertEquals(DASH, harness.state.biome(PLAYER));
        assertEquals(DASH, harness.state.dimension(PLAYER));
    }

    @Test
    public void quittingEvictsThePositionAndTheViewSoNoWorldIsHeld() {
        Harness harness = new Harness();

        harness.listener.onPlayerJoin(new PlayerJoinEvent(harness.player.handle(), Component.empty()));
        assertEquals("Hot Desert Dunes", harness.state.biome(PLAYER));

        harness.listener.onPlayerQuit(new PlayerQuitEvent(
                harness.player.handle(), Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertEquals("false", harness.state.worldAvailable(PLAYER));
        assertEquals(DASH, harness.state.biome(PLAYER));
        assertEquals(DASH, harness.state.region(PLAYER));
        assertEquals(DASH, harness.state.dimension(PLAYER));
    }

    @Test
    public void thePregenHandlerLatchesProgressAndRetiresItOnATerminalPhase() {
        Harness harness = new Harness();

        assertEquals("false", harness.state.pregenAvailable(PLAYER));

        harness.listener.onPregeneration(new IrisPregenerationEvent(IrisPregenPhase.TICK, progress(42.5D)));

        assertEquals("true", harness.state.pregenAvailable(PLAYER));
        assertEquals("sandbox", harness.state.pregenWorld(PLAYER));
        assertEquals("42.50", harness.state.pregenPercent(PLAYER));
        assertEquals("2m 5s", harness.state.pregenEtaText(PLAYER));

        harness.listener.onPregeneration(new IrisPregenerationEvent(IrisPregenPhase.COMPLETED, progress(100.0D)));

        assertEquals("false", harness.state.pregenAvailable(PLAYER));
        assertEquals(DASH, harness.state.pregenPercent(PLAYER));
    }

    @Test
    public void trackingPublishesTheBlockColumnOfTheLocation() {
        FakeIrisTerrainService terrain = new FakeIrisTerrainService();
        World world = IrisPapiTestSupport.world("sandbox");
        terrain.addIrisWorld(world);
        IrisPapiState state = new IrisPapiState(() -> terrain, new IrisPapiTestSupport.Clock());

        IrisPapiListener.track(state, PLAYER, new Location(world, 128.7D, 71.0D, -512.2D));

        assertEquals("true", state.worldAvailable(PLAYER));
        assertEquals("Hot Desert Dunes", state.biome(PLAYER));
        assertEquals("128,-513", terrain.sampledColumn());
    }

    @Test
    public void trackingIgnoresMissingInputsInsteadOfThrowing() {
        FakeIrisTerrainService terrain = new FakeIrisTerrainService();
        World world = IrisPapiTestSupport.world("sandbox");
        terrain.addIrisWorld(world);
        IrisPapiState state = new IrisPapiState(() -> terrain, new IrisPapiTestSupport.Clock());

        IrisPapiListener.track(null, PLAYER, new Location(world, 0.0D, 0.0D, 0.0D));
        IrisPapiListener.track(state, null, new Location(world, 0.0D, 0.0D, 0.0D));
        IrisPapiListener.track(state, PLAYER, null);
        IrisPapiListener.track(state, PLAYER, new Location(null, 0.0D, 0.0D, 0.0D));
        IrisPapiListener.trackNow(null, PLAYER, new Location(world, 0.0D, 0.0D, 0.0D));
        IrisPapiListener.trackNow(state, null, new Location(world, 0.0D, 0.0D, 0.0D));
        IrisPapiListener.trackNow(state, PLAYER, null);
        IrisPapiListener.trackNow(state, PLAYER, new Location(null, 0.0D, 0.0D, 0.0D));

        assertEquals("false", state.worldAvailable(PLAYER));
    }
}
