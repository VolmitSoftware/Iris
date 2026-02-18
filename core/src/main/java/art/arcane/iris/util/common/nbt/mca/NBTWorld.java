/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.util.nbt.common.mca;

import art.arcane.iris.Iris;
import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.engine.data.cache.Cache;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.nbt.mca.MCAWorldStoreSupport;
import art.arcane.volmlib.util.nbt.mca.MCAWorldRuntimeSupport;
import art.arcane.volmlib.util.nbt.mca.NBTWorldSupport;
import art.arcane.iris.util.common.data.B;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.iris.util.common.parallel.HyperLock;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.util.Map;

public class NBTWorld {
    private static final BlockData AIR = B.get("AIR");
    private static final NBTWorldSupport.BlockStateCodec<BlockData> BLOCK_STATE_CODEC = NBTWorldSupport.blockStateCodec(
            blockStateString -> B.getOrNull(blockStateString, true),
            B::getAir,
            blockData -> blockData.getAsString(true),
            blockData -> {
                NamespacedKey key = blockData.getMaterial().getKey();
                return key.getNamespace() + ":" + key.getKey();
            }
    );
    private static final Map<Biome, Integer> biomeIds = computeBiomeIDs();
    private final HyperLock hyperLock = new HyperLock();
    private final MCAWorldStoreSupport<MCAFile> regionStore;
    private final MCAWorldRuntimeSupport<MCAFile, Chunk, Section> worldRuntime;

    public NBTWorld(File worldFolder) {
        this.regionStore = new MCAWorldStoreSupport<>(new MCAWorldStoreSupport.Config<>(
                NBTWorldSupport.keyCodec(Cache::key, Cache::keyX, Cache::keyZ),
                new MCAWorldStoreSupport.GridLock() {
                    @Override
                    public <T> T withResult(int x, int z, java.util.function.Supplier<T> supplier) {
                        return hyperLock.withResult(x, z, supplier::get);
                    }
                },
                MCAFile::new,
                NBTWorldSupport.regionFileResolver(worldFolder, "region"),
                MCAUtil::write,
                NBTWorldSupport.logger(
                        Iris::info,
                        Iris::debug,
                        (message, error) -> {
                            Iris.error(message);
                            if (error != null) {
                                error.printStackTrace();
                            }
                        }
                ),
                M::ms,
                60000L,
                "Iris MCA Writer"
        ));

        this.worldRuntime = new MCAWorldRuntimeSupport<>(
                this::getMCA,
                new MCAWorldRuntimeSupport.ChunkAccess<>() {
                    @Override
                    public Chunk getChunk(MCAFile mca, int chunkX, int chunkZ) {
                        return mca.getChunk(chunkX, chunkZ);
                    }

                    @Override
                    public void setChunk(MCAFile mca, int chunkX, int chunkZ, Chunk chunk) {
                        mca.setChunk(chunkX, chunkZ, chunk);
                    }

                    @Override
                    public Chunk createChunk() {
                        return Chunk.newChunk();
                    }
                },
                new MCAWorldRuntimeSupport.SectionAccess<>() {
                    @Override
                    public Section getSection(Chunk chunk, int sectionY) {
                        return chunk.getSection(sectionY);
                    }

                    @Override
                    public void setSection(Chunk chunk, int sectionY, Section section) {
                        chunk.setSection(sectionY, section);
                    }

                    @Override
                    public Section createSection() {
                        return Section.newSection();
                    }
                }
        );
    }

    public static BlockData getBlockData(CompoundTag tag) {
        return BLOCK_STATE_CODEC.decode(tag);
    }

    public static CompoundTag getCompound(BlockData bd) {
        return BLOCK_STATE_CODEC.encode(bd);
    }

    private static Map<Biome, Integer> computeBiomeIDs() {
        Map<Biome, Integer> biomeIds = new KMap<>();

        for (Biome biome : Biome.values()) {
            if (!biome.name().equals("CUSTOM")) {
                biomeIds.put(biome, INMS.get().getBiomeId(biome));
            }
        }

        return biomeIds;
    }

    public void close() {
        regionStore.close();
    }

    public void flushNow() {
        regionStore.flushNow();
    }

    public void queueSaveUnload(int x, int z) {
        regionStore.queueSaveUnload(x, z);
    }

    public void doSaveUnload(int x, int z) {
        regionStore.doSaveUnload(x, z);
    }

    public void save() {
        regionStore.save();
    }

    public void queueSave() {

    }

    public synchronized void unloadRegion(int x, int z) {
        regionStore.unloadRegion(x, z);
    }

    public void saveRegion(int x, int z) {
        regionStore.saveRegion(x, z);
    }

    public void saveRegion(int x, int z, MCAFile mca) {
        regionStore.saveRegion(x, z, mca);
    }

    public boolean shouldUnload(int x, int z) {
        return regionStore.shouldUnload(x, z);
    }

    public File getRegionFile(int x, int z) {
        return regionStore.getRegionFile(x, z);
    }

    public BlockData getBlockData(int x, int y, int z) {
        try {
            CompoundTag tag = worldRuntime.getBlockStateTag(x, y, z);

            if (tag == null) {
                return AIR;
            }

            return getBlockData(tag);
        } catch (Throwable e) {
            Iris.reportError(e);

        }
        return AIR;
    }

    public void setBlockData(int x, int y, int z, BlockData data) {
        worldRuntime.setBlockStateTag(x, y, z, getCompound(data), false);
    }

    public int getBiomeId(Biome b) {
        return biomeIds.get(b);
    }

    public void setBiome(int x, int y, int z, Biome biome) {
        worldRuntime.setBiomeId(x, y, z, biomeIds.get(biome));
    }

    public Section getChunkSection(int x, int y, int z) {
        return worldRuntime.getChunkSection(x, y, z);
    }

    public Chunk getChunk(int x, int z) {
        return worldRuntime.getChunk(x, z);
    }

    public Chunk getChunk(MCAFile mca, int x, int z) {
        return worldRuntime.getChunk(mca, x, z);
    }

    public Chunk getNewChunk(MCAFile mca, int x, int z) {
        return worldRuntime.getNewChunk(mca, x, z);
    }

    public long getIdleDuration(int x, int z) {
        return regionStore.getIdleDuration(x, z);
    }

    public MCAFile getMCA(int x, int z) {
        return regionStore.getMCA(x, z);
    }

    public MCAFile getMCAOrNull(int x, int z) {
        return regionStore.getMCAOrNull(x, z);
    }

    public int size() {
        return regionStore.size();
    }

    public boolean isLoaded(int x, int z) {
        return regionStore.isLoaded(x, z);
    }
}
