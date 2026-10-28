package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.studio.generation.JigsawStudioGenerator;
import art.arcane.iris.world.task.J;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JigsawStudioToolbeltTest {
    private static final UUID REQUEST = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final JigsawStudioToolPayload DELETE = JigsawStudioToolPayload.variant(
            JigsawStudioToolAction.DELETE_VARIANT, REQUEST, "workcell/end", "test/end");

    @Test
    public void heldUseRepeatsCannotConfirmADestructiveTool() {
        JigsawStudioToolbelt.ToolConfirmation armed =
                new JigsawStudioToolbelt.ToolConfirmation(DELETE, 1_000_000_000L, 11_000_000_000L);

        assertEquals(JigsawStudioToolbelt.ToolConfirmationState.ARMED,
                JigsawStudioToolbelt.confirmationState(null, DELETE, 1_000_000_000L));
        assertEquals(JigsawStudioToolbelt.ToolConfirmationState.HELD,
                JigsawStudioToolbelt.confirmationState(armed, DELETE, 1_200_000_000L));
        assertEquals(JigsawStudioToolbelt.ToolConfirmationState.CONFIRMED,
                JigsawStudioToolbelt.confirmationState(armed, DELETE, 1_600_000_000L));
        assertEquals(JigsawStudioToolbelt.ToolConfirmationState.ARMED,
                JigsawStudioToolbelt.confirmationState(armed, DELETE, 12_000_000_000L));
        assertEquals(JigsawStudioToolbelt.ToolConfirmationState.ARMED,
                JigsawStudioToolbelt.confirmationState(
                        armed,
                        JigsawStudioToolPayload.request(JigsawStudioToolAction.DELETE_PROJECT, REQUEST),
                        1_600_000_000L));
    }

    @Test
    public void variantBoundSticksRequireTheirBoundVariant() {
        assertTrue(JigsawStudioToolbelt.requiresBoundVariant(JigsawStudioToolPayload.variant(
                JigsawStudioToolAction.TOGGLE_ROTATION, REQUEST, "workcell/end", "test/end")));
        assertTrue(JigsawStudioToolbelt.requiresBoundVariant(JigsawStudioToolPayload.variant(
                JigsawStudioToolAction.EXPAND_TO_CELL, REQUEST, "workcell/end", "test/end")));
        assertTrue(JigsawStudioToolbelt.requiresBoundVariant(JigsawStudioToolPayload.variant(
                JigsawStudioToolAction.DUPLICATE_VARIANT, REQUEST, "workcell/end", "test/end")));
        assertFalse(JigsawStudioToolbelt.requiresBoundVariant(JigsawStudioToolPayload.workcell(
                JigsawStudioToolAction.CREATE_VARIANT, REQUEST, "workcell/end")));
        assertFalse(JigsawStudioToolbelt.requiresBoundVariant(JigsawStudioToolPayload.request(
                JigsawStudioToolAction.CONNECTOR_TOOL, REQUEST)));
    }

    @Test
    public void staleRotationStickDoesNotToggleTheNewlyLoadedVariant() throws Exception {
        JigsawStudioVariant first = variant("test/end");
        JigsawStudioVariant second = variant("test/end-b");
        JigsawStudioLayout layout = JigsawStudioLayout.create(
                JigsawStudioMode.PLANAR_JIGSAW,
                new JigsawStudioCellDimensions(16, 16, 16),
                new JigsawStudioVariantCatalog(List.of(second, first)));
        JigsawStudioSession session = new JigsawStudioSession(REQUEST, "overworld", "test/project", layout);
        assertEquals("test/end-b", session.activeVariant("workcell/end").orElseThrow().pieceKey());
        JigsawStudioService service = new JigsawStudioService();
        JigsawStudioVariantProperties properties = mock(JigsawStudioVariantProperties.class);
        setField(service, "variantProperties", properties);
        Player player = player(service, session);

        try (MockedStatic<J> scheduling = mockStatic(J.class)) {
            scheduling.when(() -> J.isOwnedByCurrentRegion(any(Player.class))).thenReturn(true);

            boolean used = service.toolbelt.useTool(
                    player,
                    JigsawStudioToolPayload.variant(
                            JigsawStudioToolAction.TOGGLE_ROTATION, REQUEST, "workcell/end", "test/end"),
                    null,
                    false);

            assertFalse(used);
            verify(properties, never()).toggleVariantRotatable(any(Player.class), anyString());
        }
    }

    private static Player player(JigsawStudioService service, JigsawStudioSession session) throws Exception {
        UUID worldId = UUID.randomUUID();
        World world = mock(World.class);
        when(world.getUID()).thenReturn(worldId);
        Player player = mock(Player.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        JigsawStudioActivation.Request request = new JigsawStudioActivation.Request(
                REQUEST,
                "overworld",
                "test/project",
                JigsawStudioMode.PLANAR_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                new JigsawStudioCellDimensions(16, 16, 16),
                mock(IrisData.class),
                null);
        JigsawStudioGenerator generator = mock(JigsawStudioGenerator.class);
        when(generator.getRequest()).thenReturn(request);
        when(generator.getSession()).thenReturn(session);
        when(generator.getLayout()).thenAnswer(invocation -> session.layout());
        Class<?> studioType = Class.forName(JigsawStudioService.class.getName() + "$ActiveStudio");
        Constructor<?> constructor = studioType.getDeclaredConstructor(
                UUID.class,
                World.class,
                Engine.class,
                JigsawStudioGenerator.class,
                ConcurrentHashMap.class,
                Set.class,
                AtomicLong.class);
        constructor.setAccessible(true);
        Object studio = constructor.newInstance(
                worldId,
                world,
                mock(Engine.class),
                generator,
                new ConcurrentHashMap<>(),
                ConcurrentHashMap.newKeySet(),
                new AtomicLong());
        @SuppressWarnings("unchecked")
        Map<UUID, Object> studios = (Map<UUID, Object>) getField(service, "studios");
        studios.put(worldId, studio);
        return player;
    }

    private static JigsawStudioVariant variant(String pieceKey) {
        return new JigsawStudioVariant(
                pieceKey,
                pieceKey,
                "",
                Optional.of(new JigsawStudioCellDimensions(16, 16, 16)),
                JigsawStudioMode.PLANAR_JIGSAW,
                Optional.of(JigsawPlanarTopology.NORTH_END),
                true,
                true,
                List.of(),
                new JigsawStudioPieceRules(0, 30, 0, 0, false),
                List.of());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
