package art.arcane.iris.studio;

import art.arcane.iris.studio.jigsaw.JigsawStudioService;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.studio.workspace.IrisProject;
import art.arcane.iris.studio.object.StudioOpenCoordinator;
import art.arcane.iris.studio.jigsaw.JigsawStudioActivation;
import art.arcane.iris.studio.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.studio.jigsaw.JigsawStudioCompatibilityTarget;
import art.arcane.iris.studio.jigsaw.JigsawStudioLayout;
import art.arcane.iris.studio.jigsaw.JigsawStudioMode;
import art.arcane.iris.studio.jigsaw.JigsawStudioVariantCatalog;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.platform.bukkit.plugin.VolmitPlugin;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StudioSVCJigsawProtectionTest {
    private static final UUID OWNER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private JigsawStudioService jigsawService;
    private MockedStatic<BukkitPlatform> bukkitPlatform;

    @Before
    public void enableJigsawService() {
        VolmitPlugin plugin = mock(VolmitPlugin.class);
        bukkitPlatform = mockStatic(BukkitPlatform.class);
        bukkitPlatform.when(BukkitPlatform::volmitPlugin).thenReturn(plugin);
        jigsawService = new JigsawStudioService();
        jigsawService.onEnable();
    }

    @After
    public void clearLifecycle() {
        jigsawService.onDisable();
        JigsawStudioActivation.finishOpen(OWNER);
        JigsawStudioActivation.deactivate("overworld");
        bukkitPlatform.close();
    }

    @Test
    public void directStudioCloseRequiresJigsawCloseAuthorization() throws ReflectiveOperationException {
        JigsawStudioActivation.Request request = activateOwnedStudio();
        IrisProject project = mock(IrisProject.class);
        when(project.getName()).thenReturn("overworld");
        StudioSVC studio = new StudioSVC();
        setActiveProject(studio, project);

        CompletionException blocked = assertThrows(CompletionException.class, () -> studio.close().join());

        assertTrue(blocked.getCause().getMessage().contains("owner-controlled"));
        verify(project, never()).close();

        assertEquals(
                JigsawStudioService.CloseStart.STARTED,
                jigsawService.tryBeginClose(request.requestId(), OWNER, false));
        StudioOpenCoordinator.StudioCloseResult closeResult = successfulClose();
        when(project.close()).thenReturn(CompletableFuture.completedFuture(closeResult));

        assertEquals(closeResult, studio.close().join());
        assertNull(studio.getActiveProject());
        verify(project).close();
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
    public void ownerCanReplaceJigsawStudioThroughOrdinaryStudioOpen() throws ReflectiveOperationException {
        activateOwnedStudio();
        IrisProject project = mock(IrisProject.class);
        when(project.getName()).thenReturn("overworld");
        StudioOpenCoordinator.StudioCloseResult closeResult = successfulClose();
        when(project.close()).thenReturn(CompletableFuture.completedFuture(closeResult));
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(OWNER);
        VolmitSender sender = mock(VolmitSender.class);
        when(sender.isPlayer()).thenReturn(true);
        when(sender.player()).thenReturn(player);
        StudioSVC studio = new StudioSVC();
        setActiveProject(studio, project);

        assertEquals(closeResult, studio.closeActiveProjectForReplacement(sender).join());
        assertNull(studio.getActiveProject());
        verify(project).close();
    }

    private static JigsawStudioActivation.Request activateOwnedStudio() {
        assertTrue(JigsawStudioActivation.tryBeginOpen(OWNER));
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(16, 16, 16);
        JigsawStudioActivation.Request request = JigsawStudioActivation.activate(
                "overworld",
                "stronghold",
                JigsawStudioMode.SPATIAL_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                dimensions,
                mock(IrisData.class),
        JigsawStudioLayout.create(
                JigsawStudioMode.SPATIAL_JIGSAW,
                dimensions,
                JigsawStudioVariantCatalog.empty()),
                OWNER);
        JigsawStudioActivation.finishOpen(OWNER);
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
