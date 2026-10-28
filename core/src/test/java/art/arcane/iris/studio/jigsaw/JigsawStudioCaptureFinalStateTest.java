package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.pack.value.IrisDirection;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.jigsaw.JigsawJoint;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class JigsawStudioCaptureFinalStateTest {
    @Test
    public void hiddenConnectorOverDugOutAirStoresExplicitAirLikeVisibleMarkers() throws Throwable {
        JigsawStudioBounds bounds = new JigsawStudioBounds(0, 64, 0, new JigsawStudioCellDimensions(1, 1, 1));
        IrisJigsawPiece piece = new IrisJigsawPiece().setConnectors(new KList<>());
        piece.getConnectors().add(new IrisJigsawConnector()
                .setPosition(new IrisPosition(0, 0, 0))
                .setDirection(IrisDirection.NORTH_NEGATIVE_Z)
                .setTop(IrisDirection.UP_POSITIVE_Y)
                .setPool("fort/pieces")
                .setName("iris:door")
                .setTargetName("iris:door")
                .setJoint(JigsawJoint.ALIGNED)
                .setFinalState("minecraft:structure_void"));
        BlockData air = mock(BlockData.class);
        when(air.getMaterial()).thenReturn(Material.AIR);
        when(air.getAsString()).thenReturn("minecraft:air");
        Block block = mock(Block.class);
        when(block.getBlockData()).thenReturn(air);
        World world = mock(World.class);
        when(world.getBlockAt(0, 64, 0)).thenReturn(block);
        JigsawStudioCapture.ChunkCaptureArea area = JigsawStudioCapture.chunkIntersections(bounds).getFirst();

        JigsawStudioCapture.ChunkSnapshot snapshot = JigsawStudioCapture.captureChunkIntersection(
                world, bounds, piece, new IrisObject(1, 1, 1), area, 0, false);
        JigsawStudioCapture.Capture capture = JigsawStudioCapture.aggregateSnapshots(
                bounds, List.of(area), List.of(snapshot));

        assertEquals("minecraft:air", capture.connectors().getFirst().getFinalState());
        NativeBlockState restoredAir = mock(NativeBlockState.class);
        when(restoredAir.key()).thenReturn("minecraft:air");
        IrisObject restored = read(capture.objectContent(), restoredAir);
        assertTrue(restored.getBlocks().containsKey(restored.getSigned(0, 0, 0)));
        assertEquals("minecraft:air", restored.getBlocks().get(restored.getSigned(0, 0, 0)).key());
    }

    @Test
    public void autosaveNotificationsFallBackToTheOnlineOwner() {
        Player explicit = mock(Player.class);
        Player owner = mock(Player.class);
        UUID ownerId = UUID.randomUUID();
        try (MockedStatic<Bukkit> server = mockStatic(Bukkit.class)) {
            server.when(() -> Bukkit.getPlayer(ownerId)).thenReturn(owner);

            assertSame(explicit, JigsawStudioCaptureScheduler.notificationTarget(explicit, ownerId));
            assertSame(owner, JigsawStudioCaptureScheduler.notificationTarget(null, ownerId));
            assertNull(JigsawStudioCaptureScheduler.notificationTarget(null, null));
        }
    }

    private static IrisObject read(byte[] content, NativeBlockState restoredState) throws Throwable {
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
}
