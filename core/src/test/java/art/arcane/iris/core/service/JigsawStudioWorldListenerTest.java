package art.arcane.iris.core.service;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.runtime.jigsaw.JigsawPlanarTopology;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBay;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCompatibilityTarget;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioControlPosition;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioLayout;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioMode;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioPieceRules;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioSession;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariant;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariantCatalog;
import art.arcane.iris.engine.platform.studio.generators.JigsawStudioGenerator;
import com.volmit.ModuleLog;
import com.volmit.block.BlockState;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioToolAction;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioToolPayload;
import com.volmit.geometry.BlockPosition;
import com.volmit.geometry.ChunkPos;
import com.volmit.tooling.world.ToolText;
import com.volmit.tooling.world.BlockChange;
import com.volmit.tooling.world.BlockChange.Cause;
import com.volmit.tooling.world.ToolWorldListener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** What a studio world's listener decides for each cause of a block change, in a bay that is editable and in ones that are not. */
public class JigsawStudioWorldListenerTest {
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Set<Cause> PLAYER_EDIT_CAUSES = EnumSet.of(Cause.PLACE, Cause.BREAK, Cause.BUCKET);
    private static final Set<Cause> WORLD_CAUSES = EnumSet.complementOf(
            EnumSet.of(Cause.PLACE, Cause.BREAK, Cause.BUCKET, Cause.CONTAINER, Cause.MACHINE));
    private static final String EDITABLE_BAY = "workcell/end";
    private static final String SECOND_EDITABLE_BAY = "workcell/straight";
    private static final String EMPTY_BAY = "workcell/blank";

    private final JigsawStudioService service = new JigsawStudioService(mock(ModuleLog.class));
    private final FakeToolWorld world = new FakeToolWorld("jigsaw");
    private final FakeToolPlayer owner = new FakeToolPlayer(OWNER, "owner", world);
    private final FakeToolPlayer other = new FakeToolPlayer(OTHER, "other", world);
    private JigsawStudioLayout layout;
    private JigsawStudioSession session;
    private JigsawStudioService.ActiveStudio studio;
    private UUID requestId;
    private ToolWorldListener listener;

    @Before
    public void openStudio() throws ReflectiveOperationException {
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(16, 16, 16);
        layout = JigsawStudioLayout.create(
                JigsawStudioMode.PLANAR_JIGSAW,
                dimensions,
                new JigsawStudioVariantCatalog(List.of(
                        ownedVariant("test/end", JigsawPlanarTopology.NORTH_END),
                        ownedVariant("test/straight", JigsawPlanarTopology.NORTH_SOUTH_STRAIGHT))));
        assertTrue(service.registry().tryBeginOpen(OWNER));
        JigsawStudioRequest request = service.registry().activate(
                "overworld",
                "village",
                JigsawStudioMode.PLANAR_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                dimensions,
                mock(IrisData.class),
                layout,
                OWNER);
        service.registry().finishOpen(OWNER);
        session = service.registry().getSession(request.requestId());
        assertTrue(session.activeVariant(EDITABLE_BAY).isPresent());
        assertTrue(session.activeVariant(EMPTY_BAY).isEmpty());
        JigsawStudioGenerator generator = mock(JigsawStudioGenerator.class);
        when(generator.getRequest()).thenReturn(request);
        when(generator.getSession()).thenReturn(session);
        when(generator.getLayout()).thenReturn(layout);
        when(generator.renderBay(any(JigsawStudioBay.class))).thenReturn(
                JigsawStudioGenerator.RenderedBay.empty(dimensions));
        JigsawStudioService.ActiveStudio studio = new JigsawStudioService.ActiveStudio(
                request.requestId(),
                world,
                generator,
                new ConcurrentHashMap<>(),
                ConcurrentHashMap.newKeySet(),
                new AtomicLong());
        Field studiosField = JigsawStudioService.class.getDeclaredField("studios");
        studiosField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, JigsawStudioService.ActiveStudio> studios =
                (Map<UUID, JigsawStudioService.ActiveStudio>) studiosField.get(service);
        studios.put(request.requestId(), studio);
        this.studio = studio;
        this.requestId = request.requestId();
        listener = new JigsawStudioWorldListener(service, request.requestId());
    }

