package art.arcane.iris.probe;

import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.modded.ModdedTileData;
import art.arcane.iris.world.storage.matter.TileWrapper;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedBlockState;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeTileData;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

final class HeadlessNativeTiles {
    private HeadlessNativeTiles() {
    }

    static List<NativeRegionTerrainWriter.TileInput> capture(Engine engine,
                                                             RealPackProbeSupport.GeneratedChunk chunk,
                                                             BlockPos origin) {
        List<NativeRegionTerrainWriter.TileInput> tiles = new ArrayList<>();
        engine.getMantle().getMantle().iterateChunk(origin.getX() >> 4, origin.getZ() >> 4,
                TileWrapper.class, (x, y, z, wrapper) -> tiles.add(prepare(new Placement(x, y, z, origin,
                        chunk.blocks().get(x, y, z), wrapper.getData()))));
        return List.copyOf(tiles);
    }

    static NativeRegionTerrainWriter.TileInput prepare(Placement placement) {
        if (!(placement.tile() instanceof ModdedTileData tile)
                || !(placement.state() instanceof ModdedBlockState original)) {
            throw new IllegalStateException("Native tile export requires native block and tile data");
        }
        NativeTileData data = tile.nativeData();
        BlockState state = data.adjustBlockState(original.handle());
        BlockPos position = placement.origin().offset(placement.x(), placement.y(), placement.z());
        if (!(state.getBlock() instanceof EntityBlock block)) {
            throw new IllegalStateException("Tile data has no native block entity at " + position);
        }
        BlockEntity entity = block.newBlockEntity(position, state);
        if (entity == null || !data.isApplicable(state, entity)) {
            throw new IllegalStateException("Tile data does not match native block at " + position);
        }
        try {
            CompoundTag payload = data.payload();
            if (payload == null) {
                throw new IllegalStateException("Tile data has no native payload at " + position);
            }
            return new NativeRegionTerrainWriter.TileInput(placement.x(), placement.y(), placement.z(),
                    ModdedBlockState.serialize(state), payload);
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot export native tile payload at " + position, failure);
        }
    }

    record Placement(int x, int y, int z, BlockPos origin, NativeBlockState state, TileData tile) {
    }
}
