package art.arcane.iris.integration;

import art.arcane.iris.world.lifecycle.ManagedWorldLoader;
import art.arcane.iris.platform.generation.BukkitChunkGenerator;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.PluginManager;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;
import org.mvplugins.multiverse.core.event.world.MVWorldDeleteEvent;
import org.mvplugins.multiverse.core.world.LoadedMultiverseWorld;
import org.mvplugins.multiverse.external.vavr.control.Option;

import java.io.File;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class MultiverseGuardListenerTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void multiverseCommandRootsAreRecognisedThroughEveryDispatchForm() {
        assertEquals("mv", MultiverseGuardListener.multiverseCommandRoot("/mv clone a b"));
        assertEquals("mv", MultiverseGuardListener.multiverseCommandRoot("mv clone a b"));
        assertEquals("mv", MultiverseGuardListener.multiverseCommandRoot("/multiverse-core:mv delete a"));
        assertEquals("mvclone", MultiverseGuardListener.multiverseCommandRoot("/MVClone a b"));
        assertEquals("mvregen", MultiverseGuardListener.multiverseCommandRoot("/mvregen a"));
        assertNull(MultiverseGuardListener.multiverseCommandRoot("/iris remove world=a"));
        assertNull(MultiverseGuardListener.multiverseCommandRoot("/tp somebody"));
        assertNull(MultiverseGuardListener.multiverseCommandRoot("   "));
        assertNull(MultiverseGuardListener.multiverseCommandRoot(null));
    }

    @Test
    public void cloneSourceAndDestinationAreReadFromEveryCloneAlias() {
        assertClone("mvtest", "copy", "/mv clone mvtest copy");
        assertClone("mvtest", "copy", "mv clone mvtest copy --reset-gamerules");
        assertClone("mvtest", "copy", "/mvcl mvtest copy");
        assertClone("mvtest", "copy", "/mvclone mvtest copy");
        assertClone("mvtest", "copy", "/mv  clone   mvtest   copy");
        assertClone("mvtest", null, "/mvcl mvtest");
        assertClone("mvtest", null, "/mvcl mvtest --reset-gamerules");
    }

    @Test
    public void loadTargetsAreReadFromEveryLoadAlias() {
        assertEquals("world_iris_mvtest", MultiverseGuardListener.multiverseLoad("/mv load world_iris_mvtest"));
        assertEquals("world_iris_mvtest", MultiverseGuardListener.multiverseLoad("mv LOAD world_iris_mvtest"));
        assertEquals("world_iris_mvtest",
                MultiverseGuardListener.multiverseLoad("/mvload world_iris_mvtest --skip-folder-check"));
        assertEquals("world_iris_mvtest",
                MultiverseGuardListener.multiverseLoad("/mv  load   world_iris_mvtest"));
        assertNull(MultiverseGuardListener.multiverseLoad("/mv load"));
        assertNull(MultiverseGuardListener.multiverseLoad("/mvload"));
        assertNull(MultiverseGuardListener.multiverseLoad("/mv unload world_iris_mvtest"));
        assertNull(MultiverseGuardListener.multiverseLoad("/mv clone a b"));
        assertNull(MultiverseGuardListener.multiverseLoad("/iris load world_iris_mvtest"));
    }

    /**
     * Multiverse rebuilds an unloaded world from a WorldCreator carrying the environment it stored, and an
     * Iris world reports CUSTOM after its first restart, which CraftServer refuses outright. Iris owns the
     * load, so the command is taken over rather than left to fail.
     */
    @Test
    public void loadingAnUnloadedIrisWorldIsDelegatedToIris() throws Exception {
        File levelRoot = temporaryFolder.newFolder("load-delegate", "world");
        assertTrue(new File(levelRoot, "dimensions/iris/mvtest").mkdirs());
        RecordingLoader loader = new RecordingLoader(true, "Loaded world_iris_mvtest.");
        IrisServices.register(ManagedWorldLoader.class, loader);
        try {
            withServer(levelRoot, null, listener -> {
                ServerCommandEvent event = consoleCommand("mv load world_iris_mvtest");
                listener.onServerCommand(event);
                assertTrue("Multiverse must never reach CraftServer.createWorld for an Iris world",
                        event.isCancelled());
            });
            assertEquals("world_iris_mvtest", loader.requested);
        } finally {
            IrisServices.remove(ManagedWorldLoader.class);
        }
    }

    /**
     * Multiverse takes the bare key or iris:<key> just as happily as the startup name; Iris has to load the
     * one name the world is registered under either way.
     */
    @Test
    public void anAliasFormOfAnIrisWorldIsLoadedUnderItsStartupName() throws Exception {
        File levelRoot = temporaryFolder.newFolder("load-alias", "world");
        assertTrue(new File(levelRoot, "dimensions/iris/mvtest").mkdirs());
        RecordingLoader loader = new RecordingLoader(true, "Loaded.");
        IrisServices.register(ManagedWorldLoader.class, loader);
        try {
            withServer(levelRoot, null, listener -> {
                ServerCommandEvent event = consoleCommand("mv load iris:mvtest");
                listener.onServerCommand(event);
                assertTrue(event.isCancelled());
            });
            assertEquals("world_iris_mvtest", loader.requested);
        } finally {
            IrisServices.remove(ManagedWorldLoader.class);
        }
    }

    @Test
    public void loadingAnIrisWorldThatIsAlreadyLiveIsLeftToMultiverse() throws Exception {
        File levelRoot = temporaryFolder.newFolder("load-live", "world");
        assertTrue(new File(levelRoot, "dimensions/iris/mvtest").mkdirs());
        RecordingLoader loader = new RecordingLoader(true, "Loaded.");
        IrisServices.register(ManagedWorldLoader.class, loader);
        try {
            withServer(levelRoot, irisWorld("world_iris_mvtest"), listener -> {
                ServerCommandEvent event = consoleCommand("mv load world_iris_mvtest");
                listener.onServerCommand(event);
                assertFalse("Multiverse adopts a live world without a WorldCreator", event.isCancelled());
            });
            assertNull(loader.requested);
        } finally {
            IrisServices.remove(ManagedWorldLoader.class);
        }
    }

    @Test
    public void loadingAWorldIrisDoesNotOwnIsLeftAlone() throws Exception {
        File levelRoot = temporaryFolder.newFolder("load-vanilla", "world");
        RecordingLoader loader = new RecordingLoader(true, "Loaded.");
        IrisServices.register(ManagedWorldLoader.class, loader);
        try {
            withServer(levelRoot, null, listener -> {
                ServerCommandEvent event = consoleCommand("mv load plots");
                listener.onServerCommand(event);
                assertFalse(event.isCancelled());
            });
            assertNull(loader.requested);
        } finally {
            IrisServices.remove(ManagedWorldLoader.class);
        }
    }

    /**
     * Without a loader the command is still refused: letting Multiverse through would either fail on the
     * CUSTOM environment or build a vanilla world beside the level root under the Iris world's name.
     */
    @Test
    public void loadingAnIrisWorldWithNoLoaderRegisteredIsRefusedRatherThanPassedThrough() throws Exception {
        File levelRoot = temporaryFolder.newFolder("load-no-loader", "world");
        assertTrue(new File(levelRoot, "dimensions/iris/mvtest").mkdirs());
        IrisServices.remove(ManagedWorldLoader.class);

        withServer(levelRoot, null, listener -> {
            ServerCommandEvent event = consoleCommand("mv load world_iris_mvtest");
            listener.onServerCommand(event);
            assertTrue(event.isCancelled());
        });
    }

    /**
     * Taking the command over must never do for a sender what Multiverse would have refused them.
     */
    @Test
    public void loadingAnIrisWorldWithoutTheMultiversePermissionIsLeftToMultiverse() throws Exception {
        File levelRoot = temporaryFolder.newFolder("load-unprivileged", "world");
        assertTrue(new File(levelRoot, "dimensions/iris/mvtest").mkdirs());
        RecordingLoader loader = new RecordingLoader(true, "Loaded.");
        IrisServices.register(ManagedWorldLoader.class, loader);
        try {
            withServer(levelRoot, null, listener -> {
                ServerCommandEvent event = unprivilegedCommand("mv load world_iris_mvtest");
                listener.onServerCommand(event);
                assertFalse(event.isCancelled());
            });
            assertNull(loader.requested);
        } finally {
            IrisServices.remove(ManagedWorldLoader.class);
        }
    }

    /**
     * The tail of a delegated load runs on the main thread and hands the world to Multiverse. Without
     * Multiverse it must still settle, in both directions, rather than throw into the scheduler.
     */
    @Test
    public void aSettledLoadHandsTheWorldToMultiverseAndNeverThrows() throws Exception {
        File levelRoot = temporaryFolder.newFolder("load-settle", "world");
        assertTrue(new File(levelRoot, "dimensions/iris/mvtest").mkdirs());

        withServer(levelRoot, null, listener -> {
            listener.settleLoad(null, "world_iris_mvtest",
                    new ManagedWorldLoader.ManagedWorldLoad(true, "Loaded."), null);
            listener.settleLoad(null, "world_iris_mvtest",
                    new ManagedWorldLoader.ManagedWorldLoad(false, "Storage is missing."), null);
            listener.settleLoad(null, "world_iris_mvtest", null, new IllegalStateException("boom"));
            listener.settleLoad(null, "world_iris_mvtest", null, null);
        });
    }

    private static final class RecordingLoader implements ManagedWorldLoader {
        private final boolean loaded;
        private final String message;
        private volatile String requested;

        private RecordingLoader(boolean loaded, String message) {
            this.loaded = loaded;
            this.message = message;
        }

        @Override
        public CompletableFuture<ManagedWorldLoad> load(String configuredWorldName) {
            requested = configuredWorldName;
            return CompletableFuture.completedFuture(new ManagedWorldLoad(loaded, message));
        }
    }

    @Test
    public void nonCloneMultiverseCommandsCarryNoCloneSource() {
        assertNull(MultiverseGuardListener.multiverseClone("/mv delete mvtest"));
        assertNull(MultiverseGuardListener.multiverseClone("/mv regen mvtest"));
        assertNull(MultiverseGuardListener.multiverseClone("/mv clone"));
        assertNull(MultiverseGuardListener.multiverseClone("/mv"));
        assertNull(MultiverseGuardListener.multiverseClone("/mvcl"));
        assertNull(MultiverseGuardListener.multiverseClone("/iris create mvtest"));
    }

    @Test
    public void cloningOntoAnIrisWorldSlotIsRefused() throws Exception {
        File levelRoot = temporaryFolder.newFolder("clone-destination", "world");
        assertTrue(new File(levelRoot, "dimensions/iris/mvtest").mkdirs());

        withServer(levelRoot, null, listener -> {
            ServerCommandEvent event = consoleCommand("mv clone plots world_iris_mvtest");
            listener.onServerCommand(event);
            assertTrue("Multiverse resolves the vanilla path and never sees the Iris slot",
                    event.isCancelled());
        });
    }

    @Test
    public void cloningOntoAFreeNameIsLeftAlone() throws Exception {
        File levelRoot = temporaryFolder.newFolder("clone-destination-free", "world");
        assertTrue(new File(levelRoot, "dimensions/iris/mvtest").mkdirs());

        withServer(levelRoot, null, listener -> {
            ServerCommandEvent event = consoleCommand("mv clone plots plots_copy");
            listener.onServerCommand(event);
            assertFalse(event.isCancelled());
        });
    }

    private static void assertClone(String source, String destination, String commandLine) {
        MultiverseGuardListener.CloneCommand clone = MultiverseGuardListener.multiverseClone(commandLine);
        assertEquals(commandLine, new MultiverseGuardListener.CloneCommand(source, destination), clone);
    }

    @Test
    public void multiverseDeleteOfALoadedIrisWorldIsCancelled() {
        World world = irisWorld("world_iris_mvtest");
        LoadedMultiverseWorld multiverseWorld = multiverseWorld("world_iris_mvtest", world);
        MVWorldDeleteEvent event = new MVWorldDeleteEvent(multiverseWorld);

        withServer(emptyLevelRoot("delete-iris"), listener -> listener.onMultiverseWorldDelete(event));

        assertTrue("mv delete and mv regen both funnel through this event", event.isCancelled());
    }

    @Test
    public void multiverseDeleteOfAWorldIrisDoesNotOwnIsLeftAlone() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("plots");
        LoadedMultiverseWorld multiverseWorld = multiverseWorld("plots", world);
        MVWorldDeleteEvent event = new MVWorldDeleteEvent(multiverseWorld);

        withServer(emptyLevelRoot("delete-vanilla"), listener -> listener.onMultiverseWorldDelete(event));

        assertFalse(event.isCancelled());
    }

    @Test
    public void multiverseDeleteOfAnUnloadedIrisWorldIsCancelledFromStorage() throws Exception {
        File levelRoot = temporaryFolder.newFolder("delete-unloaded", "world");
        assertTrue(new File(levelRoot, "dimensions/iris/mvtest").mkdirs());
        LoadedMultiverseWorld multiverseWorld = multiverseWorld("world_iris_mvtest", null);
        MVWorldDeleteEvent event = new MVWorldDeleteEvent(multiverseWorld);

        withServer(levelRoot, listener -> listener.onMultiverseWorldDelete(event));

        assertTrue(event.isCancelled());
    }

    @Test
    public void cloningALoadedIrisWorldIsRefusedAtTheCommand() {
        World world = irisWorld("world_iris_mvtest");

        withServer(emptyLevelRoot("clone-iris"), world, listener -> {
            ServerCommandEvent event = consoleCommand("mv clone world_iris_mvtest copy");
            listener.onServerCommand(event);
            assertTrue("Multiverse fires no cancellable event for a clone", event.isCancelled());
        });
    }

    @Test
    public void cloningAnUnloadedIrisWorldIsRefusedFromStorage() throws Exception {
        File levelRoot = temporaryFolder.newFolder("clone-unloaded", "world");
        assertTrue(new File(levelRoot, "dimensions/iris/mvtest").mkdirs());

        withServer(levelRoot, null, listener -> {
            ServerCommandEvent event = consoleCommand("mvclone world_iris_mvtest copy");
            listener.onServerCommand(event);
            assertTrue(event.isCancelled());
        });
    }

    @Test
    public void cloningAWorldIrisDoesNotOwnIsLeftAlone() {
        withServer(emptyLevelRoot("clone-vanilla"), null, listener -> {
            ServerCommandEvent event = consoleCommand("mv clone plots copy");
            listener.onServerCommand(event);
            assertFalse(event.isCancelled());
        });
    }

    @Test
    public void unrelatedCommandsAreNeverInspected() {
        withServer(emptyLevelRoot("clone-unrelated"), null, listener -> {
            ServerCommandEvent event = consoleCommand("iris remove world=world_iris_mvtest delete=true");
            listener.onServerCommand(event);
            assertFalse(event.isCancelled());
        });
    }

    private static ServerCommandEvent consoleCommand(String command) {
        ConsoleCommandSender sender = mock(ConsoleCommandSender.class);
        when(sender.hasPermission(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        return new ServerCommandEvent(sender, command);
    }

    private static ServerCommandEvent unprivilegedCommand(String command) {
        return new ServerCommandEvent(mock(ConsoleCommandSender.class), command);
    }

    private File emptyLevelRoot(String scope) {
        try {
            return temporaryFolder.newFolder(scope, "world");
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static World irisWorld(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        when(world.getGenerator()).thenReturn(mock(BukkitChunkGenerator.class));
        return world;
    }

    private static LoadedMultiverseWorld multiverseWorld(String name, World bukkitWorld) {
        LoadedMultiverseWorld multiverseWorld = mock(LoadedMultiverseWorld.class);
        when(multiverseWorld.getName()).thenReturn(name);
        when(multiverseWorld.asLoadedWorld()).thenReturn(Option.of(multiverseWorld));
        when(multiverseWorld.getBukkitWorld()).thenReturn(Option.of(bukkitWorld));
        return multiverseWorld;
    }

    private static void withServer(File levelRoot, java.util.function.Consumer<MultiverseGuardListener> body) {
        withServer(levelRoot, null, body);
    }

    /**
     * Multiverse is left disabled so the listener resolves everything from Bukkit and Iris storage; that is
     * the shape the guard has to hold in whether or not the Multiverse API answers.
     */
    private static void withServer(
            File levelRoot,
            World namedWorld,
            java.util.function.Consumer<MultiverseGuardListener> body
    ) {
        Server server = mock(Server.class);
        when(server.getLevelDirectory()).thenReturn(levelRoot.toPath());
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.isPluginEnabled(MultiverseCoreLink.MULTIVERSE_PLUGIN)).thenReturn(false);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(levelRoot.getParentFile());
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            if (namedWorld != null) {
                bukkit.when(() -> Bukkit.getWorld(namedWorld.getName())).thenReturn(namedWorld);
            }
            body.accept(new MultiverseGuardListener(new MultiverseCoreLink()));
        }
    }
}
