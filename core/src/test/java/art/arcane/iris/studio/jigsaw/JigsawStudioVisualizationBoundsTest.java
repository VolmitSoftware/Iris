package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.placement.PlacedStructurePiece;
import art.arcane.iris.studio.generation.JigsawStudioGenerator;
import art.arcane.iris.world.task.J;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.invocation.Invocation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

public class JigsawStudioVisualizationBoundsTest {
    @Test
    public void livePreviewUsesFullAssemblyBoundsBeyondEveryCellLimit() throws Exception {
        List<JigsawStudioPreviewRenderer.PreviewBounds> bounds = List.of(
                new JigsawStudioPreviewRenderer.PreviewBounds(-300, 60, -400, 300, 80, 400),
                new JigsawStudioPreviewRenderer.PreviewBounds(-10, -50, -10, 10, 220, 10),
                new JigsawStudioPreviewRenderer.PreviewBounds(0, 0, 0, 127, 191, 127));
        Fixture fixture = fixture();
        for (JigsawStudioPreviewRenderer.PreviewBounds preview : bounds) {
            Location viewer = new Location(fixture.world(),
                    preview.maximumX() + 1.0D, preview.maximumY() + 1.0D, preview.maximumZ() + 1.0D);
            evaluate(fixture, preview);
            clearInvocations(fixture.player());

            drawLive(fixture, viewer, 384);

            List<Emission> emissions = emissions(fixture.player());
            assertFalse(emissions.isEmpty());
            assertTrue(emissions.size() <= 384);
            assertTrue(emissions.stream().anyMatch(emission ->
                    emission.x() == viewer.getX() && emission.y() == viewer.getY()
                            && emission.z() == viewer.getZ()));
            for (Emission emission : emissions) {
                double x = emission.x() - viewer.getX();
                double y = emission.y() - viewer.getY();
                double z = emission.z() - viewer.getZ();
                assertTrue(x * x + y * y + z * z <= 96.0D * 96.0D);
            }
        }
    }

    @Test
    public void ordinaryPreviewKeepsCellOutlineParticleOrderAndAppearance() throws Exception {
        Fixture fixture = fixture();
        JigsawStudioBounds bounds = new JigsawStudioBounds(-7, 60, -11,
                new JigsawStudioCellDimensions(13, 9, 17));
        evaluate(fixture, new JigsawStudioPreviewRenderer.PreviewBounds(
                bounds.originX(), bounds.originY(), bounds.originZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()));
        Location viewer = new Location(fixture.world(), 0, 64, 0);
        drawLive(fixture, viewer, 384);
        List<Emission> preview = emissions(fixture.player());
        clearInvocations(fixture.player());

        Method draw = JigsawStudioVisualization.class.getDeclaredMethod("drawBounds", Player.class, Location.class,
                JigsawStudioBounds.class, Color.class, float.class, double.class, visualizationNested("ParticleBudget"));
        draw.setAccessible(true);
        Field color = JigsawStudioVisualization.class.getDeclaredField("ASSEMBLY_PREVIEW_COLOR");
        color.setAccessible(true);
        invoke(draw, null, fixture.player(), viewer, bounds, color.get(null), 0.85F, 2.0D, budget(384));

        assertFalse(preview.isEmpty());
        assertEquals(preview, emissions(fixture.player()));
    }

    @Test
    public void livePreviewRefreshesChangedBoundsAndHonorsExhaustedBudget() throws Exception {
        Fixture fixture = fixture();
        Location viewer = new Location(fixture.world(), 201, 65, 201);
        evaluate(fixture, JigsawStudioPreviewRenderer.PreviewBounds.empty());
        drawLive(fixture, viewer, 384);
        assertTrue(emissions(fixture.player()).isEmpty());

        evaluate(fixture, new JigsawStudioPreviewRenderer.PreviewBounds(0, 64, 0, 200, 64, 200));
        drawLive(fixture, viewer, 0);
        assertTrue(emissions(fixture.player()).isEmpty());
        drawLive(fixture, viewer, 7);
        assertEquals(7, emissions(fixture.player()).size());
    }