    @After
    public void closeStudio() {
        service.registry().deactivate("overworld");
        service.quiesceForServerShutdown();
    }

    @Test
    public void everyCauseIsAllowedAndMarksTheBayDirtyInAnEditableBay() {
        for (Cause cause : Cause.values()) {
            assertTrue(cause.name(), listener.blockChanging(change(cause, owner, inside(EDITABLE_BAY))));
            assertTrue(cause.name(), dirty(EDITABLE_BAY));
            clean(EDITABLE_BAY);
        }
    }

    @Test
    public void causesWithoutAPlayerAreAllowedInAnEditableBay() {
        for (Cause cause : WORLD_CAUSES) {
            assertTrue(cause.name(), listener.blockChanging(change(cause, null, inside(EDITABLE_BAY))));
        }
        assertTrue(listener.blockChanging(change(Cause.MACHINE, null, inside(EDITABLE_BAY))));
    }

    @Test
    public void everyCauseIsRefusedAndLeavesTheBayCleanInABayWithoutAnEditableVariant() {
        for (Cause cause : Cause.values()) {
            assertFalse(cause.name(), listener.blockChanging(change(cause, owner, inside(EMPTY_BAY))));
            assertFalse(cause.name(), dirty(EMPTY_BAY));
        }
    }

    @Test
    public void aPlayerEditInAnUneditableBayTellsThePlayerWhy() {
        assertFalse(listener.blockChanging(change(Cause.PLACE, owner, inside(EMPTY_BAY))));

        assertEquals(
                List.of("[Iris Jigsaw Studio] This empty Jigsaw Studio workcell has no loaded editable variant. Load one before editing it."),
                owner.messages);
    }

    @Test
    public void anotherPlayerCannotEditOrOpenContainersInAnEditableBay() {
        for (Cause cause : PLAYER_EDIT_CAUSES) {
            assertFalse(cause.name(), listener.blockChanging(change(cause, other, inside(EDITABLE_BAY))));
        }
        assertFalse(listener.blockChanging(change(Cause.CONTAINER, other, inside(EDITABLE_BAY))));
        assertFalse(dirty(EDITABLE_BAY));
        assertTrue(other.messages.contains("[Iris Jigsaw Studio] This Jigsaw Studio is owned by another player session."));
        assertTrue(other.messages.contains(
                "[Iris Jigsaw Studio] This Jigsaw Studio container is read-only during the current operation or owner session."));
    }

    @Test
    public void anotherPlayersWorldCausesStillRunInAnEditableBay() {
        for (Cause cause : WORLD_CAUSES) {
            assertTrue(cause.name(), listener.blockChanging(change(cause, other, inside(EDITABLE_BAY))));
        }
    }

    @Test
    public void outsideEveryBayThePlayersEditsAndTheWorldAreLeftAlone() {
        BlockPosition outside = new BlockPosition(100_000, 64, 100_000);
        for (Cause cause : Cause.values()) {
            assertTrue(cause.name(), listener.blockChanging(change(cause, owner, outside)));
        }
        assertFalse(dirty(EDITABLE_BAY));
        assertFalse(dirty(SECOND_EDITABLE_BAY));
    }

