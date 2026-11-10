package art.arcane.iris.core.service;

import art.arcane.iris.core.runtime.jigsaw.JigsawPlanarArchetype;
import art.arcane.iris.core.runtime.jigsaw.JigsawPlanarTopology;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBay;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBayKind;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBounds;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCompatibilityTarget;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioLayout;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioMode;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioPieceRules;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioPoolMembership;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioSession;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariant;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariantCatalog;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioWorkcellSpec;
import art.arcane.iris.core.structure.authoring.StructureWriteResult;
import art.arcane.iris.engine.framework.structure.assembly.PieceTurn;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.engine.object.structure.jigsaw.JigsawJoint;
import art.arcane.iris.engine.object.structure.jigsaw.JigsawDirection;
import com.volmit.geometry.BlockPosition;
import art.arcane.iris.engine.platform.studio.generators.JigsawStudioGenerator;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import com.volmit.ModuleLog;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.BlockEntityData;
import com.volmit.object.GeneratedObject;
import com.volmit.object.ObjectBlock;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.ObjectCodec;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JigsawStudioServiceCaptureTest {
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final long WAIT_SECONDS = 10L;
    private static final ResourceId TILE_FORMAT = ResourceId.parse("iris:tile");

    private final ModuleLog log = mock(ModuleLog.class);
    private final JigsawStudioService service = new JigsawStudioService(log);
    private final BlockRegistry registry = FakeBlockRegistry.create();
    private final ObjectCodec codec = new TextObjectCodec();

    @After
    public void stopService() {
        service.registry().finishOpen(OWNER);
        service.registry().deactivate("overworld");
        service.quiesceForServerShutdown();
    }

    @Test
    public void shutdownQuiesceIsIdempotent() throws Exception {
        Field disableStartedField = JigsawStudioService.class.getDeclaredField("disableStarted");
        Field enabledField = JigsawStudioService.class.getDeclaredField("enabled");
        disableStartedField.setAccessible(true);
        enabledField.setAccessible(true);
        AtomicBoolean disableStarted = (AtomicBoolean) disableStartedField.get(service);
        assertFalse(disableStarted.get());
        assertTrue(enabledField.getBoolean(service));

        service.quiesceForServerShutdown();
        service.quiesceForServerShutdown();

        assertTrue(disableStarted.get());
        assertFalse(enabledField.getBoolean(service));
    }

    @Test
    public void successfulStudioSavePlaysOneOwnerLocalBell() {
        FakeToolPlayer player = new FakeToolPlayer(OWNER, "owner", new FakeToolWorld("jigsaw"));

        JigsawStudioService.playSaveSound(player);

        assertEquals(
                List.of(new FakeToolPlayer.Sound(ResourceId.parse("minecraft:block.note_block.bell"), 0.65F, 1.65F)),
                player.sounds);
    }

    @Test
    public void hiddenConnectorResetRestoresOnlyItsSavedOrdinaryBlock() throws Exception {
        FakeToolWorld world = new FakeToolWorld("jigsaw");
        BlockState stone = BlockState.parse("minecraft:stone");
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(15, 15, 15);
        JigsawStudioBay workcell = new JigsawStudioBay(
                "spatial",
                JigsawStudioBayKind.SPATIAL_WORKCELL,
                Optional.empty(),
                "",
                new JigsawStudioBounds(10, 20, 30, dimensions));
        JigsawStudioGenerator.RenderedConnector renderedConnector =
                new JigsawStudioGenerator.RenderedConnector(
                        1,
                        2,
                        3,
                        connectorAt(1, 2, 3).setFinalState("minecraft:stone"),
                        "north_up");
        JigsawStudioGenerator.RenderedBlock renderedBlock =
                new JigsawStudioGenerator.RenderedBlock(1, 2, 3, stone, null);

        JigsawStudioService.restoreConnectorChunk(
                world,
                registry,
                workcell,
                List.of(renderedConnector),
                Map.of(new JigsawStudioService.LocalPosition(1, 2, 3), renderedBlock),
                false);

        assertEquals(Map.of(new BlockPosition(11, 22, 33), stone), world.blocks);
    }

    @Test
    public void visibleConnectorResetRestoresItsJigsawMarkerWithTheConnectorNbt() throws Exception {
        FakeToolWorld world = new FakeToolWorld("jigsaw");
        JigsawStudioBay workcell = new JigsawStudioBay(
                "spatial",
                JigsawStudioBayKind.SPATIAL_WORKCELL,
                Optional.empty(),
                "",
                new JigsawStudioBounds(10, 20, 30, new JigsawStudioCellDimensions(15, 15, 15)));
        IrisJigsawConnector connector = connectorAt(1, 2, 3).setFinalState("minecraft:stone");
        JigsawStudioGenerator.RenderedConnector rendered =
                new JigsawStudioGenerator.RenderedConnector(1, 2, 3, connector, "north_up");

        JigsawStudioService.restoreConnectorChunk(world, registry, workcell, List.of(rendered), Map.of(), true);

        BlockPosition at = new BlockPosition(11, 22, 33);
        assertEquals(BlockState.parse("minecraft:jigsaw[orientation=north_up]"), world.blocks.get(at));
        KMap<String, Object> nbt = JigsawStudioTileCodec.nbt(world.entities.get(at)).orElseThrow();
        assertEquals("minecraft:stone", nbt.get("final_state"));
        assertEquals("iris:door", nbt.get("name"));
    }

    @Test
    public void liveRelayoutDetectsMovedBoundsAndIncludesCageChunks() {
        JigsawStudioCellDimensions originalDimensions = new JigsawStudioCellDimensions(16, 8, 16);
        JigsawStudioLayout original = JigsawStudioLayout.create(
                JigsawStudioMode.PLANAR_JIGSAW,
                originalDimensions,
                JigsawStudioVariantCatalog.empty());
        List<JigsawStudioWorkcellSpec> expandedSpecs = new ArrayList<>();
        for (JigsawPlanarArchetype archetype : JigsawPlanarArchetype.values()) {
            expandedSpecs.add(new JigsawStudioWorkcellSpec(
                    archetype,
                    "",
                    archetype == JigsawPlanarArchetype.BLANK
                            ? new JigsawStudioCellDimensions(33, 12, 17)
                            : originalDimensions,
                    true));
        }
        JigsawStudioLayout expanded = JigsawStudioLayout.createPlanar(
                originalDimensions,
                expandedSpecs,
                JigsawStudioVariantCatalog.empty());

        assertFalse(JigsawStudioService.layoutGeometryChanged(original, original));
        assertTrue(JigsawStudioService.layoutGeometryChanged(original, expanded));
        Set<Long> chunks = JigsawStudioService.relayoutChunks(original, expanded);
        assertTrue(chunks.contains(0L));
        assertTrue(chunks.contains(((long) 4 << 32)));
    }

    @Test
    public void mappedGraphOwnershipControlsNewVariantsEvenWhenTheCatalogIsEmpty() {
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(16, 16, 16);
        JigsawStudioLayout editable = JigsawStudioLayout.create(
                JigsawStudioMode.PLANAR_JIGSAW,
                dimensions,
                new JigsawStudioVariantCatalog(List.of(), true));
        JigsawStudioLayout managed = JigsawStudioLayout.create(
                JigsawStudioMode.PLANAR_JIGSAW,
                dimensions,
                new JigsawStudioVariantCatalog(List.of(), false));

        assertTrue(JigsawStudioService.canCreateVariants(editable));
        assertFalse(JigsawStudioService.canCreateVariants(managed));
    }

    @Test
    public void guiVariantCreationRequiresAnActiveOwnedAssignedSource() {
        JigsawStudioVariant assigned = new JigsawStudioVariant(
                "test/end",
                "test/end",
                "",
                Optional.of(new JigsawStudioCellDimensions(16, 16, 16)),
                JigsawStudioMode.PLANAR_JIGSAW,
                Optional.of(JigsawPlanarTopology.NORTH_END),
                true,
                true,
                List.of("variant-1"),
                new JigsawStudioPieceRules(0, 30, 0, 0, true),
                List.of(
                        new JigsawStudioPoolMembership("test/pieces", 0, 4, 0.35D),
                        new JigsawStudioPoolMembership("test/caps", 0, 7, 0.8D)));

        assertEquals("", JigsawStudioService.variantCreationSourceFailure(assigned, false));
        assertEquals("", JigsawStudioService.variantCreationSourceFailure(assigned, true));
        assertTrue(JigsawStudioService.variantCreationSourceFailure(null, false)
                .contains("piece create <poolKey> <pieceKey>"));
        assertTrue(JigsawStudioService.variantCreationSourceFailure(planarVariant(true, true), false)
                .contains("no owned pool membership"));
        assertTrue(JigsawStudioService.variantCreationSourceFailure(planarVariant(true, false), true)
                .contains("read-only variant"));
    }

    @Test
    public void recognizesVanillaNamespacedAndWorldEditMutationCommands() {
        assertTrue(JigsawStudioService.isMutatingCommand("/fill 0 0 0 1 1 1 stone"));
        assertTrue(JigsawStudioService.isMutatingCommand("minecraft:setblock 0 0 0 air"));
        assertTrue(JigsawStudioService.isMutatingCommand("//paste -a"));
        assertTrue(JigsawStudioService.isMutatingCommand("/execute as @s run setblock 0 0 0 stone"));
        assertTrue(JigsawStudioService.isMutatingCommand("/function test:build"));
        assertTrue(JigsawStudioService.isMutatingCommand("/data merge block 0 0 0 {}"));
        assertTrue(JigsawStudioService.isMutatingCommand("/item replace block 0 0 0 container.0 with stone"));
        assertFalse(JigsawStudioService.isMutatingCommand("/iris jigsaw status"));
        assertFalse(JigsawStudioService.isMutatingCommand("/tp 0 80 0"));
    }

    @Test
    public void nonOwnerMutatingCommandsAreBlocked() {
        UUID ownerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID otherId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        assertTrue(JigsawStudioService.ownerMatches(ownerId, ownerId));
        assertTrue(JigsawStudioService.ownerMatches(null, otherId));
        assertFalse(JigsawStudioService.ownerMatches(ownerId, otherId));
        assertTrue(JigsawStudioService.blocksStudioEdit(ownerId, otherId));
        assertFalse(JigsawStudioService.blocksStudioEdit(ownerId, ownerId));
        assertFalse(JigsawStudioService.blocksStudioEdit(null, otherId));
        assertTrue(JigsawStudioService.blocksMutatingCommand(ownerId, otherId, "//paste -a"));
        assertFalse(JigsawStudioService.blocksMutatingCommand(ownerId, ownerId, "//paste -a"));
        assertFalse(JigsawStudioService.blocksMutatingCommand(null, otherId, "//paste -a"));
        assertFalse(JigsawStudioService.blocksMutatingCommand(ownerId, otherId, "/iris jigsaw status"));
        assertFalse(JigsawStudioService.blocksMutatingCommand(ownerId, otherId, "/msg owner hello"));
        assertTrue(JigsawStudioService.blocksMutatingCommand(ownerId, otherId, "/execute run say bypass"));
        assertTrue(JigsawStudioService.blocksMutatingCommand(ownerId, otherId, "/unknownplugin mutate"));
        assertTrue(JigsawStudioService.blocksNonEditableWorkcellMutation(
                true, "/fill 0 0 0 1 1 1 stone"));
        assertTrue(JigsawStudioService.blocksNonEditableWorkcellMutation(true, "//paste -a"));
        assertFalse(JigsawStudioService.blocksNonEditableWorkcellMutation(
                true, "/iris jigsaw variant load test/end"));
        assertFalse(JigsawStudioService.blocksNonEditableWorkcellMutation(
                false, "/fill 0 0 0 1 1 1 stone"));
    }

    @Test
    public void studioContainerMutationPolicyProtectsOperationsOwnersAndAutomation() {
        UUID ownerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID otherId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        assertFalse(JigsawStudioService.blocksStudioInventoryMutation(
                false, false, true, true, ownerId, otherId));
        assertFalse(JigsawStudioService.blocksStudioInventoryMutation(
                true, false, false, false, ownerId, ownerId));
        assertTrue(JigsawStudioService.blocksStudioInventoryMutation(
                true, false, true, false, ownerId, ownerId));
        assertTrue(JigsawStudioService.blocksStudioInventoryMutation(
                true, false, false, true, ownerId, ownerId));
        assertTrue(JigsawStudioService.blocksStudioInventoryMutation(
                true, false, false, false, ownerId, otherId));
        assertFalse(JigsawStudioService.blocksStudioInventoryMutation(
                true, false, false, false, ownerId, null));
        assertTrue(JigsawStudioService.blocksStudioInventoryMutation(
                true, true, false, false, ownerId, null));
        assertTrue(JigsawStudioService.blocksStudioInventoryMutation(
                true, true, false, false, ownerId, ownerId));
    }

    @Test
    public void jigsawTileSnapshotDetectsLateVanillaGuiMutation() {
        KMap<String, Object> baseline = new KMap<>();
        baseline.put("name", "iris:start");
        KMap<String, Object> unchanged = new KMap<>();
        unchanged.put("name", "iris:start");
        KMap<String, Object> changed = new KMap<>();
        changed.put("name", "iris:hall");
        KMap<String, Object> changedAgain = new KMap<>();
        changedAgain.put("name", "iris:cap");

        assertFalse(JigsawStudioService.tileSnapshotChanged(baseline, unchanged));
        assertTrue(JigsawStudioService.tileSnapshotChanged(baseline, changed));
        assertEquals(
                new JigsawStudioService.JigsawTilePollDecision(true, true),
                JigsawStudioService.jigsawTilePollDecision(baseline, changed, false));
        assertEquals(
                new JigsawStudioService.JigsawTilePollDecision(true, true),
                JigsawStudioService.jigsawTilePollDecision(changed, changedAgain, false));
        assertEquals(
                new JigsawStudioService.JigsawTilePollDecision(false, false),
                JigsawStudioService.jigsawTilePollDecision(changedAgain, changedAgain, true));
    }

    @Test
    public void persistentAutosaveFailuresLogOncePerDirtyIdentityAndUseCappedBackoff() {
        UUID requestId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        IOException validationFailure = new IOException("closure rejected an empty piece entry");
        String validationDetail = "Jigsaw Studio save failed: closure rejected an empty piece entry";
        String validationContext = JigsawStudioService.autosaveFailureContext(
                requestId,
                "qa/profile_final",
                "workcell/end",
                "qa/profile_final/end",
                validationDetail);
        JigsawStudioService.AutosaveFailureState validationState =
                new JigsawStudioService.AutosaveFailureState();
        StructureWriteResult writerResult = new StructureWriteResult(
                StructureWriteResult.Status.OWNERSHIP_CONFLICT,
                StructureWriteResult.Action.NONE,
                List.of(StructureWriteResult.Conflict.at(
                        "jigsaw-pieces/qa/profile_final/end.json",
                        StructureWriteResult.ConflictReason.MODIFIED_RESOURCE)),
                List.of(),
                "",
                Optional.empty());
        String writerDetail = JigsawStudioService.writeFailure(writerResult);
        String writerContext = JigsawStudioService.autosaveFailureContext(
                requestId,
                "qa/profile_final",
                "workcell/end",
                "qa/profile_final/end",
                writerDetail);
        JigsawStudioService.AutosaveFailureState writerState =
                new JigsawStudioService.AutosaveFailureState();

        JigsawStudioService.AutosaveFailureDecision firstValidation =
                JigsawStudioService.recordPersistentAutosaveFailure(
                        log,
                        validationState,
                        requestId,
                        "qa/profile_final",
                        "workcell/end",
                        "qa/profile_final/end",
                        validationDetail,
                        validationFailure);
        JigsawStudioService.AutosaveFailureDecision secondValidation =
                JigsawStudioService.recordPersistentAutosaveFailure(
                        log,
                        validationState,
                        requestId,
                        "qa/profile_final",
                        "workcell/end",
                        "qa/profile_final/end",
                        validationDetail,
                        validationFailure);
        List<Integer> writerDelays = new ArrayList<>();
        for (int attempt = 0; attempt < 7; attempt++) {
            writerDelays.add(JigsawStudioService.recordPersistentAutosaveFailure(
                    log,
                    writerState,
                    requestId,
                    "qa/profile_final",
                    "workcell/end",
                    "qa/profile_final/end",
                    writerDetail,
                    null).retryTicks());
        }

        assertEquals(40, firstValidation.retryTicks());
        assertTrue(firstValidation.logFailure());
        assertEquals(80, secondValidation.retryTicks());
        assertFalse(secondValidation.logFailure());
        assertEquals(List.of(40, 80, 160, 320, 600, 600, 600), writerDelays);
        verify(log, times(1)).error(validationContext, validationFailure);
        verify(log, times(1)).warn(writerContext);

        JigsawStudioService.AutosaveFailureDecision newIdentity =
                new JigsawStudioService.AutosaveFailureState().recordPersistentFailure();
        assertEquals(40, newIdentity.retryTicks());
        assertTrue(newIdentity.logFailure());
    }

    @Test
    public void siblingAutosaveResolvesTheCurrentBayAfterACommittedSaveReloadsLayout()
            throws ReflectiveOperationException, InterruptedException {
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(16, 16, 16);
        JigsawStudioVariant endVariant = planarVariant(
                "test/end",
                JigsawPlanarTopology.NORTH_END,
                true,
                true);
        JigsawStudioVariant straightVariant = planarVariant(
                "test/straight",
                JigsawPlanarTopology.NORTH_SOUTH_STRAIGHT,
                true,
                true);
        JigsawStudioVariantCatalog catalog = new JigsawStudioVariantCatalog(
                List.of(endVariant, straightVariant));
        JigsawStudioLayout initialLayout = JigsawStudioLayout.create(
                JigsawStudioMode.PLANAR_JIGSAW,
                dimensions,
                catalog);
        JigsawStudioRequest request = service.registry().activate(
                "overworld",
                "qa/profile_final",
                JigsawStudioMode.PLANAR_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                dimensions,
                StudioToolPacks.toolPack(),
                initialLayout);
        JigsawStudioSession session = service.registry().getSession(request.requestId());
        JigsawStudioBay initialEnd = initialLayout.get("workcell/end");
        JigsawStudioBay initialStraight = initialLayout.get("workcell/straight");
        JigsawStudioSession.DirtyIdentity endDirty = session.markWorkcellDirty(initialEnd.stableId())
                .identity()
                .orElseThrow();
        JigsawStudioSession.DirtyIdentity straightDirty = session.markWorkcellDirty(initialStraight.stableId())
                .identity()
                .orElseThrow();
        FakeStudioPack pack = new FakeStudioPack()
                .piece("test/end", new IrisJigsawPiece().setObject("test/end"))
                .piece("test/straight", new IrisJigsawPiece().setObject("test/straight"))
                .emptyObject("test/end", 16, 16, 16)
                .emptyObject("test/straight", 16, 16, 16);
        FakeToolWorld world = new FakeToolWorld("jigsaw");
        world.holdTasks = true;
        JigsawStudioGenerator generator = generatorOf(pack, request, session);
        ConcurrentHashMap<String, JigsawStudioService.BayPopulation> populations = new ConcurrentHashMap<>();
        populations.put(initialEnd.stableId(), readyPopulation());
        populations.put(initialStraight.stableId(), readyPopulation());
        JigsawStudioService.ActiveStudio studio = register(world, generator, populations);
        Method scheduleAutosave = JigsawStudioService.class.getDeclaredMethod(
                "scheduleAutosave",
                JigsawStudioService.ActiveStudio.class,
                JigsawStudioBay.class,
                JigsawStudioSession.DirtyIdentity.class,
                int.class);
        scheduleAutosave.setAccessible(true);

        scheduleAutosave.invoke(service, studio, initialEnd, endDirty, 1);
        scheduleAutosave.invoke(service, studio, initialStraight, straightDirty, 1);
        awaitTrue(() -> world.held.size() == 2);

        JigsawStudioSession.SaveIdentity committedEnd = session.beginSave(initialEnd.stableId())
                .identity()
                .orElseThrow();
        assertTrue(session.markWorkcellSaved(committedEnd));
        JigsawStudioLayout replacementLayout = JigsawStudioLayout.create(
                JigsawStudioMode.PLANAR_JIGSAW,
                dimensions,
                catalog);
        assertTrue(session.replaceLayout(replacementLayout));
        JigsawStudioBay currentStraight = JigsawStudioService.resolveCurrentAutosaveBay(
                session,
                initialStraight.stableId());
        assertNotSame(initialStraight, currentStraight);
        assertSame(replacementLayout.get(initialStraight.stableId()), currentStraight);
        assertTrue(session.isDirtyCurrent(straightDirty));

        world.holdTasks = true;
        world.runHeld();

        assertTrue(session.workcellSnapshot(initialStraight.stableId()).saveInProgress());
    }

    @Test
    public void saveNowRetainsAutosaveAcrossNotReadyInProgressAndSchedulerRejection()
            throws ReflectiveOperationException {
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(16, 16, 16);
        JigsawStudioLayout layout = JigsawStudioLayout.create(
                JigsawStudioMode.PLANAR_JIGSAW,
                dimensions,
                new JigsawStudioVariantCatalog(List.of(planarVariant(true, true))));
        JigsawStudioBay bay = layout.get("workcell/end");
        assertTrue(service.registry().tryBeginOpen(OWNER));
        JigsawStudioRequest request = service.registry().activate(
                "overworld",
                "village",
                JigsawStudioMode.PLANAR_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                dimensions,
                StudioToolPacks.toolPack(),
                layout,
                OWNER);
        service.registry().finishOpen(OWNER);
        JigsawStudioSession session = service.registry().getSession(request.requestId());
        JigsawStudioSession.DirtyIdentity identity = session.markWorkcellDirty(bay.stableId())
                .identity()
                .orElseThrow();
        FakeStudioPack pack = new FakeStudioPack()
                .piece("test/end", new IrisJigsawPiece().setObject("test/end"))
                .emptyObject("test/end", 16, 16, 16);
        FakeToolWorld world = new FakeToolWorld("jigsaw");
        world.schedulingFails = true;
        FakeToolPlayer player = new FakeToolPlayer(OWNER, "owner", world);
        JigsawStudioGenerator generator = generatorOf(pack, request, session);
        ConcurrentHashMap<String, JigsawStudioService.BayPopulation> populations = new ConcurrentHashMap<>();
        populations.put(
                bay.stableId(),
                new JigsawStudioService.BayPopulation(Set.of(0L), "workcell hydration failed"));
        JigsawStudioService.ActiveStudio studio = register(world, generator, populations);

        Class<?> keyType = Class.forName(JigsawStudioService.class.getName() + "$AutosaveKey");
        Constructor<?> keyConstructor = keyType.getDeclaredConstructor(UUID.class, String.class);
        keyConstructor.setAccessible(true);
        Object key = keyConstructor.newInstance(request.requestId(), bay.stableId());
        Class<?> ticketType = Class.forName(JigsawStudioService.class.getName() + "$AutosaveTicket");
        Constructor<?> ticketConstructor = ticketType.getDeclaredConstructor(
                keyType,
                JigsawStudioService.ActiveStudio.class,
                JigsawStudioSession.DirtyIdentity.class,
                AtomicBoolean.class,
                AtomicBoolean.class);
        ticketConstructor.setAccessible(true);
        Object ticket = ticketConstructor.newInstance(
                key,
                studio,
                identity,
                new AtomicBoolean(false),
                new AtomicBoolean(false));
        Field autosavesField = JigsawStudioService.class.getDeclaredField("autosaves");
        autosavesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Object, Object> autosaves = (Map<Object, Object>) autosavesField.get(service);
        autosaves.put(key, ticket);
        Method failureStateAccessor = ticketType.getDeclaredMethod("failureState");
        failureStateAccessor.setAccessible(true);
        JigsawStudioService.AutosaveFailureState failureState =
                (JigsawStudioService.AutosaveFailureState) failureStateAccessor.invoke(ticket);
        assertEquals(40, failureState.recordPersistentFailure().retryTicks());

        assertFalse(service.flushAutosave(player, bay.stableId()));
        assertEquals(1, autosaves.size());
        assertTrue(session.isDirtyCurrent(identity));

        populations.put(
                bay.stableId(),
                new JigsawStudioService.BayPopulation(Set.of(0L), ""));
        assertFalse(service.flushAutosave(player, bay.stableId()));
        assertEquals(1, autosaves.size());
        assertTrue(session.isDirtyCurrent(identity));

        populations.put(bay.stableId(), readyPopulation());
        JigsawStudioSession.SaveIdentity inProgress = session.beginSave(bay.stableId())
                .identity()
                .orElseThrow();
        assertFalse(service.flushAutosave(player, bay.stableId()));
        assertEquals(1, autosaves.size());
        assertTrue(session.abortSave(inProgress));
        Object retainedTicket = autosaves.values().iterator().next();
        assertSame(failureState, failureStateAccessor.invoke(retainedTicket));
        assertEquals(80, failureState.recordPersistentFailure().retryTicks());
    }

    @Test
    public void vanillaPortableRotationCannotBeDisabledFromTheControlMenu() {
        JigsawStudioVariant rotatable = planarVariant(true, true);
        JigsawStudioVariant fixed = planarVariant(false, true);
        JigsawStudioVariant readOnly = planarVariant(true, false);

        assertFalse(JigsawStudioService.canToggleVariantRotation(
                JigsawStudioCompatibilityTarget.VANILLA_PORTABLE, rotatable));
        assertTrue(JigsawStudioService.canToggleVariantRotation(
                JigsawStudioCompatibilityTarget.VANILLA_PORTABLE, fixed));
        assertTrue(JigsawStudioService.canToggleVariantRotation(
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED, rotatable));
        assertFalse(JigsawStudioService.canToggleVariantRotation(
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED, readOnly));
    }

    @Test
    public void planarSaveRequiresTheCanonicalWorkcellConnectorOrientation() throws IOException {
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(16, 16, 16);
        JigsawStudioLayout layout = JigsawStudioLayout.create(
                JigsawStudioMode.PLANAR_JIGSAW,
                dimensions,
                JigsawStudioVariantCatalog.empty());
        IrisJigsawConnector eastSource = connector().setDirection(JigsawDirection.EAST_POSITIVE_X);
        IrisJigsawConnector southDisplayed = connector().setDirection(JigsawDirection.SOUTH_POSITIVE_Z);

        JigsawStudioService.requireWorkcellTopology(
                layout.get("workcell/end"),
                List.of(eastSource),
                3);
        JigsawStudioService.WorkcellTopologyException wrongDirection = assertThrows(
                JigsawStudioService.WorkcellTopologyException.class,
                () -> JigsawStudioService.requireWorkcellTopology(
                        layout.get("workcell/end"),
                        List.of(southDisplayed),
                        0));
        assertTrue(wrongDirection.getMessage().contains("south end (1 horizontal connector)"));
        assertTrue(wrongDirection.getMessage().contains("Reset Connector Blocks"));

        JigsawStudioService.WorkcellTopologyException missingTee = assertThrows(
                JigsawStudioService.WorkcellTopologyException.class,
                () -> JigsawStudioService.requireWorkcellTopology(
                        layout.get("workcell/tee"),
                        List.of(),
                        0));
        assertTrue(missingTee.getMessage().contains("north east west tee (3 horizontal connectors)"));
        assertTrue(missingTee.getMessage().contains("blank (0 horizontal connectors)"));
    }

    @Test
    public void rotatedConnectorFinalStatesReturnToStoredOrientation() throws IOException {
        JigsawStudioBounds bounds = new JigsawStudioBounds(
                0,
                64,
                0,
                new JigsawStudioCellDimensions(3, 1, 3));
        List<JigsawStudioService.ChunkCaptureArea> areas = JigsawStudioService.chunkIntersections(bounds);
        BlockState sourceState = BlockState.parse("minecraft:observer[facing=north]");
        for (int quarterTurns = 1; quarterTurns <= 3; quarterTurns++) {
            PieceTurn displayRotation = PieceTurn.aroundY(-90 * quarterTurns);
            BlockState displayedState = FakeBlockRegistry.rotated(sourceState, displayRotation.blockTurn());
            JigsawDirection displayedDirection = JigsawDirection.NORTH_NEGATIVE_Z.turnedBy(displayRotation.quarterTurns());
            JigsawStudioService.CapturedConnector connector = new JigsawStudioService.CapturedConnector(
                    1,
                    0,
                    1,
                    displayedDirection,
                    JigsawDirection.UP_POSITIVE_Y,
                    "test/pool",
                    "door",
                    "door",
                    "",
                    JigsawJoint.ALIGNED,
                    displayedState.key(),
                    -3,
                    7);
            JigsawStudioService.ChunkSnapshot snapshot = new JigsawStudioService.ChunkSnapshot(
                    areas.getFirst(),
                    List.of(),
                    List.of(connector));

            JigsawStudioService.Capture capture = JigsawStudioService.aggregateSnapshots(
                    registry,
                    codec,
                    bounds,
                    areas,
                    List.of(snapshot),
                    quarterTurns);

            assertEquals(sourceState.key(), capture.connectors().getFirst().getFinalState());
            assertEquals(-3, capture.connectors().getFirst().getSelectionPriority());
            assertEquals(7, capture.connectors().getFirst().getPlacementPriority());
        }
    }

    @Test
    public void hiddenConnectorCapturesExactBlockStateTilePayloadAndMetadata() throws Exception {
        JigsawStudioBounds bounds = new JigsawStudioBounds(
                0,
                64,
                0,
                new JigsawStudioCellDimensions(1, 1, 1));
        IrisJigsawConnector connector = connector()
                .setChannel("gate/owned")
                .setSelectionPriority(-7)
                .setPlacementPriority(11);
        IrisJigsawPiece piece = new IrisJigsawPiece().setConnectors(new KList<>());
        piece.getConnectors().add(connector);
        BlockState chest = BlockState.parse("minecraft:chest[facing=east]");
        BlockEntityData tile = tile(1);
        FakeToolWorld world = new FakeToolWorld("jigsaw");
        world.setBlock(new BlockPosition(0, 64, 0), chest, Optional.of(tile));
        JigsawStudioService.ChunkCaptureArea area = JigsawStudioService.chunkIntersections(bounds).getFirst();

        JigsawStudioService.ChunkSnapshot snapshot = JigsawStudioService.captureChunkIntersection(
                world,
                registry,
                bounds,
                piece,
                new JigsawStudioService.SourceObject(1, 1, 1, Map.of()),
                area,
                0,
                false);
        JigsawStudioService.Capture capture = JigsawStudioService.aggregateSnapshots(
                registry,
                codec,
                bounds,
                List.of(area),
                List.of(snapshot));
        GeneratedObject restored = codec.decode(capture.objectContent());
        IrisJigsawConnector captured = capture.connectors().getFirst();

        assertEquals(chest.key(), captured.getFinalState());
        assertEquals("gate/owned", captured.getChannel());
        assertEquals(-7, captured.getSelectionPriority());
        assertEquals(11, captured.getPlacementPriority());
        assertEquals(Optional.of(tile), restored.blocks().getFirst().blockEntity());
        assertTrue(capture.hasBlockEntities());
    }

    @Test
    public void noOpTeeAndCrossCapturePreservesCreatorOrderForSeededAssembly() throws IOException {
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(16, 16, 16);
        List<List<JigsawDirection>> authoredOrders = List.of(
                List.of(
                        JigsawDirection.NORTH_NEGATIVE_Z,
                        JigsawDirection.EAST_POSITIVE_X,
                        JigsawDirection.WEST_NEGATIVE_X),
                List.of(
                        JigsawDirection.NORTH_NEGATIVE_Z,
                        JigsawDirection.EAST_POSITIVE_X,
                        JigsawDirection.SOUTH_POSITIVE_Z,
                        JigsawDirection.WEST_NEGATIVE_X));
        List<List<JigsawDirection>> captureOrders = List.of(
                List.of(
                        JigsawDirection.WEST_NEGATIVE_X,
                        JigsawDirection.NORTH_NEGATIVE_Z,
                        JigsawDirection.EAST_POSITIVE_X),
                List.of(
                        JigsawDirection.WEST_NEGATIVE_X,
                        JigsawDirection.NORTH_NEGATIVE_Z,
                        JigsawDirection.SOUTH_POSITIVE_Z,
                        JigsawDirection.EAST_POSITIVE_X));

        for (int index = 0; index < authoredOrders.size(); index++) {
            IrisJigsawPiece source = pieceWithPlanarConnectors(dimensions, authoredOrders.get(index));
            List<IrisJigsawConnector> captured = planarConnectors(dimensions, captureOrders.get(index));

            List<IrisJigsawConnector> ordered = JigsawStudioService.preserveCapturedConnectorOrder(
                    source,
                    captured);

            assertEquals(authoredOrders.get(index), connectorDirections(ordered));
        }
    }

    @Test
    public void connectorMetadataEditsAtTheSamePositionKeepAuthoredOrder() throws IOException {
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(16, 16, 16);
        IrisJigsawPiece source = pieceWithPlanarConnectors(dimensions, List.of(
                JigsawDirection.NORTH_NEGATIVE_Z,
                JigsawDirection.EAST_POSITIVE_X));
        IrisJigsawConnector capturedNorth = planarConnector(
                dimensions,
                JigsawDirection.NORTH_NEGATIVE_Z)
                .setPool("changed/pool")
                .setName("changed:name")
                .setTargetName("changed:target")
                .setChannel("changed-channel")
                .setJoint(JigsawJoint.ROLLABLE)
                .setFinalState("minecraft:stone")
                .setSelectionPriority(9)
                .setPlacementPriority(-4);
        IrisJigsawConnector capturedEast = planarConnector(
                dimensions,
                JigsawDirection.EAST_POSITIVE_X);

        List<IrisJigsawConnector> ordered = JigsawStudioService.preserveCapturedConnectorOrder(
                source,
                List.of(capturedEast, capturedNorth));

        assertSame(capturedNorth, ordered.getFirst());
        assertSame(capturedEast, ordered.getLast());
        assertEquals("changed/pool", ordered.getFirst().getPool());
        assertEquals(9, ordered.getFirst().getSelectionPriority());
    }

    @Test
    public void removedConnectorsDisappearWithoutReorderingSurvivors() throws IOException {
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(16, 16, 16);
        IrisJigsawPiece source = pieceWithPlanarConnectors(dimensions, List.of(
                JigsawDirection.NORTH_NEGATIVE_Z,
                JigsawDirection.EAST_POSITIVE_X,
                JigsawDirection.WEST_NEGATIVE_X));
        IrisJigsawConnector capturedWest = planarConnector(
                dimensions,
                JigsawDirection.WEST_NEGATIVE_X);
        IrisJigsawConnector capturedNorth = planarConnector(
                dimensions,
                JigsawDirection.NORTH_NEGATIVE_Z);

        List<IrisJigsawConnector> ordered = JigsawStudioService.preserveCapturedConnectorOrder(
                source,
                List.of(capturedWest, capturedNorth));

        assertEquals(List.of(
                JigsawDirection.NORTH_NEGATIVE_Z,
                JigsawDirection.WEST_NEGATIVE_X), connectorDirections(ordered));
    }

    @Test
    public void newAndMovedConnectorsAppendInDeterministicSourcePositionOrder() throws IOException {
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(16, 16, 16);
        IrisJigsawPiece source = pieceWithPlanarConnectors(
                dimensions,
                List.of(JigsawDirection.NORTH_NEGATIVE_Z));
        IrisJigsawConnector capturedNorth = planarConnector(
                dimensions,
                JigsawDirection.NORTH_NEGATIVE_Z);
        IrisJigsawConnector highY = connectorAt(2, 5, 9);
        IrisJigsawConnector highZ = connectorAt(2, 3, 10);
        IrisJigsawConnector lowZ = connectorAt(2, 3, 4);

        List<IrisJigsawConnector> ordered = JigsawStudioService.preserveCapturedConnectorOrder(
                source,
                List.of(highY, capturedNorth, highZ, lowZ));

        assertSame(capturedNorth, ordered.get(0));
        assertSame(lowZ, ordered.get(1));
        assertSame(highZ, ordered.get(2));
        assertSame(highY, ordered.get(3));
    }

    @Test
    public void duplicateSourceOrCapturedConnectorPositionsAreRejected() {
        IrisJigsawConnector first = connectorAt(1, 2, 3);
        IrisJigsawConnector second = connectorAt(1, 2, 3)
                .setDirection(JigsawDirection.SOUTH_POSITIVE_Z);
        IrisJigsawPiece duplicateSource = new IrisJigsawPiece().setConnectors(new KList<>());
        duplicateSource.getConnectors().add(first);
        duplicateSource.getConnectors().add(second);
        IrisJigsawPiece emptySource = new IrisJigsawPiece().setConnectors(new KList<>());

        assertThrows(IOException.class, () -> JigsawStudioService.preserveCapturedConnectorOrder(
                duplicateSource,
                List.of(first)));
        assertThrows(IOException.class, () -> JigsawStudioService.preserveCapturedConnectorOrder(
                emptySource,
                List.of(first, second)));
    }

    @Test
    public void rotatedCapturesMatchAuthoredOrderByInverseSourcePosition() throws IOException {
        JigsawStudioBounds bounds = new JigsawStudioBounds(
                0,
                64,
                0,
                new JigsawStudioCellDimensions(5, 1, 3));
        List<JigsawStudioService.ChunkCaptureArea> areas = JigsawStudioService.chunkIntersections(bounds);
        IrisJigsawConnector sourceFirst = connectorAt(0, 0, 1)
                .setDirection(JigsawDirection.NORTH_NEGATIVE_Z);
        IrisJigsawConnector sourceSecond = connectorAt(2, 0, 2)
                .setDirection(JigsawDirection.EAST_POSITIVE_X);
        IrisJigsawPiece source = new IrisJigsawPiece().setConnectors(new KList<>());
        source.getConnectors().add(sourceFirst);
        source.getConnectors().add(sourceSecond);
        BlockState sourceState = BlockState.parse("minecraft:observer[facing=north]");

        for (int quarterTurns = 1; quarterTurns <= 3; quarterTurns++) {
            PieceTurn displayRotation = PieceTurn.aroundY(-90 * quarterTurns);
            BlockState displayedState = FakeBlockRegistry.rotated(sourceState, displayRotation.blockTurn());
            JigsawStudioService.CapturedConnector displayedFirst = displayedConnector(
                    sourceFirst,
                    bounds.dimensions(),
                    quarterTurns,
                    displayRotation,
                    displayedState.key());
            JigsawStudioService.CapturedConnector displayedSecond = displayedConnector(
                    sourceSecond,
                    bounds.dimensions(),
                    quarterTurns,
                    displayRotation,
                    displayedState.key());
            JigsawStudioService.ChunkSnapshot snapshot = new JigsawStudioService.ChunkSnapshot(
                    areas.getFirst(),
                    List.of(),
                    List.of(displayedSecond, displayedFirst));

            JigsawStudioService.Capture capture = JigsawStudioService.aggregateSnapshots(
                    registry,
                    codec,
                    bounds,
                    areas,
                    List.of(snapshot),
                    quarterTurns);
            List<IrisJigsawConnector> ordered = JigsawStudioService.preserveCapturedConnectorOrder(
                    source,
                    capture.connectors());

            assertEquals(sourceFirst.getPosition(), ordered.getFirst().getPosition());
            assertEquals(sourceSecond.getPosition(), ordered.getLast().getPosition());
        }
    }

    @Test
    public void rotatedBlockEntityPayloadReturnsToItsStoredCoordinateUnchanged() throws IOException {
        int width = 5;
        int height = 2;
        int depth = 3;
        JigsawStudioBounds bounds = new JigsawStudioBounds(
                0,
                64,
                0,
                new JigsawStudioCellDimensions(width, height, depth));
        List<JigsawStudioService.ChunkCaptureArea> areas = JigsawStudioService.chunkIntersections(bounds);
        BlockEntityData tile = tile(2);
        int[][] expectedPositions = {
                {0, 0, 0},
                {1, 1, 3},
                {3, 1, 1},
                {1, 1, 1}
        };
        for (int quarterTurns = 1; quarterTurns <= 3; quarterTurns++) {
            BlockState displayedState = BlockState.parse("minecraft:chest[facing=east]");
            JigsawStudioService.CapturedBlock capturedBlock = new JigsawStudioService.CapturedBlock(
                    1,
                    1,
                    1,
                    displayedState,
                    tile);
            JigsawStudioService.ChunkSnapshot snapshot = new JigsawStudioService.ChunkSnapshot(
                    areas.getFirst(),
                    List.of(capturedBlock),
                    List.of());

            JigsawStudioService.Capture capture = JigsawStudioService.aggregateSnapshots(
                    registry,
                    codec,
                    bounds,
                    areas,
                    List.of(snapshot),
                    quarterTurns);
            GeneratedObject restored = codec.decode(capture.objectContent());
            int sourceWidth = (quarterTurns & 1) == 0 ? width : depth;
            int sourceDepth = (quarterTurns & 1) == 0 ? depth : width;
            int[] expected = expectedPositions[quarterTurns];
            BlockPosition storedAt = new BlockPosition(
                    expected[0] - sourceWidth / 2,
                    expected[1] - height / 2,
                    expected[2] - sourceDepth / 2);
            ObjectBlock restoredBlock = restored.blocks().stream()
                    .filter(block -> block.position().equals(storedAt))
                    .findFirst()
                    .orElse(null);

            assertTrue(capture.hasBlockEntities());
            assertNotNull(restoredBlock);
            assertEquals(Optional.of(tile), restoredBlock.blockEntity());
        }
    }

    @Test
    public void rotatedTilePayloadDoesNotBlockMaterialization() {
        BlockState state = BlockState.parse("minecraft:chest[facing=north]");
        JigsawStudioGenerator.RenderedBay rendered = JigsawStudioGenerator.RenderedBay.valid(
                new JigsawStudioCellDimensions(3, 2, 5),
                List.of(new JigsawStudioGenerator.RenderedBlock(0, 0, 0, state, tile(3))),
                List.of());

        assertEquals("", JigsawStudioService.validateMaterialization(rendered, registry));
    }

    @Test
    public void explicitAirFinalStateRemainsInTheCapturedObject() throws Exception {
        JigsawStudioBounds bounds = new JigsawStudioBounds(
                0,
                64,
                0,
                new JigsawStudioCellDimensions(2, 1, 1));
        JigsawStudioService.ChunkCaptureArea area = JigsawStudioService.chunkIntersections(bounds).getFirst();
        FakeToolWorld world = new FakeToolWorld("jigsaw");
        IrisJigsawConnector airMarker = connectorAt(0, 0, 0).setName("iris:air").setFinalState("minecraft:air");
        IrisJigsawConnector voidMarker = connectorAt(1, 0, 0).setName("iris:void").setFinalState("minecraft:structure_void");
        IrisJigsawPiece piece = new IrisJigsawPiece().setConnectors(new KList<>());
        piece.getConnectors().add(airMarker);
        piece.getConnectors().add(voidMarker);
        world.setBlock(new BlockPosition(0, 64, 0), BlockState.parse("minecraft:jigsaw[orientation=north_up]"),
                Optional.of(JigsawStudioTileCodec.encode("minecraft:jigsaw", JigsawStudioService.markerNbt(airMarker))));
        world.setBlock(new BlockPosition(1, 64, 0), BlockState.parse("minecraft:jigsaw[orientation=north_up]"),
                Optional.of(JigsawStudioTileCodec.encode("minecraft:jigsaw", JigsawStudioService.markerNbt(voidMarker))));

        JigsawStudioService.ChunkSnapshot snapshot = JigsawStudioService.captureChunkIntersection(
                world,
                registry,
                bounds,
                piece,
                new JigsawStudioService.SourceObject(2, 1, 1, Map.of()),
                area,
                0,
                true);
        JigsawStudioService.Capture capture = JigsawStudioService.aggregateSnapshots(
                registry,
                codec,
                bounds,
                List.of(area),
                List.of(snapshot));
        GeneratedObject restored = codec.decode(capture.objectContent());

        assertEquals(2, snapshot.connectors().size());
        assertEquals(1, restored.blocks().size());
        assertEquals("minecraft:air", restored.blocks().getFirst().state().key());
    }

    @Test
    public void originalExplicitAirOutsideAConnectorSurvivesWhileNewAirRemainsAbsent() {
        BlockState air = BlockState.parse("minecraft:air");
        BlockState structureVoid = BlockState.parse("minecraft:structure_void");
        JigsawStudioService.SourceObject source = new JigsawStudioService.SourceObject(
                2, 2, 2, Map.of(new JigsawStudioService.LocalPosition(0, 0, 0), air));

        assertEquals(air, JigsawStudioService.retainedSourceAir(source, 0, 0, 0, air));
        assertNull(JigsawStudioService.retainedSourceAir(source, 1, 0, 0, air));
        assertNull(JigsawStudioService.retainedSourceAir(source, 0, 0, 0, structureVoid));
    }

    @Test
    public void markerHydrationUsesValidIdentifiersAndCaptureRestoresIrisKeysAndChannels() {
        IrisJigsawConnector source = connector()
                .setPool("fort/start")
                .setName("door")
                .setTargetName("door")
                .setChannel("castle/door")
                .setSelectionPriority(-4)
                .setPlacementPriority(17);
        IrisJigsawPiece piece = new IrisJigsawPiece().setConnectors(new KList<>());
        piece.getConnectors().add(source);

        KMap<String, Object> nbt = JigsawStudioService.markerNbt(source);
        assertEquals("iris:fort/start", nbt.get("pool"));
        assertEquals("iris:door", nbt.get("name"));
        assertEquals(-4, nbt.get("selection_priority"));
        assertEquals(17, nbt.get("placement_priority"));
        assertFalse(nbt.containsKey("channel"));

        IrisJigsawConnector captured = connector()
                .setPool("fort/start")
                .setName("iris:door")
                .setTargetName("iris:door");
        JigsawStudioService.restoreCapturedMetadata(captured, piece);

        assertEquals("fort/start", captured.getPool());
        assertEquals("door", captured.getName());
        assertEquals("door", captured.getTargetName());
        assertEquals("castle/door", captured.getChannel());
    }

    @Test
    public void authoredBayReadinessRequiresEveryChunkToPopulateAndHydrate() {
        JigsawStudioService.BayPopulation population = new JigsawStudioService.BayPopulation(
                Set.of(11L, 12L), "");

        assertFalse(population.readiness().ready());
        assertTrue(population.markGenerated(11L));
        assertTrue(population.needsApplication(11L));
        population.markApplied(11L);
        assertTrue(population.needsVerification(11L));
        population.markHydrated(11L);
        assertFalse(population.readiness().ready());

        assertTrue(population.markGenerated(12L));
        population.markApplied(12L);
        population.markHydrated(12L);

        JigsawStudioService.BayReadiness readiness = population.readiness();
        assertTrue(readiness.ready());
        assertEquals(2, readiness.requiredChunks());
        assertEquals(2, readiness.generatedChunks());
        assertEquals(2, readiness.hydratedChunks());
    }

    @Test
    public void chunkLoadRecoversAGenerationSignalMissedDuringStudioRegistration() {
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(16, 16, 16);
        JigsawStudioLayout layout = JigsawStudioLayout.create(
                JigsawStudioMode.PLANAR_JIGSAW,
                dimensions,
                new JigsawStudioVariantCatalog(List.of(planarVariant(true, true))));
        JigsawStudioBay blank = layout.get("workcell/blank");
        long chunkKey = ((long) 1 << 32) ^ 1L;
        JigsawStudioService.BayPopulation population =
                new JigsawStudioService.BayPopulation(Set.of(chunkKey), "");
        ConcurrentHashMap<String, JigsawStudioService.BayPopulation> populations = new ConcurrentHashMap<>();
        populations.put(blank.stableId(), population);
        JigsawStudioRequest request = requestOf(UUID.randomUUID(), JigsawStudioMode.PLANAR_JIGSAW, dimensions);
        FakeToolWorld world = new FakeToolWorld("jigsaw");
        world.holdTasks = true;
        JigsawStudioGenerator generator = mock(JigsawStudioGenerator.class);
        when(generator.getLayout()).thenReturn(layout);
        when(generator.getRequest()).thenReturn(request);
        when(generator.renderBay(any(JigsawStudioBay.class)))
                .thenReturn(JigsawStudioGenerator.RenderedBay.empty(dimensions));
        register(world, generator, populations);

        service.chunkLoaded(request.requestId(), 1, 1);

        assertTrue(population.needsApplication(chunkKey));
        assertEquals(1, population.readiness().generatedChunks());
        assertEquals(0, population.readiness().hydratedChunks());
    }

    @Test
    public void hydrationFailurePermanentlyBlocksSaveReadiness() {
        JigsawStudioService.BayPopulation population = new JigsawStudioService.BayPopulation(
                Set.of(27L), "");
        population.markGenerated(27L);
        population.markApplied(27L);
        population.markHydrated(27L);

        assertTrue(population.readiness().ready());
        assertTrue(population.fail("tile NBT was not preserved"));
        assertFalse(population.readiness().ready());
        assertEquals("tile NBT was not preserved", population.readiness().failure());
        assertFalse(population.fail("replacement failure"));
    }

    @Test
    public void chunkIntersectionsCoverNegativeAndPositiveCoordinatesExactlyOnce() {
        JigsawStudioBounds bounds = new JigsawStudioBounds(
                -5,
                64,
                9,
                new JigsawStudioCellDimensions(25, 3, 20));

        List<JigsawStudioService.ChunkCaptureArea> areas = JigsawStudioService.chunkIntersections(bounds);

        assertEquals(List.of(
                new JigsawStudioService.ChunkCaptureArea(-1, 0, 0, 5, 0, 7),
                new JigsawStudioService.ChunkCaptureArea(-1, 1, 0, 5, 7, 20),
                new JigsawStudioService.ChunkCaptureArea(0, 0, 5, 21, 0, 7),
                new JigsawStudioService.ChunkCaptureArea(0, 1, 5, 21, 7, 20),
                new JigsawStudioService.ChunkCaptureArea(1, 0, 21, 25, 0, 7),
                new JigsawStudioService.ChunkCaptureArea(1, 1, 21, 25, 7, 20)
        ), areas);
        for (int x = 0; x < bounds.dimensions().width(); x++) {
            for (int z = 0; z < bounds.dimensions().depth(); z++) {
                int matches = 0;
                for (JigsawStudioService.ChunkCaptureArea area : areas) {
                    if (area.contains(x, z)) {
                        matches++;
                    }
                }
                assertEquals(1, matches);
            }
        }
    }

    @Test
    public void chunkSnapshotsAggregateDeterministicallyAndRequireEveryIntersection() throws IOException {
        JigsawStudioBounds bounds = new JigsawStudioBounds(
                8,
                64,
                0,
                new JigsawStudioCellDimensions(20, 2, 1));
        List<JigsawStudioService.ChunkCaptureArea> areas = JigsawStudioService.chunkIntersections(bounds);
        BlockState stone = BlockState.parse("minecraft:stone");
        JigsawStudioService.ChunkSnapshot first = new JigsawStudioService.ChunkSnapshot(
                areas.getFirst(),
                List.of(new JigsawStudioService.CapturedBlock(0, 0, 0, stone, null)),
                List.of());
        JigsawStudioService.ChunkSnapshot second = new JigsawStudioService.ChunkSnapshot(
                areas.getLast(),
                List.of(new JigsawStudioService.CapturedBlock(8, 1, 0, stone, null)),
                List.of());

        JigsawStudioService.Capture forward = JigsawStudioService.aggregateSnapshots(
                registry, codec, bounds, areas, List.of(first, second));
        JigsawStudioService.Capture reverse = JigsawStudioService.aggregateSnapshots(
                registry, codec, bounds, areas, List.of(second, first));

        assertArrayEquals(forward.objectContent(), reverse.objectContent());
        assertFalse(forward.hasBlockEntities());
        assertTrue(forward.connectors().isEmpty());
        assertThrows(IOException.class,
                () -> JigsawStudioService.aggregateSnapshots(registry, codec, bounds, areas, List.of(first)));
    }

    @Test
    public void saveRegistrationIsRequestScoped() {
        UUID firstRequest = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID secondRequest = UUID.fromString("55555555-5555-5555-5555-555555555555");

        assertEquals(JigsawStudioService.SaveStart.STARTED, service.tryBeginSave(firstRequest));
        assertEquals(JigsawStudioService.SaveStart.IN_PROGRESS, service.tryBeginSave(firstRequest));
        assertEquals(JigsawStudioService.SaveStart.STARTED, service.tryBeginSave(secondRequest));

        service.finishSave(firstRequest);
        assertEquals(JigsawStudioService.SaveStart.STARTED, service.tryBeginSave(firstRequest));
        service.finishSave(firstRequest);
        service.finishSave(secondRequest);
    }

    private JigsawStudioService.ActiveStudio register(
            FakeToolWorld world,
            JigsawStudioGenerator generator,
            ConcurrentHashMap<String, JigsawStudioService.BayPopulation> populations
    ) {
        UUID studioId = generator.getRequest().requestId();
        JigsawStudioService.ActiveStudio studio = new JigsawStudioService.ActiveStudio(
                studioId,
                world,
                generator,
                populations,
                ConcurrentHashMap.newKeySet(),
                new AtomicLong());
        try {
            Field studiosField = JigsawStudioService.class.getDeclaredField("studios");
            studiosField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<UUID, JigsawStudioService.ActiveStudio> studios =
                    (Map<UUID, JigsawStudioService.ActiveStudio>) studiosField.get(service);
            studios.put(studioId, studio);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
        return studio;
    }

    private static JigsawStudioGenerator generatorOf(
            FakeStudioPack pack,
            JigsawStudioRequest request,
            JigsawStudioSession session
    ) {
        JigsawStudioGenerator generator = mock(JigsawStudioGenerator.class);
        when(generator.getRequest()).thenReturn(request);
        when(generator.getSession()).thenReturn(session);
        when(generator.getLayout()).thenAnswer(invocation -> session.layout());
        when(generator.getPack()).thenReturn(pack.pack);
        when(generator.renderBay(any(JigsawStudioBay.class))).thenAnswer(invocation ->
                JigsawStudioGenerator.RenderedBay.empty(request.cellDimensions()));
        return generator;
    }

    private static JigsawStudioRequest requestOf(UUID id, JigsawStudioMode mode, JigsawStudioCellDimensions dimensions) {
        return new JigsawStudioRequest(
                id,
                "overworld",
                "qa/profile_final",
                mode,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                dimensions,
                mock(com.volmit.tooling.ToolPack.class),
                java.nio.file.Path.of("pack"),
                null);
    }

    private static JigsawStudioService.BayPopulation readyPopulation() {
        JigsawStudioService.BayPopulation ready = new JigsawStudioService.BayPopulation(Set.of(0L), "");
        ready.markFullyReady();
        return ready;
    }

    private static BlockEntityData tile(int seed) {
        return new BlockEntityData(TILE_FORMAT, 1, new byte[]{(byte) seed, 2, 3});
    }

    private static void awaitTrue(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Timed out waiting for the studio scheduler");
            }
            Thread.sleep(10L);
        }
    }

    private static IrisJigsawConnector connector() {
        return new IrisJigsawConnector()
                .setPosition(new BlockPosition(0, 0, 0))
                .setDirection(JigsawDirection.NORTH_NEGATIVE_Z)
                .setTop(JigsawDirection.UP_POSITIVE_Y)
                .setPool("fort/start")
                .setName("iris:door")
                .setTargetName("iris:door")
                .setJoint(JigsawJoint.ALIGNED)
                .setFinalState("minecraft:air");
    }

    private static IrisJigsawConnector connectorAt(int x, int y, int z) {
        return connector().setPosition(new BlockPosition(x, y, z));
    }

    private static IrisJigsawConnector planarConnector(
            JigsawStudioCellDimensions dimensions,
            JigsawDirection direction
    ) {
        BlockPosition size = new BlockPosition(
                dimensions.width(),
                dimensions.height(),
                dimensions.depth());
        return connector()
                .setPosition(IrisJigsawConnector.canonicalPlanarPosition(size, direction))
                .setDirection(direction);
    }

    private static List<IrisJigsawConnector> planarConnectors(
            JigsawStudioCellDimensions dimensions,
            List<JigsawDirection> directions
    ) {
        List<IrisJigsawConnector> connectors = new ArrayList<>(directions.size());
        for (JigsawDirection direction : directions) {
            connectors.add(planarConnector(dimensions, direction));
        }
        return connectors;
    }

    private static IrisJigsawPiece pieceWithPlanarConnectors(
            JigsawStudioCellDimensions dimensions,
            List<JigsawDirection> directions
    ) {
        IrisJigsawPiece piece = new IrisJigsawPiece().setConnectors(new KList<>());
        piece.getConnectors().addAll(planarConnectors(dimensions, directions));
        return piece;
    }

    private static List<JigsawDirection> connectorDirections(List<IrisJigsawConnector> connectors) {
        List<JigsawDirection> directions = new ArrayList<>(connectors.size());
        for (IrisJigsawConnector connector : connectors) {
            directions.add(connector.getDirection());
        }
        return directions;
    }

    private static JigsawStudioService.CapturedConnector displayedConnector(
            IrisJigsawConnector source,
            JigsawStudioCellDimensions displayDimensions,
            int displayRotationQuarterTurns,
            PieceTurn displayRotation,
            String finalState
    ) {
        BlockPosition displayedPosition = displayedPosition(
                source.getPosition(),
                displayDimensions,
                displayRotationQuarterTurns);
        return new JigsawStudioService.CapturedConnector(
                displayedPosition.x(),
                displayedPosition.y(),
                displayedPosition.z(),
                source.getDirection().turnedBy(displayRotation.quarterTurns()),
                source.getTop().turnedBy(displayRotation.quarterTurns()),
                source.getPool(),
                source.getName(),
                source.getTargetName(),
                source.getChannel(),
                source.getJoint(),
                finalState,
                source.getSelectionPriority(),
                source.getPlacementPriority());
    }

    private static BlockPosition displayedPosition(
            BlockPosition source,
            JigsawStudioCellDimensions displayDimensions,
            int displayRotationQuarterTurns
    ) {
        int quarterTurns = Math.floorMod(displayRotationQuarterTurns, 4);
        int sourceWidth = (quarterTurns & 1) == 0
                ? displayDimensions.width()
                : displayDimensions.depth();
        int sourceDepth = (quarterTurns & 1) == 0
                ? displayDimensions.depth()
                : displayDimensions.width();
        return switch (quarterTurns) {
            case 0 -> source;
            case 1 -> new BlockPosition(sourceDepth - 1 - source.z(), source.y(), source.x());
            case 2 -> new BlockPosition(
                    sourceWidth - 1 - source.x(),
                    source.y(),
                    sourceDepth - 1 - source.z());
            case 3 -> new BlockPosition(source.z(), source.y(), sourceWidth - 1 - source.x());
            default -> throw new IllegalStateException("Unreachable Jigsaw Studio test rotation");
        };
    }

    private static JigsawStudioVariant planarVariant(boolean rotatable, boolean owned) {
        return planarVariant(
                "test/end",
                JigsawPlanarTopology.NORTH_END,
                rotatable,
                owned);
    }

    private static JigsawStudioVariant planarVariant(
            String pieceKey,
            JigsawPlanarTopology topology,
            boolean rotatable,
            boolean owned
    ) {
        return new JigsawStudioVariant(
                pieceKey,
                pieceKey,
                "",
                Optional.of(new JigsawStudioCellDimensions(16, 16, 16)),
                JigsawStudioMode.PLANAR_JIGSAW,
                Optional.of(topology),
                rotatable,
                owned,
                List.of(),
                new JigsawStudioPieceRules(0, 30, 0, 0, false),
                List.of());
    }
}
