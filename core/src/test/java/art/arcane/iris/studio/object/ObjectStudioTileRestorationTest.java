package art.arcane.iris.studio.object;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineTarget;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.studio.generation.ObjectStudioGenerator.ChunkTiles;
import art.arcane.iris.studio.generation.ObjectStudioGenerator.PlacedTile;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.spi.IrisLogging;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.world.ChunkLoadEvent;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class ObjectStudioTileRestorationTest {
    @Test
    public void restoresGeneratedTileOnlyAfterChunkLoadAndKeepsLaterEdits() {
        Fixture fixture = fixture();
        queue(fixture);

        verifyNoInteractions(fixture.tile());
        fixture.service().onChunkLoad(new ChunkLoadEvent(fixture.chunk(), true));
        fixture.service().onChunkLoad(new ChunkLoadEvent(fixture.chunk(), false));

        verify(fixture.tile(), times(1)).toBukkitTry(fixture.block());
    }

    @Test
    public void failedRestorationRemainsPendingForTheNextOwnedLoad() {
        Fixture fixture = fixture();
        when(fixture.tile().toBukkitTry(fixture.block())).thenReturn(false, true);
        queue(fixture);

        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            fixture.service().onChunkLoad(new ChunkLoadEvent(fixture.chunk(), true));
        }
        fixture.service().onChunkLoad(new ChunkLoadEvent(fixture.chunk(), false));
        fixture.service().onChunkLoad(new ChunkLoadEvent(fixture.chunk(), false));

        verify(fixture.tile(), times(2)).toBukkitTry(fixture.block());
    }

    @Test
    public void retriesOnlyUnrestoredTilesAfterPartialFailure() {
        Fixture fixture = fixture();
        TileData second = mock(TileData.class);
        Block secondBlock = mock(Block.class);
        BlockData secondState = mock(BlockData.class);
        when(fixture.chunk().getBlock(15, 161, 1)).thenReturn(secondBlock);
        when(secondBlock.getBlockData()).thenReturn(secondState);
        when(second.isApplicable(secondState)).thenReturn(true);
        when(second.toBukkitTry(secondBlock)).thenReturn(false, true);
        try (MockedStatic<BukkitWorldBinding> binding = mockStatic(BukkitWorldBinding.class)) {
            binding.when(() -> BukkitWorldBinding.world(fixture.irisWorld())).thenReturn(fixture.world());
            fixture.service().queueTiles(fixture.engine(), new ChunkTiles(-2, 3, List.of(
                    new PlacedTile(14, 161, 1, fixture.tile()), new PlacedTile(15, 161, 1, second))));
        }

        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            fixture.service().onChunkLoad(new ChunkLoadEvent(fixture.chunk(), true));
        }
        fixture.service().onChunkLoad(new ChunkLoadEvent(fixture.chunk(), false));
        fixture.service().onChunkLoad(new ChunkLoadEvent(fixture.chunk(), false));

        verify(fixture.tile(), times(1)).toBukkitTry(fixture.block());
        verify(second, times(2)).toBukkitTry(secondBlock);
    }

    @Test
    public void worldUnregistrationDiscardsPendingTiles() {
        Fixture fixture = fixture();
        queue(fixture);

        fixture.service().unregister(fixture.world());
        fixture.service().onChunkLoad(new ChunkLoadEvent(fixture.chunk(), true));

        verifyNoInteractions(fixture.tile());
    }

    private static void queue(Fixture fixture) {
        try (MockedStatic<BukkitWorldBinding> binding = mockStatic(BukkitWorldBinding.class)) {
            binding.when(() -> BukkitWorldBinding.world(fixture.irisWorld())).thenReturn(fixture.world());
            fixture.service().queueTiles(fixture.engine(), new ChunkTiles(-2, 3,
                    List.of(new PlacedTile(14, 161, 1, fixture.tile()))));
        }
    }

    private static Fixture fixture() {
        ObjectStudioSaveService service = new ObjectStudioSaveService();
        Engine engine = mock(Engine.class);
        EngineTarget target = mock(EngineTarget.class);
        IrisWorld irisWorld = mock(IrisWorld.class);
        when(engine.getTarget()).thenReturn(target);
        when(target.getWorld()).thenReturn(irisWorld);
        World world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(world.getName()).thenReturn("object-studio");
        Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        when(chunk.getX()).thenReturn(-2);
        when(chunk.getZ()).thenReturn(3);
        Block block = mock(Block.class);
        BlockData state = mock(BlockData.class);
        when(chunk.getBlock(14, 161, 1)).thenReturn(block);
        when(block.getBlockData()).thenReturn(state);
        TileData tile = mock(TileData.class);
        when(tile.isApplicable(state)).thenReturn(true);
        when(tile.toBukkitTry(block)).thenReturn(true);
        return new Fixture(service, engine, irisWorld, world, chunk, block, tile);
    }

    private record Fixture(ObjectStudioSaveService service, Engine engine, IrisWorld irisWorld, World world,
                           Chunk chunk, Block block, TileData tile) {
    }
}