    @Test
    public void theControlChestCannotBeRemovedOrChangedByTheWorld() {
        JigsawStudioControlPosition control = layout.controlPosition();
        BlockPosition chest = new BlockPosition(control.worldX(), control.worldY(), control.worldZ());
        world.setBlock(chest, BlockState.parse("minecraft:chest[facing=south]"), Optional.empty());

        assertFalse(listener.blockChanging(change(Cause.BREAK, owner, chest)));
        assertEquals(
                List.of("[Iris Jigsaw Studio] The Jigsaw Studio control chest cannot be removed."),
                owner.messages);
        for (Cause cause : WORLD_CAUSES) {
            assertFalse(cause.name(), listener.blockChanging(change(cause, null, chest)));
        }
        assertFalse(listener.blockChanging(change(Cause.MACHINE, null, chest)));
        assertFalse(listener.blockChanging(change(Cause.CONTAINER, owner, chest)));
    }

    @Test
    public void aPistonIsCheckedAgainstItsPositionsAndMarksTheBlocksItChanged() {
        BlockPosition end = inside(EDITABLE_BAY);
        BlockPosition straight = inside(SECOND_EDITABLE_BAY);
        BlockChange change = new BlockChange(
                Cause.PISTON, List.of(end), List.of(straight), Optional.empty(), Optional.empty());

        assertTrue(listener.blockChanging(change));

        assertFalse(dirty(EDITABLE_BAY));
        assertTrue(dirty(SECOND_EDITABLE_BAY));
    }

    @Test
    public void aPistonThatMovesNothingIsAllowedAndMarksItsOwnBlocks() {
        BlockChange change = new BlockChange(
                Cause.PISTON, List.of(), List.of(inside(EDITABLE_BAY)), Optional.empty(), Optional.empty());

        assertTrue(listener.blockChanging(change));

        assertTrue(dirty(EDITABLE_BAY));
    }

    @Test
    public void aPistonCheckedAgainstAnUneditableBayIsRefusedWhateverItChanged() {
        BlockChange change = new BlockChange(
                Cause.PISTON, List.of(inside(EMPTY_BAY)), List.of(inside(EDITABLE_BAY)), Optional.empty(), Optional.empty());

        assertFalse(listener.blockChanging(change));

        assertFalse(dirty(EDITABLE_BAY));
    }

    @Test
    public void aContainerThatChangedMarksItsBayDirtyAndOneOutsideAnyBayDoesNot() {
        listener.containerChanged(Optional.of(owner), inside(EDITABLE_BAY));
        listener.containerChanged(Optional.empty(), new BlockPosition(100_000, 64, 100_000));
        listener.containerChanged(Optional.empty(), inside(EMPTY_BAY));

        assertTrue(dirty(EDITABLE_BAY));
        assertFalse(dirty(SECOND_EDITABLE_BAY));
        assertFalse(dirty(EMPTY_BAY));
    }

    @Test
    public void aTeleportMakesTheStudioReconcileTheArrivalAtOnceAndEnteringAloneDoesNot() {
        listener.entered(owner);
        assertTrue(owner.sidebars.isEmpty());

        listener.teleported(owner, inside(EDITABLE_BAY));

        assertFalse(owner.sidebars.isEmpty());
        assertTrue(owner.sidebars.getLast().stream()
                .map(ToolText::plainText).anyMatch(line -> line.startsWith("Workcell: ")));
    }

    @Test
    public void aChunkIsLoadedWhenThePlatformSaysSo() {
        world.unloadedChunks.add(new ChunkPos(3, 4));

        assertTrue(studio.isChunkLoaded(0, 0));
        assertFalse(studio.isChunkLoaded(3, 4));
    }

    @Test
    public void aToolIsNotGivenWhenTheInventoryIsFullAndThePlayerIsToldSo() {
        JigsawStudioToolPayload payload = JigsawStudioToolPayload.request(JigsawStudioToolAction.PREVIEW_GRAPH, requestId);
        owner.inventoryFull = true;

        assertFalse(service.giveTool(owner, payload));

        assertTrue(owner.given.isEmpty());
        assertEquals(
                List.of("[Iris Jigsaw Studio] Your inventory is full; no Jigsaw Studio tool was added."),
                owner.messages);
    }