    @Test
    public void explicitAssemblyPreviewAcceptsLargePlacedBounds() throws Exception {
        Fixture fixture = fixture();
        PlacedStructurePiece piece = mock(PlacedStructurePiece.class);
        when(piece.getMinX()).thenReturn(-200);
        when(piece.getMinY()).thenReturn(64);
        when(piece.getMinZ()).thenReturn(-200);
        when(piece.getMaxX()).thenReturn(200);
        when(piece.getMaxY()).thenReturn(64);
        when(piece.getMaxZ()).thenReturn(200);
        try (MockedStatic<J> scheduling = mockStatic(J.class)) {
            scheduling.when(() -> J.isOwnedByCurrentRegion(fixture.player())).thenReturn(true);
            assertTrue(fixture.service().showAssemblyPreview(fixture.player(), List.of(piece)));
        }

        Method draw = JigsawStudioVisualization.class.getDeclaredMethod("drawAssemblyPreview", Player.class,
                Location.class, visualizationNested("ParticleBudget"));
        draw.setAccessible(true);
        invoke(draw, fixture.service().visualization, fixture.player(), new Location(fixture.world(), 201, 65, 201), budget(384));

        assertTrue(emissions(fixture.player()).stream().anyMatch(emission ->
                emission.x() == 201 && emission.y() == 65 && emission.z() == 201));
    }

    @Test
    public void authoredCellLimitsRemainEnforced() {
        assertThrows(IllegalArgumentException.class, () -> new JigsawStudioCellDimensions(129, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new JigsawStudioCellDimensions(1, 1, 129));
        assertThrows(IllegalArgumentException.class, () -> new JigsawStudioCellDimensions(1, 193, 1));
        assertThrows(IllegalArgumentException.class, () -> new JigsawStudioCellDimensions(128, 192, 128));
    }

    private static Fixture fixture() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        World world = mock(World.class);
        when(world.getUID()).thenReturn(worldId);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        JigsawStudioActivation.Request request = mock(JigsawStudioActivation.Request.class);
        when(request.requestId()).thenReturn(requestId);
        JigsawStudioGenerator generator = mock(JigsawStudioGenerator.class);
        when(generator.getRequest()).thenReturn(request);
        Constructor<?> constructor = nested("ActiveStudio").getDeclaredConstructor(UUID.class, World.class,
                Engine.class, JigsawStudioGenerator.class, ConcurrentHashMap.class, Set.class, AtomicLong.class);
        constructor.setAccessible(true);
        Object studio = constructor.newInstance(worldId, world, mock(Engine.class), generator,
                new ConcurrentHashMap<>(), ConcurrentHashMap.newKeySet(), new AtomicLong());
        JigsawStudioService service = new JigsawStudioService();
        map(service, JigsawStudioService.class, "studios").put(worldId, studio);
        return new Fixture(service, player, world, requestId, studio);
    }

    private static void evaluate(Fixture fixture, JigsawStudioPreviewRenderer.PreviewBounds bounds) throws Exception {
        map(fixture.service().evaluator, JigsawStudioEvaluator.class, "evaluations").put(fixture.requestId(), new JigsawStudioGraphEvaluation(
                fixture.requestId(), 1, 1337, JigsawStudioEvaluationState.VALID, "", 2, "", bounds));
    }

    private static void drawLive(Fixture fixture, Location viewer, int remaining) throws Exception {
        Method draw = JigsawStudioVisualization.class.getDeclaredMethod("drawLivePreview", Player.class, Location.class,
                nested("ActiveStudio"), visualizationNested("ParticleBudget"));
        draw.setAccessible(true);
        invoke(draw, fixture.service().visualization, fixture.player(), viewer, fixture.studio(), budget(remaining));
    }

    private static Object budget(int remaining) throws Exception {
        Constructor<?> constructor = visualizationNested("ParticleBudget").getDeclaredConstructor(int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(remaining);
    }

    private static Class<?> nested(String name) throws ClassNotFoundException {
        return Class.forName(JigsawStudioService.class.getName() + "$" + name);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Object> map(Object target, Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<UUID, Object>) field.get(target);
    }

    private static Class<?> visualizationNested(String name) throws ClassNotFoundException {
        return Class.forName(JigsawStudioVisualization.class.getName() + "$" + name);
    }

    private static void invoke(Method method, Object target, Object... arguments) throws Exception {
        try {
            method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw failure;
        }
    }

    private static List<Emission> emissions(Player player) {
        List<Emission> emissions = new ArrayList<>();
        for (Invocation invocation : mockingDetails(player).getInvocations()) {
            if (!invocation.getMethod().getName().equals("spawnParticle")) {
                continue;
            }
            Particle.DustOptions dust = invocation.getArgument(9);
            emissions.add(new Emission(invocation.getArgument(1), invocation.getArgument(2),
                    invocation.getArgument(3), dust.getColor(), dust.getSize()));
        }
        return emissions;
    }

    private record Fixture(JigsawStudioService service, Player player, World world, UUID requestId, Object studio) {
    }

    private record Emission(double x, double y, double z, Color color, float size) {
    }
}
