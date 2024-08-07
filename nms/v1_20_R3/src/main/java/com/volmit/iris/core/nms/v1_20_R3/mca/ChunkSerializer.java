/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.core.nms.v1_20_R3.mca;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.shorts.ShortList;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map.Entry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.ChunkAccess.TicksToSave;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.GenerationStep.Carving;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.slf4j.Logger;

import static net.minecraft.world.level.chunk.storage.ChunkSerializer.BLOCK_STATE_CODEC;

public class ChunkSerializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Method CODEC = null;

    static {
        for (Method method : net.minecraft.world.level.chunk.storage.ChunkSerializer.class.getDeclaredMethods()) {
            if (method.getReturnType().equals(Codec.class) && method.getParameterCount() == 1) {
                CODEC = method;
                CODEC.setAccessible(true);
                break;
            }
        }
    }

    public static CompoundTag write(ChunkAccess chunk, LevelHeightAccessor heightAccessor, Registry<Biome> biomeRegistry) {
        ChunkPos pos = chunk.getPos();
        CompoundTag data = NbtUtils.addCurrentDataVersion(new CompoundTag());
        data.putInt("xPos", pos.x);
        data.putInt("yPos", ((LevelHeightAccessor) chunk).getMinSection());
        data.putInt("zPos", pos.z);
        data.putLong("LastUpdate", 0L);
        data.putLong("InhabitedTime", chunk.getInhabitedTime());
        data.putString("Status", BuiltInRegistries.CHUNK_STATUS.getKey(chunk.getStatus()).toString());
        BlendingData blendingdata = chunk.getBlendingData();
        if (blendingdata != null) {
            DataResult<Tag> dataResult = BlendingData.CODEC.encodeStart(NbtOps.INSTANCE, blendingdata);
            dataResult.resultOrPartial(LOGGER::error).ifPresent(base -> data.put("blending_data", base));
        }

        BelowZeroRetrogen belowzeroretrogen = chunk.getBelowZeroRetrogen();
        if (belowzeroretrogen != null) {
            DataResult<Tag> dataResult = BelowZeroRetrogen.CODEC.encodeStart(NbtOps.INSTANCE, belowzeroretrogen);
            dataResult.resultOrPartial(LOGGER::error).ifPresent(base -> data.put("below_zero_retrogen", base));
        }

        UpgradeData upgradeData = chunk.getUpgradeData();
        if (!upgradeData.isEmpty()) {
            data.put("UpgradeData", upgradeData.write());
        }

        LevelChunkSection[] chunkSections = chunk.getSections();
        ListTag sections = new ListTag();
        Codec<PalettedContainerRO<Holder<Biome>>> codec;
        try {
            codec = (Codec<PalettedContainerRO<Holder<Biome>>>) CODEC.invoke(null, biomeRegistry);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }

        int minLightSection = heightAccessor.getMinSection() - 1;
        int maxLightSection = minLightSection + heightAccessor.getSectionsCount() + 2;
        for (int y = minLightSection; y < maxLightSection; y++) {
            int i = ((LevelHeightAccessor) chunk).getSectionIndexFromSectionY(y);
            if (i >= 0 && i < chunkSections.length) {
                CompoundTag section = new CompoundTag();
                LevelChunkSection chunkSection = chunkSections[i];
                DataResult<Tag> dataResult = BLOCK_STATE_CODEC.encodeStart(NbtOps.INSTANCE, chunkSection.getStates());
                section.put("block_states", dataResult.getOrThrow(false, LOGGER::error));
                dataResult = codec.encodeStart(NbtOps.INSTANCE, chunkSection.getBiomes());
                section.put("biomes", dataResult.getOrThrow(false, LOGGER::error));

                if (!section.isEmpty()) {
                    section.putByte("Y", (byte)y);
                    sections.add(section);
                }
            }
        }

        data.put("sections", sections);
        if (chunk.isLightCorrect()) {
            data.putBoolean("isLightOn", true);
        }

        ListTag blockEntities = new ListTag();
        for (BlockPos blockPos : chunk.getBlockEntitiesPos()) {
            CompoundTag blockEntityNbt = chunk.getBlockEntityNbtForSaving(blockPos);
            if (blockEntityNbt != null) {
                blockEntities.add(blockEntityNbt);
            }
        }
        data.put("block_entities", blockEntities);

        if (chunk instanceof ProtoChunk protoChunk) {
            ListTag entities = new ListTag();
            entities.addAll(protoChunk.getEntities());
            data.put("entities", entities);

            CompoundTag carvingMasks = new CompoundTag();
            for (Carving carving : Carving.values()) {
                CarvingMask carvingMask = protoChunk.getCarvingMask(carving);
                if (carvingMask != null) {
                    carvingMasks.putLongArray(carving.toString(), carvingMask.toArray());
                }
            }
            data.put("CarvingMasks", carvingMasks);
        }

        saveTicks(data, chunk.getTicksForSerialization());
        data.put("PostProcessing", packOffsets(chunk.getPostProcessing()));
        CompoundTag heightmaps = new CompoundTag();

        for (Entry<Types, Heightmap> entry : chunk.getHeightmaps()) {
            if (chunk.getStatus().heightmapsAfter().contains(entry.getKey())) {
                heightmaps.put(entry.getKey().getSerializationKey(), new LongArrayTag(entry.getValue().getRawData()));
            }
        }

        data.put("Heightmaps", heightmaps);

        CompoundTag structures = new CompoundTag();
        structures.put("starts", new CompoundTag());
        structures.put("References", new CompoundTag());
        data.put("structures", structures);
        if (!chunk.persistentDataContainer.isEmpty()) {
            data.put("ChunkBukkitValues", chunk.persistentDataContainer.toTagCompound());
        }

        return data;
    }

    private static void saveTicks(CompoundTag compoundTag, TicksToSave ticksToSave) {
        compoundTag.put("block_ticks", ticksToSave.blocks().save(0, block -> BuiltInRegistries.BLOCK.getKey(block).toString()));
        compoundTag.put("fluid_ticks", ticksToSave.fluids().save(0, fluid -> BuiltInRegistries.FLUID.getKey(fluid).toString()));
    }

    public static ListTag packOffsets(ShortList[] offsets) {
        ListTag tags = new ListTag();
        for (ShortList shorts : offsets) {
            ListTag listTag = new ListTag();
            if (shorts != null) {
                for (Short s : shorts) {
                    listTag.add(ShortTag.valueOf(s));
                }
            }
            tags.add(listTag);
        }

        return tags;
    }
}
