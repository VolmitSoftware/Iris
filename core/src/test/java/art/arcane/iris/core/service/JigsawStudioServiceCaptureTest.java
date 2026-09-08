package art.arcane.iris.core.service;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBounds;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioActivation;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBay;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBayKind;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCompatibilityTarget;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioLayout;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioMode;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioPieceRules;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioPoolMembership;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioSession;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariant;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariantCatalog;
import art.arcane.iris.core.runtime.jigsaw.JigsawPlanarTopology;
import art.arcane.iris.core.runtime.jigsaw.JigsawPlanarArchetype;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioWorkcellSpec;
import art.arcane.iris.core.structure.authoring.StructureWriteResult;
import art.arcane.iris.engine.object.IrisDirection;
import art.arcane.iris.engine.object.IrisJigsawConnector;
import art.arcane.iris.engine.object.IrisJigsawPiece;
import art.arcane.iris.engine.object.IrisObject;
import art.arcane.iris.engine.object.IrisObjectRotation;
import art.arcane.iris.engine.object.IrisPosition;
import art.arcane.iris.engine.object.JigsawJoint;
import art.arcane.iris.engine.object.TileData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.platform.studio.generators.JigsawStudioGenerator;
import art.arcane.iris.platform.bukkit.BukkitBlockState;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.common.math.IrisBlockVector;
import art.arcane.iris.util.common.plugin.VolmitPlugin;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BrewingStartEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.BrewingStandFuelEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JigsawStudioServiceCaptureTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();


    @Test
    public void shutdownQuiesceIsIdempotentAndResetsOnEnable() throws Exception {
        JigsawStudioService service = new JigsawStudioService();
        VolmitPlugin plugin = mock(VolmitPlugin.class);
        Field disableStartedField = JigsawStudioService.class.getDeclaredField("disableStarted");
        Field enabledField = JigsawStudioService.class.getDeclaredField("enabled");
        disableStartedField.setAccessible(true);
        enabledField.setAccessible(true);
        AtomicBoolean disableStarted = (AtomicBoolean) disableStartedField.get(service);

        try (MockedStatic<BukkitPlatform> platform = mockStatic(BukkitPlatform.class)) {
            platform.when(BukkitPlatform::volmitPlugin).thenReturn(plugin);

            service.onEnable();
            assertFalse(disableStarted.get());
            assertTrue(enabledField.getBoolean(service));

            service.quiesceForServerShutdown();
            service.quiesceForServerShutdown();
            assertTrue(disableStarted.get());
            assertFalse(enabledField.getBoolean(service));

            service.onEnable();
            assertFalse(disableStarted.get());
            assertTrue(enabledField.getBoolean(service));
            service.onDisable();
        }
    }

    @Test
    public void successfulStudioSavePlaysOneOwnerLocalBell() {
        Player player = mock(Player.class);
        Location location = mock(Location.class);
        when(player.getLocation()).thenReturn(location);

        try (MockedStatic<J> scheduling = mockStatic(J.class)) {
            scheduling.when(() -> J.runEntity(same(player), any(Runnable.class))).thenAnswer(invocation -> {
                invocation.getArgument(1, Runnable.class).run();
                return true;
            });

            JigsawStudioService.playSaveSound(player);

            verify(player).playSound(location, "minecraft:block.note_block.bell", 0.65F, 1.65F);
        }
    }

    @Test
    public void hiddenConnectorResetRestoresOnlyItsSavedOrdinaryBlock() throws Exception {
        World world = mock(World.class);
        Block target = mock(Block.class);
        BlockData blockData = mock(BlockData.class);
        PlatformBlockState state = mock(PlatformBlockState.class);
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(15, 15, 15);
        JigsawStudioBay workcell = new JigsawStudioBay(
                "spatial",
                JigsawStudioBayKind.SPATIAL_WORKCELL,
                Optional.empty(),
                "",
                new JigsawStudioBounds(10, 20, 30, dimensions));
        IrisJigsawConnector connector = connectorAt(1, 2, 3)
                .setFinalState("minecraft:stone");
        JigsawStudioGenerator.RenderedConnector renderedConnector =
                new JigsawStudioGenerator.RenderedConnector(
                        1,
                        2,
                        3,
                        connector,
                        "north_up");
        JigsawStudioGenerator.RenderedBlock renderedBlock =
                new JigsawStudioGenerator.RenderedBlock(1, 2, 3, state, null);

        when(world.getBlockAt(11, 22, 33)).thenReturn(target);
        when(state.isCustom()).thenReturn(false);
        when(state.nativeHandle()).thenReturn(blockData);

        JigsawStudioChunkWriter.restoreConnectorChunk(
                world,
                workcell,
                List.of(renderedConnector),
                Map.of(new JigsawStudioCapture.LocalPosition(1, 2, 3), renderedBlock),
                false);

        verify(world).getBlockAt(11, 22, 33);
        verify(target).setBlockData(blockData, false);
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

        assertFalse(JigsawStudioGraphMutations.layoutGeometryChanged(original, original));
        assertTrue(JigsawStudioGraphMutations.layoutGeometryChanged(original, expanded));
        Set<Long> chunks = JigsawStudioGraphMutations.relayoutChunks(original, expanded);
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

        assertTrue(JigsawStudioProtection.canCreateVariants(editable));
        assertFalse(JigsawStudioProtection.canCreateVariants(managed));
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

        assertEquals("", JigsawStudioProtection.variantCreationSourceFailure(assigned, false));
        assertEquals("", JigsawStudioProtection.variantCreationSourceFailure(assigned, true));
        assertTrue(JigsawStudioProtection.variantCreationSourceFailure(null, false)
                .contains("piece create <poolKey> <pieceKey>"));
        assertTrue(JigsawStudioProtection.variantCreationSourceFailure(planarVariant(true, true), false)
                .contains("no owned pool membership"));
        assertTrue(JigsawStudioProtection.variantCreationSourceFailure(planarVariant(true, false), true)
                .contains("read-only variant"));
    }

    @Test
    public void recognizesVanillaNamespacedAndWorldEditMutationCommands() {
        assertTrue(JigsawStudioProtection.isMutatingCommand("/fill 0 0 0 1 1 1 stone"));
        assertTrue(JigsawStudioProtection.isMutatingCommand("minecraft:setblock 0 0 0 air"));
        assertTrue(JigsawStudioProtection.isMutatingCommand("//paste -a"));
        assertTrue(JigsawStudioProtection.isMutatingCommand("/execute as @s run setblock 0 0 0 stone"));
        assertTrue(JigsawStudioProtection.isMutatingCommand("/function test:build"));
        assertTrue(JigsawStudioProtection.isMutatingCommand("/data merge block 0 0 0 {}"));
        assertTrue(JigsawStudioProtection.isMutatingCommand("/item replace block 0 0 0 container.0 with stone"));
        assertFalse(JigsawStudioProtection.isMutatingCommand("/iris jigsaw status"));
        assertFalse(JigsawStudioProtection.isMutatingCommand("/tp 0 80 0"));
    }

    @Test
    public void nonOwnerMutatingCommandsAreBlockedAtHighestPriority() throws NoSuchMethodException {
        UUID ownerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID otherId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        assertTrue(JigsawStudioProtection.ownerMatches(ownerId, ownerId));
        assertTrue(JigsawStudioProtection.ownerMatches(null, otherId));
        assertFalse(JigsawStudioProtection.ownerMatches(ownerId, otherId));
        assertTrue(JigsawStudioProtection.blocksStudioEdit(ownerId, otherId));
        assertFalse(JigsawStudioProtection.blocksStudioEdit(ownerId, ownerId));
        assertFalse(JigsawStudioProtection.blocksStudioEdit(null, otherId));
        assertTrue(JigsawStudioProtection.blocksMutatingCommand(ownerId, otherId, "//paste -a"));
        assertFalse(JigsawStudioProtection.blocksMutatingCommand(ownerId, ownerId, "//paste -a"));
        assertFalse(JigsawStudioProtection.blocksMutatingCommand(null, otherId, "//paste -a"));
        assertFalse(JigsawStudioProtection.blocksMutatingCommand(ownerId, otherId, "/iris jigsaw status"));
        assertFalse(JigsawStudioProtection.blocksMutatingCommand(ownerId, otherId, "/msg owner hello"));
        assertTrue(JigsawStudioProtection.blocksMutatingCommand(ownerId, otherId, "/execute run say bypass"));
        assertTrue(JigsawStudioProtection.blocksMutatingCommand(ownerId, otherId, "/unknownplugin mutate"));
        assertTrue(JigsawStudioProtection.blocksNonEditableWorkcellMutation(
                true, "/fill 0 0 0 1 1 1 stone"));
        assertTrue(JigsawStudioProtection.blocksNonEditableWorkcellMutation(true, "//paste -a"));
        assertFalse(JigsawStudioProtection.blocksNonEditableWorkcellMutation(
                true, "/iris jigsaw variant load test/end"));
        assertFalse(JigsawStudioProtection.blocksNonEditableWorkcellMutation(
                false, "/fill 0 0 0 1 1 1 stone"));

        Method method = JigsawStudioProtectionListener.class.getMethod(
                "onUnauthorizedPlayerCommand",
                PlayerCommandPreprocessEvent.class);
        EventHandler handler = method.getAnnotation(EventHandler.class);
        assertNotNull(handler);
        assertEquals(EventPriority.HIGHEST, handler.priority());
        assertTrue(handler.ignoreCancelled());
    }

    @Test
    public void indirectMutationEventsRemainMonitorListeners() throws NoSuchMethodException {
        assertMutationHandler("onPlayerInteract", PlayerInteractEvent.class);
        assertMutationHandler("onBlockRedstone", BlockRedstoneEvent.class);
        assertMutationHandler("onPistonExtend", BlockPistonExtendEvent.class);
        assertMutationHandler("onPistonRetract", BlockPistonRetractEvent.class);
    }

    @Test
    public void studioContainerMutationPolicyProtectsOperationsOwnersAndAutomation() {
        UUID ownerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID otherId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        assertFalse(JigsawStudioProtection.blocksStudioInventoryMutation(
                false, false, true, true, ownerId, otherId));
        assertFalse(JigsawStudioProtection.blocksStudioInventoryMutation(
                true, false, false, false, ownerId, ownerId));
        assertTrue(JigsawStudioProtection.blocksStudioInventoryMutation(
                true, false, true, false, ownerId, ownerId));
        assertTrue(JigsawStudioProtection.blocksStudioInventoryMutation(
                true, false, false, true, ownerId, ownerId));
        assertTrue(JigsawStudioProtection.blocksStudioInventoryMutation(
                true, false, false, false, ownerId, otherId));
        assertFalse(JigsawStudioProtection.blocksStudioInventoryMutation(
                true, false, false, false, ownerId, null));
        assertTrue(JigsawStudioProtection.blocksStudioInventoryMutation(
                true, true, false, false, ownerId, null));
        assertTrue(JigsawStudioProtection.blocksStudioInventoryMutation(
                true, true, false, false, ownerId, ownerId));
    }

    @Test
    public void containerAndMachineMutationEventsHaveProtectionAndAutosaveContracts()
            throws NoSuchMethodException {
        assertHandler("onProtectedInventoryClick", InventoryClickEvent.class, EventPriority.HIGHEST, true);
        assertHandler("onProtectedInventoryDrag", InventoryDragEvent.class, EventPriority.HIGHEST, true);
        assertHandler("onProtectedInventoryMove", InventoryMoveItemEvent.class, EventPriority.HIGHEST, true);
        assertHandler("onProtectedInventoryPickup", InventoryPickupItemEvent.class, EventPriority.HIGHEST, true);
        assertHandler("onProtectedBlockCook", BlockCookEvent.class, EventPriority.HIGHEST, true);
        assertHandler("onProtectedFurnaceBurn", FurnaceBurnEvent.class, EventPriority.HIGHEST, true);
        assertHandler("onProtectedBrew", BrewEvent.class, EventPriority.HIGHEST, true);
        assertHandler("onProtectedBrewingStandFuel", BrewingStandFuelEvent.class, EventPriority.HIGHEST, true);
        assertHandler("onProtectedBlockDispense", BlockDispenseEvent.class, EventPriority.HIGHEST, true);
        assertHandler("onProtectedCrafterCraft", CrafterCraftEvent.class, EventPriority.HIGHEST, true);

        assertMutationHandler("onBlockCook", BlockCookEvent.class);
        assertMutationHandler("onFurnaceBurn", FurnaceBurnEvent.class);
        assertHandler("onFurnaceStartSmelt", FurnaceStartSmeltEvent.class, EventPriority.MONITOR, false);
        assertMutationHandler("onBrew", BrewEvent.class);
        assertHandler("onBrewingStart", BrewingStartEvent.class, EventPriority.MONITOR, false);
        assertMutationHandler("onBrewingStandFuel", BrewingStandFuelEvent.class);
        assertMutationHandler("onBlockDispense", BlockDispenseEvent.class);
        assertMutationHandler("onCrafterCraft", CrafterCraftEvent.class);
        assertHandler("onWorldUnload", WorldUnloadEvent.class, EventPriority.HIGHEST, true);
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

        assertFalse(JigsawStudioTileWatcher.tileSnapshotChanged(baseline, unchanged));
        assertTrue(JigsawStudioTileWatcher.tileSnapshotChanged(baseline, changed));
        assertEquals(
                new JigsawStudioTileWatcher.JigsawTilePollDecision(true, true),
                JigsawStudioTileWatcher.jigsawTilePollDecision(baseline, changed, false));
        assertEquals(
                new JigsawStudioTileWatcher.JigsawTilePollDecision(true, true),
                JigsawStudioTileWatcher.jigsawTilePollDecision(changed, changedAgain, false));
        assertEquals(
                new JigsawStudioTileWatcher.JigsawTilePollDecision(false, false),
                JigsawStudioTileWatcher.jigsawTilePollDecision(changedAgain, changedAgain, true));
    }

    @Test
    public void persistentAutosaveFailuresLogOncePerDirtyIdentityAndUseCappedBackoff() {
        UUID requestId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        IOException validationFailure = new IOException("closure rejected an empty piece entry");
        String validationDetail = "Jigsaw Studio save failed: closure rejected an empty piece entry";
        String validationContext = JigsawStudioAutosaveScheduler.autosaveFailureContext(
                requestId,
                "qa/profile_final",
                "workcell/end",
                "qa/profile_final/end",
                validationDetail);
        JigsawStudioAutosaveScheduler.AutosaveFailureState validationState =
                new JigsawStudioAutosaveScheduler.AutosaveFailureState();
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
        String writerContext = JigsawStudioAutosaveScheduler.autosaveFailureContext(
                requestId,
                "qa/profile_final",
                "workcell/end",
                "qa/profile_final/end",
                writerDetail);
        JigsawStudioAutosaveScheduler.AutosaveFailureState writerState =
                new JigsawStudioAutosaveScheduler.AutosaveFailureState();

        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            JigsawStudioAutosaveScheduler.AutosaveFailureDecision firstValidation =
                    JigsawStudioAutosaveScheduler.recordPersistentAutosaveFailure(
                            validationState,
                            requestId,
                            "qa/profile_final",
                            "workcell/end",
                            "qa/profile_final/end",
                            validationDetail,
                            validationFailure);
            JigsawStudioAutosaveScheduler.AutosaveFailureDecision secondValidation =
                    JigsawStudioAutosaveScheduler.recordPersistentAutosaveFailure(
                            validationState,
                            requestId,
                            "qa/profile_final",
                            "workcell/end",
                            "qa/profile_final/end",
                            validationDetail,
                            validationFailure);
            List<Integer> writerDelays = new ArrayList<>();
            for (int attempt = 0; attempt < 7; attempt++) {
                writerDelays.add(JigsawStudioAutosaveScheduler.recordPersistentAutosaveFailure(
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
            logging.verify(
                    () -> IrisLogging.reportError(validationContext, validationFailure),
                    times(1));
            logging.verify(() -> IrisLogging.warn("%s", writerContext), times(1));
        }

        JigsawStudioAutosaveScheduler.AutosaveFailureDecision newIdentity =
                new JigsawStudioAutosaveScheduler.AutosaveFailureState().recordPersistentFailure();
        assertEquals(40, newIdentity.retryTicks());
        assertTrue(newIdentity.logFailure());
    }

    @Test
    public void siblingAutosaveResolvesTheCurrentBayAfterACommittedSaveReloadsLayout()
            throws ReflectiveOperationException {
        UUID requestId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID worldId = UUID.fromString("44444444-4444-4444-4444-444444444444");
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
        JigsawStudioSession session = new JigsawStudioSession(
                "overworld",
                "qa/profile_final",
                initialLayout);
        JigsawStudioBay initialEnd = initialLayout.get("workcell/end");
        JigsawStudioBay initialStraight = initialLayout.get("workcell/straight");
        JigsawStudioSession.DirtyIdentity endDirty = session.markWorkcellDirty(initialEnd.stableId())
                .identity()
                .orElseThrow();
        JigsawStudioSession.DirtyIdentity straightDirty = session.markWorkcellDirty(initialStraight.stableId())
                .identity()
                .orElseThrow();
        JigsawStudioActivation.Request request = mock(JigsawStudioActivation.Request.class);
        when(request.requestId()).thenReturn(requestId);
        when(request.packKey()).thenReturn("overworld");
        when(request.structureKey()).thenReturn("qa/profile_final");
        IrisData source = mock(IrisData.class);
        @SuppressWarnings("unchecked")
        ResourceLoader<IrisJigsawPiece> pieceLoader = mock(ResourceLoader.class);
        @SuppressWarnings("unchecked")
        ResourceLoader<IrisObject> objectLoader = mock(ResourceLoader.class);
        when(request.source()).thenReturn(source);
        when(source.getJigsawPieceLoader()).thenReturn(pieceLoader);
        when(source.getObjectLoader()).thenReturn(objectLoader);
        when(pieceLoader.load("test/end", false))
                .thenReturn(new IrisJigsawPiece().setObject("test/end"));
        when(pieceLoader.load("test/straight", false))
                .thenReturn(new IrisJigsawPiece().setObject("test/straight"));
        when(objectLoader.load("test/end", false)).thenReturn(new IrisObject(16, 16, 16));
        when(objectLoader.load("test/straight", false)).thenReturn(new IrisObject(16, 16, 16));
        JigsawStudioGenerator generator = mock(JigsawStudioGenerator.class);
        when(generator.getRequest()).thenReturn(request);
        when(generator.getSession()).thenReturn(session);
        when(generator.getLayout()).thenAnswer(invocation -> session.layout());
        World world = mock(World.class);
        when(world.getUID()).thenReturn(worldId);
        ConcurrentHashMap<String, JigsawStudioChunkWriter.BayPopulation> populations = new ConcurrentHashMap<>();
        JigsawStudioChunkWriter.BayPopulation endPopulation =
                new JigsawStudioChunkWriter.BayPopulation(Set.of(0L), "");
        JigsawStudioChunkWriter.BayPopulation straightPopulation =
                new JigsawStudioChunkWriter.BayPopulation(Set.of(0L), "");
        endPopulation.markFullyReady();
        straightPopulation.markFullyReady();
        populations.put(initialEnd.stableId(), endPopulation);
        populations.put(initialStraight.stableId(), straightPopulation);
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
        Object studio = studioConstructor.newInstance(
                worldId,
                world,
                mock(Engine.class),
                generator,
                populations,
                ConcurrentHashMap.newKeySet(),
                new AtomicLong());
        Method scheduleAutosave = JigsawStudioAutosaveScheduler.class.getDeclaredMethod(
                "scheduleAutosave",
                studioType,
                JigsawStudioBay.class,
                JigsawStudioSession.DirtyIdentity.class,
                int.class);
        scheduleAutosave.setAccessible(true);
        JigsawStudioService service = new JigsawStudioService();
        Field studiosField = JigsawStudioService.class.getDeclaredField("studios");
        studiosField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, Object> studios = (Map<UUID, Object>) studiosField.get(service);
        studios.put(worldId, studio);
        List<Runnable> delayedAutosaves = new ArrayList<>();

        try (MockedStatic<J> scheduling = mockStatic(J.class);
             MockedStatic<JigsawStudioActivation> activation = mockStatic(JigsawStudioActivation.class)) {
            activation.when(() -> JigsawStudioActivation.getRequest("overworld")).thenReturn(request);
            scheduling.when(() -> J.runRegion(
                            any(World.class),
                            anyInt(),
                            anyInt(),
                            any(Runnable.class),
                            eq(40)))
                    .thenAnswer(invocation -> {
                        delayedAutosaves.add(invocation.getArgument(3, Runnable.class));
                        return true;
                    });
            scheduling.when(() -> J.runRegion(
                            any(World.class),
                            anyInt(),
                            anyInt(),
                            any(Runnable.class)))
                    .thenReturn(true);
            scheduleAutosave.invoke(service.autosaveScheduler, studio, initialEnd, endDirty, 40);
            scheduleAutosave.invoke(service.autosaveScheduler, studio, initialStraight, straightDirty, 40);
            assertEquals(2, delayedAutosaves.size());

            JigsawStudioSession.SaveIdentity committedEnd = session.beginSave(initialEnd.stableId())
                    .identity()
                    .orElseThrow();
            assertTrue(session.markWorkcellSaved(committedEnd));
            JigsawStudioLayout replacementLayout = JigsawStudioLayout.create(
                    JigsawStudioMode.PLANAR_JIGSAW,
                    dimensions,
                    catalog);
            assertTrue(session.replaceLayout(replacementLayout));
            JigsawStudioBay currentStraight = JigsawStudioAutosaveScheduler.resolveCurrentAutosaveBay(
                    session,
                    initialStraight.stableId());
            assertNotSame(initialStraight, currentStraight);
            assertSame(replacementLayout.get(initialStraight.stableId()), currentStraight);
            assertTrue(session.isDirtyCurrent(straightDirty));

            delayedAutosaves.get(1).run();

            assertTrue(session.workcellSnapshot(initialStraight.stableId()).saveInProgress());
        }
    }

    @Test
    public void saveNowRetainsAutosaveAcrossNotReadyInProgressAndSchedulerRejection()
            throws ReflectiveOperationException {
        UUID ownerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID requestId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID worldId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(16, 16, 16);
        JigsawStudioLayout layout = JigsawStudioLayout.create(
                JigsawStudioMode.PLANAR_JIGSAW,
                dimensions,
                new JigsawStudioVariantCatalog(List.of(planarVariant(true, true))));
        JigsawStudioBay bay = layout.get("workcell/end");
        JigsawStudioSession session = new JigsawStudioSession("overworld", "village", layout);
        JigsawStudioSession.DirtyIdentity identity = session.markWorkcellDirty(bay.stableId())
                .identity()
                .orElseThrow();
        JigsawStudioActivation.Request request = mock(JigsawStudioActivation.Request.class);
        when(request.requestId()).thenReturn(requestId);
        when(request.ownerId()).thenReturn(ownerId);
        IrisData source = mock(IrisData.class);
        @SuppressWarnings("unchecked")
        ResourceLoader<IrisJigsawPiece> pieceLoader = mock(ResourceLoader.class);
        @SuppressWarnings("unchecked")
        ResourceLoader<IrisObject> objectLoader = mock(ResourceLoader.class);
        IrisJigsawPiece piece = new IrisJigsawPiece().setObject("test/end");
        IrisObject object = new IrisObject(16, 16, 16);
        when(request.source()).thenReturn(source);
        when(source.getJigsawPieceLoader()).thenReturn(pieceLoader);
        when(source.getObjectLoader()).thenReturn(objectLoader);
        when(pieceLoader.load("test/end", false)).thenReturn(piece);
        when(objectLoader.load("test/end", false)).thenReturn(object);
        JigsawStudioGenerator generator = mock(JigsawStudioGenerator.class);
        when(generator.getRequest()).thenReturn(request);
        when(generator.getSession()).thenReturn(session);
        when(generator.getLayout()).thenReturn(layout);
        when(generator.renderBay(any(JigsawStudioBay.class)))
                .thenReturn(JigsawStudioGenerator.RenderedBay.empty(dimensions));
        World world = mock(World.class);
        when(world.getUID()).thenReturn(worldId);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ownerId);
        when(player.getWorld()).thenReturn(world);

        JigsawStudioService service = new JigsawStudioService();
        ConcurrentHashMap<String, JigsawStudioChunkWriter.BayPopulation> populations = new ConcurrentHashMap<>();
        populations.put(
                bay.stableId(),
                new JigsawStudioChunkWriter.BayPopulation(Set.of(0L), "workcell hydration failed"));
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
        Object studio = studioConstructor.newInstance(
                worldId,
                world,
                mock(Engine.class),
                generator,
                populations,
                ConcurrentHashMap.newKeySet(),
                new AtomicLong());
        Field studiosField = JigsawStudioService.class.getDeclaredField("studios");
        studiosField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, Object> studios = (Map<UUID, Object>) studiosField.get(service);
        studios.put(worldId, studio);

        Class<?> keyType = Class.forName(JigsawStudioAutosaveScheduler.class.getName() + "$AutosaveKey");
        Constructor<?> keyConstructor = keyType.getDeclaredConstructor(UUID.class, String.class);
        keyConstructor.setAccessible(true);
        Object key = keyConstructor.newInstance(requestId, bay.stableId());
        Class<?> ticketType = Class.forName(JigsawStudioAutosaveScheduler.class.getName() + "$AutosaveTicket");
        Constructor<?> ticketConstructor = ticketType.getDeclaredConstructor(
                keyType,
                studioType,
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
        Field autosavesField = JigsawStudioAutosaveScheduler.class.getDeclaredField("autosaves");
        autosavesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Object, Object> autosaves = (Map<Object, Object>) autosavesField.get(service.autosaveScheduler);
        autosaves.put(key, ticket);
        Method failureStateAccessor = ticketType.getDeclaredMethod("failureState");
        failureStateAccessor.setAccessible(true);
        JigsawStudioAutosaveScheduler.AutosaveFailureState failureState =
                (JigsawStudioAutosaveScheduler.AutosaveFailureState) failureStateAccessor.invoke(ticket);
        assertEquals(40, failureState.recordPersistentFailure().retryTicks());

        try (MockedStatic<J> scheduling = mockStatic(J.class)) {
            scheduling.when(() -> J.isOwnedByCurrentRegion(player)).thenReturn(true);
            scheduling.when(() -> J.runRegion(
                    any(World.class), anyInt(), anyInt(), any(Runnable.class), anyInt()))
                    .thenReturn(false);

            assertFalse(service.flushAutosave(player, bay.stableId()));
            assertEquals(1, autosaves.size());
            assertTrue(session.isDirtyCurrent(identity));

            populations.put(
                    bay.stableId(),
                    new JigsawStudioChunkWriter.BayPopulation(Set.of(0L), ""));
            assertFalse(service.flushAutosave(player, bay.stableId()));
            assertEquals(1, autosaves.size());
            assertTrue(session.isDirtyCurrent(identity));

            JigsawStudioChunkWriter.BayPopulation ready =
                    new JigsawStudioChunkWriter.BayPopulation(Set.of(0L), "");
            ready.markFullyReady();
            populations.put(bay.stableId(), ready);
            JigsawStudioSession.SaveIdentity inProgress = session.beginSave(bay.stableId())
                    .identity()
                    .orElseThrow();
            assertFalse(service.flushAutosave(player, bay.stableId()));
            assertEquals(1, autosaves.size());
            assertTrue(session.abortSave(inProgress));
            scheduling.verify(() -> J.s(any(Runnable.class), eq(5)), times(3));
            Object retainedTicket = autosaves.values().iterator().next();
            assertSame(failureState, failureStateAccessor.invoke(retainedTicket));
            assertEquals(80, failureState.recordPersistentFailure().retryTicks());
        }
    }

    @Test
    public void vanillaPortableRotationCannotBeDisabledFromTheControlMenu() {
        JigsawStudioVariant rotatable = planarVariant(true, true);
        JigsawStudioVariant fixed = planarVariant(false, true);
        JigsawStudioVariant readOnly = planarVariant(true, false);

        assertFalse(JigsawStudioProtection.canToggleVariantRotation(
                JigsawStudioCompatibilityTarget.VANILLA_PORTABLE, rotatable));
        assertTrue(JigsawStudioProtection.canToggleVariantRotation(
                JigsawStudioCompatibilityTarget.VANILLA_PORTABLE, fixed));
        assertTrue(JigsawStudioProtection.canToggleVariantRotation(
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED, rotatable));
        assertFalse(JigsawStudioProtection.canToggleVariantRotation(
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED, readOnly));
    }

    @Test
    public void planarSaveRequiresTheCanonicalWorkcellConnectorOrientation() throws IOException {
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(16, 16, 16);
        JigsawStudioLayout layout = JigsawStudioLayout.create(
                JigsawStudioMode.PLANAR_JIGSAW,
                dimensions,
                JigsawStudioVariantCatalog.empty());
        IrisJigsawConnector eastSource = connector().setDirection(IrisDirection.EAST_POSITIVE_X);
        IrisJigsawConnector southDisplayed = connector().setDirection(IrisDirection.SOUTH_POSITIVE_Z);

        JigsawStudioCapture.requireWorkcellTopology(
                layout.get("workcell/end"),
                List.of(eastSource),
                3);
        JigsawStudioCapture.WorkcellTopologyException wrongDirection = assertThrows(
                JigsawStudioCapture.WorkcellTopologyException.class,
                () -> JigsawStudioCapture.requireWorkcellTopology(
                        layout.get("workcell/end"),
                        List.of(southDisplayed),
                        0));
        assertTrue(wrongDirection.getMessage().contains("south end (1 horizontal connector)"));
        assertTrue(wrongDirection.getMessage().contains("Reset Connector Blocks"));

        JigsawStudioCapture.WorkcellTopologyException missingTee = assertThrows(
                JigsawStudioCapture.WorkcellTopologyException.class,
                () -> JigsawStudioCapture.requireWorkcellTopology(
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
        List<JigsawStudioCapture.ChunkCaptureArea> areas = JigsawStudioCapture.chunkIntersections(bounds);
        PlatformBlockState sourceState = BukkitBlockState.of(directionalBlockData(BlockFace.NORTH));
        for (int quarterTurns = 1; quarterTurns <= 3; quarterTurns++) {
            IrisObjectRotation displayRotation = IrisObjectRotation.of(0, -90.0D * quarterTurns, 0);
            PlatformBlockState displayedState = displayRotation.rotate(sourceState, 0, 0, 0);
            IrisDirection displayedDirection = displayRotation.rotate(IrisDirection.NORTH_NEGATIVE_Z);
            JigsawStudioCapture.CapturedConnector connector = new JigsawStudioCapture.CapturedConnector(
                    1,
                    0,
                    1,
                    displayedDirection,
                    IrisDirection.UP_POSITIVE_Y,
                    "test/pool",
                    "door",
                    "door",
                    "",
                    JigsawJoint.ALIGNED,
                    displayedState.key(),
                    -3,
                    7);
            JigsawStudioCapture.ChunkSnapshot snapshot = new JigsawStudioCapture.ChunkSnapshot(
                    areas.getFirst(),
                    List.of(),
                    List.of(connector));
            try (MockedStatic<B> blocks = mockStatic(B.class)) {
                blocks.when(() -> B.getStateOrNull(
                        displayedState.key(), false)).thenReturn(displayedState);
                JigsawStudioCapture.Capture capture = JigsawStudioCapture.aggregateSnapshots(
                        bounds,
                        areas,
                        List.of(snapshot),
                        quarterTurns);

                assertEquals(sourceState.key(), capture.connectors().getFirst().getFinalState());
                assertEquals(-3, capture.connectors().getFirst().getSelectionPriority());
                assertEquals(7, capture.connectors().getFirst().getPlacementPriority());
            }
        }
    }

    @Test
    public void hiddenConnectorCapturesExactBlockStateTilePayloadAndMetadata() throws Throwable {
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
        BlockData chestData = directionalBlockData(Material.CHEST, BlockFace.EAST);
        PlatformBlockState chestState = BukkitBlockState.of(chestData);
        IrisObject sourceObject = new IrisObject(1, 1, 1);
        sourceObject.setUnsigned(0, 0, 0, chestState);
        KMap<String, Object> properties = new KMap<>();
        properties.put("CustomName", "Hidden Connector Chest");
        properties.put("Lock", "iris:hidden");
        TileData tileData = new TileData("minecraft:chest", properties);
        Block block = mock(Block.class);
        when(block.getBlockData()).thenReturn(chestData);
        World world = mock(World.class);
        when(world.getBlockAt(0, 64, 0)).thenReturn(block);
        JigsawStudioCapture.ChunkCaptureArea area = JigsawStudioCapture.chunkIntersections(bounds).getFirst();

        JigsawStudioCapture.ChunkSnapshot snapshot;
        try (MockedStatic<TileData> tiles = mockStatic(TileData.class)) {
            tiles.when(() -> TileData.getTileState(block, false)).thenReturn(tileData);
            snapshot = JigsawStudioCapture.captureChunkIntersection(
                    world,
                    bounds,
                    piece,
                    sourceObject,
                    area,
                    0,
                    false);
        }
        JigsawStudioCapture.Capture capture = JigsawStudioCapture.aggregateSnapshots(
                bounds,
                List.of(area),
                List.of(snapshot));
        IrisObject restored = readCapturedObject(capture.objectContent(), chestState);
        IrisJigsawConnector captured = capture.connectors().getFirst();

        assertEquals(chestData.getAsString(), captured.getFinalState());
        assertEquals("gate/owned", captured.getChannel());
        assertEquals(-7, captured.getSelectionPriority());
        assertEquals(11, captured.getPlacementPriority());
        assertEquals(tileData, restored.getStates().get(restored.getSigned(0, 0, 0)));
        assertTrue(capture.hasBlockEntities());
    }

    @Test
    public void noOpTeeAndCrossCapturePreservesCreatorOrderForSeededAssembly() throws IOException {
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(16, 16, 16);
        List<List<IrisDirection>> authoredOrders = List.of(
                List.of(
                        IrisDirection.NORTH_NEGATIVE_Z,
                        IrisDirection.EAST_POSITIVE_X,
                        IrisDirection.WEST_NEGATIVE_X),
                List.of(
                        IrisDirection.NORTH_NEGATIVE_Z,
                        IrisDirection.EAST_POSITIVE_X,
                        IrisDirection.SOUTH_POSITIVE_Z,
                        IrisDirection.WEST_NEGATIVE_X));
        List<List<IrisDirection>> captureOrders = List.of(
                List.of(
                        IrisDirection.WEST_NEGATIVE_X,
                        IrisDirection.NORTH_NEGATIVE_Z,
                        IrisDirection.EAST_POSITIVE_X),
                List.of(
                        IrisDirection.WEST_NEGATIVE_X,
                        IrisDirection.NORTH_NEGATIVE_Z,
                        IrisDirection.SOUTH_POSITIVE_Z,
                        IrisDirection.EAST_POSITIVE_X));

        for (int index = 0; index < authoredOrders.size(); index++) {
            IrisJigsawPiece source = pieceWithPlanarConnectors(dimensions, authoredOrders.get(index));
            List<IrisJigsawConnector> captured = planarConnectors(dimensions, captureOrders.get(index));

            List<IrisJigsawConnector> ordered = JigsawStudioCapture.preserveCapturedConnectorOrder(
                    source,
                    captured);

            assertEquals(authoredOrders.get(index), connectorDirections(ordered));
        }
    }

    @Test
    public void connectorMetadataEditsAtTheSamePositionKeepAuthoredOrder() throws IOException {
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(16, 16, 16);
        IrisJigsawPiece source = pieceWithPlanarConnectors(dimensions, List.of(
                IrisDirection.NORTH_NEGATIVE_Z,
                IrisDirection.EAST_POSITIVE_X));
        IrisJigsawConnector capturedNorth = planarConnector(
                dimensions,
                IrisDirection.NORTH_NEGATIVE_Z)
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
                IrisDirection.EAST_POSITIVE_X);

        List<IrisJigsawConnector> ordered = JigsawStudioCapture.preserveCapturedConnectorOrder(
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
                IrisDirection.NORTH_NEGATIVE_Z,
                IrisDirection.EAST_POSITIVE_X,
                IrisDirection.WEST_NEGATIVE_X));
        IrisJigsawConnector capturedWest = planarConnector(
                dimensions,
                IrisDirection.WEST_NEGATIVE_X);
        IrisJigsawConnector capturedNorth = planarConnector(
                dimensions,
                IrisDirection.NORTH_NEGATIVE_Z);

        List<IrisJigsawConnector> ordered = JigsawStudioCapture.preserveCapturedConnectorOrder(
                source,
                List.of(capturedWest, capturedNorth));

        assertEquals(List.of(
                IrisDirection.NORTH_NEGATIVE_Z,
                IrisDirection.WEST_NEGATIVE_X), connectorDirections(ordered));
    }

    @Test
    public void newAndMovedConnectorsAppendInDeterministicSourcePositionOrder() throws IOException {
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(16, 16, 16);
        IrisJigsawPiece source = pieceWithPlanarConnectors(
                dimensions,
                List.of(IrisDirection.NORTH_NEGATIVE_Z));
        IrisJigsawConnector capturedNorth = planarConnector(
                dimensions,
                IrisDirection.NORTH_NEGATIVE_Z);
        IrisJigsawConnector highY = connectorAt(2, 5, 9);
        IrisJigsawConnector highZ = connectorAt(2, 3, 10);
        IrisJigsawConnector lowZ = connectorAt(2, 3, 4);

        List<IrisJigsawConnector> ordered = JigsawStudioCapture.preserveCapturedConnectorOrder(
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
                .setDirection(IrisDirection.SOUTH_POSITIVE_Z);
        IrisJigsawPiece duplicateSource = new IrisJigsawPiece().setConnectors(new KList<>());
        duplicateSource.getConnectors().add(first);
        duplicateSource.getConnectors().add(second);
        IrisJigsawPiece emptySource = new IrisJigsawPiece().setConnectors(new KList<>());

        assertThrows(IOException.class, () -> JigsawStudioCapture.preserveCapturedConnectorOrder(
                duplicateSource,
                List.of(first)));
        assertThrows(IOException.class, () -> JigsawStudioCapture.preserveCapturedConnectorOrder(
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
        List<JigsawStudioCapture.ChunkCaptureArea> areas = JigsawStudioCapture.chunkIntersections(bounds);
        IrisJigsawConnector sourceFirst = connectorAt(0, 0, 1)
                .setDirection(IrisDirection.NORTH_NEGATIVE_Z);
        IrisJigsawConnector sourceSecond = connectorAt(2, 0, 2)
                .setDirection(IrisDirection.EAST_POSITIVE_X);
        IrisJigsawPiece source = new IrisJigsawPiece().setConnectors(new KList<>());
        source.getConnectors().add(sourceFirst);
        source.getConnectors().add(sourceSecond);
        PlatformBlockState sourceState = BukkitBlockState.of(
                directionalBlockData(Material.OBSERVER, BlockFace.NORTH));

        for (int quarterTurns = 1; quarterTurns <= 3; quarterTurns++) {
            IrisObjectRotation displayRotation = IrisObjectRotation.of(0, -90.0D * quarterTurns, 0);
            PlatformBlockState displayedState = displayRotation.rotate(sourceState, 0, 0, 0);
            JigsawStudioCapture.CapturedConnector displayedFirst = displayedConnector(
                    sourceFirst,
                    bounds.dimensions(),
                    quarterTurns,
                    displayRotation,
                    displayedState.key());
            JigsawStudioCapture.CapturedConnector displayedSecond = displayedConnector(
                    sourceSecond,
                    bounds.dimensions(),
                    quarterTurns,
                    displayRotation,
                    displayedState.key());
            JigsawStudioCapture.ChunkSnapshot snapshot = new JigsawStudioCapture.ChunkSnapshot(
                    areas.getFirst(),
                    List.of(),
                    List.of(displayedSecond, displayedFirst));

            try (MockedStatic<B> blocks = mockStatic(B.class)) {
                blocks.when(() -> B.getStateOrNull(displayedState.key(), false)).thenReturn(displayedState);
                JigsawStudioCapture.Capture capture = JigsawStudioCapture.aggregateSnapshots(
                        bounds,
                        areas,
                        List.of(snapshot),
                        quarterTurns);
                List<IrisJigsawConnector> ordered = JigsawStudioCapture.preserveCapturedConnectorOrder(
                        source,
                        capture.connectors());

                assertEquals(sourceFirst.getPosition(), ordered.getFirst().getPosition());
                assertEquals(sourceSecond.getPosition(), ordered.getLast().getPosition());
            }
        }
    }

    @Test
    public void rotatedBlockEntityPayloadReturnsToItsStoredCoordinateUnchanged() throws Throwable {
        JigsawStudioBounds bounds = new JigsawStudioBounds(
                0,
                64,
                0,
                new JigsawStudioCellDimensions(5, 2, 3));
        List<JigsawStudioCapture.ChunkCaptureArea> areas = JigsawStudioCapture.chunkIntersections(bounds);
        KMap<String, Object> properties = new KMap<>();
        properties.put("CustomName", "QA Chest");
        properties.put("Lock", "iris:test");
        TileData tileData = new TileData("minecraft:chest", properties);
        int[][] expectedPositions = {
                {0, 0, 0},
                {1, 1, 3},
                {3, 1, 1},
                {1, 1, 1}
        };
        for (int quarterTurns = 1; quarterTurns <= 3; quarterTurns++) {
            BlockData displayedData = directionalBlockData(Material.CHEST, BlockFace.EAST);
            PlatformBlockState displayedState = BukkitBlockState.of(displayedData);
            JigsawStudioCapture.CapturedBlock capturedBlock = new JigsawStudioCapture.CapturedBlock(
                    1,
                    1,
                    1,
                    displayedState,
                    tileData);
            JigsawStudioCapture.ChunkSnapshot snapshot = new JigsawStudioCapture.ChunkSnapshot(
                    areas.getFirst(),
                    List.of(capturedBlock),
                    List.of());

            JigsawStudioCapture.Capture capture = JigsawStudioCapture.aggregateSnapshots(
                    bounds,
                    areas,
                    List.of(snapshot),
                    quarterTurns);
            IrisObject restored = readCapturedObject(capture.objectContent(), displayedState);
            int[] expected = expectedPositions[quarterTurns];
            TileData restoredTile = restored.getStates().get(restored.getSigned(
                    expected[0], expected[1], expected[2]));

            assertTrue(capture.hasBlockEntities());
            assertNotNull(restoredTile);
            assertEquals(tileData, restoredTile);
        }
    }

    @Test
    public void rotatedTilePayloadDoesNotBlockMaterialization() {
        KMap<String, Object> properties = new KMap<>();
        properties.put("CustomName", "QA Chest");
        TileData tileData = new TileData("minecraft:chest", properties);
        PlatformBlockState state = BukkitBlockState.of(
                directionalBlockData(Material.CHEST, BlockFace.NORTH));
        JigsawStudioGenerator.RenderedBay rendered = JigsawStudioGenerator.RenderedBay.valid(
                new JigsawStudioCellDimensions(3, 2, 5),
                List.of(new JigsawStudioGenerator.RenderedBlock(0, 0, 0, state, tileData)),
                List.of());

        assertEquals("", JigsawStudioChunkWriter.validateMaterialization(rendered));
    }

    @Test
    public void explicitAirFinalStateRemainsInTheCapturedObject() throws Throwable {
        IrisObject object = new IrisObject(2, 2, 2);
        BlockData air = blockData(Material.AIR, "minecraft:air");
        BlockData structureVoid = blockData(Material.STRUCTURE_VOID, "minecraft:structure_void");

        JigsawStudioCapture.storeConnectorFinalState(object, 0, 0, 0, air);
        JigsawStudioCapture.storeConnectorFinalState(object, 1, 0, 0, structureVoid);

        IrisBlockVector airPosition = object.getSigned(0, 0, 0);
        IrisBlockVector structureVoidPosition = object.getSigned(1, 0, 0);
        assertTrue(object.getBlocks().containsKey(airPosition));
        assertEquals("minecraft:air", object.getBlocks().get(airPosition).key());
        assertFalse(object.getBlocks().containsKey(structureVoidPosition));

        assertAirRoundTrip(object);
    }

    @Test
    public void originalExplicitAirOutsideAConnectorSurvivesWhileNewAirRemainsAbsent() throws Throwable {
        IrisObject source = new IrisObject(2, 2, 2);
        BlockData air = blockData(Material.AIR, "minecraft:air");
        BlockData structureVoid = blockData(Material.STRUCTURE_VOID, "minecraft:structure_void");
        PlatformBlockState sourceAir = mock(PlatformBlockState.class);
        when(sourceAir.isAir()).thenReturn(true);
        when(sourceAir.key()).thenReturn("minecraft:air");
        source.setUnsigned(0, 0, 0, sourceAir);

        PlatformBlockState retained = JigsawStudioCapture.retainedSourceAir(source, 0, 0, 0, air);
        assertNotNull(retained);
        assertNull(JigsawStudioCapture.retainedSourceAir(source, 1, 0, 0, air));
        assertNull(JigsawStudioCapture.retainedSourceAir(source, 0, 0, 0, structureVoid));

        IrisObject captured = new IrisObject(2, 2, 2);
        captured.setUnsigned(0, 0, 0, retained);
        assertAirRoundTrip(captured);
    }

    private static void assertAirRoundTrip(IrisObject object) throws Throwable {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        object.write(output);
        PlatformBlockState restoredAir = mock(PlatformBlockState.class);
        when(restoredAir.key()).thenReturn("minecraft:air");
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block(anyString())).thenReturn(restoredAir);
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.unbind();
        IrisPlatforms.bind(platform);
        try {
            IrisObject restored = new IrisObject();
            restored.read(new ByteArrayInputStream(output.toByteArray()));

            assertTrue(restored.getBlocks().containsKey(restored.getSigned(0, 0, 0)));
            assertEquals("minecraft:air", restored.getBlocks().get(restored.getSigned(0, 0, 0)).key());
            assertFalse(restored.getBlocks().containsKey(restored.getSigned(1, 0, 0)));
        } finally {
            IrisPlatforms.unbind();
        }
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

        KMap<String, Object> nbt = JigsawStudioChunkWriter.markerNbt(source);
        assertEquals("iris:fort/start", nbt.get("pool"));
        assertEquals("iris:door", nbt.get("name"));
        assertEquals(-4, nbt.get("selection_priority"));
        assertEquals(17, nbt.get("placement_priority"));
        assertFalse(nbt.containsKey("channel"));

        IrisJigsawConnector captured = connector()
                .setPool("fort/start")
                .setName("iris:door")
                .setTargetName("iris:door");
        JigsawStudioChunkWriter.restoreCapturedMetadata(captured, piece);

        assertEquals("fort/start", captured.getPool());
        assertEquals("door", captured.getName());
        assertEquals("door", captured.getTargetName());
        assertEquals("castle/door", captured.getChannel());
    }

    @Test
    public void authoredBayReadinessRequiresEveryChunkToPopulateAndHydrate() {
        JigsawStudioChunkWriter.BayPopulation population = new JigsawStudioChunkWriter.BayPopulation(
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

        JigsawStudioSaveLifecycle.BayReadiness readiness = population.readiness();
        assertTrue(readiness.ready());
        assertEquals(2, readiness.requiredChunks());
        assertEquals(2, readiness.generatedChunks());
        assertEquals(2, readiness.hydratedChunks());
    }

    @Test
    public void chunkLoadRecoversAGenerationSignalMissedDuringStudioRegistration()
            throws ReflectiveOperationException {
        UUID worldId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(16, 16, 16);
        JigsawStudioLayout layout = JigsawStudioLayout.create(
                JigsawStudioMode.PLANAR_JIGSAW,
                dimensions,
                new JigsawStudioVariantCatalog(List.of(planarVariant(true, true))));
        JigsawStudioBay blank = layout.get("workcell/blank");
        long chunkKey = ((long) 1 << 32) ^ 1L;
        JigsawStudioChunkWriter.BayPopulation population =
                new JigsawStudioChunkWriter.BayPopulation(Set.of(chunkKey), "");
        ConcurrentHashMap<String, JigsawStudioChunkWriter.BayPopulation> populations = new ConcurrentHashMap<>();
        populations.put(blank.stableId(), population);

        World world = mock(World.class);
        when(world.getUID()).thenReturn(worldId);
        JigsawStudioGenerator generator = mock(JigsawStudioGenerator.class);
        when(generator.getLayout()).thenReturn(layout);
        JigsawStudioActivation.Request request = mock(JigsawStudioActivation.Request.class);
        when(request.requestId()).thenReturn(UUID.randomUUID());
        when(generator.getRequest()).thenReturn(request);
        when(generator.renderBay(any(JigsawStudioBay.class)))
                .thenReturn(JigsawStudioGenerator.RenderedBay.empty(dimensions));
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
        Object studio = studioConstructor.newInstance(
                worldId,
                world,
                mock(Engine.class),
                generator,
                populations,
                ConcurrentHashMap.newKeySet(),
                new AtomicLong());
        JigsawStudioService service = new JigsawStudioService();
        Field studiosField = JigsawStudioService.class.getDeclaredField("studios");
        studiosField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, Object> studios = (Map<UUID, Object>) studiosField.get(service);
        studios.put(worldId, studio);

        Chunk chunk = mock(Chunk.class);
        when(chunk.getX()).thenReturn(1);
        when(chunk.getZ()).thenReturn(1);
        ChunkLoadEvent event = mock(ChunkLoadEvent.class);
        when(event.getWorld()).thenReturn(world);
        when(event.getChunk()).thenReturn(chunk);

        service.protectionListener.onChunkLoad(event);

        assertTrue(population.needsApplication(chunkKey));
        assertEquals(1, population.readiness().generatedChunks());
        assertEquals(0, population.readiness().hydratedChunks());
    }

    @Test
    public void hydrationFailurePermanentlyBlocksSaveReadiness() {
        JigsawStudioChunkWriter.BayPopulation population = new JigsawStudioChunkWriter.BayPopulation(
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

        List<JigsawStudioCapture.ChunkCaptureArea> areas = JigsawStudioCapture.chunkIntersections(bounds);

        assertEquals(List.of(
                new JigsawStudioCapture.ChunkCaptureArea(-1, 0, 0, 5, 0, 7),
                new JigsawStudioCapture.ChunkCaptureArea(-1, 1, 0, 5, 7, 20),
                new JigsawStudioCapture.ChunkCaptureArea(0, 0, 5, 21, 0, 7),
                new JigsawStudioCapture.ChunkCaptureArea(0, 1, 5, 21, 7, 20),
                new JigsawStudioCapture.ChunkCaptureArea(1, 0, 21, 25, 0, 7),
                new JigsawStudioCapture.ChunkCaptureArea(1, 1, 21, 25, 7, 20)
        ), areas);
        for (int x = 0; x < bounds.dimensions().width(); x++) {
            for (int z = 0; z < bounds.dimensions().depth(); z++) {
                int matches = 0;
                for (JigsawStudioCapture.ChunkCaptureArea area : areas) {
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
        List<JigsawStudioCapture.ChunkCaptureArea> areas = JigsawStudioCapture.chunkIntersections(bounds);
        PlatformBlockState stone = mock(PlatformBlockState.class);
        when(stone.key()).thenReturn("minecraft:stone");
        JigsawStudioCapture.ChunkSnapshot first = new JigsawStudioCapture.ChunkSnapshot(
                areas.getFirst(),
                List.of(new JigsawStudioCapture.CapturedBlock(0, 0, 0, stone, null)),
                List.of());
        JigsawStudioCapture.ChunkSnapshot second = new JigsawStudioCapture.ChunkSnapshot(
                areas.getLast(),
                List.of(new JigsawStudioCapture.CapturedBlock(8, 1, 0, stone, null)),
                List.of());

        JigsawStudioCapture.Capture forward = JigsawStudioCapture.aggregateSnapshots(
                bounds, areas, List.of(first, second));
        JigsawStudioCapture.Capture reverse = JigsawStudioCapture.aggregateSnapshots(
                bounds, areas, List.of(second, first));

        assertArrayEquals(forward.objectContent(), reverse.objectContent());
        assertFalse(forward.hasBlockEntities());
        assertTrue(forward.connectors().isEmpty());
        assertThrows(IOException.class,
                () -> JigsawStudioCapture.aggregateSnapshots(bounds, areas, List.of(first)));
    }

    @Test
    public void saveRegistrationIsRequestScoped() {
        JigsawStudioService service = new JigsawStudioService();
        UUID firstRequest = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID secondRequest = UUID.fromString("55555555-5555-5555-5555-555555555555");

        assertEquals(JigsawStudioSaveLifecycle.SaveStart.STARTED, service.saveLifecycle.tryBeginSave(firstRequest));
        assertEquals(JigsawStudioSaveLifecycle.SaveStart.IN_PROGRESS, service.saveLifecycle.tryBeginSave(firstRequest));
        assertEquals(JigsawStudioSaveLifecycle.SaveStart.STARTED, service.saveLifecycle.tryBeginSave(secondRequest));

        service.saveLifecycle.finishSave(firstRequest);
        assertEquals(JigsawStudioSaveLifecycle.SaveStart.STARTED, service.saveLifecycle.tryBeginSave(firstRequest));
        service.saveLifecycle.finishSave(firstRequest);
        service.saveLifecycle.finishSave(secondRequest);
    }

    private static IrisJigsawConnector connector() {
        return new IrisJigsawConnector()
                .setPosition(new IrisPosition(0, 0, 0))
                .setDirection(IrisDirection.NORTH_NEGATIVE_Z)
                .setTop(IrisDirection.UP_POSITIVE_Y)
                .setPool("fort/start")
                .setName("iris:door")
                .setTargetName("iris:door")
                .setJoint(JigsawJoint.ALIGNED)
                .setFinalState("minecraft:air");
    }

    private static IrisJigsawConnector connectorAt(int x, int y, int z) {
        return connector().setPosition(new IrisPosition(x, y, z));
    }

    private static IrisJigsawConnector planarConnector(
            JigsawStudioCellDimensions dimensions,
            IrisDirection direction
    ) {
        IrisPosition size = new IrisPosition(
                dimensions.width(),
                dimensions.height(),
                dimensions.depth());
        return connector()
                .setPosition(IrisJigsawConnector.canonicalPlanarPosition(size, direction))
                .setDirection(direction);
    }

    private static List<IrisJigsawConnector> planarConnectors(
            JigsawStudioCellDimensions dimensions,
            List<IrisDirection> directions
    ) {
        List<IrisJigsawConnector> connectors = new ArrayList<>(directions.size());
        for (IrisDirection direction : directions) {
            connectors.add(planarConnector(dimensions, direction));
        }
        return connectors;
    }

    private static IrisJigsawPiece pieceWithPlanarConnectors(
            JigsawStudioCellDimensions dimensions,
            List<IrisDirection> directions
    ) {
        IrisJigsawPiece piece = new IrisJigsawPiece().setConnectors(new KList<>());
        piece.getConnectors().addAll(planarConnectors(dimensions, directions));
        return piece;
    }

    private static List<IrisDirection> connectorDirections(List<IrisJigsawConnector> connectors) {
        List<IrisDirection> directions = new ArrayList<>(connectors.size());
        for (IrisJigsawConnector connector : connectors) {
            directions.add(connector.getDirection());
        }
        return directions;
    }

    private static JigsawStudioCapture.CapturedConnector displayedConnector(
            IrisJigsawConnector source,
            JigsawStudioCellDimensions displayDimensions,
            int displayRotationQuarterTurns,
            IrisObjectRotation displayRotation,
            String finalState
    ) {
        IrisPosition displayedPosition = displayedPosition(
                source.getPosition(),
                displayDimensions,
                displayRotationQuarterTurns);
        return new JigsawStudioCapture.CapturedConnector(
                displayedPosition.getX(),
                displayedPosition.getY(),
                displayedPosition.getZ(),
                displayRotation.rotate(source.getDirection()),
                displayRotation.rotate(source.getTop()),
                source.getPool(),
                source.getName(),
                source.getTargetName(),
                source.getChannel(),
                source.getJoint(),
                finalState,
                source.getSelectionPriority(),
                source.getPlacementPriority());
    }

    private static IrisPosition displayedPosition(
            IrisPosition source,
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
            case 1 -> new IrisPosition(sourceDepth - 1 - source.getZ(), source.getY(), source.getX());
            case 2 -> new IrisPosition(
                    sourceWidth - 1 - source.getX(),
                    source.getY(),
                    sourceDepth - 1 - source.getZ());
            case 3 -> new IrisPosition(source.getZ(), source.getY(), sourceWidth - 1 - source.getX());
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

    private static Directional directionalBlockData(BlockFace initialFacing) {
        return directionalBlockData(Material.OBSERVER, initialFacing);
    }

    private static Directional directionalBlockData(Material material, BlockFace initialFacing) {
        AtomicReference<BlockFace> facing = new AtomicReference<>(initialFacing);
        Directional data = mock(Directional.class);
        when(data.getFacing()).thenAnswer(invocation -> facing.get());
        when(data.getFaces()).thenReturn(Set.of(
                BlockFace.NORTH,
                BlockFace.EAST,
                BlockFace.SOUTH,
                BlockFace.WEST));
        doAnswer(invocation -> {
            facing.set(invocation.getArgument(0, BlockFace.class));
            return null;
        }).when(data).setFacing(any(BlockFace.class));
        when(data.getMaterial()).thenReturn(material);
        when(data.getAsString()).thenAnswer(invocation ->
                "minecraft:" + material.name().toLowerCase(Locale.ROOT) + "[facing="
                        + facing.get().name().toLowerCase(Locale.ROOT) + "]");
        when(data.clone()).thenAnswer(invocation -> directionalBlockData(material, facing.get()));
        return data;
    }

    private static IrisObject readCapturedObject(
            byte[] content,
            PlatformBlockState restoredState
    ) throws Throwable {
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block(anyString())).thenReturn(restoredState);
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.unbind();
        IrisPlatforms.bind(platform);
        try {
            IrisObject restored = new IrisObject();
            restored.read(new ByteArrayInputStream(content));
            return restored;
        } finally {
            IrisPlatforms.unbind();
        }
    }

    private static void assertMutationHandler(String methodName, Class<?> eventType) throws NoSuchMethodException {
        assertHandler(methodName, eventType, EventPriority.MONITOR, true);
    }

    private static void assertHandler(
            String methodName,
            Class<?> eventType,
            EventPriority priority,
            boolean ignoreCancelled
    ) throws NoSuchMethodException {
        Method method = JigsawStudioProtectionListener.class.getMethod(methodName, eventType);
        EventHandler annotation = method.getAnnotation(EventHandler.class);
        assertNotNull(annotation);
        assertEquals(priority, annotation.priority());
        assertEquals(ignoreCancelled, annotation.ignoreCancelled());
    }

    private static BlockData blockData(Material material, String state) {
        BlockData blockData = mock(BlockData.class);
        when(blockData.getMaterial()).thenReturn(material);
        when(blockData.getAsString()).thenReturn(state);
        return blockData;
    }
}
