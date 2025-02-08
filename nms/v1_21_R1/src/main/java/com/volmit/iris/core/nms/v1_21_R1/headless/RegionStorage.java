package com.volmit.iris.core.nms.v1_21_R1.headless;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.headless.IRegion;
import com.volmit.iris.core.nms.headless.IRegionStorage;
import com.volmit.iris.core.nms.headless.SerializableChunk;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.context.ChunkContext;
import com.volmit.iris.util.math.RNG;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.FileUtil;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_21_R1.CraftServer;
import org.bukkit.craftbukkit.v1_21_R1.block.CraftBiome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.minecraft.world.level.chunk.storage.ChunkSerializer.BLOCK_STATE_CODEC;
import static net.minecraft.world.level.chunk.storage.ChunkSerializer.packOffsets;

public class RegionStorage implements IRegionStorage, LevelHeightAccessor {
    private static final AtomicCache<RegistryAccess> CACHE = new AtomicCache<>();
    private final KMap<Long, Region> regions = new KMap<>();
    private final Path folder;

    private final Engine engine;
    private final KMap<String, Holder<Biome>> customBiomes = new KMap<>();
    private final KMap<org.bukkit.block.Biome, Holder<Biome>> minecraftBiomes;
    private final RNG biomeRng;
    private final @Getter int minBuildHeight;
    private final @Getter int height;

    private transient boolean closed = false;

    public RegionStorage(Engine engine) {
        this.engine = engine;
        this.folder = new File(engine.getWorld().worldFolder(), "region").toPath();
        this.biomeRng = new RNG(engine.getSeedManager().getBiome());

        this.minBuildHeight = engine.getDimension().getMinHeight();
        this.height = engine.getDimension().getMaxHeight() - minBuildHeight;

        AtomicInteger failed = new AtomicInteger();
        var dimKey = engine.getDimension().getLoadKey();
        for (var biome : engine.getAllBiomes()) {
            if (!biome.isCustom()) continue;
            for (var custom : biome.getCustomDerivitives()) {
                biomeHolder(dimKey, custom.getId()).ifPresentOrElse(holder -> customBiomes.put(custom.getId(), holder), () -> {
                    Iris.error("Failed to load custom biome " + dimKey + " " + custom.getId());
                    failed.incrementAndGet();
                });
            }
        }
        if (failed.get() > 0) {
            throw new IllegalStateException("Failed to load " + failed.get() + " custom biomes");
        }

        minecraftBiomes = new KMap<>(org.bukkit.Registry.BIOME.stream()
                .collect(Collectors.toMap(Function.identity(), CraftBiome::bukkitToMinecraftHolder)));
        minecraftBiomes.values().removeAll(customBiomes.values());
    }