    @Test
    public void aToolThatFitsIsGivenAndAnnounced() {
        JigsawStudioToolPayload payload = JigsawStudioToolPayload.request(JigsawStudioToolAction.PREVIEW_GRAPH, requestId);

        assertTrue(service.giveTool(owner, payload));

        assertEquals(1, owner.given.size());
        assertEquals(
                List.of("[Iris Jigsaw Studio] Added " + JigsawStudioToolAction.PREVIEW_GRAPH.displayName() + " tool."),
                owner.messages);
    }

    @Test
    public void studioMessagesAreWhiteTextWithTheStudioPrefix() {
        assertFalse(listener.blockChanging(change(Cause.PLACE, owner, inside(EMPTY_BAY))));

        assertEquals(
                List.of(ToolText.plain("[Iris Jigsaw Studio] This empty Jigsaw Studio workcell has no loaded editable variant. Load one before editing it.")),
                owner.texts);
    }

    @Test
    public void aMultiBlockChangeMarksTheBaysItTouchesByItsCause() {
        BlockPosition end = inside(EDITABLE_BAY);
        BlockPosition straight = inside(SECOND_EDITABLE_BAY);

        assertTrue(listener.blockChanging(change(Cause.PHYSICS, null, end, straight)));
        assertTrue(dirty(EDITABLE_BAY));
        assertFalse(dirty(SECOND_EDITABLE_BAY));
        clean(EDITABLE_BAY);

        assertTrue(listener.blockChanging(change(Cause.FLUID, null, end, straight)));
        assertFalse(dirty(EDITABLE_BAY));
        assertTrue(dirty(SECOND_EDITABLE_BAY));
        clean(SECOND_EDITABLE_BAY);

        assertTrue(listener.blockChanging(change(Cause.PISTON, null, end, straight)));
        assertTrue(dirty(EDITABLE_BAY));
        assertTrue(dirty(SECOND_EDITABLE_BAY));
    }

    @Test
    public void aChangeThatTouchesAnUneditableBayIsRefusedAsAWhole() {
        assertFalse(listener.blockChanging(change(
                Cause.EXPLOSION, null, inside(EDITABLE_BAY), inside(EMPTY_BAY))));
        assertFalse(dirty(EDITABLE_BAY));
    }

    @Test
    public void aStudioThatIsNotOpenFreezesItsWorld() {
        ToolWorldListener stranger = new JigsawStudioWorldListener(service, UUID.randomUUID());

        for (Cause cause : Cause.values()) {
            assertFalse(cause.name(), stranger.blockChanging(change(cause, owner, inside(EDITABLE_BAY))));
        }
    }

    private BlockChange change(Cause cause, FakeToolPlayer player, BlockPosition... positions) {
        return new BlockChange(cause, List.of(positions), Optional.ofNullable(player), Optional.empty());
    }

    private BlockPosition inside(String bayId) {
        JigsawStudioBay bay = layout.get(bayId);
        return new BlockPosition(
                bay.bounds().originX() + 1,
                bay.bounds().originY() + 1,
                bay.bounds().originZ() + 1);
    }

    private boolean dirty(String bayId) {
        return session.workcellSnapshot(bayId).dirty();
    }

    private void clean(String bayId) {
        JigsawStudioSession.SaveIdentity save = session.beginSave(bayId).identity().orElseThrow();
        assertTrue(session.markWorkcellSaved(save));
    }

    private static JigsawStudioVariant ownedVariant(String key, JigsawPlanarTopology topology) {
        return new JigsawStudioVariant(
                key,
                key,
                "",
                Optional.of(new JigsawStudioCellDimensions(16, 16, 16)),
                JigsawStudioMode.PLANAR_JIGSAW,
                Optional.of(topology),
                true,
                true,
                List.of(),
                new JigsawStudioPieceRules(0, 30, 0, 0, false),
                List.of());
    }
}
