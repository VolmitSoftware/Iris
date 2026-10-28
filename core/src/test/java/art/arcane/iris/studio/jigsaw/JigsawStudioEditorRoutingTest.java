package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.studio.generation.JigsawStudioGenerator;
import art.arcane.iris.world.task.J;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPistonExtendEvent;
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

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JigsawStudioEditorRoutingTest {
    private static final UUID REQUEST = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @Test
    public void renamingASpatialWorkcellRenamesTheVariantThatLabelsIt() throws Exception {
        JigsawStudioVariant variant = new JigsawStudioVariant(
                "demo/cross",
                "demo/cross",
                "",
                Optional.of(new JigsawStudioCellDimensions(15, 15, 15)),
                JigsawStudioMode.SPATIAL_JIGSAW,
                Optional.empty(),
                true,
                true,
                List.of(),
                new JigsawStudioPieceRules(0, 30, 0, 0, false),
                List.of());
        JigsawStudioLayout layout = JigsawStudioLayout.create(
                JigsawStudioMode.SPATIAL_JIGSAW,
                new JigsawStudioCellDimensions(15, 15, 15),
                new JigsawStudioVariantCatalog(List.of(variant)));
        JigsawStudioSession session = new JigsawStudioSession(REQUEST, "overworld", "demo", layout);
        JigsawStudioService service = new JigsawStudioService();
        JigsawStudioVariantProperties properties = mock(JigsawStudioVariantProperties.class);
        setField(service, "variantProperties", properties);
        Player player = register(service, session);

        try (MockedStatic<J> scheduling = mockStatic(J.class)) {
            scheduling.when(() -> J.isOwnedByCurrentRegion(any(Player.class))).thenReturn(true);

            service.workcellEditor.updateWorkcellDisplayName(player, JigsawStudioLayout.SPATIAL_WORKCELL_ID, "Great Hall");

            verify(properties).updateVariantDisplayName(
                    player, JigsawStudioLayout.SPATIAL_WORKCELL_ID, "demo/cross", "Great Hall");
        }
    }

    @Test
    public void pistonHeadsCannotExtendIntoProtectedStudioCells() throws Exception {
        JigsawStudioService service = new JigsawStudioService();
        JigsawStudioProtection protection = mock(JigsawStudioProtection.class);
        setField(service, "protection", protection);
        World world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        Block piston = mock(Block.class);
        Block head = mock(Block.class);
        when(piston.getWorld()).thenReturn(world);
        when(piston.getRelative(BlockFace.EAST)).thenReturn(head);
        when(protection.isImmutableStudioBlock(head)).thenReturn(true);
        BlockPistonExtendEvent event = new BlockPistonExtendEvent(piston, List.of(), BlockFace.EAST);

        service.protectionListener.onProtectedPistonExtend(event);

        assertTrue(event.isCancelled());
    }

    private static Player register(JigsawStudioService service, JigsawStudioSession session) throws Exception {
        UUID worldId = UUID.randomUUID();
        World world = mock(World.class);
        when(world.getUID()).thenReturn(worldId);
        Player player = mock(Player.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        JigsawStudioActivation.Request request = new JigsawStudioActivation.Request(
                REQUEST,
                "overworld",
                "demo",
                JigsawStudioMode.SPATIAL_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                new JigsawStudioCellDimensions(15, 15, 15),
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