    @Override
    public boolean exists(int x, int z) {
        try (IRegion region = getRegion(x, z, true)) {
            return region != null && region.exists(x, z);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public IRegion getRegion(int x, int z, boolean existingOnly) throws IOException {
        AtomicReference<IOException> exception = new AtomicReference<>();
        Region region = regions.computeIfAbsent(Cache.key(x, z), k -> {
            if (regions.size() >= 256) {
                regions.values().removeIf(Region::remove);
            }

            try {
                FileUtil.createDirectoriesSafe(this.folder);
                Path path = folder.resolve("r." + x + "." + z + ".mca");
                if (existingOnly && !Files.exists(path)) {
                    return null;
                } else {
                    return new Region(path, this.folder);
                }
            } catch (IOException e) {
                exception.set(e);
                return null;
            }
        });

        if (region == null) {
            if (exception.get() != null)
                throw exception.get();
            return null;
        }
        region.references++;
        return region;
    }

    @NotNull
    @Override
    public SerializableChunk createChunk(int x, int z) {
        ProtoChunk chunk = new ProtoChunk(new ChunkPos(x, z), UpgradeData.EMPTY, this, registryAccess().registryOrThrow(Registries.BIOME), null);
        chunk.setPersistedStatus(ChunkStatus.FULL);
        return new DirectTerrainChunk(chunk);
    }

    @Override
    public void fillBiomes(@NonNull SerializableChunk chunk, @Nullable ChunkContext ctx) {
        if (!(chunk instanceof DirectTerrainChunk tc))
            return;
        tc.getAccess().fillBiomesFromNoise((qX, qY, qZ, sampler) -> getNoiseBiome(engine, ctx, qX << 2, qY << 2, qZ << 2), null);
    }

    @Override
    public synchronized void close() {
        if (closed) return;

        while (!regions.isEmpty()) {
            regions.values().removeIf(Region::remove);
        }

        closed = true;
        customBiomes.clear();
        minecraftBiomes.clear();
    }

    private Holder<Biome> getNoiseBiome(Engine engine, ChunkContext ctx, int x, int y, int z) {
        int m = y - engine.getMinHeight();
        IrisBiome ib = ctx == null ? engine.getSurfaceBiome(x, z) : ctx.getBiome().get(x & 15, z & 15);
        if (ib.isCustom()) {
            return customBiomes.get(ib.getCustomBiome(biomeRng, x, m, z).getId());
        } else {
            return minecraftBiomes.get(ib.getSkyBiome(biomeRng, x, m, z));
        }
    }

    private static RegistryAccess registryAccess() {
        return CACHE.aquire(() -> ((CraftServer) Bukkit.getServer()).getServer().registryAccess());
    }

    private static Optional<Holder.Reference<Biome>> biomeHolder(String namespace, String path) {
        return registryAccess().registryOrThrow(Registries.BIOME).getHolder(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    static CompoundTag serialize(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        CompoundTag tag = NbtUtils.addCurrentDataVersion(new CompoundTag());
        tag.putInt("xPos", chunkPos.x);
        tag.putInt("yPos", chunk.getMinSection());
        tag.putInt("zPos", chunkPos.z);
        tag.putLong("LastUpdate", 0);
        tag.putLong("InhabitedTime", chunk.getInhabitedTime());
        tag.putString("Status", BuiltInRegistries.CHUNK_STATUS.getKey(chunk.getPersistedStatus()).toString());
        BlendingData blendingdata = chunk.getBlendingData();
        if (blendingdata != null) {
            DataResult<Tag> dataresult = BlendingData.CODEC.encodeStart(NbtOps.INSTANCE, blendingdata);
            dataresult.resultOrPartial(LogUtils.getLogger()::error).ifPresent((nbt) -> tag.put("blending_data", nbt));
        }

        BelowZeroRetrogen retrogen = chunk.getBelowZeroRetrogen();
        if (retrogen != null) {
            DataResult<Tag> dataresult = BelowZeroRetrogen.CODEC.encodeStart(NbtOps.INSTANCE, retrogen);
            dataresult.resultOrPartial(LogUtils.getLogger()::error).ifPresent((nbt) -> tag.put("below_zero_retrogen", nbt));
        }

        UpgradeData upgradeData = chunk.getUpgradeData();
        if (!upgradeData.isEmpty()) {
            tag.put("UpgradeData", upgradeData.write());
        }

        LevelChunkSection[] sections = chunk.getSections();
        ListTag sectionsTag = new ListTag();
        Registry<Biome> biomeRegistry = registryAccess().registryOrThrow(Registries.BIOME);
        Codec<PalettedContainerRO<Holder<Biome>>> codec = PalettedContainer.codecRO(biomeRegistry.asHolderIdMap(), biomeRegistry.holderByNameCodec(), PalettedContainer.Strategy.SECTION_BIOMES, biomeRegistry.getHolderOrThrow(Biomes.PLAINS));
        boolean flag = chunk.isLightCorrect();

        int minLightSection = chunk.getMinSection() - 1;
        int maxLightSection = minLightSection + chunk.getSectionsCount() + 2;
        for(int y = minLightSection; y < maxLightSection; ++y) {
            int j = chunk.getSectionIndexFromSectionY(y);
            if (j < 0 || j >= sections.length)
                continue;
            CompoundTag sectionTag = new CompoundTag();
            LevelChunkSection section = sections[j];
            sectionTag.put("block_states", BLOCK_STATE_CODEC.encodeStart(NbtOps.INSTANCE, section.getStates()).getOrThrow());
            sectionTag.put("biomes", codec.encodeStart(NbtOps.INSTANCE, section.getBiomes()).getOrThrow());

            if (!sectionTag.isEmpty()) {
                sectionTag.putByte("Y", (byte) y);
                sectionsTag.add(sectionTag);
            }
        }

        tag.put("sections", sectionsTag);
        if (flag) {
            tag.putBoolean("isLightOn", true);
        }

        ListTag blockEntities = new ListTag();
        for(BlockPos blockPos : chunk.getBlockEntitiesPos()) {
            CompoundTag entityNbt = chunk.getBlockEntityNbtForSaving(blockPos, registryAccess());
            if (entityNbt != null) {
                blockEntities.add(entityNbt);
            }
        }

        tag.put("block_entities", blockEntities);
        if (chunk.getPersistedStatus().getChunkType() == ChunkType.PROTOCHUNK) {
            ProtoChunk protochunk = (ProtoChunk)chunk;
            ListTag entities = new ListTag();
            entities.addAll(protochunk.getEntities());
            tag.put("entities", entities);
            CompoundTag carvingMasks = new CompoundTag();

            for(GenerationStep.Carving carving : GenerationStep.Carving.values()) {
                CarvingMask mask = protochunk.getCarvingMask(carving);
                if (mask != null) {
                    carvingMasks.putLongArray(carving.toString(), mask.toArray());
                }
            }

            tag.put("CarvingMasks", carvingMasks);
        }

        saveTicks(tag, chunk.getTicksForSerialization());
        tag.put("PostProcessing", packOffsets(chunk.getPostProcessing()));
        CompoundTag heightMaps = new CompoundTag();

        for(Map.Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
            if (chunk.getPersistedStatus().heightmapsAfter().contains(entry.getKey())) {
                heightMaps.put(entry.getKey().getSerializationKey(), new LongArrayTag(entry.getValue().getRawData()));
            }
        }

        tag.put("Heightmaps", heightMaps);

        CompoundTag structureData = new CompoundTag();
        structureData.put("starts", new CompoundTag());
        structureData.put("References", new CompoundTag());
        tag.put("structures", structureData);
        if (!chunk.persistentDataContainer.isEmpty()) {
            tag.put("ChunkBukkitValues", chunk.persistentDataContainer.toTagCompound());
        }

        return tag;
    }

    private static void saveTicks(CompoundTag tag, ChunkAccess.TicksToSave ticks) {
        tag.put("block_ticks", ticks.blocks().save(0, (block) -> BuiltInRegistries.BLOCK.getKey(block).toString()));
        tag.put("fluid_ticks", ticks.fluids().save(0, (fluid) -> BuiltInRegistries.FLUID.getKey(fluid).toString()));
    }
}
