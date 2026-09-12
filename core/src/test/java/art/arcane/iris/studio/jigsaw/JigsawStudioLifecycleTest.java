package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.studio.generation.JigsawStudioGenerator;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.collection.KMap;
import org.bukkit.World;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.junit.After;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public class JigsawStudioLifecycleTest {
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_OWNER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @After
    public void clearLifecycle() {
        JigsawStudioActivation.finishOpen(OWNER);
        JigsawStudioActivation.finishOpen(OTHER_OWNER);
        JigsawStudioActivation.deactivate("overworld");
    }

    @Test
    public void openingLeaseAndActivationOwnerAreCentralized() {
        assertTrue(JigsawStudioActivation.tryBeginOpen(OWNER));
        assertFalse(JigsawStudioActivation.tryBeginOpen(OTHER_OWNER));
        JigsawStudioActivation.finishOpen(OTHER_OWNER);
        assertEquals(OWNER, JigsawStudioActivation.openingOwnerId());

        JigsawStudioActivation.Request request = activateOwnedStudio();

        assertEquals(OWNER, request.ownerId());
        assertEquals(OWNER, JigsawStudioActivation.activeOwnerId());
        JigsawStudioActivation.finishOpen(OWNER);
        assertNull(JigsawStudioActivation.openingOwnerId());
        assertFalse(JigsawStudioActivation.tryBeginOpen(OTHER_OWNER));
    }

    @Test
    public void studioRejectsNaturalCreatureSpawns() {
        assertTrue(JigsawStudioService.isNaturalStudioSpawn(
                CreatureSpawnEvent.SpawnReason.NATURAL));
        assertFalse(JigsawStudioService.isNaturalStudioSpawn(
                CreatureSpawnEvent.SpawnReason.CUSTOM));
        assertFalse(JigsawStudioService.isNaturalStudioSpawn(
                CreatureSpawnEvent.SpawnReason.SPAWNER));
    }

    @Test
    public void closeAuthorizationChecksOwnerDirtyStateAndSaveBarrierAtomically() {
        assertTrue(JigsawStudioActivation.tryBeginOpen(OWNER));
        JigsawStudioActivation.Request request = activateOwnedStudio();
        JigsawStudioActivation.finishOpen(OWNER);
        JigsawStudioSession session = JigsawStudioActivation.getSession(request.requestId());
        JigsawStudioService service = new JigsawStudioService();

        assertEquals(
                JigsawStudioService.CloseStart.NOT_OWNER,
                service.tryBeginClose(request.requestId(), OTHER_OWNER, false));
        assertTrue(service.closeProtectionFailure(request.requestId()).contains("owner-controlled"));

        assertEquals(
                JigsawStudioSession.DirtyStatus.MARKED,
                session.markWorkcellDirty(JigsawStudioLayout.SPATIAL_WORKCELL_ID).status());
        assertEquals(
                JigsawStudioService.CloseStart.DIRTY,
                service.tryBeginClose(request.requestId(), OWNER, false));
        assertTrue(service.closeProtectionFailure(request.requestId()).contains("autosave"));

        String towerWorkcellId = session.layout().workcellForVariant("stronghold/tower")
                .orElseThrow().stableId();
        JigsawStudioSession.VariantSwitchToken switchToken = session.beginVariantReload(
                towerWorkcellId).token().orElseThrow();
        assertEquals(
                JigsawStudioService.CloseStart.OPERATION_IN_PROGRESS,
                service.tryBeginClose(request.requestId(), OWNER, true));
        assertTrue(service.closeProtectionFailure(request.requestId()).contains("loading a variant"));
        assertTrue(session.abortVariantSwitch(switchToken));

        assertEquals(JigsawStudioSaveLifecycle.SaveStart.STARTED, service.saveLifecycle.tryBeginSave(request.requestId()));
        assertEquals(
                JigsawStudioService.CloseStart.SAVE_IN_PROGRESS,
                service.tryBeginClose(request.requestId(), OWNER, true));
        assertTrue(service.closeProtectionFailure(request.requestId()).contains("saving"));

        service.saveLifecycle.finishSave(request.requestId());
        assertEquals(
                JigsawStudioService.CloseStart.STARTED,
                service.tryBeginClose(request.requestId(), OWNER, true));
        assertNull(service.closeProtectionFailure(request.requestId()));
        assertEquals(JigsawStudioSaveLifecycle.SaveStart.CLOSING, service.saveLifecycle.tryBeginSave(request.requestId()));
    }

    @Test
    public void ownerReplacementWaitsForAutosaveThenClaimsClose() {
        assertTrue(JigsawStudioActivation.tryBeginOpen(OWNER));
        JigsawStudioActivation.Request request = activateOwnedStudio();
        JigsawStudioActivation.finishOpen(OWNER);
        JigsawStudioSession session = JigsawStudioActivation.getSession(request.requestId());
        JigsawStudioService service = new JigsawStudioService();
        assertEquals(
                JigsawStudioSession.DirtyStatus.MARKED,
                session.markWorkcellDirty(JigsawStudioLayout.SPATIAL_WORKCELL_ID).status());
        AtomicReference<Runnable> retry = new AtomicReference<>();

        try (MockedStatic<J> scheduling = mockStatic(J.class)) {
            scheduling.when(() -> J.s(any(Runnable.class), eq(5))).thenAnswer(invocation -> {
                retry.set(invocation.getArgument(0));
                return null;
            });
            CompletableFuture<Void> readiness = service.saveLifecycle.awaitCloseForReplacement(
                    request.requestId(), OWNER);

            assertFalse(readiness.isDone());
            assertTrue(retry.get() != null);
            JigsawStudioSession.SaveStart save = session.beginSave(
                    JigsawStudioLayout.SPATIAL_WORKCELL_ID);
            assertEquals(JigsawStudioSession.SaveStatus.STARTED, save.status());
            assertTrue(session.markWorkcellSaved(save.identity().orElseThrow()));
            retry.get().run();

            readiness.join();
            assertNull(service.closeProtectionFailure(request.requestId()));
        }
    }

    @Test
    public void nonOwnerReplacementFailsWithoutWaiting() {
        assertTrue(JigsawStudioActivation.tryBeginOpen(OWNER));
        JigsawStudioActivation.Request request = activateOwnedStudio();
        JigsawStudioActivation.finishOpen(OWNER);
        JigsawStudioService service = new JigsawStudioService();

        try (MockedStatic<J> scheduling = mockStatic(J.class)) {
            CompletableFuture<Void> readiness = service.saveLifecycle.awaitCloseForReplacement(
                    request.requestId(), OTHER_OWNER);

            assertTrue(readiness.isCompletedExceptionally());
            scheduling.verifyNoInteractions();
        }
    }

    @Test
    public void lateJigsawGuiMutationKeepsCloseBehindTheFinalSnapshotAndAutosaveBarriers()
            throws ReflectiveOperationException {
        assertTrue(JigsawStudioActivation.tryBeginOpen(OWNER));
        JigsawStudioActivation.Request request = activateOwnedStudio();
        JigsawStudioActivation.finishOpen(OWNER);
        JigsawStudioSession session = JigsawStudioActivation.getSession(request.requestId());
        JigsawStudioService service = new JigsawStudioService();
        KMap<String, Object> baseline = new KMap<>();
        baseline.put("name", "iris:start");
        KMap<String, Object> updated = new KMap<>();
        updated.put("name", "iris:hall");
        assertTrue(JigsawStudioTileWatcher.tileSnapshotChanged(baseline, updated));

        Class<?> keyType = Class.forName(JigsawStudioTileWatcher.class.getName() + "$JigsawTileWatchKey");
        Constructor<?> constructor = keyType.getDeclaredConstructor(
                UUID.class, UUID.class, int.class, int.class, int.class);
        constructor.setAccessible(true);
        Object key = constructor.newInstance(request.requestId(), UUID.randomUUID(), 1, 64, 1);
        Class<?> studioType = Class.forName(JigsawStudioService.class.getName() + "$ActiveStudio");
        Constructor<?> studioConstructor = studioType.getDeclaredConstructor(
                UUID.class,
                World.class,
                Engine.class,
                JigsawStudioGenerator.class,
                ConcurrentHashMap.class,
                Set.class,
                AtomicLong.class);
        studioConstructor.setAccessible(true);
        World world = mock(World.class);
        Object studio = studioConstructor.newInstance(
                UUID.randomUUID(),
                world,
                mock(Engine.class),
                mock(JigsawStudioGenerator.class),
                new ConcurrentHashMap<>(),
                ConcurrentHashMap.newKeySet(),
                new AtomicLong());
        Class<?> watchType = Class.forName(JigsawStudioTileWatcher.class.getName() + "$JigsawTileWatch");
        Constructor<?> watchConstructor = watchType.getDeclaredConstructor(
                keyType,
                studioType,
                UUID.class,
                String.class,
                KMap.class,
                AtomicBoolean.class,
                AtomicBoolean.class);
        watchConstructor.setAccessible(true);
        Object watch = watchConstructor.newInstance(
                key,
                studio,
                OWNER,
                JigsawStudioLayout.SPATIAL_WORKCELL_ID,
                baseline,
                new AtomicBoolean(),
                new AtomicBoolean());
        Field watchesField = JigsawStudioTileWatcher.class.getDeclaredField("jigsawTileWatches");
        watchesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Object, Object> watches = (Map<Object, Object>) watchesField.get(service.tileWatcher);
        watches.put(key, watch);

        try (MockedStatic<J> scheduling = mockStatic(J.class)) {
            scheduling.when(() -> J.isOwnedByCurrentRegion(any(World.class), anyInt(), anyInt()))
                    .thenReturn(false);
            scheduling.when(() -> J.runRegion(
                    any(World.class), anyInt(), anyInt(), any(Runnable.class)))
                    .thenReturn(false);
            assertEquals(
                    JigsawStudioService.CloseStart.OPERATION_IN_PROGRESS,
                    service.tryBeginClose(request.requestId(), OWNER, false));
            assertTrue(service.closeProtectionFailure(request.requestId()).contains("finalizing"));
            assertEquals(1, watches.size());
            scheduling.verify(() -> J.s(any(Runnable.class), eq(5)));
        }

        watches.clear();
        assertEquals(
                JigsawStudioSession.DirtyStatus.MARKED,
                session.markWorkcellDirty(JigsawStudioLayout.SPATIAL_WORKCELL_ID).status());
        assertEquals(
                JigsawStudioService.CloseStart.DIRTY,
                service.tryBeginClose(request.requestId(), OWNER, false));
    }

    @Test
    public void exportLeaseSerializesSavesAndCloseAgainstThePinnedRequest() {
        assertTrue(JigsawStudioActivation.tryBeginOpen(OWNER));
        JigsawStudioActivation.Request request = activateOwnedStudio();
        JigsawStudioActivation.finishOpen(OWNER);
        JigsawStudioService service = new JigsawStudioService();

        assertEquals(
                JigsawStudioService.ExportStart.NOT_OWNER,
                service.tryBeginExport(request.requestId(), OTHER_OWNER));
        assertEquals(
                JigsawStudioService.ExportStart.STARTED,
                service.tryBeginExport(request.requestId(), OWNER));
        assertEquals(
                JigsawStudioService.ExportStart.IN_PROGRESS,
                service.tryBeginExport(request.requestId(), OWNER));
        assertEquals(
                JigsawStudioSaveLifecycle.SaveStart.EXPORT_OPERATION,
                service.saveLifecycle.tryBeginSave(request.requestId()));
        assertEquals(
                JigsawStudioService.CloseStart.OPERATION_IN_PROGRESS,
                service.tryBeginClose(request.requestId(), OWNER, false));
        assertTrue(service.closeProtectionFailure(request.requestId()).contains("exporting"));

        service.finishExport(request.requestId());
        assertEquals(JigsawStudioSaveLifecycle.SaveStart.STARTED, service.saveLifecycle.tryBeginSave(request.requestId()));
        service.saveLifecycle.finishSave(request.requestId());
    }

    @Test
    public void immediateSingleAndFamilyDuplicatesWaitForAutosaveThenRunExactlyOnce() {
        assertEquals(
                JigsawStudioToolbelt.DeferredDuplicationReadiness.WAITING_FOR_AUTOSAVE,
                JigsawStudioToolbelt.deferredDuplicationReadiness(
                        true, true, true, false, false));
        assertEquals(
                JigsawStudioToolbelt.DeferredDuplicationReadiness.WAITING_FOR_AUTOSAVE,
                JigsawStudioToolbelt.deferredDuplicationReadiness(
                        true, true, true, true, false));
        assertEquals(
                JigsawStudioToolbelt.DeferredDuplicationReadiness.WAITING_FOR_OPERATION,
                JigsawStudioToolbelt.deferredDuplicationReadiness(
                        true, true, false, true, false));
        assertEquals(
                JigsawStudioToolbelt.DeferredDuplicationReadiness.READY,
                JigsawStudioToolbelt.deferredDuplicationReadiness(
                        true, true, false, false, false));
        assertEquals(
                JigsawStudioToolbelt.DeferredDuplicationReadiness.STALE,
                JigsawStudioToolbelt.deferredDuplicationReadiness(
                        true, false, false, false, false));
    }

    @Test
    public void initialEvaluationWaitsForCommitAndSchedulesExactlyOnce() {
        AtomicLong registrationBeforeCommit = new AtomicLong();
        assertFalse(JigsawStudioEvaluator.claimInitialEvaluation(registrationBeforeCommit, false));
        assertEquals(0L, registrationBeforeCommit.get());
        assertTrue(JigsawStudioEvaluator.claimInitialEvaluation(registrationBeforeCommit, true));
        assertEquals(1L, registrationBeforeCommit.get());
        assertFalse(JigsawStudioEvaluator.claimInitialEvaluation(registrationBeforeCommit, true));

        AtomicLong registrationAfterCommit = new AtomicLong();
        assertTrue(JigsawStudioEvaluator.claimInitialEvaluation(registrationAfterCommit, true));
        assertEquals(1L, registrationAfterCommit.get());
        assertFalse(JigsawStudioEvaluator.claimInitialEvaluation(registrationAfterCommit, true));
    }

    @Test
    public void ownerEnteringAWorkcellMakesItTheNextMenuSelection() {
        JigsawStudioLayout layout = JigsawStudioLayout.create(
                JigsawStudioMode.PLANAR_JIGSAW,
                new JigsawStudioCellDimensions(16, 16, 16),
                new JigsawStudioVariantCatalog(List.of()));
        JigsawStudioSession session = new JigsawStudioSession("overworld", "village", layout);
        JigsawStudioBay end = layout.get("workcell/end");
        JigsawStudioBay blank = layout.get("workcell/blank");

        assertTrue(session.selectedBayId().isEmpty());
        assertTrue(JigsawStudioPlayerContext.selectEnteredWorkcell(session, end, true));
        assertEquals(end.stableId(), session.selectedBayId().orElseThrow());
        assertFalse(JigsawStudioPlayerContext.selectEnteredWorkcell(session, blank, false));
        assertEquals(end.stableId(), session.selectedBayId().orElseThrow());
    }

    private static JigsawStudioActivation.Request activateOwnedStudio() {
        return JigsawStudioActivation.activate(
                "overworld",
                "stronghold",
                JigsawStudioMode.SPATIAL_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                new JigsawStudioCellDimensions(16, 16, 16),
                mock(IrisData.class),
                JigsawStudioLayout.create(
                        JigsawStudioMode.SPATIAL_JIGSAW,
                        new JigsawStudioCellDimensions(16, 16, 16),
                        new JigsawStudioVariantCatalog(List.of(
                                spatialVariant("stronghold/hall"),
                                spatialVariant("stronghold/tower")))),
                OWNER);
    }

    private static JigsawStudioVariant spatialVariant(String key) {
        return new JigsawStudioVariant(
                key,
                key,
                "",
                Optional.of(new JigsawStudioCellDimensions(16, 16, 16)),
                JigsawStudioMode.SPATIAL_JIGSAW,
                Optional.empty(),
                true,
                true,
                List.of(),
                new JigsawStudioPieceRules(0, 30, 0, 0, false),
                List.of());
    }
}
