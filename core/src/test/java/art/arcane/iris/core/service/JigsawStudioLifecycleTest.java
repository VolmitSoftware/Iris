package art.arcane.iris.core.service;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBay;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCompatibilityTarget;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioLayout;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioMode;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioPieceRules;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioSession;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariant;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariantCatalog;
import art.arcane.iris.engine.platform.studio.generators.JigsawStudioGenerator;
import art.arcane.volmlib.util.collection.KMap;
import com.volmit.ModuleLog;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class JigsawStudioLifecycleTest {
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_OWNER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final long REPLACEMENT_WAIT_SECONDS = 10L;

    private final JigsawStudioService service = new JigsawStudioService(mock(ModuleLog.class));
    private final JigsawStudioRegistry registry = service.registry();

    @After
    public void stopStudio() {
        registry.finishOpen(OWNER);
        registry.finishOpen(OTHER_OWNER);
        registry.deactivate("overworld");
        service.quiesceForServerShutdown();
    }

    @Test
    public void openingLeaseAndActivationOwnerAreCentralized() {
        assertTrue(registry.tryBeginOpen(OWNER));
        assertFalse(registry.tryBeginOpen(OTHER_OWNER));
        registry.finishOpen(OTHER_OWNER);
        assertEquals(OWNER, registry.openingOwnerId());

        JigsawStudioRequest request = activateOwnedStudio();

        assertEquals(OWNER, request.ownerId());
        assertEquals(OWNER, registry.activeOwnerId());
        registry.finishOpen(OWNER);
        assertNull(registry.openingOwnerId());
        assertFalse(registry.tryBeginOpen(OTHER_OWNER));
    }

    @Test
    public void closeAuthorizationChecksOwnerDirtyStateAndSaveBarrierAtomically() {
        assertTrue(registry.tryBeginOpen(OWNER));
        JigsawStudioRequest request = activateOwnedStudio();
        registry.finishOpen(OWNER);
        JigsawStudioSession session = registry.getSession(request.requestId());

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

        assertEquals(JigsawStudioService.SaveStart.STARTED, service.tryBeginSave(request.requestId()));
        assertEquals(
                JigsawStudioService.CloseStart.SAVE_IN_PROGRESS,
                service.tryBeginClose(request.requestId(), OWNER, true));
        assertTrue(service.closeProtectionFailure(request.requestId()).contains("saving"));

        service.finishSave(request.requestId());
        assertEquals(
                JigsawStudioService.CloseStart.STARTED,
                service.tryBeginClose(request.requestId(), OWNER, true));
        assertNull(service.closeProtectionFailure(request.requestId()));
        assertEquals(JigsawStudioService.SaveStart.CLOSING, service.tryBeginSave(request.requestId()));
    }

    @Test
    public void ownerReplacementWaitsForAutosaveThenClaimsClose() throws Exception {
        assertTrue(registry.tryBeginOpen(OWNER));
        JigsawStudioRequest request = activateOwnedStudio();
        registry.finishOpen(OWNER);
        JigsawStudioSession session = registry.getSession(request.requestId());
        assertEquals(
                JigsawStudioSession.DirtyStatus.MARKED,
                session.markWorkcellDirty(JigsawStudioLayout.SPATIAL_WORKCELL_ID).status());

        CompletableFuture<Void> readiness = service.awaitCloseForReplacement(request.requestId(), OWNER);

        assertFalse(readiness.isDone());
        JigsawStudioSession.SaveStart save = session.beginSave(JigsawStudioLayout.SPATIAL_WORKCELL_ID);
        assertEquals(JigsawStudioSession.SaveStatus.STARTED, save.status());
        assertTrue(session.markWorkcellSaved(save.identity().orElseThrow()));

        readiness.get(REPLACEMENT_WAIT_SECONDS, TimeUnit.SECONDS);
        assertNull(service.closeProtectionFailure(request.requestId()));
    }

    @Test
    public void nonOwnerReplacementFailsWithoutWaiting() {
        assertTrue(registry.tryBeginOpen(OWNER));
        JigsawStudioRequest request = activateOwnedStudio();
        registry.finishOpen(OWNER);

        CompletableFuture<Void> readiness = service.awaitCloseForReplacement(request.requestId(), OTHER_OWNER);

        assertTrue(readiness.isCompletedExceptionally());
    }

    @Test
    public void lateJigsawGuiMutationKeepsCloseBehindTheFinalSnapshotAndAutosaveBarriers()
            throws ReflectiveOperationException {
        assertTrue(registry.tryBeginOpen(OWNER));
        JigsawStudioRequest request = activateOwnedStudio();
        registry.finishOpen(OWNER);
        JigsawStudioSession session = registry.getSession(request.requestId());
        KMap<String, Object> baseline = new KMap<>();
        baseline.put("name", "iris:start");
        KMap<String, Object> updated = new KMap<>();
        updated.put("name", "iris:hall");
        assertTrue(JigsawStudioService.tileSnapshotChanged(baseline, updated));

        Class<?> keyType = Class.forName(JigsawStudioService.class.getName() + "$JigsawTileWatchKey");
        Constructor<?> constructor = keyType.getDeclaredConstructor(
                UUID.class, UUID.class, int.class, int.class, int.class);
        constructor.setAccessible(true);
        Object key = constructor.newInstance(request.requestId(), UUID.randomUUID(), 1, 64, 1);
        FakeToolWorld world = new FakeToolWorld("jigsaw-" + request.requestId());
        world.schedulingFails = true;
        JigsawStudioService.ActiveStudio studio = new JigsawStudioService.ActiveStudio(
                UUID.randomUUID(),
                world,
                mock(JigsawStudioGenerator.class),
                new ConcurrentHashMap<>(),
                ConcurrentHashMap.newKeySet(),
                new AtomicLong());
        Class<?> watchType = Class.forName(JigsawStudioService.class.getName() + "$JigsawTileWatch");
        Constructor<?> watchConstructor = watchType.getDeclaredConstructor(
                keyType,
                JigsawStudioService.ActiveStudio.class,
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
        Field watchesField = JigsawStudioService.class.getDeclaredField("jigsawTileWatches");
        watchesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Object, Object> watches = (Map<Object, Object>) watchesField.get(service);
        watches.put(key, watch);

        assertEquals(
                JigsawStudioService.CloseStart.OPERATION_IN_PROGRESS,
                service.tryBeginClose(request.requestId(), OWNER, false));
        assertTrue(service.closeProtectionFailure(request.requestId()).contains("finalizing"));
        assertEquals(1, watches.size());

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
        assertTrue(registry.tryBeginOpen(OWNER));
        JigsawStudioRequest request = activateOwnedStudio();
        registry.finishOpen(OWNER);

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
                JigsawStudioService.SaveStart.EXPORT_OPERATION,
                service.tryBeginSave(request.requestId()));
        assertEquals(
                JigsawStudioService.CloseStart.OPERATION_IN_PROGRESS,
                service.tryBeginClose(request.requestId(), OWNER, false));
        assertTrue(service.closeProtectionFailure(request.requestId()).contains("exporting"));

        service.finishExport(request.requestId());
        assertEquals(JigsawStudioService.SaveStart.STARTED, service.tryBeginSave(request.requestId()));
        service.finishSave(request.requestId());
    }

    @Test
    public void immediateSingleAndFamilyDuplicatesWaitForAutosaveThenRunExactlyOnce() {
        assertEquals(
                JigsawStudioService.DeferredDuplicationReadiness.WAITING_FOR_AUTOSAVE,
                JigsawStudioService.deferredDuplicationReadiness(
                        true, true, true, false, false));
        assertEquals(
                JigsawStudioService.DeferredDuplicationReadiness.WAITING_FOR_AUTOSAVE,
                JigsawStudioService.deferredDuplicationReadiness(
                        true, true, true, true, false));
        assertEquals(
                JigsawStudioService.DeferredDuplicationReadiness.WAITING_FOR_OPERATION,
                JigsawStudioService.deferredDuplicationReadiness(
                        true, true, false, true, false));
        assertEquals(
                JigsawStudioService.DeferredDuplicationReadiness.READY,
                JigsawStudioService.deferredDuplicationReadiness(
                        true, true, false, false, false));
        assertEquals(
                JigsawStudioService.DeferredDuplicationReadiness.STALE,
                JigsawStudioService.deferredDuplicationReadiness(
                        true, false, false, false, false));
    }

    @Test
    public void initialEvaluationWaitsForCommitAndSchedulesExactlyOnce() {
        AtomicLong registrationBeforeCommit = new AtomicLong();
        assertFalse(JigsawStudioService.claimInitialEvaluation(registrationBeforeCommit, false));
        assertEquals(0L, registrationBeforeCommit.get());
        assertTrue(JigsawStudioService.claimInitialEvaluation(registrationBeforeCommit, true));
        assertEquals(1L, registrationBeforeCommit.get());
        assertFalse(JigsawStudioService.claimInitialEvaluation(registrationBeforeCommit, true));

        AtomicLong registrationAfterCommit = new AtomicLong();
        assertTrue(JigsawStudioService.claimInitialEvaluation(registrationAfterCommit, true));
        assertEquals(1L, registrationAfterCommit.get());
        assertFalse(JigsawStudioService.claimInitialEvaluation(registrationAfterCommit, true));
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
        assertTrue(JigsawStudioService.selectEnteredWorkcell(session, end, true));
        assertEquals(end.stableId(), session.selectedBayId().orElseThrow());
        assertFalse(JigsawStudioService.selectEnteredWorkcell(session, blank, false));
        assertEquals(end.stableId(), session.selectedBayId().orElseThrow());
    }

    private JigsawStudioRequest activateOwnedStudio() {
        return registry.activate(
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
