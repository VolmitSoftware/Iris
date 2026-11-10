package art.arcane.iris.core.service;

import art.arcane.iris.core.project.IrisProject;
import art.arcane.iris.core.runtime.StudioOpenCoordinator;
import art.arcane.iris.core.runtime.StudioSlotOccupant;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCompatibilityTarget;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioLayout;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioMode;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariantCatalog;
import art.arcane.iris.engine.platform.PlatformChunkGenerator;
import art.arcane.iris.util.common.plugin.VolmitSender;
import com.volmit.ModuleLog;
import org.bukkit.entity.Player;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StudioSVCJigsawProtectionTest {
    private static final UUID OWNER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private JigsawStudioService jigsawService;

    @Before
    public void startJigsawService() {
        jigsawService = new JigsawStudioService(mock(ModuleLog.class));
    }

    @After
    public void clearLifecycle() {
        jigsawService.registry().finishOpen(OWNER);
        jigsawService.registry().deactivate("overworld");
    }

    @Test
    public void directStudioCloseRequiresJigsawCloseAuthorization() {
        JigsawStudioRequest request = activateOwnedStudio();
        AtomicBoolean closed = new AtomicBoolean();
        StudioSlotOccupant occupant = occupantOf(request, closed);
        StudioSVC studio = new StudioSVC();
        studio.occupyStudioSlot(occupant, () -> CompletableFuture.completedFuture(null)).join();

        CompletionException blocked = assertThrows(CompletionException.class, () -> studio.close().join());

        assertTrue(blocked.getCause().getMessage().contains("owner-controlled"));
        assertFalse(closed.get());

        assertEquals(
                JigsawStudioService.CloseStart.STARTED,
                jigsawService.tryBeginClose(request.requestId(), OWNER, false));

        assertTrue(studio.close().join().successful());
        assertTrue(closed.get());
    }

    @Test
    public void ordinaryStudioCloseBehaviorIsUnchanged() throws ReflectiveOperationException {
        IrisProject project = mock(IrisProject.class);
        when(project.getName()).thenReturn("ordinary");
        StudioOpenCoordinator.StudioCloseResult closeResult = successfulClose();
        when(project.close()).thenReturn(CompletableFuture.completedFuture(closeResult));
        StudioSVC studio = new StudioSVC();
        setActiveProject(studio, project);

        assertEquals(closeResult, studio.close().join());
        assertNull(studio.getActiveProject());
        verify(project).close();
    }

    @Test
    public void anOpenJigsawStudioIsTheOpenStudioProjectToEveryoneWhoAsks() {
        JigsawStudioRequest request = activateOwnedStudio();
        AtomicBoolean closed = new AtomicBoolean();
        StudioSVC studio = new StudioSVC();
        StudioSlotOccupant occupant = occupantOf(request, closed);
        studio.occupyStudioSlot(occupant, () -> CompletableFuture.completedFuture(null)).join();

        assertFalse(studio.isProjectOpen());
        occupant.project().setActiveProvider(mock(PlatformChunkGenerator.class));
        assertTrue(studio.isProjectOpen());
        assertSame(occupant.project(), studio.getActiveProject());

        jigsawService.tryBeginClose(request.requestId(), OWNER, false);
        studio.close().join();
        occupant.project().setActiveProvider(null);
        assertFalse(studio.isProjectOpen());
        assertNull(studio.getActiveProject());
    }

    @Test
    public void ownerCanReplaceJigsawStudioThroughOrdinaryStudioOpen() {
        JigsawStudioRequest request = activateOwnedStudio();
        AtomicBoolean closed = new AtomicBoolean();
        StudioSVC studio = new StudioSVC();
        studio.occupyStudioSlot(occupantOf(request, closed), () -> CompletableFuture.completedFuture(null)).join();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(OWNER);
        VolmitSender sender = mock(VolmitSender.class);
        when(sender.isPlayer()).thenReturn(true);
        when(sender.player()).thenReturn(player);

        assertTrue(studio.closeActiveProjectForReplacement(sender).join().successful());
        assertTrue(closed.get());
    }

    @Test
    public void aStudioThatRefusesToCloseKeepsTheSlotAndNeverClosesTheProject() throws ReflectiveOperationException {
        JigsawStudioRequest request = activateOwnedStudio();
        AtomicBoolean closed = new AtomicBoolean();
        StudioSVC studio = new StudioSVC();
        studio.occupyStudioSlot(occupantOf(request, closed), () -> CompletableFuture.completedFuture(null)).join();
        IrisProject project = mock(IrisProject.class);
        setActiveProject(studio, project);

        assertThrows(CompletionException.class, () -> studio.close().join());

        verify(project, never()).close();
        assertFalse(closed.get());
    }

    /** The occupant the Bukkit host makes of a tool world: it asks the studio and closes the world. */
    private StudioSlotOccupant occupantOf(JigsawStudioRequest request, AtomicBoolean closed) {
        IrisProject project = new IrisProject(new File("overworld"));
        return new StudioSlotOccupant() {
            @Override
            public IrisProject project() {
                return project;
            }

            @Override
            public Optional<String> closeRefusal() {
                return jigsawService.closeRefusal(request.requestId());
            }

            @Override
            public CompletableFuture<Void> prepareReplacement(Optional<UUID> requestedBy) {
                return jigsawService.closing(request.requestId(), requestedBy.map(id ->
                        new FakeToolPlayer(id, "owner", new FakeToolWorld("jigsaw"))));
            }

            @Override
            public CompletableFuture<Void> close() {
                closed.set(true);
                return CompletableFuture.completedFuture(null);
            }
        };
    }

    private JigsawStudioRequest activateOwnedStudio() {
        JigsawStudioRegistry registry = jigsawService.registry();
        assertTrue(registry.tryBeginOpen(OWNER));
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(16, 16, 16);
        JigsawStudioRequest request = registry.activate(
                "overworld",
                "stronghold",
                JigsawStudioMode.SPATIAL_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                dimensions,
                StudioToolPacks.toolPack(),
                JigsawStudioLayout.create(
                        JigsawStudioMode.SPATIAL_JIGSAW,
                        dimensions,
                        JigsawStudioVariantCatalog.empty()),
                OWNER);
        registry.finishOpen(OWNER);
        return request;
    }

    private static StudioOpenCoordinator.StudioCloseResult successfulClose() {
        return new StudioOpenCoordinator.StudioCloseResult(null, true, true, false, null);
    }

    private static void setActiveProject(StudioSVC studio, IrisProject project)
            throws ReflectiveOperationException {
        Field field = StudioSVC.class.getDeclaredField("activeProject");
        field.setAccessible(true);
        field.set(studio, project);
    }
}
