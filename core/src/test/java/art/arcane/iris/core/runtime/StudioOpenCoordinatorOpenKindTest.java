package art.arcane.iris.core.runtime;

import art.arcane.iris.core.project.IrisProject;
import art.arcane.iris.core.tools.IrisCreator;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StudioOpenCoordinatorOpenKindTest {
    @Test
    public void standardStudioOwnsWorkspaceAndEntryTeleport() {
        StudioOpenCoordinator.StudioOpenKind kind = StudioOpenCoordinator.StudioOpenKind.STANDARD;

        assertTrue(kind.openWorkspace());
        assertTrue(kind.teleportThroughStandardEntry());
        assertTrue(kind.prepareGeneratorState());
        assertEquals(
                IrisCreator.DatapackPreparation.REUSE_LOADED_RUNTIME_IF_READY,
                kind.datapackPreparation());
    }

    @Test
    public void ordinaryStudioKindsReuseOnlyAnUnchangedLoadedRuntime() {
        for (StudioOpenCoordinator.StudioOpenKind kind : StudioOpenCoordinator.StudioOpenKind.values()) {
            if (kind == StudioOpenCoordinator.StudioOpenKind.FORCED_STANDARD) {
                continue;
            }
            assertEquals(
                    IrisCreator.DatapackPreparation.REUSE_LOADED_RUNTIME_IF_READY,
                    kind.datapackPreparation());
        }
    }

    @Test
    public void forcedStandardStudioAttemptsTheLoadedRuntime() {
        StudioOpenCoordinator.StudioOpenKind kind = StudioOpenCoordinator.StudioOpenKind.FORCED_STANDARD;

        assertTrue(kind.openWorkspace());
        assertTrue(kind.teleportThroughStandardEntry());
        assertTrue(kind.prepareGeneratorState());
        assertEquals(
                IrisCreator.DatapackPreparation.FORCE_REUSE_LOADED_RUNTIME,
                kind.datapackPreparation());
    }

    @Test
    public void objectStudioOwnsWorkspaceWithoutUsingTheStandardEntry() {
        StudioOpenCoordinator.StudioOpenKind kind = StudioOpenCoordinator.StudioOpenKind.OBJECT;

        assertTrue(kind.openWorkspace());
        assertFalse(kind.teleportThroughStandardEntry());
        assertTrue(kind.prepareGeneratorState());
    }

    @Test
    public void jigsawStudioLeavesWorkspaceClosedAndUsesItsDestinationTeleport() {
        StudioOpenCoordinator.StudioOpenKind kind = StudioOpenCoordinator.StudioOpenKind.JIGSAW;

        assertFalse(kind.openWorkspace());
        assertFalse(kind.teleportThroughStandardEntry());
        assertFalse(kind.prepareGeneratorState());
        assertEquals(
                IrisCreator.DatapackPreparation.REUSE_LOADED_RUNTIME_IF_READY,
                kind.datapackPreparation());
    }

    @Test
    public void toolWorldsOpenLikeAJigsawStudioWithTheirOwnDestinationTeleport() {
        StudioOpenCoordinator.StudioOpenKind kind = StudioOpenCoordinator.StudioOpenKind.TOOL;

        assertFalse(kind.openWorkspace());
        assertFalse(kind.teleportThroughStandardEntry());
        assertFalse(kind.prepareGeneratorState());
        assertEquals(
                IrisCreator.DatapackPreparation.REUSE_LOADED_RUNTIME_IF_READY,
                kind.datapackPreparation());
    }

    @Test
    public void studioRequestRetainsExplicitOpenKind() {
        IrisProject project = new IrisProject(new File("overworld"));
        StudioOpenCoordinator.StudioOpenRequest request =
                StudioOpenCoordinator.StudioOpenRequest.studioProject(
                        project,
                        null,
                        1337L,
                        StudioOpenCoordinator.StudioOpenKind.JIGSAW,
                        null,
                        null);

        assertEquals(StudioOpenCoordinator.StudioOpenKind.JIGSAW, request.openKind());
        assertTrue(request.requestedAtNanos() > 0L);
    }

    @Test
    public void nativeTeleportWaitsForCompletionWithoutAnArrivalDeadline() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/core/runtime/StudioOpenCoordinator.java"))
                .replace("\r\n", "\n");
        int methodStart = source.indexOf("private void executeOpen(");
        int delegation = source.indexOf("WorldRuntimeControlService.get().teleportInMode(", methodStart);
        int completion = source.indexOf("nativeTeleportFuture.get();", delegation);

        assertTrue(delegation >= 0);
        assertTrue(completion > delegation);
        assertFalse(source.substring(methodStart, completion).contains("orTimeout("));
        assertFalse(source.substring(methodStart, completion).contains("deadlineNanos"));
    }

    @Test
    public void activeStudioTeleportIsSerializedWithoutAnArrivalDeadline() throws Exception {
        String coordinator = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/core/runtime/StudioOpenCoordinator.java"))
                .replace("\r\n", "\n");
        String service = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/core/service/StudioSVC.java"))
                .replace("\r\n", "\n");
        int coordinatorStart = coordinator.indexOf(
                "public CompletableFuture<Boolean> teleportPlayerToProject(");
        int coordinatorEnd = coordinator.indexOf("private void executeOpen", coordinatorStart);
        String coordinatorMethod = coordinator.substring(coordinatorStart, coordinatorEnd);
        int serviceStart = service.indexOf(
                "public CompletableFuture<Boolean> teleportToActiveProject(Player player)");
        int serviceEnd = service.indexOf("public void open(VolmitSender", serviceStart);
        String serviceMethod = service.substring(serviceStart, serviceEnd);
        int transitionAdmission = serviceMethod.indexOf("studioTransitions.submit(() ->");
        int projectCapture = serviceMethod.indexOf("IrisProject project = activeProject");
        int nativeDelegation = coordinatorMethod.indexOf(
                "WorldRuntimeControlService.get().teleportInMode(player, entry, GameMode.SPECTATOR)");

        assertTrue(transitionAdmission >= 0);
        assertTrue(projectCapture > transitionAdmission);
        assertFalse(serviceMethod.contains("orTimeout("));
        assertFalse(serviceMethod.contains("deadlineNanos"));
        assertTrue(coordinatorMethod.contains(
                "WorldRuntimeControlService.get().resolveEntryAnchor(world, provider)"));
        assertTrue(coordinatorMethod.contains(
                "project.getActiveOpenKind().teleportThroughStandardEntry()"));
        assertFalse(coordinatorMethod.contains("requestChunkAsync("));
        assertFalse(coordinatorMethod.contains("getHighestBlockYAt("));
        assertTrue(nativeDelegation >= 0);
        assertFalse(coordinatorMethod.contains("orTimeout("));
        assertFalse(coordinatorMethod.contains("deadlineNanos"));
    }

    @Test
    public void standardEntryDelegatesWithoutAnExplicitChunkOrSurfaceLookup() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/core/runtime/StudioOpenCoordinator.java"))
                .replace("\r\n", "\n");

        assertTrue(source.contains("Location entryLocation = entryAnchor;"));
        assertFalse(source.contains("prepareStudioEntryChunks("));
        assertFalse(source.contains("findStudioEntryLocation"));
        assertFalse(source.contains("EntryChunkResolution"));
        assertFalse(source.contains("resolveSafeEntry(world, entryAnchor)"));
        assertFalse(source.contains("getHighestBlockYAt("));
    }

    @Test
    public void standardEntryBindsSpectatorToTheNativeTeleport() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/core/runtime/StudioOpenCoordinator.java"))
                .replace("\r\n", "\n");
        String runtime = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/core/runtime/WorldRuntimeControlService.java"))
                .replace("\r\n", "\n");
        int mode = runtime.indexOf("modeRestore.apply(gameMode)");
        int teleport = runtime.indexOf("teleporter.teleport(player, location)", mode);

        assertTrue(source.contains("WorldRuntimeControlService.get().teleportInMode("));
        int openKind = source.indexOf("request.project().setActiveOpenKind(request.openKind())");
        int provider = source.indexOf("request.project().setActiveProvider(provider)", openKind);
        assertTrue(openKind >= 0);
        assertTrue(provider > openKind);
        assertTrue(source.contains("project.setActiveOpenKind(null)"));
        assertTrue(mode >= 0);
        assertTrue(teleport > mode);
        assertTrue(runtime.contains("modeRestore.restore()"));
    }

    @Test
    public void studioTimingSeparatesOrderedLifecyclePhases() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/core/runtime/StudioOpenCoordinator.java")).replace("\r\n", "\n");
        int previous = -1;
        for (String phase : List.of(
                "resolve_dimension_and_cleanup",
                "create_world_total",
                "apply_world_rules",
                "prepare_generator",
                "resolve_entry_anchor",
                "prepare_structure_rings",
                "prepare_generation_caches",
                "teleport_standard_entry",
                "finalize_open")) {
            int current = source.indexOf("\"" + phase + "\"", previous + 1);
            assertTrue("Missing or out-of-order Studio timing phase " + phase, current > previous);
            previous = current;
        }
        assertTrue(source.contains("long openStart = System.nanoTime();"));
    }

    @Test
    public void structureStateCompletesBeforeImmediateNativeTeleport() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/core/runtime/StudioOpenCoordinator.java")).replace("\r\n", "\n");
        int executeOpen = source.indexOf("private void executeOpen(");
        int completionCall = source.indexOf("endStudioEntryBootstrap(entryWorld, entryProvider)", executeOpen);
        int completionAwait = source.indexOf("entryBootstrap.get(", completionCall);
        int cacheAwait = source.indexOf("irisEngine.awaitGenerationCacheWarm()", completionAwait);
        int teleportStart = source.indexOf("WorldRuntimeControlService.get().teleportInMode(", cacheAwait);
        int finalizeOpen = source.indexOf(
                "updateStage(request, \"finalize_open\", 1.00D)", teleportStart);
        int futureComplete = source.indexOf(
                "future.complete(new StudioOpenResult(world, entryLocation))", finalizeOpen);
        int methodStart = source.indexOf(
                "private void endStudioEntryBootstrap(World world, PlatformChunkGenerator provider)");
        int methodEnd = source.indexOf("private void abandonStudioEntryBootstrap", methodStart);
        String method = source.substring(methodStart, methodEnd);
        int scheduled = method.indexOf("J.sfut(() ->");
        int claim = method.indexOf("activationClaim.compareAndSet(true, false)");
        int activation = method.indexOf("INMS.get().completeStudioStructureBootstrap(world)");
        int gateRelease = method.indexOf("bukkitGenerator::endStudioEntryBootstrap");

        assertTrue(completionCall >= 0);
        assertTrue(completionAwait > completionCall);
        assertTrue(cacheAwait > completionAwait);
        assertTrue(teleportStart > cacheAwait);
        assertTrue(finalizeOpen > teleportStart);
        assertTrue(futureComplete > finalizeOpen);
        assertFalse(source.contains("requestChunkAsync("));
        assertTrue(scheduled >= 0);
        assertTrue(claim > scheduled);
        assertTrue(activation > claim);
        int ringCompletion = method.indexOf("thenCompose(nativeActivation -> nativeActivation)");
        assertTrue(ringCompletion > activation);
        assertTrue(gateRelease > ringCompletion);
        assertTrue(source.contains("abandonStudioEntryBootstrap(world, e);"));
    }

    @Test
    public void failedOpenMarshalsRetainedStateAbandonmentToTheServerScheduler() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/core/runtime/StudioOpenCoordinator.java")).replace("\r\n", "\n");
        int methodStart = source.indexOf(
                "private void abandonStudioEntryBootstrap(World world, Throwable failure)");
        int methodEnd = source.indexOf("private CompletableFuture<Void> cleanupFailedOpen", methodStart);
        String method = source.substring(methodStart, methodEnd);

        assertTrue(method.contains("if (J.isPrimaryThread())"));
        assertTrue(method.contains("CompletableFuture<Void> abandonment = J.sfut("));
        assertTrue(method.contains("abandonment.get(STUDIO_STRUCTURE_ACTIVATION_TIMEOUT_SECONDS"));
    }

    @Test
    public void queuedRestartDefersFailedOpenCleanupWithoutAcquiringALiveCloseLease() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/core/runtime/StudioOpenCoordinator.java")).replace("\r\n", "\n");
        int openCatch = source.indexOf("} catch (Throwable e) {");
        int restartCheck = source.indexOf(
                ".active(LifecycleOperationCoordinator.Domain.SERVER_LIFECYCLE)",
                openCatch);
        int restartDeferral = source.indexOf(
                "deferFailedOpenCleanupToRestart(",
                restartCheck);
        int liveCleanup = source.indexOf("cleanupFailedOpen(", restartDeferral);
        int methodStart = source.indexOf(
                "private void deferFailedOpenCleanupToRestart(",
                restartDeferral);
        int methodEnd = source.indexOf("private boolean transientWorldStorageExists", methodStart);
        String method = source.substring(methodStart, methodEnd);

        assertTrue(restartCheck > openCatch);
        assertTrue(restartDeferral > restartCheck);
        assertTrue(liveCleanup > restartDeferral);
        assertTrue(method.contains("queueStartupCleanup("));
        assertFalse(method.contains("closeWorldCoordinated("));
    }

    @Test
    public void openFinalizerReturnsToTheServerThreadBeforeCompletion() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/core/runtime/StudioOpenCoordinator.java")).replace("\r\n", "\n");
        int finalizerCall = source.indexOf("runOpenFinalizer(request.onDone(), world);");
        int futureCompletion = source.indexOf(
                "future.complete(new StudioOpenResult(world, entryLocation))", finalizerCall);
        int methodStart = source.indexOf(
                "private void runOpenFinalizer(Consumer<World> finalizer, World world)");
        int methodEnd = source.indexOf("private long elapsedMillis", methodStart);
        String method = source.substring(methodStart, methodEnd);

        assertTrue(finalizerCall >= 0);
        assertTrue(futureCompletion > finalizerCall);
        assertTrue(method.contains("if (J.isPrimaryThread())"));
        assertTrue(method.contains("J.sfut(() -> finalizer.accept(world))"));
        assertTrue(method.contains(
                "completion.get(STUDIO_STRUCTURE_ACTIVATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)"));
    }
}
