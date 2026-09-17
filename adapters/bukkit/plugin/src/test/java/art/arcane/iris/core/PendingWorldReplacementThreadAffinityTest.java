package art.arcane.iris.core;

import art.arcane.iris.core.ExactWorldSlotPathPolicy.SlotKind;
import art.arcane.iris.core.lifecycle.BukkitWorldConfiguration.WorldGeneratorSnapshot;
import art.arcane.iris.core.lifecycle.WorldReplacementJournal.Phase;
import art.arcane.iris.core.lifecycle.WorldReplacementJournal.Transaction;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.engine.framework.EngineTarget;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.IrisEnvironment;
import art.arcane.iris.engine.platform.PlatformChunkGenerator;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class PendingWorldReplacementThreadAffinityTest {
    @Test
    public void runtimeStateIsCapturedIntoAnImmutableDetachedSnapshot() {
        World world = mock(World.class);
        PlatformChunkGenerator generator = mock(PlatformChunkGenerator.class);
        EngineTarget target = mock(EngineTarget.class);
        IrisDimension dimension = mock(IrisDimension.class);
        when(world.getKey()).thenReturn(NamespacedKey.minecraft("the_nether"));
        when(world.getSeed()).thenReturn(-18273645L);
        when(world.getEnvironment()).thenReturn(World.Environment.NETHER);
        when(generator.getTarget()).thenReturn(target);
        when(target.getDimension()).thenReturn(dimension);
        when(dimension.getLoadKey()).thenReturn("underworld");
        when(dimension.getEnvironment()).thenReturn(IrisEnvironment.NETHER);

        PendingWorldReplacementManager.PublishedWorldRuntimeState runtimeState;
        try (MockedStatic<IrisToolbelt> toolbelt = mockStatic(IrisToolbelt.class)) {
            toolbelt.when(() -> IrisToolbelt.isIrisWorld(world)).thenReturn(true);
            toolbelt.when(() -> IrisToolbelt.access(world)).thenReturn(generator);
            runtimeState = PendingWorldReplacementManager.capturePublishedWorldRuntime(world);
        }

        assertEquals(WorldSlotKey.minecraft("the_nether"), runtimeState.worldKey());
        assertTrue(runtimeState.irisWorld());
        assertEquals(-18273645L, runtimeState.seed());
        assertEquals(World.Environment.NETHER, runtimeState.bukkitEnvironment());
        assertEquals("underworld", runtimeState.dimension());
        assertEquals(IrisEnvironment.NETHER, runtimeState.dimensionEnvironment());
    }

    @Test
    public void runtimeValidationRejectsIdentitySeedAndEnvironmentMismatches() throws Exception {
        Transaction transaction = transaction();
        PendingWorldReplacementManager.PublishedWorldRuntimeState valid = runtimeState(
                WorldSlotKey.minecraft("the_nether"),
                918273645L,
                World.Environment.NETHER,
                IrisEnvironment.NETHER
        );
        PendingWorldReplacementManager.validatePublishedWorldRuntime(
                valid,
                transaction,
                SlotKind.VANILLA_NETHER
        );

        IOException identityFailure = assertThrows(
                IOException.class,
                () -> PendingWorldReplacementManager.validatePublishedWorldRuntime(
                        runtimeState(
                                WorldSlotKey.minecraft("overworld"),
                                918273645L,
                                World.Environment.NETHER,
                                IrisEnvironment.NETHER
                        ),
                        transaction,
                        SlotKind.VANILLA_NETHER
                )
        );
        IOException seedFailure = assertThrows(
                IOException.class,
                () -> PendingWorldReplacementManager.validatePublishedWorldRuntime(
                        runtimeState(
                                WorldSlotKey.minecraft("the_nether"),
                                1L,
                                World.Environment.NETHER,
                                IrisEnvironment.NETHER
                        ),
                        transaction,
                        SlotKind.VANILLA_NETHER
                )
        );
        IOException bukkitEnvironmentFailure = assertThrows(
                IOException.class,
                () -> PendingWorldReplacementManager.validatePublishedWorldRuntime(
                        runtimeState(
                                WorldSlotKey.minecraft("the_nether"),
                                918273645L,
                                World.Environment.NORMAL,
                                IrisEnvironment.NETHER
                        ),
                        transaction,
                        SlotKind.VANILLA_NETHER
                )
        );
        IllegalArgumentException irisEnvironmentFailure = assertThrows(
                IllegalArgumentException.class,
                () -> PendingWorldReplacementManager.validatePublishedWorldRuntime(
                        runtimeState(
                                WorldSlotKey.minecraft("the_nether"),
                                918273645L,
                                World.Environment.NETHER,
                                IrisEnvironment.NORMAL
                        ),
                        transaction,
                        SlotKind.VANILLA_NETHER
                )
        );

        assertEquals("Loaded world identity does not match the replacement journal.", identityFailure.getMessage());
        assertEquals("The replaced world loaded with an unexpected seed.", seedFailure.getMessage());
        assertEquals("The replaced world loaded with an unexpected environment.",
                bukkitEnvironmentFailure.getMessage());
        assertTrue(irisEnvironmentFailure.getMessage().contains("requires a pack environment of NETHER"));
    }

    @Test
    public void bukkitAccessIsConfinedToTheGlobalCaptureStage() throws Exception {
        String managerSource = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/core/PendingWorldReplacementManager.java")).replace("\r\n", "\n");
        String irisSource = Files.readString(Path.of("src/main/java/art/arcane/iris/Iris.java")).replace("\r\n", "\n");
        String startup = method(managerSource, "public void verifyLoadedPublishedWorlds()");
        String worldLoad = method(managerSource, "public void onWorldLoad(WorldLoadEvent event)");
        String discovery = method(managerSource, "private void discoverLoadedPublishedWorlds()");
        String capture = method(managerSource,
                "private void captureLoadedPublishedWorldOnGlobal(Transaction transaction)");
        String snapshot = method(managerSource, "static PublishedWorldRuntimeState capturePublishedWorldRuntime(World world)");
        String verification = method(managerSource,
                "private void verifyPublishedWorld(PublishedWorldRuntimeState runtimeState, Transaction transaction)");

        assertTrue(startup.contains("J.a(this::discoverLoadedPublishedWorlds)"));
        assertTrue(worldLoad.contains("WorldIdentity.key(event.getWorld())"));
        assertTrue(worldLoad.contains("J.a(() -> discoverLoadedWorldTransaction(worldKey))"));
        assertTrue(capture.contains("WorldIdentity.resolve("));
        assertBefore(capture, "capturePublishedWorldRuntime(world)",
                "J.a(() -> runPublishedWorldVerification(runtimeState, transaction))");
        assertTrue(snapshot.contains("WorldIdentity.key(requiredWorld)"));
        assertTrue(snapshot.contains("IrisToolbelt.isIrisWorld(requiredWorld)"));
        assertTrue(snapshot.contains("requiredWorld.getSeed()"));
        assertTrue(snapshot.contains("requiredWorld.getEnvironment()"));
        assertTrue(snapshot.contains("IrisToolbelt.access(requiredWorld)"));
        assertNoBukkitRuntimeAccess(discovery);
        assertNoBukkitRuntimeAccess(verification);
        assertTrue(verification.contains("WorldReplacementFilesystem.fingerprintPack("));
        assertTrue(irisSource.contains("pendingWorldReplacements.verifyLoadedPublishedWorlds();"));
        assertFalse(irisSource.contains("J.a(pendingWorldReplacements::verifyLoadedPublishedWorlds)"));
    }

    @Test
    public void paperLoginHookIsIsolatedFromAlwaysLoadedSpigotClasses() throws Exception {
        String managerSource = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/core/PendingWorldReplacementManager.java")).replace("\r\n", "\n");
        String irisSource = Files.readString(Path.of("src/main/java/art/arcane/iris/Iris.java")).replace("\r\n", "\n");
        String listenerSource = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/core/PaperWorldReplacementEntryListener.java")).replace("\r\n", "\n");
        String registration = method(managerSource, "public void registerPlatformEntryListener()");

        assertFalse(managerSource.contains("import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent"));
        assertFalse(managerSource.contains("AsyncPlayerSpawnLocationEvent event"));
        assertFalse(irisSource.contains("AsyncPlayerSpawnLocationEvent"));
        assertTrue(registration.contains("Class.forName(\"io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent\""));
        assertTrue(registration.contains("Class.forName("));
        assertTrue(registration.contains("PaperWorldReplacementEntryListener"));
        assertTrue(irisSource.contains("pendingWorldReplacements.registerPlatformEntryListener();"));
        assertTrue(listenerSource.contains("onAsyncPlayerSpawnLocation(AsyncPlayerSpawnLocationEvent event)"));
    }

    @Test
    public void redirectedPlayerReceiptSurvivesUntilTheMaterializedPositionIsSaved() throws Exception {
        String managerSource = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/core/PendingWorldReplacementManager.java")).replace("\r\n", "\n");
        String listenerSource = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/core/PaperWorldReplacementEntryListener.java")).replace("\r\n", "\n");
        String preparation = method(
                managerSource,
                "ReplacementEntryRedirect prepareReplacementEntry(UUID playerId, Location savedLocation, boolean newPlayer)"
        );
        String acknowledgement = method(
                managerSource,
                "void expectReplacementEntryAcknowledgement(UUID playerId, UUID transactionId)"
        );
        String join = method(managerSource, "public void onPlayerJoin(PlayerJoinEvent event)");
        String listener = method(
                listenerSource,
                "public void onAsyncPlayerSpawnLocation(AsyncPlayerSpawnLocationEvent event)"
        );

        assertTrue(preparation.contains("return new ReplacementEntryRedirect(guard.transactionId(), prepared, pendingPlayer)"));
        assertFalse(preparation.substring(preparation.indexOf("CompletableFuture<Location> safeEntry"))
                .contains("completeOverworldEntry("));
        assertBefore(listener, "event.setSpawnLocation(location)",
                "manager.expectReplacementEntryAcknowledgement(playerId, redirect.transactionId())");
        assertTrue(acknowledgement.contains("pendingEntryAcknowledgements.put(playerId, transactionId)"));
        assertBefore(join, "event.getPlayer().saveData()", "completeOverworldEntry(playerId, transactionId)");
    }

    @Test
    public void replacementSpawnIsPersistedBeforeFinalMarkerRetirement() throws Exception {
        String managerSource = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/core/PendingWorldReplacementManager.java")).replace("\r\n", "\n");
        String listenerSource = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/core/PaperWorldReplacementEntryListener.java")).replace("\r\n", "\n");
        String preparation = method(managerSource, "private void prepareOverworldEntry(World world)");
        String persistence = method(managerSource, "private CompletableFuture<Location> persistOverworldSpawn(Location safeEntry)");
        String retirement = method(
                managerSource,
                "private synchronized void retireOverworldEntryIfCompleteAsync(UUID transactionId)"
        );
        String listener = method(
                listenerSource,
                "public void onAsyncPlayerSpawnLocation(AsyncPlayerSpawnLocationEvent event)"
        );
        String generatorSource = Files.readString(Path.of(System.getProperty("iris.bukkitChunkGeneratorSource")));

        assertBefore(preparation, ".thenCompose(this::applyOverworldSpawn)",
                ".thenCompose(this::persistOverworldSpawn)");
        assertBefore(preparation, "targetFuture.complete(safeEntry.clone())",
                "retireOverworldEntryIfComplete(guard.transactionId())");
        assertTrue(persistence.contains("world.save()"));
        assertTrue(retirement.contains("!current.pendingPlayers().isEmpty()"));
        assertTrue(retirement.contains("!safeEntry.isDone()"));
        assertTrue(retirement.contains("safeEntry.isCompletedExceptionally()"));
        assertTrue(listener.contains("event.isNewPlayer()"));
        assertTrue(generatorSource.contains("world.getHighestBlockYAt(initialSpawn) + 1"));
    }

    private static PendingWorldReplacementManager.PublishedWorldRuntimeState runtimeState(
            WorldSlotKey worldKey,
            long seed,
            World.Environment bukkitEnvironment,
            IrisEnvironment irisEnvironment
    ) {
        return new PendingWorldReplacementManager.PublishedWorldRuntimeState(
                worldKey,
                true,
                seed,
                bukkitEnvironment,
                "underworld",
                irisEnvironment
        );
    }

    private static Transaction transaction() {
        return new Transaction(
                UUID.fromString("2e488654-c259-4587-a7f2-8a053d59b60f"),
                WorldSlotKey.minecraft("the_nether"),
                "world_nether",
                Path.of("build", "replacement-thread-test", "world"),
                "underworld",
                918273645L,
                "fingerprint",
                new WorldGeneratorSnapshot(false, false, false, null, false, null),
                true,
                Phase.PUBLISHED
        );
    }

    private static void assertNoBukkitRuntimeAccess(String source) {
        assertFalse(source.contains("WorldIdentity."));
        assertFalse(source.contains("IrisToolbelt."));
        assertFalse(source.contains("getSeed()"));
        assertFalse(source.contains("getEnvironment()"));
        assertFalse(source.contains("Bukkit."));
    }

    private static void assertBefore(String source, String first, String second) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second);
        assertTrue("Missing source contract token: " + first, firstIndex >= 0);
        assertTrue("Missing source contract token: " + second, secondIndex >= 0);
        assertTrue(first + " must occur before " + second, firstIndex < secondIndex);
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("Missing source contract signature: " + signature, start >= 0);
        int openBrace = source.indexOf('{', start);
        assertTrue("Missing source contract method body: " + signature, openBrace >= 0);
        int depth = 0;
        for (int index = openBrace; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, index + 1);
                }
            }
        }
        throw new IllegalArgumentException("Unclosed source contract method: " + signature);
    }
}
