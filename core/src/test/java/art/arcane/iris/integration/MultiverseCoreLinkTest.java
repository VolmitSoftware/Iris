package art.arcane.iris.integration;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.plugin.PluginManager;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;
import org.mvplugins.multiverse.core.utils.result.Attempt;
import org.mvplugins.multiverse.core.world.LoadedMultiverseWorld;
import org.mvplugins.multiverse.core.world.MultiverseWorld;
import org.mvplugins.multiverse.core.world.WorldManager;
import org.mvplugins.multiverse.core.world.options.LoadWorldOptions;
import org.mvplugins.multiverse.core.world.options.RemoveWorldOptions;
import org.mvplugins.multiverse.external.vavr.control.Option;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MultiverseCoreLinkTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void multiverseRegistrationRequiresTheConfiguredWorldName() throws Exception {
        Method updateWorld = MultiverseCoreLink.class
                .getDeclaredMethod("updateWorld", World.class, String.class, String.class);

        assertEquals(void.class, updateWorld.getReturnType());
        assertFalse("updateWorld must not keep an overload that derives the name from the live world",
                Arrays.stream(MultiverseCoreLink.class.getDeclaredMethods())
                        .anyMatch(method -> "updateWorld".equals(method.getName())
                                && method.getParameterCount() == 2));
    }

    @Test
    public void lookupNamesAddTheIrisKeyAndRuntimeNameForConfiguredStartupNames() {
        assertEquals(
                List.of("world_iris_mvtest", "iris:mvtest", "iris_mvtest"),
                MultiverseCoreLink.lookupNames("world_iris_mvtest", "world")
        );
        assertEquals(
                List.of("survival_iris_mvtest", "iris:mvtest", "iris_mvtest"),
                MultiverseCoreLink.lookupNames("survival_iris_mvtest", "survival")
        );
    }

    /**
     * Multiverse re-creates a world under whatever name its own WorldCreator produced, so an entry it
     * reloaded can be indexed under the keyed runtime name instead of the startup name Iris registered.
     */
    @Test
    public void resolveFindsAWorldMultiverseOnlyHoldsUnderItsRuntimeName() {
        MultiverseWorld target = multiverseWorld("iris_mvtest", new NamespacedKey("iris", "mvtest"));

        assertSame(target, new MultiverseCoreLink()
                .resolve(worldManagerHolding(Map.of("iris_mvtest", target)), "world_iris_mvtest", "world"));
    }

    @Test
    public void resolveNeverReturnsADifferentWorldSittingOnTheRuntimeName() {
        MultiverseWorld impostor = multiverseWorld("iris_mvtest", NamespacedKey.minecraft("iris_mvtest"));

        assertNull(new MultiverseCoreLink()
                .resolve(worldManagerHolding(Map.of("iris_mvtest", impostor)), "world_iris_mvtest", "world"));
    }

    @Test
    public void resolveStillMatchesALegacyNameKeyedEntryUnderTheConfiguredName() {
        MultiverseWorld legacy = multiverseWorld("world_iris_mvtest", NamespacedKey.minecraft("world_iris_mvtest"));

        assertSame(legacy, new MultiverseCoreLink()
                .resolve(worldManagerHolding(Map.of("world_iris_mvtest", legacy)), "world_iris_mvtest", "world"));
    }

    /**
     * Iris unloads the world and closes its generator before it unregisters it, so Multiverse must not run
     * its own unload-before-remove: that dereferences a Bukkit world which is already gone and fails the
     * whole removal.
     */
    @Test
    public void removalSkipsTheMultiverseUnloadWhenTheBukkitWorldIsAlreadyGone() {
        MultiverseWorld unloaded = multiverseWorld("world_iris_mvtest", new NamespacedKey("iris", "mvtest"));
        when(unloaded.asLoadedWorld()).thenReturn(Option.none());

        RemoveWorldOptions options = MultiverseCoreLink.removalOptions(unloaded);

        assertFalse(options.unloadBukkitWorld());
        assertFalse(options.saveBukkitWorld());
    }

    @Test
    public void removalStillLetsMultiverseUnloadAWorldThatIsStillLive() {
        MultiverseWorld live = multiverseWorld("world_iris_mvtest", new NamespacedKey("iris", "mvtest"));
        LoadedMultiverseWorld loaded = mock(LoadedMultiverseWorld.class);
        when(loaded.getBukkitWorld()).thenReturn(Option.of(mock(World.class)));
        when(live.asLoadedWorld()).thenReturn(Option.of(loaded));

        assertTrue(MultiverseCoreLink.removalOptions(live).unloadBukkitWorld());
    }

    /**
     * Multiverse's loadWorld short-circuits to the already-loaded Bukkit world only when Bukkit has one
     * under the name Multiverse recorded. Without that world it builds one, so adoption is skipped rather
     * than letting Multiverse re-create a world Iris already owns.
     */
    @Test
    public void adoptionIsSkippedWhenBukkitHasNoWorldUnderTheRecordedName() {
        NamespacedKey worldKey = new NamespacedKey("iris", "mvtest");
        MultiverseWorld target = multiverseWorld("world_iris_mvtest", worldKey);
        WorldManager manager = mock(WorldManager.class);
        when(manager.isLoadedWorld(target)).thenReturn(false);

        withBukkitWorld(null, () -> assertFalse(
                new MultiverseCoreLink().adoptLoadedWorld(manager, target, worldKey)));
        verify(manager, never()).loadWorld(any(LoadWorldOptions.class));
    }

    @Test
    public void adoptionIsSkippedWhenTheLiveWorldUnderThatNameIsNotTheTarget() {
        NamespacedKey worldKey = new NamespacedKey("iris", "mvtest");
        MultiverseWorld target = multiverseWorld("world_iris_mvtest", worldKey);
        WorldManager manager = mock(WorldManager.class);
        when(manager.isLoadedWorld(target)).thenReturn(false);
        World other = mock(World.class);
        when(other.getKey()).thenReturn(NamespacedKey.minecraft("overworld"));

        withBukkitWorld(other, () -> assertFalse(
                new MultiverseCoreLink().adoptLoadedWorld(manager, target, worldKey)));
        verify(manager, never()).loadWorld(any(LoadWorldOptions.class));
    }

    @Test
    public void adoptionIsSkippedWhenMultiverseAlreadyHoldsTheWorldAsLoaded() {
        NamespacedKey worldKey = new NamespacedKey("iris", "mvtest");
        MultiverseWorld target = multiverseWorld("world_iris_mvtest", worldKey);
        WorldManager manager = mock(WorldManager.class);
        when(manager.isLoadedWorld(target)).thenReturn(true);

        assertFalse(new MultiverseCoreLink().adoptLoadedWorld(manager, target, worldKey));
        verify(manager, never()).loadWorld(any(LoadWorldOptions.class));
    }

    @Test
    public void adoptionBindsALiveIrisWorldIntoTheMultiverseLoadedRegistry() {
        NamespacedKey worldKey = new NamespacedKey("iris", "mvtest");
        MultiverseWorld target = multiverseWorld("world_iris_mvtest", worldKey);
        WorldManager manager = mock(WorldManager.class);
        when(manager.isLoadedWorld(target)).thenReturn(false);
        when(manager.loadWorld(any(LoadWorldOptions.class)))
                .thenReturn(Attempt.success(mock(LoadedMultiverseWorld.class)));
        World live = mock(World.class);
        when(live.getKey()).thenReturn(worldKey);

        withBukkitWorld(live, () -> assertTrue(
                new MultiverseCoreLink().adoptLoadedWorld(manager, target, worldKey)));
    }

    /**
     * Multiverse refuses to adopt a world whose stored environment disagrees with the live one, so the live
     * world is the value the entry is corrected to whenever there is one.
     */
    @Test
    public void theStoredEnvironmentIsTakenFromTheLiveWorld() {
        NamespacedKey worldKey = new NamespacedKey("iris", "mvtest");
        World live = mock(World.class);
        when(live.getKey()).thenReturn(worldKey);
        when(live.getEnvironment()).thenReturn(World.Environment.NETHER);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(live));

            assertEquals(World.Environment.NETHER,
                    new MultiverseCoreLink().ownedWorldEnvironment(worldKey, "overworld"));
        }
    }

    @Test
    public void anEnvironmentThatMatchesWhatMultiverseAlreadyStoredIsNotRewritten() {
        MultiverseWorld multiverseWorld = multiverseWorld("world_iris_mvtest", new NamespacedKey("iris", "mvtest"));
        when(multiverseWorld.getEnvironment()).thenReturn(World.Environment.NORMAL);
        MultiverseCoreLink link = new MultiverseCoreLink();

        assertFalse(link.applyIrisWorldEnvironment(multiverseWorld, World.Environment.NORMAL));
        assertFalse(link.applyIrisWorldEnvironment(multiverseWorld, null));
    }

    private static void withBukkitWorld(World world, Runnable body) {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(anyString())).thenReturn(world);
            body.run();
        }
    }

    private static MultiverseWorld multiverseWorld(String name, NamespacedKey key) {
        MultiverseWorld multiverseWorld = mock(MultiverseWorld.class);
        when(multiverseWorld.getName()).thenReturn(name);
        when(multiverseWorld.getKey()).thenReturn(key);
        when(multiverseWorld.asLoadedWorld()).thenReturn(Option.none());
        return multiverseWorld;
    }

    private static WorldManager worldManagerHolding(Map<String, MultiverseWorld> registry) {
        WorldManager manager = mock(WorldManager.class);
        when(manager.getWorld(anyString()))
                .thenAnswer(invocation -> Option.of(registry.get(invocation.<String>getArgument(0))));
        return manager;
    }

    @Test
    public void lookupNamesKeepNonConfiguredNamesUntouched() {
        assertEquals(List.of("iris_mvtest"), MultiverseCoreLink.lookupNames("iris_mvtest", "world"));
        assertEquals(List.of("world"), MultiverseCoreLink.lookupNames("world", "world"));
        assertEquals(List.of("iris:mvtest"), MultiverseCoreLink.lookupNames("iris:mvtest", "world"));
        assertEquals(List.of("iris_studio-demo"), MultiverseCoreLink.lookupNames("iris_studio-demo", "world"));
    }

    @Test
    public void everyLinkCallIsANoOpWithoutMultiverse() {
        withMultiverse(false, link -> {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world_iris_mvtest");

            assertFalse(link.isActive());
            assertFalse(link.removeIfPresent(world));
            assertFalse(link.removeFromConfig("world_iris_mvtest"));
            assertEquals(0, link.reconcileOwnedWorlds());
            link.updateWorld(world, "world_iris_mvtest", "overworld");
            link.prepareOwnedWorldLoad("world_iris_mvtest");
            assertFalse(link.adoptOwnedWorld("world_iris_mvtest"));
        });
    }

    @Test
    public void anEnabledButUnloadedMultiverseDegradesInsteadOfThrowing() {
        // Iris is asked for generators while Multiverse is still enabling, so plugin enablement alone
        // must never be treated as a usable API.
        withMultiverse(true, link -> {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world_iris_mvtest");

            assertFalse("MultiverseCoreApi has no instance until Multiverse finishes enabling",
                    link.isActive());
            assertFalse(link.removeIfPresent(world));
            assertFalse(link.removeFromConfig("world_iris_mvtest"));
            assertEquals(0, link.reconcileOwnedWorlds());
            link.updateWorld(world, "world_iris_mvtest", "overworld");
            link.prepareOwnedWorldLoad("world_iris_mvtest");
            assertFalse(link.adoptOwnedWorld("world_iris_mvtest"));
        });
    }

    @Test
    public void aBlankWorldNameIsNotAnErrorWhileMultiverseIsAbsent() {
        withMultiverse(false, link -> {
            assertFalse(link.removeFromConfig("   "));
            assertFalse(link.removeFromConfig(null));
            link.prepareOwnedWorldLoad(null);
            assertFalse(link.adoptOwnedWorld("   "));
        });
    }

    @Test
    public void irisOwnershipOfAWorldNameFollowsItsStorage() throws Exception {
        File levelRoot = temporaryFolder.newFolder("ownership", "world");
        assertTrue(new File(levelRoot, "dimensions/iris/mvtest").mkdirs());

        assertTrue(MultiverseCoreLink.isIrisOwnedWorldName("world_iris_mvtest", levelRoot));
        assertTrue(MultiverseCoreLink.isIrisOwnedWorldName("mvtest", levelRoot));
        assertTrue(MultiverseCoreLink.isIrisOwnedWorldName("iris:mvtest", levelRoot));
        assertFalse(MultiverseCoreLink.isIrisOwnedWorldName("world_iris_other", levelRoot));
        assertFalse(MultiverseCoreLink.isIrisOwnedWorldName("world", levelRoot));
        assertFalse(MultiverseCoreLink.isIrisOwnedWorldName("../escape", levelRoot));
        assertFalse(MultiverseCoreLink.isIrisOwnedWorldName(null, levelRoot));
        assertFalse(MultiverseCoreLink.isIrisOwnedWorldName("mvtest", null));
    }

    private static void withMultiverse(boolean enabled, java.util.function.Consumer<MultiverseCoreLink> body) {
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.isPluginEnabled(MultiverseCoreLink.MULTIVERSE_PLUGIN)).thenReturn(enabled);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            body.accept(new MultiverseCoreLink());
        }
    }
}
