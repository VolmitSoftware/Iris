package com.volmit.iris.core.nms.v1_21_R3.headless;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.BiomeBaseInjector;
import com.volmit.iris.core.nms.headless.SerializableChunk;
import com.volmit.iris.util.data.IrisCustomData;
import com.volmit.iris.util.math.Position2;
import lombok.Data;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_21_R3.block.CraftBiome;
import org.bukkit.craftbukkit.v1_21_R3.block.CraftBlockType;
import org.bukkit.craftbukkit.v1_21_R3.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v1_21_R3.util.CraftMagicNumbers;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.material.MaterialData;
import org.jetbrains.annotations.NotNull;

@Data
public final class DirectTerrainChunk implements SerializableChunk {
    private final ProtoChunk access;
    private final int minHeight, maxHeight;

    public DirectTerrainChunk(ProtoChunk access) {
        this.access = access;
        this.minHeight = access.getMinY();
        this.maxHeight = access.getMaxY();
    }

    @Override
    public BiomeBaseInjector getBiomeBaseInjector() {
        return null;
    }

    @NotNull
    @Override
    public Biome getBiome(int x, int z) {
        return getBiome(x, 0, z);
    }

    @NotNull
    @Override
    public Biome getBiome(int x, int y, int z) {
        if (y < minHeight || y > maxHeight) return Biome.PLAINS;
        return CraftBiome.minecraftHolderToBukkit(access.getNoiseBiome(x >> 2, y >> 2, z >> 2));
    }

    @Override
    public void setBiome(int x, int z, Biome bio) {
        for (int y = minHeight; y < maxHeight; y += 4) {
            setBiome(x, y, z, bio);
        }
    }

    @Override
    public void setBiome(int x, int y, int z, Biome bio) {
        if (y < minHeight || y > maxHeight) return;
        access.setBiome(x & 15, y, z & 15, CraftBiome.bukkitToMinecraftHolder(bio));
    }

    public void setBlock(int x, int y, int z, Material material) {
        this.setBlock(x, y, z, material.createBlockData());
    }

    public void setBlock(int x, int y, int z, MaterialData material) {
        this.setBlock(x, y, z, CraftMagicNumbers.getBlock(material));
    }

    @Override
    public void setBlock(int x, int y, int z, BlockData blockData) {
        if (blockData == null) {
            Iris.error("NULL BD");
        }
        if (blockData instanceof IrisCustomData data)
            blockData = data.getBase();
        if (!(blockData instanceof CraftBlockData craftBlockData))
            throw new IllegalArgumentException("Expected CraftBlockData, got " + blockData.getClass().getSimpleName() + " instead");
        access.setBlockState(new BlockPos(x & 15, y, z & 15), craftBlockData.getState(), false);
    }

    public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, Material material) {
        this.setRegion(xMin, yMin, zMin, xMax, yMax, zMax, material.createBlockData());
    }

    public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, MaterialData material) {
        this.setRegion(xMin, yMin, zMin, xMax, yMax, zMax, CraftMagicNumbers.getBlock(material));
    }

    public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, BlockData blockData) {
        this.setRegion(xMin, yMin, zMin, xMax, yMax, zMax, ((CraftBlockData) blockData).getState());
    }

    public Material getType(int x, int y, int z) {
        return CraftBlockType.minecraftToBukkit(this.getTypeId(x, y, z).getBlock());
    }

    public MaterialData getTypeAndData(int x, int y, int z) {
        return CraftMagicNumbers.getMaterial(this.getTypeId(x, y, z));
    }

    public BlockData getBlockData(int x, int y, int z) {
        return CraftBlockData.fromData(this.getTypeId(x, y, z));
    }

    @Override
    public ChunkGenerator.ChunkData getRaw() {
        return null;
    }

    @Override
    public void setRaw(ChunkGenerator.ChunkData data) {

    }

    @Override
    public void inject(ChunkGenerator.BiomeGrid biome) {

    }

    public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, BlockState type) {
        if (xMin > 15 || yMin >= this.maxHeight || zMin > 15)
            return;

        if (xMin < 0) {
            xMin = 0;
        }

        if (yMin < this.minHeight) {
            yMin = this.minHeight;
        }

        if (zMin < 0) {
            zMin = 0;
        }

        if (xMax > 16) {
            xMax = 16;
        }

        if (yMax > this.maxHeight) {
            yMax = this.maxHeight;
        }

        if (zMax > 16) {
            zMax = 16;
        }

        if (xMin >= xMax || yMin >= yMax || zMin >= zMax)
            return;

        for (int y = yMin; y < yMax; ++y) {
            for (int x = xMin; x < xMax; ++x) {
                for (int z = zMin; z < zMax; ++z) {
                    this.setBlock(x, y, z, type);
                }
            }
        }

    }

    public BlockState getTypeId(int x, int y, int z) {
        if (x != (x & 15) || y < this.minHeight || y >= this.maxHeight || z != (z & 15))
            return Blocks.AIR.defaultBlockState();
        return access.getBlockState(new BlockPos(access.getPos().getMinBlockX() + x, y, access.getPos().getMinBlockZ() + z));
    }

    public byte getData(int x, int y, int z) {
        return CraftMagicNumbers.toLegacyData(this.getTypeId(x, y, z));
    }

    private void setBlock(int x, int y, int z, BlockState type) {
        if (x != (x & 15) || y < this.minHeight || y >= this.maxHeight || z != (z & 15))
            return;
        BlockPos blockPosition = new BlockPos(access.getPos().getMinBlockX() + x, y, access.getPos().getMinBlockZ() + z);
        BlockState oldBlockData = access.setBlockState(blockPosition, type, false);
        if (type.hasBlockEntity()) {
            BlockEntity tileEntity = ((EntityBlock) type.getBlock()).newBlockEntity(blockPosition, type);
            if (tileEntity == null) {
                access.removeBlockEntity(blockPosition);
            } else {
                access.setBlockEntity(tileEntity);
            }
        } else if (oldBlockData != null && oldBlockData.hasBlockEntity()) {
            access.removeBlockEntity(blockPosition);
        }

    }

    @Override
    public Position2 getPos() {
        return new Position2(access.getPos().x, access.getPos().z);
    }

    @Override
    public Object serialize() {
        return RegionStorage.serialize(access);
    }

    @Override
    public void mark() {
        access.setPersistedStatus(ChunkStatus.FULL);
    }
}